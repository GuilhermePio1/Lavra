# CLAUDE.md — contexto do projeto lavra

## O que é

Automatizador de pós-produção de podcasts: o criador sobe o áudio bruto; o sistema limpa o áudio, transcreve, gera títulos/descrição/capítulos/tags com IA e entrega tudo para revisão humana antes do export. Produto **e** portfólio — qualidade de arquitetura e documentação importam tanto quanto a feature.

## Arquitetura (resumo)

Monorepo com três serviços que se comunicam por REST (frontend→core) e Azure Service Bus (core↔worker):

- `core-api/` — Java 21 + **Spring Boot 4** + Gradle. Domínio, máquina de estados, REST API, geração de conteúdo via **Claude API direta da Anthropic** (SDK `com.anthropic:anthropic-java`, modelo `claude-opus-5`, com prompt caching da transcrição). NÃO usar Foundry (ADR-0007).
- `media-worker/` — Python 3.12 + FastAPI. FFmpeg (limpeza/normalização) e transcrição via **Whisper no Azure OpenAI** (ADR-0008).
- `frontend/` — TypeScript + Next.js.

Infra Azure: Container Apps, PostgreSQL Flexible Server, Blob Storage, Service Bus, Key Vault, Application Insights. Trial de US$200/30 dias → **projetar sempre para custo mínimo** (escala a zero, tiers básicos).

## Autenticação e planos

- **Identidade:** Microsoft Entra External ID (OIDC; MSAL no frontend, Spring Security resource server no core) — ADR-0009. Provisionamento JIT de usuários.
- **Autorização de negócio no domínio, não no IdP:** posse (User → Show → Episode), planos (`FREE`/`CREATOR`/`STUDIO`), cota em **minutos processados/mês** com ledger imutável — spec 0004.
- Regras que o código deve respeitar: recurso de outro usuário → **404** (não 403); cota esgotada no upload → `403 PLAN_QUOTA_EXCEEDED`; episódio em processamento nunca é abortado por cota (overdraft único); reprocessamento não debita duas vezes.

## Máquina de estados do episódio

`RECEIVED → AUDIO_PROCESSING → TRANSCRIBING → GENERATING → IN_REVIEW → READY`, com `FAILED` alcançável de qualquer estado de processamento. Fonte da verdade: Postgres. Spec: `docs/specs/0001-pipeline-mvp.md`.

## Regras do projeto

1. **Decisão arquitetural ⇒ ADR.** Qualquer decisão de arquitetura, tecnologia ou trade-off relevante gera um ADR em `docs/adr/` (use a skill `/adr`). Nunca contradiga um ADR aceito sem criar um novo que o substitua.
2. **Contracts-first para eventos.** Mensagens do Service Bus seguem os JSON Schemas em `contracts/events/`. Schema publicado é **imutável**: mudança breaking ⇒ novo arquivo `vN+1` (use a skill `/contract`). A REST API segue `contracts/openapi/core-api.v1.yaml`.
3. **Feature nova ⇒ spec antes de código.** Specs em `docs/specs/` (use a skill `/spec`).
4. **Idiomas:** documentação em **pt-BR**; código, identificadores, nomes de eventos, mensagens de commit e logs em **inglês**.
5. **Revisão humana é inegociável no produto:** nenhum conteúdo gerado por IA é publicado sem aprovação do criador.

## Convenções por serviço

- **Java:** Gradle (Kotlin DSL), pacote raiz `dev.lavra`. Preferir os recursos do Spring Boot 4 (HTTP service clients declarativos, `@Retryable`/`@ConcurrencyLimit`, API versioning) — parte do valor de portfólio é demonstrá-los.
- **Python:** gerenciado com `uv`, lint/format com `ruff`, type hints obrigatórios.
- **TypeScript:** Next.js App Router, `pnpm`.
- **FFmpeg:** parâmetros de áudio definidos em `docs/specs/0002-processamento-de-audio.md` — não inventar valores.

## Ambiente de desenvolvimento

- SO do desenvolvedor: **Windows 11** (PowerShell). Scripts em `scripts/*.ps1`.
- `scripts/dev-up.ps1` sobe Postgres + Azurite + emulador do Service Bus (`infra/docker-compose.dev.yml`).
- Segredos **nunca** em código ou compose: local via variáveis de ambiente/`.env` (gitignored); produção via Key Vault.

## Skills disponíveis

- `/adr` — cria um novo ADR numerado seguindo o template.
- `/spec` — cria uma nova spec funcional numerada.
- `/contract` — cria/evolui contratos de eventos respeitando as regras de versionamento.
