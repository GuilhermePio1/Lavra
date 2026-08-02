# ADR-0012: Autenticação no desenvolvimento local

- **Status:** aceito
- **Data:** 2026-07-28
- **Substitui / Substituído por:** — (complementa o ADR-0009)

## Contexto

O ADR-0009 escolheu o Entra External ID como IdP, mas deixou o desenvolvimento local em aberto: "tenant de teste, ou perfil `dev` com token mockado". A questão vira bloqueante agora, porque a primeira fatia do Core API (`GET /me` + CRUD de `/shows`) já exige JWT válido, posse estrita e papel `ADMIN` — não há como escrevê-la sem decidir de onde vem o token em desenvolvimento. Três restrições pesam: o repositório é **portfólio**, então quem clona precisa conseguir rodar o sistema sem provisionar um tenant Azure; os testes de integração precisam rodar em CI **sem segredos** e offline; e um perfil que desliga a autenticação é exatamente a classe de bug que falha *aberto* em produção. O compose local já é um conjunto de emuladores (Azurite para o Blob, emulador do Service Bus) — um emulador de OIDC é coerente com o padrão estabelecido.

## Decisão

Usaremos o **`navikt/mock-oauth2-server` como emulador de OIDC no `docker-compose.dev.yml`**, e o Core API rodará **a mesma configuração de resource server em todos os ambientes**, variando apenas `issuer-uri`/`jwk-set-uri` por perfil. Nenhum `JwtDecoder` alternativo, nenhum `permitAll` condicional, nenhum bypass de autenticação existirá no código.

Detalhes:

- Container `ghcr.io/navikt/mock-oauth2-server:5.0.2` na porta `8080`, com `issuerId` `lavra` — descoberta em `/lavra/.well-known/openid-configuration`, JWKS em `/lavra/jwks`.
- Os tokens emitidos carregam claims **no formato do Entra** (`oid`, `preferred_username`, `name`, `roles`), configurados via `JSON_CONFIG`/`tokenCallbacks`. Assim o mapeamento de claims do provisionamento JIT é exercitado de verdade, e não contra um token de formato próprio.
- `interactiveLogin: true` permite escolher o `sub` na hora do login: trocar de usuário no browser é o que torna testável a regra de posse estrita (**404** para recurso de outro usuário) e o papel `ADMIN`.
- Testes: `spring-security-test` (post-processors `jwt()`) nos slices de MVC; o emulador via Testcontainers nos testes de integração ponta a ponta.
- Um **tenant Entra de teste continua necessário** — e apenas — para o trabalho de login do frontend com MSAL, que é específico do Entra e não fala com o emulador.

## Alternativas consideradas

- **Tenant Entra de teste como único caminho** — fidelidade máxima, mas exige uma conta Azure e um tenant provisionado só para rodar o projeto, o que arruína a experiência de quem clona o repositório; impede build offline e leva credenciais para o CI.
- **Perfil `dev` com `JwtDecoder` mockado ou `permitAll`** — zero infraestrutura, e era a saída prevista no ADR-0009. Rejeitada: coloca no código o único caminho que não podemos ter — um interruptor que desliga a autenticação, ativável por uma variável de ambiente errada. Falha aberto, e nenhuma quantidade de comentário "não use em produção" conserta isso.
- **Keycloak em container** — IdP real e completo, mas pesado demais (startup lento, realm para versionar e manter) para a função de emissor de tokens de teste; o ADR-0009 já rejeitou operar Keycloak.
- **Tokens auto-assinados com chave de teste, gerados dentro dos testes** — resolve os testes, mas não resolve *rodar a aplicação* localmente nem o fluxo de login pelo browser, que é onde a maior parte do desenvolvimento acontece.

## Consequências

O caminho de entrada fica trivial: `dev-up.ps1` seguido de `bootRun` dá um sistema autenticado e funcional sem nenhuma conta Azure, e o CI valida autenticação real sem segredo algum. Como a cadeia de filtros do Spring Security é idêntica em dev e em produção, um `issuer-uri` mal configurado faz o token ser **rejeitado**, não aceito — o modo de falha certo. Em troca, ganhamos mais um container no compose e uma fidelidade imperfeita: os tokens do emulador não são os do Entra, e divergências de claims (`tid`, formato de `roles`, `aud`) só aparecerão no primeiro deploy contra o tenant real — o risco é proporcional à disciplina de manter o `JSON_CONFIG` alinhado ao que o Entra de fato emite. Há também uma pegadinha conhecida de configuração: o issuer precisa resolver com o mesmo hostname dentro do container e no browser do host (daí `hostname: host.docker.internal`), detalhe que costuma custar a primeira meia hora de quem monta o ambiente.

> **Correções (2026-07-28), após a primeira implementação exercitar o emulador de verdade:**
>
> - O container escuta em **8081**, não em 8080 como dizia o parágrafo acima — 8080 é a porta do próprio Core API, e as duas não podem coincidir.
> - O `requestMapping` do `JSON_CONFIG` precisa casar em **`requestParam: "subject"`**. A versão inicial casava em `scope`, o que parece equivalente e não é: a troca do *authorization code* por token não carrega o parâmetro `scope`, então o mapping nunca disparava, o token saía sem `aud` e o Core API o rejeitava com 401 — justamente pelo fluxo de login interativo que este ADR escolheu. Casar em `subject` é também o que faz o placeholder `${subject}` ser resolvido; sem isso o claim `oid` chegava com o literal `${subject}` e **todos os usuários de desenvolvimento colapsavam em uma única conta**, anulando o propósito de poder trocar de usuário para exercitar posse estrita.
> - O claim `roles` **não** deve constar do mapping: claims definidos pelo mapping não podem ser sobrescritos pela tela de login, e é a ausência dele que permite conceder `ADMIN` a si mesmo digitando `{"roles": ["ADMIN"]}` no login.
>
> A lição vale além do detalhe: a fidelidade do emulador só se confirma exercitando o fluxo real ponta a ponta. Os três erros passariam despercebidos por qualquer teste que injetasse o token direto (como os `jwt()` post-processors), porque nenhum deles toca a emissão. Por fim, o fluxo de login do frontend permanece dependente de um tenant Entra de teste — o emulador cobre o Core API, não o MSAL.

> **Correção (2026-07-29), sobre o próprio parágrafo de Consequências acima:** a pegadinha de hostname citada ali morde mesmo depois de resolvida no compose. O `mock-oauth2-server` grava o claim `iss` a partir do `Host` header da requisição que pediu o token, não do `hostname` fixo do container — então abrir `http://localhost:8081/lavra/debugger` no browser (o caminho mais natural para "pegar um token na mão") produz um token com `iss=http://localhost:8081/lavra`, que o Core API rejeita, porque seu `issuer-uri` é `http://host.docker.internal:8081/lavra`. O 401 resultante não diz por quê: `ApiErrorAuthenticationEntryPoint` devolve a mesma mensagem genérica para qualquer `AuthenticationException`, então um issuer errado parece indistinguível de um token ausente. A correção é usar sempre `http://host.docker.internal:8081/lavra/debugger` para pegar o token manualmente — documentado agora no comentário do `docker-compose.dev.yml`.

> **Correção (2026-08-02), que substitui a nota de 2026-07-29 acima:** o issuer local passa a ser `http://localhost:8081/lavra`, e o `hostname: host.docker.internal` saiu do compose. O diagnóstico anterior estava certo sobre o mecanismo (`iss` vem do header `Host`) e errado sobre o remédio: `host.docker.internal` só resolve no host porque o Docker Desktop escreve uma linha no `hosts` do Windows apontando para o **IP de LAN da máquina**. Quando o DHCP troca o lease, a linha fica velha, o nome passa a apontar para outro aparelho da rede e o debugger morre em timeout — sem nada no projeto ter mudado. Trocar o hostname por um IP fixo no `hosts` resolveria, mas é configuração manual por máquina, e este repositório é portfólio: quem clona tropeça no mesmo buraco.
>
> A razão original do nome era fazer o issuer resolver igual dentro da rede do compose e no browser do host. Só que não há `Dockerfile` no repositório e o compose sobe apenas infraestrutura — o core-api roda no host, e **nada dentro da rede do compose valida token**. O nome estava pagando um custo real por um cenário que não existe. `localhost` não depende de DNS, de `hosts` nem de lease, e o `Host` header continua garantindo que o `iss` do token bata com o `issuer-uri`.
>
> O gatilho para revisitar está nomeado: no dia em que o core-api virar container **neste** compose, `localhost:8081` lá dentro é o próprio container. Não se conserta com variável de ambiente — o token continuará vindo do browser com `iss=http://localhost:8081/lavra`, então será preciso manter esse issuer e apontar um `jwk-set-uri` separado para o nome do serviço na rede.
>
> Vale registrar o que denunciou o problema: o `dev-up.ps1` já mandava pegar o token em `localhost:8081/lavra/debugger` desde sempre, contradizendo em silêncio o aviso em maiúsculas do compose. Documentação que se contradiz entre dois arquivos é sintoma de decisão que ninguém consegue seguir — e aqui o script estava do lado certo.
