# Spec 0003 — Geração de conteúdo (Core API + Claude)

- **Status:** aprovada
- **Data:** 2026-07-28
- **ADRs relacionados:** 0002, 0007

## Objetivo

A partir da transcrição, gerar os artefatos editoriais do episódio — **na voz do criador**, não em texto genérico de IA. Este é o diferencial do produto: um título do Expulsando Demônios precisa soar como o Expulsando Demônios.

## Artefatos gerados

| Artefato | Formato | Regras |
|---|---|---|
| Títulos | 3 a 5 opções | ≤ 90 caracteres; variados em ângulo (um direto, um provocativo, um curioso) |
| Descrição | 1 parágrafo de abertura + tópicos | Abertura ≤ 300 caracteres (é o que aparece nos apps); sem spoiler da conclusão |
| Capítulos | Lista `{startSeconds, title}` | 5–15 capítulos; timestamps referentes ao áudio limpo; títulos ≤ 60 caracteres |
| Tags | 5–10 termos | Mistura de temas do episódio + temas recorrentes do show |

## Perfil de voz

Cada show tem um **perfil de voz** persistido (tabela `show_voice_profile`):

- **Descrição do tom** — texto curto mantido pelo criador (ex.: "irreverente, filosófico, referências à tradição cristã, humor ácido, primeira pessoa").
- **Exemplos** — títulos e descrições de episódios anteriores **aprovados** (few-shot). Cresce automaticamente: toda aprovação em `IN_REVIEW → READY` adiciona os artefatos aprovados ao perfil.
- **Anti-exemplos** — padrões vetados (ex.: "Neste episódio, exploramos…", clickbait com reticências).

Efeito composto: quanto mais episódios aprovados, melhores as sugestões — retenção por acúmulo de contexto.

## Estratégia de chamada (Claude API)

- SDK Java oficial, modelo `claude-opus-5`, streaming habilitado (transcrições longas).
- **Um prefixo cacheado, N gerações:** system prompt (instruções + perfil de voz) e transcrição entram como prefixo com `cache_control: {"type": "ephemeral"}`; cada artefato é gerado em chamada própria reaproveitando o cache (~10% do custo de input nas chamadas subsequentes). Regenerações individuais na tela de revisão reusam o mesmo prefixo.
- **Structured outputs** (`output_config.format` com JSON Schema) para capítulos e tags — resposta garantidamente parseável, sem regex.
- Resiliência via Spring Boot 4: `@Retryable` (backoff exponencial, 3 tentativas) e `@ConcurrencyLimit` nas chamadas.
- Encapsulamento: interface de domínio `ContentGenerator`; a implementação Anthropic é um detalhe (ADR-0007).

### Estrutura do prompt (esboço)

```
system (cacheado):
  1. Papel: editor de podcast que escreve na voz do criador
  2. Perfil de voz do show (descrição + exemplos + anti-exemplos)
  3. Regras dos artefatos (limites, formatos)

user (cacheado):
  4. Transcrição completa com timestamps

user (por chamada, após o breakpoint de cache):
  5. Instrução do artefato: "Gere as opções de título" / "Gere os capítulos" / ...
```

## Revisão e regeneração

- Todo artefato chega em `IN_REVIEW` como **rascunho** — editável campo a campo.
- "Regenerar" aceita uma instrução opcional do criador ("menos formal", "destaca a parte sobre Husserl") anexada à chamada.
- O texto final aprovado (com edições manuais) é o que alimenta o perfil de voz — o sistema aprende com a correção, não com o próprio output.

## Critérios de aceite

- [ ] Episódio de 90 min gera os 4 artefatos em < 2 min no caminho feliz.
- [ ] `usage.cache_read_input_tokens > 0` da segunda chamada em diante (cache efetivo).
- [ ] Capítulos e tags sempre parseiam contra os schemas — zero erro de parsing em 20 execuções.
- [ ] Com perfil de voz preenchido, os títulos gerados são distinguíveis de um baseline sem perfil (avaliação qualitativa pelo criador).
- [ ] Regeneração com instrução altera visivelmente o resultado sem quebrar limites de formato.

## Questões em aberto

- Poda do few-shot quando o histórico crescer (janela? amostragem por diversidade?).
- Sugestões de corte/clipes a partir da transcrição — v2, mas o formato canônico de transcrição já foi desenhado para suportar.
