# ADR-0013: Arquitetura interna do core-api

- **Status:** aceito
- **Data:** 2026-07-28
- **Substitui / Substituído por:** —

## Contexto

O Core API concentra as regras que dão valor ao lavra: a máquina de estados do episódio (spec 0001), a cota em minutos com ledger imutável e overdraft único (spec 0004) e a posse estrita que faz recurso alheio responder 404. São regras que precisam ser testadas exaustivamente e que não têm nada a ver com HTTP, Postgres ou Azure — mas o serviço também integra quatro sistemas externos (Postgres, Blob Storage, Service Bus e a Claude API) e é onde a máquina de estados vive. A estrutura de pacotes é a decisão mais cara de reverter depois que o código existe, então precisa ser tomada antes do primeiro commit do serviço. Duas restrições delimitam o espaço: o projeto é tocado por um desenvolvedor só, e o `CLAUDE.md` estabelece que demonstrar os recursos do Spring Boot 4 é parte do valor de portfólio — o que exige que o framework apareça no código, não que seja escondido atrás de camadas.

## Decisão

Organizaremos o core-api em **package-by-feature** sob `dev.lavra` (`identity`, `episode`, `content`, mais `shared` para configuração, segurança e tratamento de erros), cada feature com os subpacotes `domain`, `web`, `persistence` e — quando houver integração — `messaging`/`claude`.

O núcleo de domínio de cada feature (transições de estado, cálculo de entitlements e cota) é composto de objetos Java sem anotações de framework, testável sem contexto Spring; **ports** são definidos apenas para os sistemas externos que não sejam o banco (Blob, Service Bus, Claude API), enquanto CRUD sem regra de negócio vai direto de controller a repository.

## Alternativas consideradas

- **Hexagonal completa em módulos Gradle separados** (domínio puro no centro, todo acesso externo — inclusive Postgres — atrás de port, dependências apontando para dentro) — o benefício concreto que se busca com ela, testar as regras sem infraestrutura e trocar integrações sem tocar no domínio, já é obtido mantendo o domínio livre de anotações. O que sobra é custo: mapeamento domínio↔entidade em toda leitura e escrita, incluindo os agregados que não têm regra alguma, e um build multi-módulo para um desenvolvedor só. Além disso, ela empurra o Spring para a borda justamente quando um dos objetivos declarados é exibir os recursos do Boot 4.
- **Camadas clássicas com `@Entity` como modelo de domínio** (`controller`/`service`/`repository`/`entity`) — é o caminho mais rápido até o primeiro endpoint, mas a regra de overdraft e as transições de estado são exatamente o tipo de lógica que apodrece dentro de uma entidade gerenciada: testar "episódio que estoura o limite conclui e deixa saldo negativo" passaria a exigir contexto de persistência. Também espalha cada feature por cinco pacotes, o que piora à medida que o serviço cresce.
- **Spring Modulith** — é a formalização natural do que decidimos, com verificação automática das fronteiras entre módulos em teste. Foi adiado, não descartado: seu valor principal é impedir que um time atravesse fronteiras acidentalmente, o que rende pouco com um desenvolvedor, e sua segunda função (eventos de aplicação entre módulos) é redundante aqui, já que a comunicação assíncrona real acontece via Service Bus. Vale reavaliar quando as fronteiras estiverem estáveis.

## Consequências

- As regras que mais importam ficam verificáveis por testes JUnit comuns, em milissegundos e sem Docker: a tabela de transições válidas e os casos de cota da spec 0004 não dependem de banco nem de broker para serem exercitados.
- Feature nova é um pacote novo, não uma edição espalhada por cinco lugares — e ler `episode/` entrega o assunto inteiro, do controller ao publisher.
- A fronteira entre `domain` e `persistence` cobra mapeamento manual onde ela existe. Aceitamos isso apenas onde há regra: agregados sem comportamento (como `Show`) podem trafegar a própria entidade até que ganhem regra, e essa assimetria é intencional, não descuido.
- Risco assumido, e o principal desta decisão: o critério "port e domínio puro só onde há regra real" é subjetivo, e não há revisor para segurá-lo. Sem verificação automática de fronteiras, a tendência natural é erosão — anotação de JPA que aparece no `domain`, regra que vaza para o controller. A mitigação prevista é adotar ArchUnit ou Spring Modulith assim que a terceira feature existir; até lá, a disciplina é manual e o ADR é o que a torna explícita.
- Ports para Blob, Service Bus e Claude permitem exercitar o pipeline de ponta a ponta com implementações de teste — sem conta Azure e, mais relevante para o trial de US$ 200, sem gastar tokens da Anthropic a cada execução da suíte.
