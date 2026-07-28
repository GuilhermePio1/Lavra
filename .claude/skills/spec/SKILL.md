---
name: spec
description: Cria uma nova especificação funcional em docs/specs/. Use antes de implementar qualquer feature nova ou comportamento não trivial — regra do projeto é "spec antes de código".
---

# Criar uma spec funcional

## Passos

1. Liste `docs/specs/` e determine o próximo número sequencial (`NNNN`, 4 dígitos).
2. Crie `docs/specs/NNNN-<slug-do-titulo>.md` com esta estrutura:

```markdown
# Spec NNNN — <Título>

- **Status:** rascunho | aprovada
- **Data:** YYYY-MM-DD
- **ADRs relacionados:** <números, se houver>

## Objetivo
(O problema do usuário que isto resolve, em 2–3 frases.)

## Escopo
**Dentro:** ... **Fora:** ... (explicitar o que NÃO entra é obrigatório)

## Comportamento
(O coração da spec: fluxos, estados, regras, formatos. Tabelas e diagramas quando ajudarem.)

## Critérios de aceite
- [ ] Critérios verificáveis, um por linha — cada um deve ser testável.

## Questões em aberto
(O que foi deliberadamente adiado, para não travar a spec.)
```

## Regras

- **pt-BR** na prosa; nomes de estados, eventos, campos e endpoints em **inglês** (consistentes com `contracts/`).
- Critérios de aceite devem ser **verificáveis** — "funciona bem" não é critério; "loudness em −16 ± 1 LUFS" é.
- Se a spec exigir novo evento ou mudança na REST API, referencie os contratos e use a skill `/contract` — a spec não substitui o contrato.
- Consulte as specs existentes (`0001` a `0003`) como referência de tom e nível de detalhe.
