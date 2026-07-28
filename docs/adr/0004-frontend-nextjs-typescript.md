# ADR-0004: Frontend em Next.js + TypeScript

- **Status:** aceito
- **Data:** 2026-07-28

## Contexto

A primeira proposta de stack usava Thymeleaf + HTMX (server-rendered dentro do Spring), otimizando para o menor esforço de um dev solo. A definição do projeto como **portfólio** mudou o trade-off: um frontend TypeScript real demonstra amplitude que o mercado espera, e a tela de revisão (edição de conteúdo gerado, player de áudio sincronizado com transcrição) tende a exigir interatividade rica de qualquer forma.

## Decisão

Frontend em **TypeScript + Next.js (App Router)**, gerenciado com pnpm, hospedado no **Azure Static Web Apps** (tier gratuito permanente). Comunica com o Core API exclusivamente via REST, seguindo `contracts/openapi/core-api.v1.yaml`.

## Alternativas consideradas

- **Thymeleaf + HTMX** — mais simples, um deployável só; rejeitado pelo objetivo de portfólio e pela previsão de UI rica na tela de revisão.
- **React + Vite (SPA pura)** — válido, mas Next.js agrega SSR/roteamento/conveções prontas e é o padrão de mercado atual para portfólio React.

## Consequências

- Portfólio cobre as três stacks mais demandadas (Java, Python, TypeScript).
- Custo: projeto de build separado, CORS e autenticação entre domínios a resolver — mitigado por hospedar no Static Web Apps com proxy para a API.
