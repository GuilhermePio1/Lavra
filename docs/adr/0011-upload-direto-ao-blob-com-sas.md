# ADR-0011: Upload do áudio bruto direto ao Blob com SAS

- **Status:** aceito
- **Data:** 2026-07-28

## Contexto

A spec 0001 e o `core-api.v1.yaml` previam `POST /api/v1/episodes` como `multipart/form-data`, com o Core API recebendo o arquivo e repassando ao Blob Storage. O limite documentado é de 2 GB — um episódio de 90 minutos em WAV ou FLAC chega perto disso — e o Core API roda em **Container Apps com escala a zero** (ADR-0005), onde cada upload prende uma réplica pelo tempo inteiro da transferência e esbarra no timeout de ingress (240 s no padrão). Pior: numa conexão doméstica instável, uma queda aos 80% descarta tudo e o criador recomeça do zero, porque HTTP multipart não tem retomada parcial. O byte do áudio bruto não precisa passar pelo Core API — ele não faz nada com o conteúdo além de armazená-lo.

## Decisão

O áudio bruto será enviado **direto do browser para o Blob Storage**, usando uma **SAS de escrita de escopo mínimo** emitida pelo Core API; o Core API deixa de trafegar o arquivo e passa a apenas autorizar, confirmar e disparar o pipeline.

O fluxo passa a ser:

1. `POST /api/v1/episodes` — valida autenticação, posse do show e **saldo de cota** (`403 PLAN_QUOTA_EXCEEDED`), cria o episódio em `PENDING_UPLOAD` e devolve uma SAS restrita a **escrita, um único blob** (`raw/{episodeId}/original.{ext}`), com TTL curto (2 h).
2. O frontend envia em blocos com o SDK do Azure Storage — retry por bloco, retomada e progresso real, sem passar pelo Core API.
3. `POST /api/v1/episodes/{id}/upload-complete` — o Core valida o blob (existência, tamanho, `Content-Type`), transiciona para `RECEIVED` e publica `episode.uploaded.v1`, seguindo daí o pipeline já especificado.

A máquina de estados ganha `PENDING_UPLOAD` como estado inicial, anterior a `RECEIVED`. Um job periódico remove episódios parados em `PENDING_UPLOAD` há mais de 24 h, junto com o blob parcial. O CORS da conta de storage é restrito à origem do frontend.

Como um arquivo de 2 GB em conexão residencial pode levar mais que o TTL da SAS, `POST /api/v1/episodes/{id}/upload-ticket` reemite a credencial para um episódio ainda em `PENDING_UPLOAD` — sem recriar o episódio nem rechecar cota, preservando os blocos já enviados. Sem isso, expirar no meio do envio significaria recomeçar do zero, que é exatamente o problema que este ADR existe para resolver.

## Alternativas consideradas

- **Manter multipart pela API (contrato v1 atual)** — um endpoint só e nenhum órfão possível, mas paga com o problema que motivou este ADR: réplica ocupada durante toda a transferência, teto de 240 s no ingress e nenhuma retomada. Para arquivos de centenas de MB em conexão residencial, é falha previsível, não caso de borda.
- **Sessão de upload separada (`POST /uploads` + `POST /episodes {uploadId}`)** — evitaria criar episódio antes dos bytes, mas o blob aterrissaria fora de `raw/{episodeId}/`, exigindo um *copy* server-side depois ou um layout de storage menos previsível. Trocar um estado explícito por um passo de cópia é pior negócio.
- **Event Grid `BlobCreated` no lugar da confirmação explícita** — dispensaria o `upload-complete` e seria imune ao usuário fechar a aba, mas acrescenta mais um serviço Azure ao trial e torna o disparo do pipeline assíncrono e mais difícil de testar localmente. Fica registrado como evolução caso a confirmação explícita se mostre frágil na prática.

## Consequências

- O Core API deixa de ser proporcional ao tamanho do arquivo: sem timeout de ingress, sem memória/disco efêmero consumidos por upload, e a réplica volta a escalar a zero durante a transferência. É também mais barato — o byte trafega uma vez só, em vez de duas.
- O criador ganha barra de progresso real e retomada por bloco, que é a diferença entre "subir um WAV de 90 minutos" ser viável ou não.
- Fica **pior no frontend**: dependência do `@azure/storage-blob`, CORS a configurar na conta de storage, e dois passos em vez de um — o que multiplica os estados de erro que a UI precisa tratar (SAS expirada durante upload lento, confirmação que falha com os bytes já no lugar).
- A validação do conteúdo do arquivo só acontece depois dos bytes gravados: o `upload-complete` checa metadados (tamanho, `Content-Type`), e a validação real do áudio continua onde já estava — no `ffprobe` do worker (spec 0002), que reporta `processing.failed` para arquivo corrompido.
- Risco de segurança assumido: uma SAS vazada permite escrever naquele blob até expirar. Mitigado pelo escopo mínimo (um blob, só escrita, 2 h) e pelo fato de que o blob pertence a um episódio já validado como do usuário; não há permissão de leitura, listagem nem de outro caminho.
- **O contrato REST v1 precisa mudar antes da implementação** (`POST /episodes` deixa de ser multipart, entra `POST /episodes/{id}/upload-complete`, `EpisodeStatus` ganha `PENDING_UPLOAD`), assim como a spec 0001. O contrato de evento `episode.uploaded.v1` permanece intacto — o caminho do blob e o momento da publicação não mudam.
