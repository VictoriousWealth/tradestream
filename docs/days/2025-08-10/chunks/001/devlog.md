# Devlog

## 2025-08-10 21:22:18 +0100 — commit `a4162e7`

This chunk is a single large bootstrap commit that brought `orders-service` into the runnable local stack and connected it to both sides of the trading flow already implied elsewhere in the repo: upstream order submission and downstream execution updates.

The evidence for that scope is distributed across:

- [`docker-compose.yml`](docker-compose.yml)
- [`services/orders-service/`](services/orders-service)
- [`services/market-data-consumer/src/main/java/com/tradestream/market_data_consumer/dto/TradeExecuted.java`](services/market-data-consumer/src/main/java/com/tradestream/market_data_consumer/dto/TradeExecuted.java)

The changed-file list also includes a large number of generated Gradle and build outputs under `services/orders-service/.gradle/` and `services/orders-service/build/`, which indicates the new service was not only scaffolded but built locally during the same commit.

### `orders-service` moved from deferred compose entry to active local runtime

Before this commit, [`docker-compose.yml`](docker-compose.yml) already contained an `orders-service` block, but it was marked with:

- `profiles: ["later"]`

That meant the service definition existed without being part of the default stack activation path.

After commit `a4162e7`, that profile gate was removed. The same patch also added two explicit topic environment variables to the compose definition:

- `KAFKA_TOPIC_ORDER_CANCELLED=order.cancelled.v1`
- `KAFKA_TOPIC_TRADE_EXECUTED=trade.executed.v1`

This changed the status of `orders-service` in a concrete way. Before the commit, the repo had a placeholder runtime slot for order management. After the commit, compose was configured to start the service as part of the normal application graph and to bind it to three named Kafka topics:

- `order.placed.v1`
- `order.cancelled.v1`
- `trade.executed.v1`

That is the earliest evidence in this chunk that the service was being treated as an active participant in the end-to-end trade lifecycle rather than a parked future component.

### The commit also corrected cross-service execution payload types

[`services/market-data-consumer/src/main/java/com/tradestream/market_data_consumer/dto/TradeExecuted.java`](services/market-data-consumer/src/main/java/com/tradestream/market_data_consumer/dto/TradeExecuted.java) changed in the same commit:

- `price` from `Double` to `BigDecimal`
- `quantity` from `Integer` to `BigDecimal`

This matters because `orders-service` was introduced in this chunk with `BigDecimal`-based order quantity and price handling throughout its domain model, DTOs, migrations, and event consumption path. The market-data consumer patch suggests the developer was aligning at least one other service’s representation of execution messages to the same precision model at the moment `orders-service` was added.

The evidence does not prove full repo-wide schema consistency yet, but it does show that introducing `orders-service` immediately exposed a need to tighten numeric types around `trade.executed`-style payloads.

## `orders-service` was introduced as a complete Spring Boot application, not a partial placeholder

The commit created the full project skeleton under [`services/orders-service/`](services/orders-service):

- [`.gitattributes`](services/orders-service/.gitattributes)
- [`.gitignore`](services/orders-service/.gitignore)
- [`build.gradle`](services/orders-service/build.gradle)
- [`settings.gradle`](services/orders-service/settings.gradle)
- [`Dockerfile`](services/orders-service/Dockerfile)
- Gradle wrapper files
- [`HELP.md`](services/orders-service/HELP.md)
- source tree under `src/main/java`
- migrations under `src/main/resources/db/migration`
- test scaffold under `src/test/java`

This was not a patch to an existing implemented service. Before `a4162e7`, the evidence for `orders-service` inside this chunk is limited to compose configuration and a pre-existing `application.yml` baseline. After the commit, the service had:

- build configuration
- packaging configuration
- a runnable application entrypoint
- persistence model
- REST API
- Kafka producer
- Kafka consumer
- Flyway migrations
- a basic Spring Boot test

### Package naming was normalized during project generation

[`services/orders-service/HELP.md`](services/orders-service/HELP.md) records a Spring Initializr generation note saying the original package name `com.tradestream.orders-service` was invalid and `com.tradestream.orders_service` was used instead.

That note is small, but it explains the underscore-based Java package naming found throughout the service. This is useful evidence because otherwise the underscore package layout could look like an arbitrary style choice rather than a generation-time correction.

## Build and runtime conventions were aligned with the repo’s Java service pattern

[`services/orders-service/build.gradle`](services/orders-service/build.gradle) and [`services/orders-service/Dockerfile`](services/orders-service/Dockerfile) establish the service as another Java 17 Spring Boot application built via Gradle and containerized from inside the service directory.

The generated/build-artifact footprint in the changed files shows:

- Gradle wrapper execution happened
- `bootJar`-style output was produced
- generated resources under `bin/main` mirror source resources

That means this commit did not stop at source authoring. The developer built the service and committed the resulting local build state as well.

I cannot state from this chunk alone whether committing `.gradle`, `bin`, and `build` outputs was intentional policy or incidental local residue, but the evidence is strong that the service was exercised enough to reach a successful local build.

## The first domain model centered on durable order state, fill tracking, and optimistic versioning

[`services/orders-service/src/main/java/com/tradestream/orders_service/domain/Order.java`](services/orders-service/src/main/java/com/tradestream/orders_service/domain/Order.java) is the core aggregate introduced in this commit.

Before this commit, there was no evidence-backed `Order` entity in this service. After it, the order model stored:

- `id`
- `userId`
- `ticker`
- `side`
- `type`
- `timeInForce`
- `quantity`
- `price`
- `status`
- `filledQuantity`
- `lastFillPrice`
- `createdAt`
- `updatedAt`
- `version`

Several implementation details matter:

- `price` is nullable, which matches market-order support
- `filledQuantity` defaults to zero
- `lastFillPrice` is nullable until execution arrives
- `@Version` adds optimistic-version metadata to the row
- ticker normalization is handled in entity lifecycle hooks by uppercasing on persist/update

The `remainingQuantity()` helper and `applyFill(BigDecimal execQty)` method show the intended state progression:

- a new order starts with no fills
- fills accumulate against `filledQuantity`
- status moves from `NEW` to `PARTIALLY_FILLED` or `FILLED` based on remaining quantity

That is a meaningful before-to-after shift. Before `a4162e7`, there was no local persistence contract for order lifecycle state in this chunk. After it, order rows could represent partial execution rather than only submission/cancellation.

### Enumerations made the service’s accepted state space explicit

The commit added these enums:

- [`OrderStatus.java`](services/orders-service/src/main/java/com/tradestream/orders_service/domain/OrderStatus.java)
- [`OrderType.java`](services/orders-service/src/main/java/com/tradestream/orders_service/domain/OrderType.java)
- [`Side.java`](services/orders-service/src/main/java/com/tradestream/orders_service/domain/Side.java)
- [`TimeInForce.java`](services/orders-service/src/main/java/com/tradestream/orders_service/domain/TimeInForce.java)

The visible status set includes:

- `NEW`
- `PARTIALLY_FILLED`
- `FILLED`
- `CANCELED`
- `REJECTED`
- `EXPIRED`

Only some of those states are actually exercised by this first implementation. The service code in this commit explicitly creates `NEW`, transitions to `CANCELED`, and updates to `PARTIALLY_FILLED` / `FILLED`. There is no evidence in this chunk of code paths that set `REJECTED` or `EXPIRED`.

So the enum space was broader than the implemented transition surface on day one.

## The API contract accepted full order intent from the caller, including `userId`

[`services/orders-service/src/main/java/com/tradestream/orders_service/dto/PlaceOrderRequest.java`](services/orders-service/src/main/java/com/tradestream/orders_service/dto/PlaceOrderRequest.java) introduced the first request model:

- `userId`
- `ticker`
- `side`
- `type`
- `timeInForce`
- `quantity`
- `price`

Validation in the record covers:

- `userId` required
- `ticker` not blank, max length 16
- enums required
- `quantity` minimum `0.000001`
- `price` minimum `0.000001` when present

[`OrderService.place(...)`](services/orders-service/src/main/java/com/tradestream/orders_service/service/OrderService.java) adds the missing business rules:

- LIMIT orders must provide `price`
- MARKET orders must not provide `price`

This means the API contract at introduction time trusted the client to supply `userId` in the JSON body. There is no evidence in this chunk of gateway-propagated authenticated identity or server-side derivation of the user. That is not an inference from repo-wide behavior; it is directly visible in the request DTO and service construction path.

[`OrderResponse.java`](services/orders-service/src/main/java/com/tradestream/orders_service/dto/OrderResponse.java) returned the persisted and derived state:

- original order intent fields
- `status`
- `filledQuantity`
- `remainingQuantity`
- `lastFillPrice`
- timestamps

So the first API was designed not just for fire-and-forget acceptance, but also for later inspection of execution progress.

## REST endpoints were minimal but already split into placement, lookup, and cancellation

[`services/orders-service/src/main/java/com/tradestream/orders_service/web/OrdersController.java`](services/orders-service/src/main/java/com/tradestream/orders_service/web/OrdersController.java) introduced three endpoints:

- `POST /orders`
- `GET /orders/{id}`
- `POST /orders/{id}/cancel`

The placement endpoint returned `202 Accepted`, not `201 Created`. That is a deliberate signal that order submission and actual execution are decoupled. The code confirms that interpretation: placement persists the order and publishes an event for matching instead of executing synchronously.

The lookup endpoint returned the `OrderResponse` projection of current order state.

The cancellation endpoint returned the domain `Order` object directly rather than the same response DTO used elsewhere. That asymmetry is already present in the first version.

The evidence does not show whether that mixed response shape was intentional or just expedient first-cut wiring, but it is a real API inconsistency introduced in this commit.

### Error handling was added for validation and invalid state transitions

[`services/orders-service/src/main/java/com/tradestream/orders_service/web/RestExceptionHandler.java`](services/orders-service/src/main/java/com/tradestream/orders_service/web/RestExceptionHandler.java) added mappings for:

- `IllegalArgumentException` -> `400 BAD_REQUEST`
- `MethodArgumentTypeMismatchException` -> `400 BAD_REQUEST`
- `MethodArgumentNotValidException` -> `400 BAD_REQUEST`
- `ValidationException` -> `400 BAD_REQUEST`
- `IllegalStateException` -> `409 CONFLICT`

The `409` mapping is specifically relevant to cancellation because `cancelOrder(...)` only allows cancellation for `NEW` orders.

One implementation detail is slightly uncertain from the evidence alone: the handler imports `org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException` instead of the more typical web-binding class. The diff shows that exact import, but this chunk alone does not show whether it compiled cleanly because the committed build outputs do not expose compiler diagnostics line by line. So the safest statement is that the error-handling intent is clear even if the specific exception type import may have been fragile.

## Placement flow became durable-first, then asynchronous

[`services/orders-service/src/main/java/com/tradestream/orders_service/service/OrderService.java`](services/orders-service/src/main/java/com/tradestream/orders_service/service/OrderService.java) is where the service’s initial orchestration logic lives.

The `place(...)` method performs work in this order:

1. validate LIMIT vs MARKET pricing rules
2. build an `Order` entity with `status = NEW`
3. save the order through [`OrderRepository`](services/orders-service/src/main/java/com/tradestream/orders_service/repo/OrderRepository.java)
4. construct an [`OrderPlaced`](services/orders-service/src/main/java/com/tradestream/orders_service/events/OrderPlaced.java) event from persisted state
5. publish that event through [`OrderProducer`](services/orders-service/src/main/java/com/tradestream/orders_service/kafka/OrderProducer.java)
6. return an `OrderResponse`

That sequence matters. Before this commit, no chunk-local order submission flow existed. After it, the service used the database record as the source of truth and then emitted a broker event keyed by ticker for downstream matching.

[`OrderProducer`](services/orders-service/src/main/java/com/tradestream/orders_service/kafka/OrderProducer.java) explicitly comments that the key is the ticker so the same symbol stays in the same partition. That is strong evidence that partition ordering by instrument was part of the design at first implementation.

### The first event payload for new orders was already richer than a bare command

[`OrderPlaced.java`](services/orders-service/src/main/java/com/tradestream/orders_service/events/OrderPlaced.java) carries:

- `orderId`
- `userId`
- `ticker`
- `side`
- `type`
- `timeInForce`
- `quantity`
- `price`
- `timestamp`

This was not a minimal internal trigger. It was a substantial event payload that could support downstream matching without rereading the order row.

The comment in the record says it is the payload sent to topic `order.placed.v1`, which matches the new compose and application topic configuration.

## Cancellation support was added in the same bootstrap commit, but only for untouched orders

The `cancelOrder(UUID orderId)` method in [`OrderService.java`](services/orders-service/src/main/java/com/tradestream/orders_service/service/OrderService.java) introduced immediate cancellation rules:

- if status is already `CANCELED`, return the order without publishing again
- if status is not `NEW`, throw `IllegalStateException`
- otherwise set status to `CANCELED`, update timestamp, save, and emit [`OrderCancelledEvent`](services/orders-service/src/main/java/com/tradestream/orders_service/events/OrderCancelledEvent.java)

This is a very specific first version of cancellation semantics. It does not allow cancellation of partially filled orders. It does not try to send a compensating message for already-executing work. It treats cancellation as valid only before the order leaves the untouched `NEW` state.

[`OrderCancelledEvent.java`](services/orders-service/src/main/java/com/tradestream/orders_service/events/OrderCancelledEvent.java) carries:

- `orderId`
- `userId`
- `ticker`
- `quantity`
- `price`
- `timestamp`

Unlike `OrderPlaced`, it does not include side, type, or time-in-force. The evidence suggests the cancellation event only carried the minimum needed for downstream removal/invalidation, but the chunk does not show the consumer of that event, so anything beyond that would be speculation.

### Cancellation publishing bypassed the dedicated producer abstraction

`OrderService` uses `OrderProducer` for order placement, but sends cancellation events directly with `KafkaTemplate<String, Object>`.

That inconsistency is visible in the diff and worth recording because it shows the abstraction boundary was incomplete in the first cut:

- placed orders had a dedicated producer component
- cancelled orders did not

This is not necessarily a bug, but it does show the service was introduced with one side of event publishing already wrapped and the other side still inlined.

## Execution consumption was idempotent and row-locking from the start

The other major behavior added in this commit is downstream trade ingestion through [`TradeExecutedConsumer.java`](services/orders-service/src/main/java/com/tradestream/orders_service/kafka/TradeExecutedConsumer.java).

This consumer listens to:

- `${tradestream.topics.tradeExecuted:trade.executed.v1}`

with default group:

- `orders-exec-consumer`

and handles [`TradeExecuted`](services/orders-service/src/main/java/com/tradestream/orders_service/dto/TradeExecuted.java) messages containing:

- `tradeId`
- `orderId`
- `userId`
- `ticker`
- `price`
- `quantity`
- `side`
- `timestamp`

The consumption algorithm is detailed and important:

1. insert `tradeId` into `ingested_trades` using `ON CONFLICT DO NOTHING`
2. if insert count is zero, treat the message as duplicate and stop
3. lock the target order row using `findByIdForUpdate(...)`
4. if the order does not exist, log a warning and stop
5. if the order is already `CANCELED` or `FILLED`, log a warning and stop
6. cap execution quantity at remaining quantity to prevent overfill from malformed events
7. call `order.applyFill(execQty)`
8. update `updatedAt` and `lastFillPrice`
9. save the order

This is a stronger first implementation than a naive consumer. The evidence shows the developer explicitly accounted for:

- duplicate delivery
- unknown-order execution events
- terminal-state protection
- overfill protection
- pessimistic row locking

That matters because it means the first version of `orders-service` did not treat `trade.executed` as an append-only notification; it treated it as a potentially duplicated and potentially unsafe external signal that had to be reconciled against current order state.

### There was already a deliberate split between optimistic entity versioning and pessimistic execution updates

[`Order.java`](services/orders-service/src/main/java/com/tradestream/orders_service/domain/Order.java) carries `@Version`, but [`OrderRepository.java`](services/orders-service/src/main/java/com/tradestream/orders_service/repo/OrderRepository.java) also adds:

- `@Lock(LockModeType.PESSIMISTIC_WRITE)`
- `findByIdForUpdate(UUID id)`

So the service combined:

- optimistic version metadata on the entity
- pessimistic locking for execution application

The chunk evidence does not show any code using the version field directly in business logic, but it does show that execution handling was intentionally serialized at the row level while other persistence paths still carried version information.

## The database schema evolved in three migrations within the same first commit

The migration sequence under [`services/orders-service/src/main/resources/db/migration/`](services/orders-service/src/main/resources/db/migration/) is especially informative because it reveals the model was already split into successive concerns instead of one monolithic SQL file.

### `V1__init_orders.sql` created the base order ledger

[`V1__init_orders.sql`](services/orders-service/src/main/resources/db/migration/V1__init_orders.sql) created `orders` with:

- UUID primary key
- user, instrument, side, type, and time-in-force columns
- numeric quantity
- nullable numeric price
- textual status
- timestamps
- version column
- indexes on `user_id` and `ticker`

This is the base persistence contract for accepted orders.

### `V2__order_fill_tracking.sql` added execution-state columns

[`V2__order_fill_tracking.sql`](services/orders-service/src/main/resources/db/migration/V2__order_fill_tracking.sql) added:

- `filled_quantity`
- `last_fill_price`

That means fill tracking was treated as a distinct concern layered onto the base order table even within the same commit. The order model was not frozen at “accept order request” level; execution reconciliation was part of the initial design.

### `V3__ingested_trades.sql` added a dedicated idempotency ledger

[`V3__ingested_trades.sql`](services/orders-service/src/main/resources/db/migration/V3__ingested_trades.sql) created:

- `ingested_trades`

with:

- primary key `trade_id`
- `order_id`
- `ticker`
- `ts`
- index on `order_id`

This table directly supports the `tryInsert(...)` logic in [`IngestedTradeRepository.java`](services/orders-service/src/main/java/com/tradestream/orders_service/repo/IngestedTradeRepository.java).

The before-to-after evolution across the migration set is clear:

- before: no orders schema in this service is evidenced
- after V1: durable order acceptance exists
- after V2: partial/full fill state is representable
- after V3: duplicate execution message handling is durable, not in-memory

## Configuration changed from generic Spring defaults to a service-specific Kafka-and-Postgres contract

[`services/orders-service/src/main/resources/application.yml`](services/orders-service/src/main/resources/application.yml) was not created from scratch in this commit; it existed before and was heavily rewritten.

Before the patch, the visible configuration was generic:

- `spring.application.name: ${APP_NAME:service}`
- no Kafka section
- `server.port: ${SERVER_PORT:8080}`
- management port comment aimed at same-port health checks

After the patch, the file became specific to this service:

- `spring.application.name: orders-service`
- Kafka producer config using JSON serialization
- Kafka consumer config using error-handling deserializers
- default trusted packages `com.tradestream.*`
- default consumer payload type `com.tradestream.orders_service.dto.TradeExecuted`
- `server.port: 8085`
- logging for Spring Web set to `DEBUG`
- topic names exposed under `tradestream.topics.*`

This is an important shift because it shows the commit was not only about adding Java classes. It converted the service from a generic app skeleton into a configured participant in the repo’s broker topology.

### Source and generated resources both reflect the same topic contract

The changed-file list includes both:

- `src/main/resources/application.yml`
- `bin/main/application.yml`

as well as the migration files under both source and generated-resource paths.

That reinforces the earlier point that the service was built locally in the same commit. It also means the Kafka topic naming and DB migration layout were not merely authored; they were carried through the build pipeline.

## The test surface at introduction time was minimal

[`services/orders-service/src/test/java/com/tradestream/orders_service/OrdersServiceApplicationTests.java`](services/orders-service/src/test/java/com/tradestream/orders_service/OrdersServiceApplicationTests.java) contains only a `contextLoads()` test.

So while the service logic introduced in `a4162e7` includes substantial behavior around:

- request validation
- cancellation state transitions
- duplicate execution detection
- row locking
- overfill capping

the evidence in this chunk shows only a bootstrapping test, not behavior-level coverage.

That is an important limitation to record because the same commit introduced several non-trivial paths without accompanying unit or integration tests in the visible patch.

## The first cut already exposed some implementation asymmetries and likely follow-up areas

Several details in the evidence suggest this was a functional but still early version:

- placement returns `OrderResponse`, while cancellation returns the entity `Order`
- order placement publishes through `OrderProducer`, while cancellation publishes directly via `KafkaTemplate`
- the enum set includes statuses not exercised by any visible path in this chunk
- user identity is accepted from the request body rather than derived internally
- build outputs and Gradle internals were committed alongside source changes

None of these observations require external repo history; they are all visible directly in commit `a4162e7`.

The safest conclusion from this chunk’s evidence is that 2025-08-10 was the date `orders-service` became real in the codebase as a database-backed, REST-fronted, Kafka-integrated service with both outbound order events and inbound execution reconciliation, while still carrying the uneven edges typical of a first end-to-end implementation.
