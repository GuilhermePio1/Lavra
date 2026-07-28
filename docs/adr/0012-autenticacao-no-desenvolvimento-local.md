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

O caminho de entrada fica trivial: `dev-up.ps1` seguido de `bootRun` dá um sistema autenticado e funcional sem nenhuma conta Azure, e o CI valida autenticação real sem segredo algum. Como a cadeia de filtros do Spring Security é idêntica em dev e em produção, um `issuer-uri` mal configurado faz o token ser **rejeitado**, não aceito — o modo de falha certo. Em troca, ganhamos mais um container no compose e uma fidelidade imperfeita: os tokens do emulador não são os do Entra, e divergências de claims (`tid`, formato de `roles`, `aud`) só aparecerão no primeiro deploy contra o tenant real — o risco é proporcional à disciplina de manter o `JSON_CONFIG` alinhado ao que o Entra de fato emite. Há também uma pegadinha conhecida de configuração: o issuer precisa resolver com o mesmo hostname dentro do container e no browser do host (daí `hostname: host.docker.internal`), detalhe que costuma custar a primeira meia hora de quem monta o ambiente. Por fim, o fluxo de login do frontend permanece dependente de um tenant Entra de teste — o emulador cobre o Core API, não o MSAL.
