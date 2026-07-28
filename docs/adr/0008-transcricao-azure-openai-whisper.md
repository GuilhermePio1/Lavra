# ADR-0008: Transcrição com Whisper via Azure OpenAI

- **Status:** aceito
- **Data:** 2026-07-28

## Contexto

O pipeline precisa de transcrição pt-BR de alta qualidade com timestamps (base para capítulos e para a futura edição por texto). O **Azure OpenAI está liberado na conta/região do desenvolvedor**, então a transcrição pode consumir os créditos do trial (ADR-0005).

## Decisão

Transcrição via **Whisper no Azure OpenAI**, chamada pelo Media Worker (Python):

- Response format `verbose_json` com timestamps por segmento.
- Idioma fixado em `pt` (evita detecção incorreta em aberturas com música/vinheta).
- Áudios maiores que o limite da API são fatiados pelo worker (com sobreposição pequena entre fatias) e os segmentos re-alinhados no resultado.
- A transcrição resultante é persistida no Blob Storage como JSON canônico (formato definido em `contracts/`), independente do provedor.

## Alternativas consideradas

- **Azure AI Speech (batch transcription)** — também usa créditos e lida nativamente com arquivos longos; fica registrado como **plano B** se os limites de tamanho do Whisper se mostrarem dolorosos na prática.
- **OpenAI/Groq direto** — qualidade equivalente, mas cobrança fora do Azure sem necessidade.
- **Whisper self-hosted (faster-whisper)** — custo zero de API, porém exige GPU ou paciência, e infraestrutura própria de inferência não é o foco do projeto agora.

## Consequências

- Transcrição paga com créditos do trial; integração simples (HTTP) a partir do worker.
- O formato canônico de transcrição no Blob desacopla o resto do pipeline do provedor — trocar para Azure AI Speech ou self-hosted não afeta o Core API.
- Custo pós-trial vira variável a monitorar; o plano B (Speech batch) e o plano C (self-hosted) ficam documentados.
