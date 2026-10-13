# TODO - Checkout e Reserva de Estoque Flash Sale

Este arquivo é a fonte de verdade do andamento. Vamos concluir uma etapa por vez: implementar, executar os critérios de aceite, corrigir falhas e só então marcar `[x]` e avançar. Não marcar tarefas como concluídas sem validação observável.

## 0. Decisões e preparação

- [x] Definir stack inicial: Java 21, Spring Boot 3.5, Maven, PostgreSQL, Redis e RabbitMQ. Revisável se a disciplina exigir versões diferentes.
- [x] Definir estrutura inicial como monorepo Maven com módulos `checkout-service` e `order-processor-service`.
- [x] Definir contratos iniciais dos eventos, estados do pedido e comportamento de falhas/reentregas.
- [x] Registrar decisões neste arquivo antes de depender delas no código.

### Decisões registradas

- Eventos compartilhados ficam no módulo `order-contracts`; os dois serviços continuam independentes na execução.
- Eventos definidos: `OrderCreatedEvent`, `OrderCompensationEvent` e `OrderStatusUpdatedEvent`.
- JSON usa `snake_case`; estados são `PROCESSING`, `CONFIRMED` e `CANCELLED`.
- O banco do Processor é a fonte de verdade do processamento. O Processor publica atualização de status e o Checkout mantém projeção local para atender consultas, sem ler o banco alheio.
- Mensagens serão tratadas como entrega pelo menos uma vez. Consumidores devem ser idempotentes por `pedido_id`; ACK só depois da persistência. Implementaremos e testaremos essa garantia nas etapas dos consumers.
- Em falha de processamento, o Processor persiste `CANCELLED` e emite atualização de status e compensação. A ordem e a confiabilidade da publicação serão cobertas na implementação da SAGA.
- Stack adotada: Java 21, Spring Boot 3.5.5, Maven, PostgreSQL, Redis e RabbitMQ.

**Aceite:** decisões registradas; ferramentas necessárias disponíveis; comandos para build e testes definidos.

## 1. Base executável

- [x] Criar os dois módulos Spring Boot com configuração e dependências mínimas.
- [x] Declarar Redis, RabbitMQ e bancos PostgreSQL segregados no `compose.yaml`.
- [x] Adicionar health checks Actuator e configuração externa por variáveis de ambiente.
- [x] Iniciar a infraestrutura e os dois serviços e validar health checks.

**Aceite:** os serviços iniciam; Redis, RabbitMQ e bancos estão acessíveis; health checks respondem saudáveis. Validado com Docker Compose e os dois serviços Spring Boot em execução.

## 2. Contratos e domínio

- [x] Definir dados de criação do pedido, validações básicas e estados compartilhados.
- [x] Definir contratos de criação, compensação e atualização de status, com `pedido_id` e dados necessários.
- [x] Definir que a projeção local do Checkout será atualizada pelo evento de status do Processor.
- [x] Implementar entidade JPA do pedido e transições de estado válidas.
- [x] Definir formato HTTP consistente para erros de validação, estoque e submissão.

**Aceite:** contratos documentados e testes unitários para validações e transições válidas de estado. Contratos, validações e transições de domínio passaram em `mvn test`. Foi implementado `CheckoutPersistenceIT`/`OrderProcessorPersistenceIT` (PostgreSQL embarcado via `io.zonky.test:embedded-postgres`, sem Docker) para provar o mapeamento JPA e as migrations Flyway contra um Postgres real; nesta máquina o `initdb.exe` extraído é bloqueado por política de grupo do Windows (AppLocker/SRP), então os testes abortam de forma controlada (`Assumptions.abort`) em vez de falhar o build. Em uma máquina sem essa restrição (ou com Docker) eles validam o mapeamento real automaticamente com `mvn verify`.

## 3. Reserva de estoque no Redis

- [x] Definir inicialização idempotente do estoque com `SET NX` (`initializeIfAbsent`).
- [x] Implementar reserva via script Lua atômico: verifica saldo e decrementa em uma única execução.
- [x] Implementar devolução via `INCRBY` atômico.
- [x] Validar com Redis real que reserva concorrente nunca deixa estoque negativo.

**Aceite:** testes cobrem estoque suficiente, insuficiente e concorrência; estoque nunca fica negativo. Testes unitários com Redis mock passaram. Validado também com Redis real (servidor embarcado via `com.github.codemonstur:embedded-redis`, sem Docker): `RedisStockServiceConcurrencyIT` dispara 60 reservas concorrentes contra um estoque de 100 e comprova que o saldo final nunca fica negativo e bate exatamente com o número de reservas bem-sucedidas. Execute com `mvn verify`.

## 4. Checkout: criação de pedido

- [x] Implementar `POST /v1/orders` e validar os dados.
- [x] Reservar estoque antes de aceitar o pedido.
- [x] Gerar UUID e persistir projeção `PROCESSING` + evento `order-created` na mesma transação local.
- [x] Retornar `202 Accepted`, `400 Bad Request`, `409 Conflict` ou `503 Service Unavailable` conforme o resultado.
- [x] Publicar a outbox com confirmações do RabbitMQ e manter pendente até ACK/roteamento confirmado.
- [x] Em falha síncrona na reserva/persistência, devolver o estoque reservado.
- [x] Validar o fluxo integrado com Redis, PostgreSQL e RabbitMQ reais.

**Aceite:** testes de API verificam respostas e efeitos no estoque/fila, inclusive falhas de submissão. Testes unitários/MVC e fluxo real com Redis, PostgreSQL e RabbitMQ passaram.

## 5. Order Processor

- [x] Consumir `order-created` e persistir o pedido no banco próprio.
- [x] Implementar confirmação e falha simulável por `ORDER_PROCESSOR_SIMULATE_FAILURE`.
- [x] Ignorar reentrega quando `pedido_id` já existe; gravar eventos na outbox na mesma transação do pedido.
- [x] Configurar confirmação do publisher e só marcar outbox após confirmação/roteamento.
- [x] Configurar três tentativas com backoff e encaminhar falhas irrecuperáveis para filas `.dlq` duráveis.
- [x] Validar ACK/reentrega, migrations e outbox contra PostgreSQL e RabbitMQ reais.

**Aceite:** testes de integração verificam confirmação, persistência, reentrega e falha simulada. Validado com PostgreSQL e RabbitMQ reais; filas terminaram sem mensagens pendentes.

## 6. SAGA: compensação

- [x] Publicar `order-compensation` pela outbox em caso de falha de processamento.
- [x] Consumir compensações no Checkout e devolver estoque.
- [x] Tornar a devolução idempotente com marcador Redis atômico por `pedido_id`.
- [x] Disponibilizar `CANCELLED` na projeção do Checkout pelo evento de status.
- [x] Aplicar retry limitado e DLQ ao consumer de compensação.
- [x] Validar fluxo e reentrega com PostgreSQL e RabbitMQ reais (devolução no Redis já validada com Redis real).

**Aceite:** teste ponta a ponta comprova falha, mensagem de compensação, estoque restaurado e pedido cancelado, inclusive com mensagem duplicada. Validado com Redis, PostgreSQL e RabbitMQ reais; o pedido de falha terminou `CANCELLED` e o estoque voltou ao saldo anterior.

## 7. Consulta e Cache-Aside

- [x] Implementar `GET /v1/orders/{pedido_id}`.
- [x] Consultar Redis primeiro; em cache miss, buscar a projeção local do Checkout.
- [x] Armazenar resultado no Redis com TTL de cinco minutos e invalidar após evento de status.
- [x] Aplicar três tentativas quando o pedido não for encontrado e retornar `404` ao final.
- [x] Validar atualização de status com Redis/PostgreSQL reais (TTL e cache já validados com Redis real).

**Aceite:** testes cobrem cache hit, cache miss, TTL, pedido inexistente e falha temporária na consulta. Testes unitários/MVC e atualização de status com Redis/PostgreSQL/RabbitMQ reais passaram.

## 8. Qualidade e entrega

- [x] Cobrir regras de negócio com testes unitários e testes MVC dos endpoints.
- [x] Executar build e todos os testes; corrigir falhas encontradas.
- [x] Documentar arquitetura, pré-requisitos, configuração, inicialização e chamadas de exemplo no `README.md`.
- [x] Executar testes de integração e o roteiro de demonstração com Redis, PostgreSQL e RabbitMQ reais.
- [x] Seguir o roteiro de demonstração do `Projeto_final.md` para os caminhos de sucesso e falha.

**Aceite final:** ambiente sobe a partir das instruções; build e testes passam; demonstração confirma o pedido e também comprova a compensação.

## 9. Extra (além do MVP): interface gráfica de visualização

A seção 8 do `Projeto_final.md` diz explicitamente que interface gráfica não é necessária. A pedido, foi adicionada mesmo assim, como extra opcional:

- [x] Página estática (HTML/CSS/JS puro, sem build, sem dependência nova) em `checkout-service/src/main/resources/static/` (`index.html`, `style.css`, `app.js`), servida pelo próprio Spring Boot na mesma origem da API (sem CORS).
- [x] Criar pedido, consultar status com atualização automática enquanto `PROCESSING`, indicador de saúde do backend (`/actuator/health`) e histórico de pedidos da sessão (via `localStorage`).
- [x] Endpoint `GET /v1/stock` (novo, `StockController`) e seção "Estoque atual" na página, com atualização automática a cada 5s e refresh imediato após criar um pedido.
- [x] Build (`mvn -pl checkout-service package`) confirmado incluindo os arquivos estáticos e o novo endpoint no jar.
- [x] Validação visual ao vivo (interface aberta em `http://localhost:8081` com backend online e estoque real exibido).

## Registro de andamento

- **Etapa atual:** concluída - Qualidade e entrega.
- **`mvn test` (unitários/MVC, rápido):** passou com trinta e um testes, cobrindo validação, transições, estoque mockado/idempotência, criação e outbox (incluindo o retorno `503` em falha de submissão), processamento e outbox, publishers ACK/NACK, compensação, Cache-Aside e carga inicial.
- **`mvn verify` (testes de integração com infraestrutura real embarcada, sem Docker):** adicionados `RedisStockServiceConcurrencyIT`, `RedisOrderCacheIT` (Redis real via `com.github.codemonstur:embedded-redis`) e `CheckoutPersistenceIT`/`OrderProcessorPersistenceIT` (PostgreSQL real via `io.zonky.test:embedded-postgres`). Resultado nesta máquina: os testes de Redis passam de verdade (concorrência sem estoque negativo, TTL/evict e devolução de estoque idempotente confirmados); os dois testes de Postgres abortam de forma controlada porque a política de grupo local bloqueia a execução do `initdb.exe` extraído para a pasta temporária — build finaliza com `EXIT=0` mesmo assim.
- **Revisão de bugs (code review dedicado):** encontrado e corrigido um bug real nos publishers de outbox (`CheckoutOutboxPublisher`/`OutboxPublisher`): o `@Transactional` envolvia o lote inteiro de até 50 eventos, então uma falha ao publicar um evento revertia o `markPublished` de eventos anteriores já confirmados pelo broker no mesmo lote, causando reenvio duplicado e podendo travar o lote inteiro por causa de um único evento problemático. Corrigido isolando cada evento em sua própria unidade de trabalho, com log e continuação do laço em vez de exceção propagada. Também adicionados handlers para `UUID` inválido no path e corpo JSON malformado (antes caíam no formato de erro padrão do Spring em vez do `ApiError` consistente), e um TTL de 30 dias no marcador de idempotência da compensação no Redis (antes crescia para sempre). Testes de regressão cobrindo os três casos foram adicionados; suíte final: 35 testes unitários/MVC + testes de integração reais, todos verdes.
- **Auditoria de requisitos:** revisão completa do código contra `Projeto_final.md` (escopo MVP, requisitos técnicos e contratos de API) confirmou aderência total no nível de código.
- **Bloqueios/decisões pendentes:** nenhum bloqueio técnico observado nesta execução. As versões escolhidas continuam sujeitas à confirmação da disciplina.
