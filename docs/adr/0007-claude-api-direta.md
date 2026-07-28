# ADR-0007: Claude via API direta da Anthropic (não via Microsoft Foundry)

- **Status:** aceito
- **Data:** 2026-07-28

## Contexto

A geração de conteúdo (títulos, descrição, capítulos, tags) usa o Claude. O Claude está disponível de duas formas relevantes para nós: API direta da Anthropic e Microsoft Foundry (que manteria tudo dentro do Azure). O Foundry, porém, é cobrado via Microsoft Marketplace — e foi **verificado que o trial do Azure não cobre cobranças de Marketplace**, eliminando a vantagem principal (usar os créditos).

## Decisão

Usaremos a **API direta da Anthropic**, via SDK Java oficial (`com.anthropic:anthropic-java`):

- Modelo padrão: `claude-opus-5`.
- **Prompt caching** da transcrição: a transcrição do episódio entra como prefixo com `cache_control`; título, descrição, capítulos e tags são gerados em chamadas subsequentes pagando ~10% pelo input cacheado.
- Chave de API no Key Vault (produção) / variável de ambiente (dev).
- Chamadas encapsuladas atrás de uma interface de domínio (`ContentGenerator`), para que trocar de provedor no futuro seja uma mudança localizada.

## Alternativas consideradas

- **Claude via Microsoft Foundry** — manteria billing e rede no Azure (o SDK Java suporta via `FoundryBackend`); rejeitado porque o trial não cobre Marketplace, e alguns recursos (ex.: prompt caching) estão em beta no Foundry.
- **Modelo do Azure OpenAI para geração de texto** — usaria créditos, mas a qualidade de escrita em pt-BR com voz autoral é o coração do produto; o Claude é a escolha deliberada aqui, e o custo por episódio é baixo (centavos).

## Consequências

- Uma única cobrança fora do Azure (Anthropic), pequena e proporcional ao uso.
- A interface `ContentGenerator` preserva a opção de migrar para Foundry se um dia houver acordo/billing corporativo Azure.
