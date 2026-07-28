# Spec 0004 — Autenticação, planos e entitlements

- **Status:** aprovada
- **Data:** 2026-07-28
- **ADRs relacionados:** 0005, 0009

## Objetivo

Transformar o lavra de ferramenta pessoal em produto por assinatura: usuários autenticados, cada um dono dos próprios shows e episódios, com acesso limitado pelo plano que compraram. Esta spec **substitui** o item "múltiplos usuários (fora do escopo)" da spec 0001 — identidade e cota precisam existir desde a fundação, ou o modelo de dados nasce sem dono.

## Escopo

**Dentro:** login OIDC (Entra External ID, ADR-0009), provisionamento JIT de usuários, autorização por posse, modelo de planos e entitlements, medição de uso (minutos processados/mês), enforcement de cota no pipeline, endpoint `/me` (perfil + plano + consumo), atribuição manual de plano por admin.

**Fora (fase seguinte):** integração com provedor de pagamento (checkout, webhooks de cobrança), upgrade/downgrade self-service, times/colaboradores por show, admin UI.

## Modelo de domínio

```
User (1) ──< Show (1) ──< Episode
User (1) ──  Subscription ── Plan
User (1) ──< UsageLedgerEntry   (um por débito de minutos)
```

- `users` — chaveado pelo `oid`/`sub` do token; criado via JIT no primeiro request autenticado.
- `plans` — catálogo (código, nome, entitlements). Dados, não código: mudar limite de plano é UPDATE, não deploy.
- `subscriptions` — usuário → plano vigente + período corrente (`periodStart`/`periodEnd`, ciclo mensal).
- `usage_ledger` — débitos imutáveis de minutos processados (episódio, minutos, timestamp). Consumo do período = soma dos débitos no período. Nunca sobrescrever contador — ledger é auditável.

## Planos (valores iniciais — preço a definir)

| Plano | `monthlyProcessedMinutes` | `maxShows` | Observação |
|---|---|---|---|
| `FREE` | 90 | 1 | ~1 episódio/mês; porta de entrada |
| `CREATOR` | 600 | 1 | ~4–6 episódios/mês; o plano do podcaster semanal |
| `STUDIO` | 1800 | 3 | Quem produz mais de um show |

Todo usuário novo entra em `FREE`. Entitlements são lidos sempre do plano vigente — sem cache atravessando troca de plano.

## Regras de autorização

1. **Toda rota de negócio exige JWT válido** (401 sem token/expirado). Exceções: health checks.
2. **Posse estrita:** usuário só enxerga os próprios shows/episódios. Acesso a recurso de outro usuário retorna **404** (não 403 — não vazamos a existência do recurso).
3. **Papel `ADMIN`** (claim de app role no Entra): pode atribuir plano a usuário (substitui o billing até a integração de pagamento) e consultar qualquer recurso para suporte.
4. O `media-worker` **não** participa de auth de usuário — consome eventos internos; sua confiança vem da rede/identidade de serviço (managed identity), não de token de usuário.

## Enforcement de cota

O custo real do sistema é proporcional a **minutos de áudio processados** — a cota mede isso.

- **No upload** (`POST /episodes`): rejeita com `403 PLAN_QUOTA_EXCEEDED` se `consumo do período ≥ limite do plano` ou se `maxShows` seria violado na criação de show.
- **No débito** (ao consumir `media.processed`): debita `ceil(durationSeconds / 60)` minutos no ledger. O episódio **em processamento nunca é abortado por cota** — se o débito estourar o limite, o episódio conclui normalmente e o saldo fica negativo, bloqueando novos uploads até o próximo período (regra de overdraft único: simples e justa com o usuário).
- **Reprocessamento de `FAILED` não debita de novo** (o débito é por episódio processado com sucesso, idempotente por `episodeId` + período).
- Renovação de período: rolagem simples no início de cada ciclo; sem acúmulo de minutos não usados.

## API (refletido em `contracts/openapi/core-api.v1.yaml`)

- `GET /me` — perfil, plano vigente e consumo do período (minutos usados/limite, shows usados/limite).
- Todas as rotas: `401` sem autenticação; `404` para recurso de outro usuário; `403 PLAN_QUOTA_EXCEEDED` no upload sem saldo.
- Security scheme: `bearerAuth` (JWT) global.

## Critérios de aceite

- [ ] Request sem token ou com token expirado → 401 em qualquer rota de negócio.
- [ ] Usuário A acessando episódio do usuário B → 404, indistinguível de inexistente.
- [ ] Primeiro login de usuário novo cria o registro local e a assinatura `FREE` automaticamente.
- [ ] Upload com cota esgotada → `403` com `code: PLAN_QUOTA_EXCEEDED` e mensagem com o consumo atual.
- [ ] Episódio que estoura o limite no meio do processamento conclui, o saldo fica negativo e o upload seguinte é bloqueado.
- [ ] Reprocessar um episódio `FAILED` não gera débito duplicado no ledger.
- [ ] `GET /me` reflete o débito imediatamente após um `media.processed`.
- [ ] Admin consegue trocar o plano de um usuário e o novo limite vale no request seguinte.

## Questões em aberto

- Provedor de pagamento (Stripe vs Mercado Pago — relevante para público BR) e webhooks de cobrança → ADR próprio quando chegar a hora.
- Preços dos planos e existência de trial do `CREATOR`.
- Rollover parcial de minutos (hoje: não acumula) como diferencial de retenção.
