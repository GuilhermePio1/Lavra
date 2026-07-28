# ADR-0010: Flyway para migrations do Postgres

- **Status:** aceito
- **Data:** 2026-07-28

## Contexto

O Core API é dono do schema relacional do lavra (usuários, shows, episódios, histórico de transições, ledger de uso) e precisa evoluí-lo de forma versionada e reproduzível — no Postgres local do `docker-compose.dev.yml`, nos testes com Testcontainers e no PostgreSQL Flexible Server em produção. O banco é **exclusivamente Postgres**, sem qualquer requisito de portabilidade entre SGBDs. O projeto é tocado por um desenvolvedor só, com deploy contínuo em Container Apps: o que importa é que o schema em produção seja sempre derivável do repositório, não que exista uma máquina de desfazer.

## Decisão

Usaremos **Flyway com migrations em SQL puro** (`core-api/src/main/resources/db/migration/V<n>__<descricao>.sql`) como única forma de alterar o schema, com política **forward-only**: corrigir um erro significa uma nova migration, nunca editar uma já aplicada.

O ORM/mapper nunca gera DDL — o schema é validado contra as migrations na subida da aplicação (`ddl-auto: validate`), de modo que divergência entre modelo e banco falha o boot em vez de aparecer em runtime.

## Alternativas consideradas

- **Liquibase (changelog YAML/XML)** — rollback nativo e precondições são reais, mas o principal argumento a favor dele (abstração entre bancos) não se aplica aqui, e ele cobra uma DSL intermediária: quem lê o repositório para entender o schema passa a ler YAML em vez de SQL. Numa base Postgres-only, isso é ceremônia sem contrapartida.
- **Hibernate `ddl-auto: update`** — zero configuração e completamente inadequado para produção: gera DDL implícito, não versiona nada, não remove colunas e torna impossível auditar o que rodou no banco.
- **Scripts SQL versionados na mão** — resolveria o versionamento, mas reimplementaria mal o que Flyway já faz (tabela de controle, checksum, ordenação, execução transacional).

## Consequências

- O SQL das migrations vira a documentação executável do schema: quem abre `db/migration/` entende o modelo de dados sem abrir o código Java.
- Auto-configuração nativa no Spring Boot — o mesmo mecanismo roda no boot local, nos testes com Testcontainers e no deploy, eliminando a classe de bug "funciona na minha máquina porque meu banco está diferente".
- Perdemos rollback automático (a edição community não o oferece): reverter exige escrever a migration inversa. A política forward-only assume esse custo conscientemente — é a prática correta para um serviço com deploy contínuo, onde reverter o schema com tráfego em voo é mais arriscado do que avançar.
- Migrations destrutivas (drop/rename de coluna com dados) passam a exigir a disciplina de expand-and-contract em dois deploys. Risco assumido: ainda não há dados de produção, então o custo dessa disciplina só aparece depois — e é quando ela passa a valer.
