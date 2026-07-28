---
name: adr
description: Cria um novo ADR (Architecture Decision Record) em docs/adr/. Use sempre que uma decisão de arquitetura, tecnologia ou trade-off relevante for tomada na conversa — escolha de biblioteca, mudança de serviço Azure, alteração de padrão de comunicação, etc.
---

# Criar um ADR

## Passos

1. Liste `docs/adr/` e determine o próximo número sequencial (`NNNN`, 4 dígitos).
2. Crie `docs/adr/NNNN-<slug-do-titulo>.md` — slug em minúsculas, sem acentos, palavras separadas por hífen.
3. Siga exatamente a estrutura de `docs/adr/0000-template.md`: Status / Data / Contexto / Decisão / Alternativas consideradas / Consequências.
4. Escreva em **pt-BR**, conciso: Contexto e Consequências com 3–6 frases cada; a Decisão em 1–2 frases afirmativas ("Usaremos X para Y").

## Regras

- **Status inicial:** `aceito` se a decisão já foi tomada na conversa; `proposto` se ainda está em discussão.
- **Alternativas de verdade:** registre as alternativas realmente consideradas e o motivo real da rejeição — nunca invente strawmen.
- **Consequências honestas:** inclua o que fica *pior* e os riscos assumidos, não só os benefícios.
- **Substituição, nunca contradição:** se a decisão nova contradiz um ADR aceito, marque o antigo como `substituído por ADR-NNNN` (edite a linha de Status dele) e referencie-o no novo. Nunca deixe dois ADRs aceitos em conflito.
- Se a decisão afeta a stack registrada em `CLAUDE.md` ou no `README.md`, atualize-os no mesmo commit.
