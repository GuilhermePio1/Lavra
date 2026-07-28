# ADR-0009: Microsoft Entra External ID para autenticação

- **Status:** aceito
- **Data:** 2026-07-28

## Contexto

O lavra é um produto por assinatura: pessoas compram planos, e o acesso (quantos minutos processam, quantos shows têm) depende do plano. Isso exige identidade multiusuário, controle de posse (cada criador vê só os próprios shows/episódios) e enforcement de cotas — nada disso estava na fundação original, que assumia usuário único. Não queremos custodiar senhas (código de segurança sensível que não agrega ao produto), e a infraestrutura é Azure (ADR-0005).

## Decisão

**Autenticação via Microsoft Entra External ID** (o IdP do Azure para apps voltados a consumidor, sucessor do AD B2C; gratuito até 50k usuários ativos/mês):

- **Frontend:** login OIDC (Authorization Code + PKCE) com MSAL; obtém o access token JWT.
- **Core API:** resource server — Spring Security OAuth2 Resource Server valida o JWT (issuer, audience, assinatura). Nenhum endpoint de negócio sem token.
- **Provisionamento JIT:** no primeiro request autenticado, o core cria o registro `users` local a partir das claims (`sub`/`oid`, e-mail, nome).

**Autorização de negócio fica no domínio, não no IdP:** posse de shows/episódios, plano, cota e papéis vivem no Postgres (spec 0004). O Entra responde "quem é você"; o lavra responde "o que você pode fazer". Planos nunca são modelados como claims/grupos do IdP — regra de negócio muda rápido demais para morar lá.

## Alternativas consideradas

- **Auth própria (Spring Security + tabela de usuários + JWT próprio)** — controle total e dev local trivial, mas nos torna custodiante de senhas, com todo o código sensível (hash, reset, MFA, lockout) para escrever e manter. Não é o foco do produto.
- **Auth0 / Clerk** — DX excelente, porém fora do Azure (menos coerente com o portfólio) e free tiers mais restritivos.
- **Keycloak self-hosted** — poderoso, mas vira mais um serviço para operar e custear; contradiz a diretriz de custo mínimo.

## Consequências

- Zero senha sob nossa custódia; MFA, reset e proteção de conta vêm prontos; 50k MAU gratuitos cobrem qualquer horizonte realista.
- Portfólio demonstra integração OIDC de ponta a ponta (MSAL → JWT → resource server) — competência muito demandada.
- Custos assumidos: configuração de tenant do Entra tem curva de aprendizado; dev local precisa de um tenant de teste (ou perfil `dev` com token mockado, documentado e impossível de ativar em produção). Lock-in leve, mitigado por ser OIDC padrão — trocar de IdP não toca o domínio.

> **Nota (2026-07-28):** o dev local foi resolvido pelo **ADR-0012** — emulador de OIDC no compose, e não perfil com token mockado, que foi explicitamente rejeitado.
