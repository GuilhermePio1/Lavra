# ADR-0002: Spring Boot 4 no Core API

- **Status:** aceito
- **Data:** 2026-07-28

## Contexto

O Core API será Java (linguagem principal do desenvolvedor, JDK 21 já configurado). Spring Boot 4 (GA em nov/2025, sobre Spring Framework 7) é a versão atual; a 3.x ainda é amplamente usada no mercado.

## Decisão

Usaremos **Spring Boot 4** com Java 21 e Gradle (Kotlin DSL), explorando deliberadamente os recursos novos como demonstração de portfólio:

- **HTTP service clients declarativos** (`@ImportHttpServices`) para integrações externas;
- **Resiliência nativa** (`@Retryable`, `@ConcurrencyLimit`) nas chamadas de IA — que falham e precisam de retry/limitação por natureza;
- **API versioning nativo** na REST API;
- **Null-safety com JSpecify**.

## Alternativas consideradas

- **Spring Boot 3.x** — mais maduro/documentado, porém sem valor de diferenciação; a migração futura seria custo puro.
- **Quarkus/Micronaut** — startup mais rápido em serverless, mas ecossistema e mercado menores; Spring é a aposta certa para portfólio Java.

## Consequências

- Portfólio demonstra a versão mais atual do framework dominante do mercado Java.
- Risco assumido: bibliotecas de terceiros podem demorar a suportar o Boot 4; mitigação: manter dependências mínimas e preferir recursos nativos do Spring.
