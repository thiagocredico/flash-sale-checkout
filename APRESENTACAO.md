# Roteiro de Explicação Técnica — Apresentação

Este arquivo existe para atender à observação do `Projeto_final.md`; Ele resume as decisões de arquitetura e o **porquê** de cada uma, no formato pergunta provável → resposta, com o arquivo exato para abrir ao vivo se alguém pedir para ver o código.

## 1. Visão geral em uma frase

Dois microsserviços Spring Boot independentes (`checkout-service`, `order-processor-service`) trocam eventos por RabbitMQ, usando Redis para reserva atômica de estoque e cache de consulta, com uma SAGA coreografada simplificada para compensar falhas — cada serviço com seu próprio banco Postgres.

## 2. Por que outbox pattern em vez de publicar direto no RabbitMQ?

**Pergunta provável:** "Por que não chamar `rabbitTemplate.send()` direto no controller?"

**Resposta:** Se a reserva no Redis e a persistência local tiverem sucesso mas o RabbitMQ estiver indisponível no exato momento da chamada, a mensagem se perde e o pedido fica "preso" em `PROCESSING` para sempre. Com outbox:

1. O evento é gravado na mesma transação do banco local ([CheckoutOrderPersistence.java](checkout-service/src/main/java/br/com/ada/flashsale/checkout/persistence/CheckoutOrderPersistence.java)) — nunca perdemos o registro de que o evento _precisa_ ser enviado.
2. Um publisher agendado ([CheckoutOutboxPublisher.java](checkout-service/src/main/java/br/com/ada/flashsale/checkout/messaging/CheckoutOutboxPublisher.java)) tenta enviar a cada segundo e só marca como publicado depois de receber confirmação do broker (`publisher-confirm-type: correlated`).
3. Se o Rabbit cair, o evento continua pendente na tabela `checkout_outbox_event` e é reenviado automaticamente quando o broker voltar.

O mesmo padrão existe no `order-processor-service` ([OutboxPublisher.java](order-processor-service/src/main/java/br/com/ada/flashsale/processor/messaging/OutboxPublisher.java)).

## 3. Por que um script Lua para reservar estoque, e não `GET` + `DECRBY` em duas chamadas?

**Pergunta provável:** "Por que não fazer `GET` para checar saldo e depois `DECRBY`?"

**Resposta:** Duas chamadas separadas criam uma _race condition_ clássica: duas requisições podem ler o mesmo saldo (ex: 1 unidade) antes de qualquer uma decrementar, e as duas decrementam — estoque fica negativo (_overselling_, exatamente o problema que o spec pede para evitar). O Redis executa scripts Lua de forma atômica (single-threaded), então [RedisStockService.java](checkout-service/src/main/java/br/com/ada/flashsale/checkout/stock/RedisStockService.java) faz a checagem e o decremento **na mesma operação indivisível**:

```lua
local available = redis.call('GET', KEYS[1])
if not available then return 0 end
available = tonumber(available)
local requested = tonumber(ARGV[1])
if available < requested then return 0 end
redis.call('DECRBY', KEYS[1], requested)
return 1
```

Isso foi **provado com Redis real** (não só mock) em `RedisStockServiceConcurrencyIT`: 60 threads reservando ao mesmo tempo contra estoque de 100 nunca deixam o saldo negativo.

## 4. Por que a devolução de estoque (compensação) também precisa ser atômica e idempotente?

**Pergunta provável:** "E se a mensagem de compensação chegar duas vezes?"

**Resposta:** RabbitMQ garante entrega "pelo menos uma vez" — reentrega duplicada é esperada, não uma exceção. Se devolvêssemos o estoque toda vez que a mensagem chegasse, um reenvio duplicado devolveria estoque duas vezes (inflando o saldo incorretamente). A solução: gravar um marcador `stock-compensated:<pedido_id>` no mesmo script Lua que faz o `INCRBY`, e checar sua existência antes de devolver:

```lua
if redis.call('EXISTS', KEYS[2]) == 1 then return -1 end
local available = redis.call('INCRBY', KEYS[1], ARGV[1])
redis.call('SET', KEYS[2], '1', 'EX', ARGV[2])
return available
```

Isso é o que a seção 4.3 do spec chama de "registrar que a compensação foi concluída". O marcador tem TTL de 30 dias (`ARGV[2]`) para não crescer indefinidamente no Redis — janela bem maior que qualquer reentrega realista do RabbitMQ. Provado contra **Redis real** em `RedisStockServiceConcurrencyIT.restoreOnceReturnsReservedStockAfterCompensation` e `restoreOnceIsIdempotentForSameOrderAgainstRealRedis`.

## 5. Por que existe uma fila `order-status-updated` que não está no diagrama do spec?

**Pergunta provável:** "O diagrama do spec não mostra essa fila, por que vocês adicionaram?"

**Resposta:** O spec exige "Banco segregado" (seção 6) — o Checkout não deve ler o banco do Processor diretamente. Mas o Checkout precisa saber quando um pedido vira `CONFIRMED` ou `CANCELLED` para responder corretamente no `GET /v1/orders/{pedido_id}`. A solução: o Processor publica um evento de status (`OrderStatusUpdatedEvent`) toda vez que processa um pedido (sucesso ou falha), e o Checkout consome esse evento só para atualizar sua **projeção local** ([OrderStatusListener.java](checkout-service/src/main/java/br/com/ada/flashsale/checkout/messaging/OrderStatusListener.java)). É uma extensão deliberada do diagrama simplificado do spec, não uma divergência — resolve uma lacuna que o diagrama não detalha.

## 6. Por que Cache-Aside e não Write-Through ou cache-first sempre?

**Pergunta provável:** "Por que consultar o Redis antes do banco, e não o contrário?"

**Resposta:** É exatamente o padrão pedido no spec (seção 4.2): tentar o cache primeiro evita ida ao banco na maioria das consultas repetidas (rápido, barato). Em caso de _miss_, buscamos no Postgres (fonte da verdade) e _só então_ preenchemos o cache com TTL de 5 minutos — se preenchêssemos o cache antes de confirmar que o dado existe, arriscaríamos cachear "vazio" incorretamente. Ver [OrderQueryService.java](checkout-service/src/main/java/br/com/ada/flashsale/checkout/api/OrderQueryService.java). Provado contra **Redis real**: `RedisOrderCacheIT` confirma o TTL de 5 minutos e o comportamento de eviction.

## 7. Por que 3 tentativas antes do 404, e não apenas 1?

**Pergunta provável:** "Por que tentar 3 vezes? Não seria mais simples retornar 404 direto?"

**Resposta:** Existe uma janela de tempo entre o Checkout aceitar o pedido (`202`) e o Processor efetivamente persistir o status — é assíncrono. Se o cliente consultar `GET` muito rápido, é possível que a projeção ainda não exista fisicamente por uma fração de segundo em cenários de alta concorrência/latência de fila. As 3 tentativas com pequeno intervalo (`MAX_ATTEMPTS = 3` em `OrderQueryService`) dão uma margem antes de declarar "não encontrado" definitivamente. É exigência explícita do spec (seção 6, linha "Retry").

## 8. Por que idempotência no consumo de `order-created` (ignorar reentrega)?

**Pergunta provável:** "O que acontece se o RabbitMQ reenviar a mesma mensagem de pedido criado?"

**Resposta:** [OrderProcessorService.java](order-processor-service/src/main/java/br/com/ada/flashsale/processor/service/OrderProcessorService.java) verifica `orderRepository.existsById(orderId)` antes de processar; se já existe, a mensagem é ignorada silenciosamente. Isso evita processar o mesmo pedido duas vezes (o que poderia gerar dois eventos de status ou duas compensações) em caso de reentrega — comportamento esperado de filas com garantia "at-least-once".

## 9. Como a falha é simulada, já que não existe gateway de pagamento real?

**Pergunta provável:** "Como vocês simulam uma falha de pagamento?"

**Resposta:** Exatamente como a seção 8 do spec sugere ("falha configurável"): a variável de ambiente `ORDER_PROCESSOR_SIMULATE_FAILURE=true` faz todo pedido recebido nessa execução do processor ser cancelado (`order.cancel()`) em vez de confirmado, disparando o fluxo de compensação. É uma chave de configuração, não uma regra de negócio real de pagamento — deliberadamente simples, como o spec permite.

## 10. Como foram testados fluxos que dependem de Redis/RabbitMQ/Postgres sem Docker?

**Pergunta provável:** "Vocês não têm Docker, como testaram isso de verdade?"

**Resposta:** Dois níveis de teste:

- `mvn test`: 35 testes unitários/MVC com mocks (Mockito) para regras de negócio e endpoints — rápidos, sempre rodam.
- `mvn verify`: testes de integração com **servidores reais embarcados em processo** (sem Docker): `RedisStockServiceConcurrencyIT` (reserva concorrente sem overselling, mais devolução idempotente de estoque) e `RedisOrderCacheIT` (TTL/eviction) sobem um Redis real (`com.github.codemonstur:embedded-redis`). `CheckoutPersistenceIT`/`OrderProcessorPersistenceIT` tentam o mesmo com PostgreSQL embarcado (`io.zonky.test:embedded-postgres`), mas nesta máquina específica a política de grupo corporativa bloqueia a execução do binário extraído — os testes abortam de forma controlada em vez de falhar o build (`Assumptions.abort`).
- RabbitMQ real e o roteiro de demonstração ponta a ponta (seção 9 do spec) continuam dependendo de um ambiente com Docker disponível — não há substituto embarcado equivalente para um broker AMQP completo.

## 11. Encontramos e corrigimos um bug real durante a revisão de código — vale mencionar na apresentação

**Pergunta provável:** "Vocês revisaram o próprio código gerado? Encontraram algum problema?"

**Resposta:** Sim. Os publishers de outbox (`CheckoutOutboxPublisher`/`OutboxPublisher`) originalmente envolviam o lote inteiro de até 50 eventos em um único `@Transactional`. Se a publicação de um evento falhasse no meio do lote, a exceção revertia a transação **inteira** — desfazendo o `markPublished` de eventos anteriores que o RabbitMQ já havia confirmado com sucesso. Consequência real: reenvio duplicado de mensagens já entregues, e um único evento problemático travava o lote inteiro indefinidamente. Corrigimos isolando cada evento em sua própria unidade de trabalho (falha de um não afeta nem reverte os demais) e adicionamos um teste de regressão (`aFailingEventDoesNotBlockOrRollBackOtherEventsInTheSameBatch`) provando isso. Também fechamos duas lacunas de consistência HTTP (UUID inválido no path e corpo JSON malformado agora respondem `400` no mesmo formato `ApiError`, em vez do formato padrão do Spring) e demos TTL ao marcador de compensação (ver item 4). Isso demonstra revisão real do código gerado por LLM, como a seção 8 do spec pede.

## 12. Se pedirem para rodar a demonstração ao vivo (seção 9 do spec)

Se houver Docker disponível na hora da apresentação:

```powershell
docker compose up -d
mvn -pl checkout-service spring-boot:run      # terminal 1
mvn -pl order-processor-service spring-boot:run  # terminal 2 (adicionar ORDER_PROCESSOR_SIMULATE_FAILURE=true para demonstrar falha)
```

Depois seguir os comandos `curl` do [README.md](README.md) para criar e consultar pedidos, e mostrar as filas/mensagens no painel do RabbitMQ (`http://localhost:15672`).

## 13. Arquivos-chave para abrir ao vivo, por assunto

| Assunto                        | Arquivo                                                                                                                                                                                                                                                            |
| ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Reserva atômica de estoque     | [RedisStockService.java](checkout-service/src/main/java/br/com/ada/flashsale/checkout/stock/RedisStockService.java)                                                                                                                                                |
| Criação de pedido / outbox     | [CreateOrderService.java](checkout-service/src/main/java/br/com/ada/flashsale/checkout/api/CreateOrderService.java), [CheckoutOrderPersistence.java](checkout-service/src/main/java/br/com/ada/flashsale/checkout/persistence/CheckoutOrderPersistence.java)       |
| Cache-Aside                    | [OrderQueryService.java](checkout-service/src/main/java/br/com/ada/flashsale/checkout/api/OrderQueryService.java), [RedisOrderCache.java](checkout-service/src/main/java/br/com/ada/flashsale/checkout/cache/RedisOrderCache.java)                                 |
| Compensação                    | [OrderCompensationListener.java](checkout-service/src/main/java/br/com/ada/flashsale/checkout/messaging/OrderCompensationListener.java)                                                                                                                            |
| Processamento e falha simulada | [OrderProcessorService.java](order-processor-service/src/main/java/br/com/ada/flashsale/processor/service/OrderProcessorService.java)                                                                                                                              |
| Tratamento de erros HTTP       | [CheckoutExceptionHandler.java](checkout-service/src/main/java/br/com/ada/flashsale/checkout/api/CheckoutExceptionHandler.java)                                                                                                                                    |
| Outbox (bug corrigido)         | [CheckoutOutboxPublisher.java](checkout-service/src/main/java/br/com/ada/flashsale/checkout/messaging/CheckoutOutboxPublisher.java), [OutboxPublisher.java](order-processor-service/src/main/java/br/com/ada/flashsale/processor/messaging/OutboxPublisher.java)   |
| Contratos de eventos           | [order-contracts](order-contracts/src/main/java/br/com/ada/flashsale/contracts)                                                                                                                                                                                    |
| Testes com infra real          | [RedisStockServiceConcurrencyIT.java](checkout-service/src/test/java/br/com/ada/flashsale/checkout/stock/RedisStockServiceConcurrencyIT.java), [RedisOrderCacheIT.java](checkout-service/src/test/java/br/com/ada/flashsale/checkout/cache/RedisOrderCacheIT.java) |
