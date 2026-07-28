# ADR-0003: Python no Media Worker

- **Status:** aceito
- **Data:** 2026-07-28

## Contexto

O worker faz o trabalho pesado de mídia: orquestrar FFmpeg (normalização de loudness, remoção de silêncio, redução de ruído), fatiar áudio para respeitar limites da API de transcrição e montar o resultado. O ecossistema de manipulação de áudio e tooling de IA é Python-primeiro (pydub, ffmpeg-python, SDKs de IA com exemplos e suporte melhores).

## Decisão

O Media Worker será **Python 3.12**, com **FastAPI** (health checks e endpoints administrativos), dependências gerenciadas por **uv** e lint/format com **ruff**. Consome mensagens do Service Bus, processa e publica o resultado.

## Alternativas consideradas

- **Java com ProcessBuilder → FFmpeg** — viável (era a proposta do monolito), mas todo o entorno (chunking de áudio, formatos, bibliotecas auxiliares) é mais trabalhoso em Java, sem nenhum ganho.
- **Node.js** — ecossistema de áudio inferior ao Python; TypeScript já está representado no frontend.

## Consequências

- Processamento de mídia usa o ecossistema certo; o repositório demonstra um serviço Python idiomático e moderno (uv, ruff, type hints).
- Custo: segunda linguagem no monorepo — mitigado pelo escopo pequeno e bem delimitado do worker (consumir fila → processar → publicar).
