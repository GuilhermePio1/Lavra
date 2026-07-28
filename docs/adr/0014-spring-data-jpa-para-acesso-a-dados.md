# ADR-0014: Spring Data JPA para acesso a dados no core-api

- **Status:** aceito
- **Data:** 2026-07-28
- **Substitui / Substituído por:** —

## Contexto

O ADR-0010 já definiu que o schema é propriedade das migrations Flyway em SQL puro e que nada gera DDL a partir do código, mas deixou em aberto *como* o core-api lê e escreve nesse schema. O perfil de acesso é modesto e conhecido: CRUD sobre agregados pequenos (`users`, `shows`, `episodes`), inserções append-only no `usage_ledger` e no `episode_state_transitions`, e uma única consulta agregada de verdade — a soma de minutos do período corrente, que alimenta o `GET /me` e o enforcement de cota. Não há relatório analítico, junção larga nem volume que justifique otimização. A escolha precisa acontecer junto com o ADR-0013 porque define se existe mapeamento entre modelo de domínio e linha de tabela, e quem escreve esse mapeamento.

## Decisão

Usaremos **Spring Data JPA com Hibernate** para todo o acesso a dados do core-api, com as entidades e repositories confinados ao subpacote `persistence` de cada feature e o schema validado contra as migrations no boot (`ddl-auto: validate`, conforme ADR-0010).

## Alternativas consideradas

- **`JdbcClient`** (a API fluente do Spring 6.1+) — é a que melhor casa com a filosofia do ADR-0010, mantendo o SQL visível e eliminando de saída as armadilhas de lazy loading e N+1. Foi rejeitada porque o ganho dela cresce com a complexidade das consultas, e aqui essa complexidade não existe: pagaríamos mapeamento e paginação escritos à mão em todos os agregados para resolver bem um problema que temos em uma única query.
- **jOOQ** — validaria as consultas contra o schema em tempo de compilação, o que é genuinamente atraente num projeto onde as migrations são a fonte da verdade. Rejeitada pelo custo de introduzir uma etapa de codegen no build Gradle e uma terceira ferramenta de dados no serviço, sem que exista o tipo de SQL complexo que justifica jOOQ. É a alternativa que voltaria à mesa se o produto ganhasse consultas analíticas.
- **Hibernate puro, sem Spring Data** — evitaria a mágica dos repositories derivados de nome de método, mas troca isso por escrever à mão o que o Spring Data já entrega, sem qualquer benefício em troca.

## Consequências

- Repositories declarativos expressam a regra de posse diretamente na assinatura (`findByIdAndOwnerId`), o que torna difícil escrever por acidente a consulta que vazaria o recurso de outro usuário — a defesa fica no lugar mais barato possível.
- Integra-se sem atrito ao restante da stack: transações declarativas, auditoria de timestamps e Testcontainers funcionam com a configuração padrão do Spring Boot 4.
- O custo real: Hibernate esconde o SQL emitido, e lazy loading, `open-in-view` e N+1 são problemas de produção que não aparecem em teste unitário. Mitigação: `open-in-view: false` desde o primeiro commit e fetch explícito onde houver associação.
- A agregação do ledger fica mais legível em SQL do que em JPQL. Consultas desse tipo usarão query nativa em vez de forçar o ORM — e isso é escolha consciente, não exceção a ser escondida.
- Risco assumido: `ddl-auto: validate` confere tabelas, colunas e tipos, mas não índices, constraints de verificação nem defaults. O modelo Java pode divergir das migrations exatamente nos detalhes que o ADR-0010 considera documentação executável, sem que o boot falhe. Cabe aos testes de integração cobrir as invariantes que dependem de constraint no banco — a unicidade do débito por episódio e período, em primeiro lugar.
