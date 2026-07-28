# ADR-0001: Arquitetura poliglota com três serviços

- **Status:** aceito
- **Data:** 2026-07-28

## Contexto

O lavra é um pipeline de mídia com três naturezas de trabalho distintas: domínio/orquestração (transacional, máquina de estados), processamento de mídia (CPU-bound, ecossistema de ferramentas específico) e interface web. O projeto também é peça de portfólio: demonstrar amplitude técnica (poliglota, mensageria, cloud) tem valor próprio, desde que cada escolha seja justificável pelo problema — não complexidade gratuita.

O desenvolvedor é primariamente Java/Kotlin, mas decidiu explicitamente não restringir o projeto a Java ("no que for melhor utilizar outra linguagem, utilizaremos").

## Decisão

Monorepo com três serviços independentes, cada um na linguagem em que seu problema é melhor resolvido:

1. **Core API** — Java 21 + Spring Boot 4: domínio, estados, REST, integração com Claude.
2. **Media Worker** — Python: FFmpeg e transcrição (ecossistema de mídia/IA é Python-primeiro).
3. **Frontend** — TypeScript + Next.js.

Comunicação assíncrona core↔worker via fila (ADR-0006); frontend→core via REST.

## Alternativas consideradas

- **Monolito Java (proposta original)** — menor esforço para um dev solo, mas força Java em domínios onde é a ferramenta errada (mídia) e demonstra menos amplitude como portfólio.
- **Microserviços finos (5+ serviços)** — complexidade desproporcional ao tamanho do produto e do time (1 pessoa).

## Consequências

- Cada serviço usa a melhor ferramenta do seu domínio; o portfólio demonstra três stacks e integração entre elas.
- Custo: três pipelines de build, três deploys, contratos entre serviços que precisam de governança — mitigado por contracts-first (`contracts/`) e monorepo.
- O tracing distribuído (Java → fila → Python) vira requisito de observabilidade, não opcional.
