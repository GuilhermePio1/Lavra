---
name: contract
description: Cria ou evolui contratos entre serviços (JSON Schema de eventos do Service Bus em contracts/events/, OpenAPI em contracts/openapi/). Use sempre que um evento novo for necessário, um payload precisar mudar, ou a REST API ganhar/alterar endpoints.
---

# Criar ou evoluir um contrato

## Regra de ouro

**Schema publicado é imutável.** Se um `*.vN.json` já é produzido/consumido por código, ele nunca muda — qualquer alteração (até adicionar campo opcional, pois usamos `additionalProperties: false`) gera um novo arquivo `vN+1`. As regras completas estão em `contracts/README.md` — leia antes de editar.

## Evento novo

1. Nome: `substantivo.verbo-no-particípio.v1` em inglês (ex.: `content.approved.v1`).
2. Crie `contracts/events/<nome>.v1.json` seguindo o padrão dos existentes:
   - Envelope obrigatório: `eventId` (uuid), `eventType` (const com a versão), `occurredAt` (date-time), `data`.
   - `additionalProperties: false` em **todos** os níveis de objeto.
   - `description` em inglês em cada campo não óbvio; documente produtor e consumidor na description do schema.
   - Caminhos de blob são relativos ao contêiner — nunca URL com credencial.
3. Se o evento exigir fila nova, adicione-a em `infra/servicebus-config.json` e mencione no PR.

## Evolução de evento existente

1. Copie o `vN.json` para `vN+1.json`; aplique a mudança; atualize o `const` de `eventType` e o `$id`.
2. **Não apague nem edite o `vN.json`** — ele é aposentado só quando nenhum produtor/consumidor o usa mais (aí sim, remova o arquivo em commit próprio).
3. Descreva no PR o plano de transição (produtor migra primeiro? consumidor aceita ambos?).

## REST API (OpenAPI)

- Mudanças aditivas e compatíveis (endpoint novo, campo opcional em resposta) podem editar `core-api.v1.yaml` diretamente.
- Mudanças breaking (remover/renomear campo, mudar tipo, alterar semântica) exigem `core-api.v2.yaml` + rota `/api/v2`.
- Mantenha `operationId` em todos os endpoints e schemas nomeados em `components` — o frontend gera tipos a partir deste arquivo.
