# Spec 0001 — Pipeline do MVP

- **Status:** aprovada
- **Data:** 2026-07-28
- **ADRs relacionados:** 0001, 0006, 0007, 0008

## Objetivo

Entregar o fluxo mínimo que resolve a dor central: **o criador sobe um áudio bruto e recebe de volta um episódio pronto para publicar** — áudio limpo, transcrição, título, descrição, capítulos e tags — mediante revisão e aprovação.

## Escopo

**Dentro:** upload de áudio, limpeza/normalização, transcrição, geração de conteúdo, tela de revisão/edição/regeneração, export (download do áudio final + textos).

**Fora (v2+):** cortes verticais para redes sociais, geração de artes/capas, publicação automática (RSS/Spotify/YouTube), edição por texto (apagar frase na transcrição corta o áudio).

> **Nota (2026-07-28):** multiusuário deixou de ser "fora do escopo" — autenticação, planos e cotas entraram na fundação pela **spec 0004** (o upload passa a exigir usuário autenticado e saldo de cota; ver também ADR-0009). Esta spec permanece a fonte da verdade do pipeline em si.

## Máquina de estados

```
PENDING_UPLOAD ──► RECEIVED ──► AUDIO_PROCESSING ──► TRANSCRIBING ──► GENERATING ──► IN_REVIEW ──► READY
                                       │                   │               │
                                       └───────────────────┴───────────────┴──► FAILED
```

| Estado | Significado | Quem transiciona |
|---|---|---|
| `PENDING_UPLOAD` | Episódio registrado; aguardando os bytes do áudio no Blob | Core API (ao emitir a SAS) |
| `RECEIVED` | Áudio bruto no Blob; upload confirmado | Core API (no `upload-complete`) |
| `AUDIO_PROCESSING` | Worker limpando/normalizando o áudio | Core API (ao publicar `episode.uploaded`) |
| `TRANSCRIBING` | Worker transcrevendo via Whisper | Media Worker (reporta progresso) |
| `GENERATING` | Core chamando o Claude para gerar conteúdo | Core API (ao consumir `media.processed`) |
| `IN_REVIEW` | Conteúdo pronto aguardando o criador | Core API (geração concluída) |
| `READY` | Criador aprovou; artefatos disponíveis para export | Core API (ação de aprovação) |
| `FAILED` | Erro não recuperável em qualquer etapa | Core API (ao consumir `processing.failed`) |

Regras:

- A fonte da verdade do estado é o Postgres (Core API). O worker **não** conhece estados — apenas consome e publica eventos.
- Transições são registradas em tabela de histórico (`episode_state_transitions`) com timestamp e causa — alimenta a UI de acompanhamento e o debug.
- `FAILED` guarda `stage`, `errorCode` e `errorMessage` do evento de falha. Reprocessar (re-publicar `episode.uploaded`) é permitido a partir de `FAILED`.
- Episódios parados em `PENDING_UPLOAD` há mais de 24 h são removidos por job periódico, junto com o blob parcial — upload abandonado não vira lixo permanente nem aparece na lista do criador.

## Fluxo detalhado

1. **Upload** — `POST /api/v1/episodes` valida posse do show e saldo de cota, cria o episódio em `PENDING_UPLOAD` e devolve uma SAS de escrita restrita a `raw/{episodeId}/original.{ext}` (TTL 2 h). O browser envia o arquivo em blocos direto ao Blob (mp3/wav/m4a/flac, ≤ 2 GB). `POST /api/v1/episodes/{id}/upload-complete` valida o blob (existência, tamanho, `Content-Type`), transiciona para `RECEIVED`, publica `episode.uploaded.v1` e vai para `AUDIO_PROCESSING`. Ver ADR-0011.
2. **Processamento de mídia** — Worker consome `episode.uploaded.v1`: executa a cadeia FFmpeg (spec 0002), grava o áudio limpo em `processed/{episodeId}/clean.mp3`, transcreve (ADR-0008), grava a transcrição canônica em `processed/{episodeId}/transcript.json` e publica `media.processed.v1`.
3. **Geração de conteúdo** — Core consome `media.processed.v1`, transiciona para `GENERATING` e chama o Claude (spec 0003). Resultado persistido no Postgres; estado vai a `IN_REVIEW`.
4. **Revisão** — Frontend exibe player do áudio limpo + artefatos gerados. O criador pode: editar qualquer texto, escolher entre as opções de título, regenerar um artefato específico, aprovar tudo.
5. **Aprovação/Export** — `POST /api/v1/episodes/{id}/approve` transiciona para `READY`. `GET /api/v1/episodes/{id}/export` devolve os links do áudio final e o pacote de textos.

## Critérios de aceite

- [ ] Upload de um mp3 de 90 minutos completa o pipeline de ponta a ponta sem intervenção manual.
- [ ] Estado do episódio visível no frontend, atualizando sem refresh manual (polling ou SSE).
- [ ] Falha simulada no worker (áudio corrompido) leva o episódio a `FAILED` com mensagem legível, e o reprocessamento funciona.
- [ ] Mensagens malformadas caem na DLQ e ficam visíveis em log/alerta — nunca perdem o episódio silenciosamente.
- [ ] Nenhum artefato é exportável antes da aprovação (`READY`).
- [ ] Tracing distribuído: uma requisição de upload é rastreável até a publicação do `media.processed` no Application Insights.

## Questões em aberto

- SSE vs polling para atualização da UI (decidir na implementação do frontend; polling é aceitável no MVP).
- Retenção do áudio bruto após `READY` (custo de storage × capacidade de reprocessar).
