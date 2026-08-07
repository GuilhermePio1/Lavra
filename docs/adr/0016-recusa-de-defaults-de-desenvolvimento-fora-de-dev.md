# ADR-0016: Recusa de defaults de desenvolvimento fora do ambiente de desenvolvimento

- **Status:** aceito
- **Data:** 2026-08-06
- **Substitui / Substituído por:** —

## Contexto

O `application.yml` dá a cada propriedade de infraestrutura um default que espelha o `infra/docker-compose.dev.yml`, para que o serviço rode localmente sem nenhum setup — uma conveniência deliberada. O preço dela é um modo de falha silencioso: uma variável de ambiente com nome errado no Container App não é erro, é override ausente, e o serviço sobe perfeitamente saudável apontando para `127.0.0.1`. O `/health` fica verde e nada reclama até um usuário real subir áudio.

Bean validation não pega isso. A validação adicionada em `BlobStorageProperties` e `MessagingProperties` cobre valor vazio (a forma que um secret mal referenciado assume) e nome de container que o Azure recusaria, mas aqui o valor não está ausente nem malformado: está presente e errado *para onde está rodando*. Detectar isso exige uma segunda opinião sobre qual é o ambiente, e o projeto já tem uma — o ADR-0012 fez do `issuer-uri` a única coisa que muda entre local e Azure, sendo o local o emulador em `localhost`.

## Decisão

O core-api **recusa subir** quando o issuer OIDC não é local mas alguma connection string ainda aponta para a infraestrutura local (Azurite, emulador do Service Bus). A regra vive em `shared/config/DevelopmentDefaultsGuard` como função pura sobre três strings, exposta como bean cujo construtor lança — o contexto morre com a mensagem nomeando cada propriedade esquecida e a variável de ambiente que a corrige.

## Alternativas consideradas

- **Remover os defaults do `application.yml`** — resolveria na raiz: sem default, ausência é ausência e o binding falha sozinho. Recusada porque destruiria a propriedade que o arquivo declara explicitamente ter ("defaults match `infra/docker-compose.dev.yml`"), obrigando todo desenvolvedor a montar um `.env` antes do primeiro `gradle bootRun`. O custo recai sobre o caso comum para proteger o raro.
- **Perfil Spring (`prod`) como discriminador** — seria o mecanismo idiomático, mas depende de `SPRING_PROFILES_ACTIVE` estar setado, que é exatamente a classe de variável esquecida contra a qual isto existe: um guard que falha junto com o que deveria detectar não guarda nada. O issuer, ao contrário, já é obrigatório para o resource server funcionar.
- **Só `@NotBlank` nas properties** — foi o ponto de partida da discussão. Cobre o secret vazio, e é barato, mas daria sensação de blindagem justamente contra o cenário que não cobre: o default de dev aplicado em produção passa por `@NotBlank` sem tocar em nada. Ficou, mas como complemento e não como resposta.
- **Uma flag para desligar o guard** — permitiria rodar contra o Entra real com storage local. Recusada porque esse arranjo não é descrito por nenhum ADR, e um guard contra esquecimento vale pouco se o esquecimento pode ser declarado aceitável. Se o cenário híbrido aparecer, a saída é adicionar a escotilha conscientemente, não afrouxar a regra agora.

## Consequências

- As duas metades se cobrem. Esquecer só a variável de storage deixa o issuer real, e o guard dispara. Esquecer todas deixa o issuer apontando para um `localhost` que não existe, e o resource server falha ao buscar o metadata na subida. Não há combinação em que o serviço suba errado e calado.
- O acoplamento entre configuração de identidade e de storage é real e deliberado, e é a parte mais discutível deste ADR: `shared/config` lê uma propriedade do Spring Security para decidir sobre Azure. Está isolado num único arquivo, com o raciocínio no Javadoc, mas é uma dependência que não existiria se houvesse um discriminador de ambiente melhor.
- Se algum dia o emulador de OIDC rodar fora de `localhost` — num host nomeado dentro de uma rede de CI, por exemplo — o guard passa a recusar um ambiente legítimo de desenvolvimento. A correção é ampliar o conjunto de hosts locais, num lugar só, mas é uma manutenção que fica devendo.
- A regra é função pura de três strings, então cada combinação é testável sem contexto Spring; dois testes adicionais provam que ela está de fato ligada à subida, e não apenas calculando uma razão que ninguém consome.
- Fica um efeito colateral bom: a mensagem de recusa nomeia todas as propriedades esquecidas de uma vez, então um deploy quebrado se conserta num restart, não em três.
