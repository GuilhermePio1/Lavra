# Contracts

Contratos são a fronteira formal entre os serviços do lavra. **Nada cruza uma fronteira sem contrato.**

## Estrutura

```
contracts/
├── openapi/
│   └── core-api.v1.yaml        # REST: frontend → core-api
└── events/
    ├── episode.uploaded.v1.json    # core-api → media-worker
    ├── media.processed.v1.json     # media-worker → core-api
    └── processing.failed.v1.json   # media-worker → core-api
```

## Regras de versionamento (eventos)

1. **Schema publicado é imutável.** Depois que um evento `*.vN.json` passa a ser produzido/consumido, o arquivo não muda mais — nem "só um campo novo".
2. **Mudança breaking ⇒ novo arquivo `vN+1`.** Remover/renomear campo, mudar tipo, tornar opcional em obrigatório: tudo é breaking.
3. **Campo novo opcional também gera `vN+1`.** Os schemas usam `additionalProperties: false`; consumidores validam estritamente, então qualquer campo novo é breaking por definição. (Regra deliberadamente conservadora — simples de seguir e à prova de surpresa.)
4. **Transição:** produtor passa a emitir `vN+1`; consumidores aceitam `vN` e `vN+1` durante a migração; `vN` é aposentado quando não há mais mensagens em trânsito.
5. **`eventType` carrega a versão** (`episode.uploaded.v1`) — o consumidor sabe qual schema validar sem adivinhar.

## Convenções

- Envelope comum a todos os eventos: `eventId` (UUID), `eventType`, `occurredAt` (ISO 8601 UTC), `data`.
- Nomes de evento: `substantivo.verbo-no-particípio.vN`, em inglês.
- Referências a arquivos no Blob Storage são passadas como **caminho relativo ao contêiner** (`processed/{episodeId}/clean.mp3`), nunca URL completa com credencial.
- Validação: produtor valida antes de publicar; consumidor valida antes de processar. Mensagem inválida → DLQ, nunca descarte silencioso.

## REST (OpenAPI)

- `core-api.v1.yaml` é a fonte da verdade da API — o código segue o contrato, não o contrário.
- Versionamento pela URL (`/api/v1/...`), usando o API versioning nativo do Spring Boot 4.
- Mudanças breaking na REST seguem a mesma filosofia: `v2` novo, `v1` mantido durante a transição.
