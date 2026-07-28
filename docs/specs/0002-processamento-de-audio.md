# Spec 0002 — Processamento de áudio (Media Worker)

- **Status:** aprovada
- **Data:** 2026-07-28
- **ADRs relacionados:** 0003, 0008

## Objetivo

Transformar o áudio bruto em áudio de qualidade de publicação: loudness padronizado, sem silêncios constrangedores, com ruído de fundo reduzido — sem descaracterizar a fala do criador.

## Entradas e saídas

- **Entrada:** `raw/{episodeId}/original.{ext}` — mp3, wav, m4a ou flac; mono ou estéreo; qualquer sample rate.
- **Saídas:**
  - `processed/{episodeId}/clean.mp3` — áudio final.
  - `processed/{episodeId}/transcript.json` — transcrição canônica (formato abaixo).
  - Métricas no evento `media.processed.v1` (duração, loudness antes/depois, segundos de silêncio removidos).

## Cadeia de processamento (FFmpeg)

Ordem fixa; cada etapa é individualmente desligável por configuração do episódio:

1. **Decodificação + downmix** — converter para WAV interno 48 kHz; podcasts de voz solo saem em **mono** (padrão), estéreo apenas se a entrada for musical/multipista.
2. **Redução de ruído** — filtro `afftdn` (nível moderado; `nr=12`). Desligado por padrão se o loudness de piso indicar gravação limpa.
3. **Remoção de silêncios longos** — `silenceremove`: silêncios **> 2,0 s** são encurtados para **0,75 s** (nunca removidos por completo — pausa é retórica, especialmente em conteúdo de filosofia). Threshold: −35 dB.
4. **Normalização de loudness** — `loudnorm` em duas passadas (measure + apply): **I = −16 LUFS, TP = −1,5 dBTP, LRA = 11** (padrão de distribuição de podcast).
5. **Encode final** — MP3 CBR **128 kbps mono** (ou 192 kbps estéreo), 44,1 kHz.

Regra de ouro: **preservar a fala é mais importante que polir**. Parâmetros conservadores por padrão; agressividade é opt-in.

## Transcrição

Após a limpeza (transcrever o áudio limpo melhora a acurácia):

- Whisper via Azure OpenAI, `language=pt`, `response_format=verbose_json` (ADR-0008).
- Arquivos acima do limite da API: fatiar em blocos de ~10 min com 5 s de sobreposição; re-alinhar timestamps e deduplicar a sobreposição na junção.

### Formato canônico (`transcript.json`)

```json
{
  "version": 1,
  "language": "pt",
  "durationSeconds": 5432.1,
  "provider": "azure-openai-whisper",
  "segments": [
    { "start": 0.0, "end": 7.4, "text": "Sejam bem-vindos a mais um episódio..." }
  ]
}
```

Timestamps em segundos (float), sempre relativos ao **áudio limpo** (é ele que o ouvinte recebe — capítulos apontam para ele).

## Critérios de aceite

- [ ] Loudness integrado do resultado em −16 ± 1 LUFS para qualquer entrada de teste.
- [ ] True peak nunca acima de −1,0 dBTP.
- [ ] Nenhuma palavra cortada nos limites de remoção de silêncio (validar de ouvido com 3 episódios reais do Expulsando Demônios).
- [ ] Transcrição de um episódio de 90 min fatiado não apresenta texto duplicado nem lacunas nas junções.
- [ ] Worker é idempotente: reprocessar o mesmo `episodeId` sobrescreve as saídas sem duplicar nada.

## Questões em aberto

- Detecção automática de vinheta/música de abertura (para não "normalizar" a vinheta junto). MVP: ignorar; observar resultados reais.
- `afftdn` vs `arnndn` (modelo neural) para ruído — comparar com gravações reais antes de fixar.
