# ADR-0015: Acesso ao Blob Storage e ao Service Bus no core-api

- **Status:** aceito
- **Data:** 2026-08-04
- **Substitui / Substituído por:** —

## Contexto

O ADR-0013 estabeleceu que Blob, Service Bus e Claude ficam atrás de *ports*, mas não disse onde eles moram nem como o serviço se autentica. Três forças concretas apareceram na implementação. Primeira: quem assina a SAS de escrita do ADR-0011 precisa de uma credencial, e as duas opções — chave da conta ou *user delegation key* obtida por identidade gerenciada — têm perfis de segurança bem diferentes. Segunda: o Blob não pertence a uma feature só (o `episode` escreve o áudio bruto, o `content` lerá a transcrição), então colocá-lo dentro de `episode/` criaria dependência entre fatias. Terceira: o emulador do Service Bus só roda acompanhado de um SQL Server (~1,5 GB de imagem, minutos de subida), o que é caro demais para toda execução de `gradle test` num projeto de um desenvolvedor só.

## Decisão

Os ports de Blob e Service Bus ficam em `shared/blob` e `shared/messaging`, ambos autenticados por **connection string** — a SAS é assinada com a chave da conta, isolada num único método do adapter para que a migração para *user delegation SAS* seja uma troca local. O emulador do Service Bus fica **fora da suíte padrão** (`@Tag("emulator")`, tarefa `emulatorTest`), e a garantia de contrato no dia a dia vem de um `EventPublisher` de teste que valida cada mensagem contra o JSON Schema de `contracts/events/` antes de aceitá-la.

## Alternativas consideradas

- **Identidade gerenciada com user delegation SAS desde já** — é o alvo para produção: nenhuma chave-mestra no processo, credencial revogável e auditável. Foi adiada porque exige o serviço já rodando em Container Apps com identidade atribuída para ser exercitada de verdade, e localmente obriga a habilitar OAuth e HTTPS no Azurite — fricção paga hoje por um benefício que só existe quando houver ambiente Azure. O adapter já isola a assinatura para que a troca não vaze para o domínio.
- **Ports dentro de `episode/`** — seria a leitura literal do package-by-feature, mas o `content` também vai ler blobs, e uma fatia importando `episode.storage` inverte a fronteira que o ADR-0013 existe para proteger. Infraestrutura genuinamente compartilhada em `shared` é a exceção honesta.
- **Emulador do Service Bus em todo `gradle test`** — daria cobertura real de entrega a cada execução, mas transformaria a suíte de segundos em minutos e tornaria o build dependente de baixar SQL Server. O teste existe e é obrigatório rodar ao mexer no publisher ou nos nomes de fila; só não roda por padrão.
- **Só validar o envelope contra o schema, sem emulador nenhum** — mais barato ainda, mas ninguém verificaria que a mensagem chega na fila com `messageId` e `contentType` corretos até o media-worker existir, que é tarde demais para descobrir.

## Consequências

- A configuração de segurança do storage é a mesma em qualquer ambiente: muda a connection string, não o código. Em compensação, a chave-mestra da conta vive no processo e no Key Vault — uma credencial vazada dá acesso total à conta, não só ao blob de um episódio. É a dívida principal deste ADR e a razão de a migração estar registrada, não esquecida.
- A versão da API de storage é fixada no cliente (`V2025_11_05`) em vez de flutuar com o SDK: a versão padrão do SDK é mais nova que qualquer Azurite lançado, e como ela também viaja dentro da SAS, deixá-la flutuar quebra o desenvolvimento local inteiro, não só os testes.
- Qualquer teste de fatia que publique um evento ganha validação de contrato de graça: o publisher de teste recusa payload fora do schema, então um `data` que divirja de `contracts/events/` derruba um teste mesmo sem broker envolvido.
- O custo é que a cobertura de entrega real depende de disciplina humana — `gradle emulatorTest` não roda sozinho. Quando houver CI, o lugar dele é um job noturno ou um gate de merge, não o build de cada commit.
- Nenhum dos dois ports abre conexão na subida do contexto (o contêiner do Blob é criado no primeiro uso, o sender do bus no primeiro envio), então os testes de fatia que não tocam em infraestrutura externa continuam subindo sem Azurite nem broker.
