# ADR-0005: Azure como nuvem

- **Status:** aceito
- **Data:** 2026-07-28

## Contexto

O desenvolvedor possui trial gratuito do Azure (US$200 por 30 dias + free tier de 12 meses em serviços selecionados) e quer aproveitá-lo. A proposta anterior usava VPS + Cloudflare R2. Azure também é uma das clouds mais demandadas no mercado corporativo — valor de portfólio.

## Decisão

Toda a infraestrutura roda no **Azure**:

| Necessidade | Serviço |
|---|---|
| Runtime dos serviços | Container Apps (escala a zero) + Container Registry |
| Banco | PostgreSQL Flexible Server (B1ms — free tier 12 meses) |
| Áudio/artefatos | Blob Storage |
| Mensageria | Service Bus (ADR-0006) |
| Transcrição | Azure OpenAI — Whisper (ADR-0008) |
| Segredos | Key Vault |
| Observabilidade | Application Insights via OpenTelemetry |
| Frontend | Static Web Apps (tier gratuito) |

**Restrição de projeto:** desenhar para custo mínimo desde o início (escala a zero, tiers básicos), para que o fim do trial não inviabilize o projeto.

## Alternativas consideradas

- **VPS (Hetzner) + Cloudflare R2** — mais barato no longo prazo, mas ignora os créditos disponíveis e demonstra menos como portfólio (sem serviços gerenciados, IaC menos interessante).
- **AWS/GCP** — sem créditos disponíveis; Azure tem a vantagem concreta do trial e do Azure OpenAI já liberado na conta.

## Consequências

- Créditos cobrem a fase de construção; free tier de 12 meses cobre o essencial depois.
- Portfólio ganha IaC (Terraform/Bicep), serviços gerenciados e observabilidade de nuvem.
- Risco: custo pós-trial em serviços fora do free tier (Service Bus, Container Apps além da cota) — mitigação: escala a zero, monitorar custo desde o dia 1, e a arquitetura em contêineres mantém portabilidade se for preciso migrar.
