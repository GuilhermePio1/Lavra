# ADR-0006: Azure Service Bus para mensageria entre serviços

- **Status:** aceito
- **Data:** 2026-07-28

## Contexto

O pipeline é assíncrono por natureza: processar um episódio leva minutos e não pode bloquear a API. Na proposta de monolito, o JobRunr (fila sobre Postgres) resolveria. Com a arquitetura poliglota (ADR-0001), a comunicação passa a cruzar linguagens (Java ↔ Python), e o JobRunr — que é Java-only — deixa de servir como mecanismo único.

## Decisão

**Azure Service Bus** (tier Basic/Standard) é o único mecanismo assíncrono do sistema:

- Filas por evento: `episode-uploaded` (core → worker), `media-processed` e `processing-failed` (worker → core).
- Mensagens seguem os JSON Schemas de `contracts/events/` (contracts-first, versionadas).
- Retry/DLQ nativos do Service Bus para falhas de consumo.
- Desenvolvimento local usa o emulador oficial do Service Bus (`infra/docker-compose.dev.yml`).

## Alternativas consideradas

- **JobRunr** — excelente para jobs Java-internos, mas não atravessa a fronteira Java↔Python; manteria a necessidade de um segundo mecanismo.
- **RabbitMQ/Kafka auto-hospedado** — mais uma peça para operar; Kafka é dimensionado para um problema que não temos.
- **Polling em tabela do Postgres** — simples, mas sem DLQ/lock/retry prontos e sem valor de portfólio.

## Consequências

- Um único mecanismo assíncrono, gerenciado, com DLQ e retry — e história de portfólio (mensageria cross-language com tracing distribuído).
- Custo: Service Bus não tem free tier permanente; tier Basic (~centavos/milhão de operações) mantém o custo desprezível no volume do projeto.
- Dependência de emulador para dev local (adiciona contêineres ao compose).
