# Devlog

## 2025-08-11 15:34:21 +0100 — commit `870d88c8a2a848514341adce65b43d4082217ced`

This chunk is a single bootstrap commit that turned `matching-engine` from a deferred compose placeholder into a built Spring Boot service with:

- its own Postgres database
- Kafka consumers for `order.placed.v1` and `order.cancelled.v1`
- in-memory per-ticker order books
- persistence for resting orders and processed message IDs
- Kafka publication of `trade.executed.v1`
- warm-start loading from the database
- retry/DLT handling for poisoned records

The scope is visible across:

- [`docker-compose.yml`](docker-compose.yml)
- the full new [`services/matching-engine/`](services/matching-engine) tree
- generated Gradle/build outputs under `services/matching-engine/.gradle/`, `build/`, and `bin/`

The changed-file set also shows one unrelated generated-cache update in [`services/orders-service/.gradle/nb-cache/subprojects.ser`](services/orders-service/.gradle/nb-cache/subprojects.ser), but there is no source-level `orders-service` change in this chunk.

### `matching-engine` moved from parked future service to active compose service

Before commit `870d88c8a2a848514341adce65b43d4082217ced`, the `matching-engine` block in [`docker-compose.yml`](docker-compose.yml) still had:

- `profiles: ["later"]`

That meant the service existed in compose only as something intentionally excluded from the default stack.

After this commit, that gate was removed, and the service gained concrete runtime wiring:

- `SPRING_DATASOURCE_URL=jdbc:postgresql://matching_postgres:5432/matchingdb`
- `SPRING_DATASOURCE_USERNAME=matchinguser`
- `SPRING_DATASOURCE_PASSWORD=matchingpass`
- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`
- `SPRING_FLYWAY_ENABLED=true`
- `SPRING_FLYWAY_LOCATIONS=classpath:db/migration`
- `KAFKA_BOOTSTRAP_SERVERS=redpanda:9092`
- `KAFKA_TOPIC_ORDER_PLACED=order.placed.v1`
- `KAFKA_TOPIC_ORDER_CANCELLED=order.cancelled.v1`
- `KAFKA_TOPIC_TRADE_EXECUTED=trade.executed.v1`
- `KAFKA_CONSUMER_GROUP=matching-engine`

Its dependencies also changed. Before the commit, `matching-engine` depended only on Redpanda. After the commit it also depended on:

- `matching_postgres` with healthcheck gating

and a new `matching_postgres` service plus `matching_postgres_data` volume were added lower in the compose file.

This is a real architectural milestone in the chunk evidence. Before `870d88c`, matching logic was only an implied future role in the stack layout. After `870d88c`, compose treated the engine as a runnable, stateful service with its own database and broker subscriptions.

## The service was introduced as a full Gradle/Spring Boot project, not just a couple of classes

The commit created the entire [`services/matching-engine/`](services/matching-engine) project structure:

- [`build.gradle`](services/matching-engine/build.gradle)
- [`settings.gradle`](services/matching-engine/settings.gradle)
- [`Dockerfile`](services/matching-engine/Dockerfile)
- Gradle wrapper files
- [`HELP.md`](services/matching-engine/HELP.md)
- [`README.md`](services/matching-engine/README.md)
- application bootstrap code
- configuration classes
- domain entities and enums
- DTOs
- matching logic
- persistence repositories
- Kafka consumers
- Flyway migrations
- a basic Spring Boot test

The large number of generated artifacts committed alongside source files shows the service was also built locally during the same change. Evidence for that includes:

- `services/matching-engine/.gradle/...`
- `services/matching-engine/build/tmp/...`
- [`services/matching-engine/build/reports/problems/problems-report.html`](services/matching-engine/build/reports/problems/problems-report.html)
- `services/matching-engine/bin/main/...`

So this was not a repo-only scaffold. The commit reflects a source authoring pass plus at least one successful local Gradle build.

### Package naming was normalized during generation

[`services/matching-engine/HELP.md`](services/matching-engine/HELP.md) records that the original package name `com.tradestream.matching-engine` was invalid and the generated package became `com.tradestream.matching_engine`.

That note is useful because the underscore-based package naming is otherwise easy to misread as a later stylistic decision. In this chunk it is clearly generation-driven.

## The first version explicitly documented a much larger operational story than the code alone would show

[`services/matching-engine/README.md`](services/matching-engine/README.md) was introduced in the same commit and is unusually detailed for a first-cut service README. It describes:

- service responsibilities
- event contracts
- matching rules
- persistence layout
- warm-start behavior
- idempotency handling
- DLT operations
- smoke tests
- performance assumptions
- limitations and future work

That README matters in this chunk because it shows the developer was trying to document the engine as a production-like component from the first implementation, not as a toy algorithm.

At the same time, some details in the README are broader or cleaner than the raw code proves. The code and docs are largely aligned, but where they diverge or overstate, that needs to be recorded rather than averaged away.

## Build and container conventions followed the repo’s Java-service pattern

[`services/matching-engine/build.gradle`](services/matching-engine/build.gradle) establishes the service as:

- Spring Boot `3.5.4`
- Java toolchain `17`
- Spring Web
- Spring Data JPA
- Flyway + PostgreSQL support
- Spring Kafka
- Actuator
- Lombok
- Jackson databind + JSR-310 time module

This is materially different from having only matching logic in isolation. It means the first implementation was conceived as a full Spring application with persistence, operations endpoints, and Kafka integration from day one.

[`services/matching-engine/Dockerfile`](services/matching-engine/Dockerfile) follows the same broad pattern already visible in other services:

- `eclipse-temurin:17-jdk` base image
- install `curl`
- copy entire service directory
- run `./gradlew clean build -x test`
- run the JAR from `build/libs`

### There was an immediate container-port mismatch

One concrete inconsistency appears in the very first Dockerfile:

- [`Dockerfile`](services/matching-engine/Dockerfile) exposes `8085`
- [`application.yml`](services/matching-engine/src/main/resources/application.yml) sets default `server.port` to `8086`
- [`docker-compose.yml`](docker-compose.yml) sets `SERVER_PORT: 8086`
- the compose healthcheck calls `http://localhost:8086/actuator/health`

That means the container metadata and the runtime configuration were already out of sync in the bootstrap commit. The service likely still ran if Spring listened on `8086`, because `EXPOSE` is metadata rather than enforcement, but the mismatch is real evidence of rough edges in the first cut.

## Application bootstrap included warm-start loading of active orders

[`MatchingEngineApplication.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/MatchingEngineApplication.java) introduced the Spring Boot entrypoint and added a `CommandLineRunner` bean:

- it loads active orders via `repo.findAllActive()`
- it passes them into `MatchingService.loadActiveOrders(...)`

This is important because it shows persistence was not added only for audit/history. From the first commit, the database existed to repopulate the in-memory order books after restart.

Before this commit, there was no evidence-backed recovery path for matching state. After it, the engine started by reconstructing active books from persisted `ACTIVE` / `PARTIALLY_FILLED` rows.

## The core domain model split durable state into resting orders and processed messages

Two primary entities were introduced:

- [`RestingOrder.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/domain/RestingOrder.java)
- [`ProcessedMessage.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/domain/ProcessedMessage.java)

### `RestingOrder` defined what the engine persists

[`RestingOrder.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/domain/RestingOrder.java) stores:

- `id`
- `userId`
- `ticker`
- `side`
- `orderType`
- `timeInForce`
- `price`
- `originalQuantity`
- `remainingQuantity`
- `status`
- `createdAt`
- `updatedAt`

Notable details visible in the class:

- `price` is allowed to be null
- comments state null is valid only for MARKET orders, but MARKET orders must never rest
- `status` is a plain `String`, not an enum
- `updatedAt` uses `@UpdateTimestamp`

The choice to keep status as a string is worth recording because the README presents a clean state model (`ACTIVE`, `PARTIALLY_FILLED`, `FILLED`, `CANCELED`), but the code does not enforce that state space at the type level.

### `ProcessedMessage` implemented durable idempotency

[`ProcessedMessage.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/domain/ProcessedMessage.java) stores:

- `messageId`
- `receivedAt`

This table is the durable dedup ledger for consumed Kafka messages. That matters because it shows the first engine implementation was already designed around at-least-once broker delivery rather than assuming clean single delivery.

## The database schema matched the domain split and recovery strategy

[`services/matching-engine/src/main/resources/db/migration/V1__init.sql`](services/matching-engine/src/main/resources/db/migration/V1__init.sql) created both core tables in the initial migration.

### `resting_orders` captured recoverable book state

The table includes:

- `id`
- `user_id`
- `ticker`
- `side`
- `order_type`
- `time_in_force`
- `price`
- `original_quantity`
- `remaining_quantity`
- `status`
- `created_at`
- `updated_at`

and an index:

- `idx_resting_orders_active (ticker, side, status)`

That index directly supports the startup load path described above.

### `processed_messages` backed broker idempotency

The same migration created:

- `processed_messages (message_id, received_at)`

So the before -> after database evolution in this single commit was:

- before: no local persistence contract for matching state is evidenced in this chunk
- after: the engine had one table for reconstructing active books and one for suppressing duplicate input processing

The generated-resource copy under [`services/matching-engine/bin/main/db/migration/V1__init.sql`](services/matching-engine/bin/main/db/migration/V1__init.sql) shows this schema also passed through the local build.

## Configuration changed from generic service defaults to a Kafka/JPA matching-engine contract

[`services/matching-engine/src/main/resources/application.yml`](services/matching-engine/src/main/resources/application.yml) was not created from scratch in this commit. It existed before with generic app defaults and was rewritten into service-specific configuration.

Before the patch, the visible configuration was generic:

- `spring.application.name: ${APP_NAME:service}`
- default `server.port: 8080`
- no Kafka section
- actuator exposure only for `health,info`
- generic Flyway/JPA defaults

After the patch, it became matching-engine specific:

- `spring.application.name: matching-engine`
- Kafka bootstrap and consumer group defaults
- Kafka consumer `enable-auto-commit: false`
- manual listener ack mode
- producer JSON serializer
- consumer `ErrorHandlingDeserializer`
- `spring.json.use.type.headers: false`
- `server.port: 8086`
- actuator exposure extended to `health,info,metrics`
- topic names under `tradestream.topics.orderPlaced`, `orderCancelled`, `tradeExecuted`

This matters because the service was introduced with broker wiring, DB wiring, and operational endpoints all in the same initial implementation.

## Matching rules were implemented in memory, one book per ticker

The main engine logic lives in:

- [`MatchingService.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/matching/MatchingService.java)
- [`OrderBook.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/matching/OrderBook.java)

### `OrderBook` encoded price-time priority directly

[`OrderBook.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/matching/OrderBook.java) introduced two priority queues:

- bids
- asks

with explicit comparator behavior:

- bids sort by highest price first, then `createdAt`
- asks sort by lowest price first, then `createdAt`

The class comment makes the design intent explicit:

- one book per ticker
- only LIMIT orders should ever be stored
- MARKET orders never rest

The `priceOrThrow(...)` helper throws if a resting order has no price, which reinforces that the in-memory book is intended only for priced liquidity.

### Crossing logic treated null aggressing price as marketable

Both `OrderBook.isCrossed(...)` and `MatchingService.crosses(...)` treat a null incoming price as:

- a MARKET order that crosses as long as there is opposite liquidity

That means the first implementation already supported both market and limit orders in its aggressing path, but only priced orders can remain in-book.

## `MatchingService` handled matching, fill publication, cancellation, and rest-placement

[`MatchingService.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/matching/MatchingService.java) is the core orchestration class added in this commit.

### Warm-start loading populated in-memory books from persisted orders

`loadActiveOrders(List<RestingOrder> active)` takes database rows and adds them into the in-memory `books` map keyed by ticker.

That establishes a clear before -> after behavior:

- before: no restart recovery path for the book is evidenced
- after: startup reconstructs book state from durable `resting_orders`

### Cancellation immediately persisted state and removed the order from the book

`cancel(UUID orderId)`:

1. finds the order by ID
2. sets `status` to `"CANCELED"`
3. saves it
4. removes it from the in-memory book for that ticker

This is simple and direct. There is no visible conditional guard in `MatchingService.cancel(...)` for:

- already filled orders
- already canceled orders
- partially filled vs active distinction

The README says cancellation applies if the order is still active/partial. The code here is looser: if a row exists, it is marked canceled and removed from the book. The actual practical effect depends on whether that row is still in the book and how callers behave, but the gap between the described rule and the exact implementation is visible in the chunk.

### Incoming order handling already included GTC, IOC, and FOK

`handleIncoming(OrderPlacedEvent evt)` converts the inbound DTO into a `RestingOrder` and then follows this sequence:

1. create or look up the per-ticker `OrderBook`
2. for `FOK`, run `canFullyFill(...)` before matching
3. while quantity remains and the book is crossed:
   - pull the best opposite resting order
   - compute `tradeQty`
   - compute `tradePrice`
   - publish a trade
   - update and persist the resting counterparty
   - requeue the counterparty if quantity remains
   - reduce incoming remaining quantity
4. after matching:
   - if fully filled: return `true`
   - if `IOC`: cancel remainder by returning `false`
   - if MARKET remainder exists: cancel by returning `false`
   - if LIMIT remainder exists:
     - require non-null price
     - set status to `ACTIVE` or `PARTIALLY_FILLED`
     - persist it
     - add it to the in-memory book

This is a substantial first implementation. The engine was not added as a skeleton that only stores orders. It already performed:

- cross detection
- partial matching
- FOK pre-checks
- IOC remainder cancellation
- persistence of resting residuals
- trade publication

### FOK was implemented as a pre-scan of opposite liquidity

`canFullyFill(...)` takes a snapshot of the opposite queue, sorts it best-first using the queue comparator, then sums available quantity while prices remain acceptable.

If cumulative available liquidity reaches the incoming need, it returns `true`; otherwise `false`.

That means FOK support was not a later extension bolted onto basic matching. It was part of the initial matching-service implementation in this chunk.

### Trade publication keyed by ticker was part of the initial design

`publishTrade(...)` creates [`TradeExecutedEvent.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/dto/TradeExecutedEvent.java) with:

- `tradeId`
- `buyOrderId`
- `sellOrderId`
- `ticker`
- `price`
- `quantity`
- `timestamp`

and hands it to [`TradePublisher.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/matching/TradePublisher.java), which sends it to `props.getTradeExecuted()` keyed by ticker.

So the engine’s first output contract was already designed around:

- unique trade identifiers
- explicit buy/sell order references
- downstream partition locality by ticker

## The trade price rule was “resting price if present, otherwise incoming price”

Inside `handleIncoming(...)`, trade price is set as:

- `top.getPrice()` if the resting order has a price
- otherwise `incoming.getPrice()`

Because resting orders in the book should always be LIMIT orders with non-null price, the common case is “resting price wins”.

The fallback branch exists because the code keeps the logic generic enough to tolerate a null on one side. The README describes MARKET orders matching at the resting order’s price, and the code mostly aligns with that because resting book entries are expected to be priced. If a null-priced resting order ever got into the book, that would violate the `OrderBook` invariant and likely surface elsewhere first.

## The initial event input contracts mirrored the orders service closely

The commit added:

- [`OrderPlacedEvent.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/dto/OrderPlacedEvent.java)
- [`OrderCancelledEvent.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/dto/OrderCancelledEvent.java)

`OrderPlacedEvent` contains:

- `orderId`
- `userId`
- `ticker`
- `side`
- `orderType`
- `timeInForce`
- `price`
- `quantity`

`OrderCancelledEvent` contains only:

- `orderId`

That shape matches the engine’s role. New-order events carry enough information to match without rereading an upstream order table, while cancellation only needs an identifier.

## Kafka consumers were idempotent and manually acknowledged from the start

The input side is implemented by:

- [`OrderPlacedConsumer.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/stream/OrderPlacedConsumer.java)
- [`OrderCancelledConsumer.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/stream/OrderCancelledConsumer.java)

Both listeners:

- use `containerFactory = "kafkaListenerContainerFactory"`
- set per-listener JSON default type
- disable reliance on type headers
- compute a `messageId` from Kafka header `eventId` or fall back to `orderId`
- check `ProcessedMessageRepository.existsById(...)`
- short-circuit duplicates
- persist the processed-message row
- acknowledge manually via `Acknowledgment`

This matters because the first engine implementation already assumed duplicate input delivery was normal enough to deserve durable deduplication instead of only in-memory suppression.

### Idempotency fallback differs slightly from the README’s phrasing

The README says the engine treats header `eventId` as the idempotency key and falls back to `orderId`.

The consumers do exactly that, with one additional detail:

- if even `orderId` is absent, they fall back to `UUID.randomUUID()`

That means the real code path is:

1. header `eventId` if present and parseable
2. event `orderId` if present
3. random UUID otherwise

The fallback-to-random behavior is visible in both consumers. It weakens deduplication for malformed events with no ID, but it also prevents null-key failures. The README does not mention that extra branch.

## DLT and retry handling were built into the initial service

The service added three supporting configuration/stream pieces:

- [`KafkaDlqConfig.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/config/KafkaDlqConfig.java)
- [`BytesListenerFactoryConfig.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/config/BytesListenerFactoryConfig.java)
- [`DltLoggingConsumer.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/stream/DltLoggingConsumer.java)

### Retry policy was explicit

`KafkaDlqConfig` introduced:

- `DeadLetterPublishingRecoverer` targeting `<originalTopic>.DLT` on the same partition
- `DefaultErrorHandler` with `ExponentialBackOffWithMaxRetries(5)`
- initial interval `200ms`
- multiplier `2.0`
- max interval `5000ms`
- `IllegalArgumentException` marked non-retryable

This is strong evidence that the developer expected malformed or transiently failing records and wanted failure isolation from the first version.

### DLT logging intentionally consumed bytes to avoid recursive DLT loops

`BytesListenerFactoryConfig` builds a byte-array consumer factory and listener container factory. `DltLoggingConsumer` then uses that bytes-based factory to consume:

- `#{engineProps.orderPlaced}.DLT`
- `#{engineProps.orderCancelled}.DLT`

and prints:

- original topic
- original offset
- exception class
- exception message
- key
- raw payload

The comment in `BytesListenerFactoryConfig` explicitly says:

- no custom error handler -> no DLT-for-DLT loops

That is not generic boilerplate; it shows the developer was already accounting for a common operational failure mode in DLT consumers.

## Repository layer was intentionally minimal

The service introduced:

- [`ProcessedMessageRepository.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/persistence/ProcessedMessageRepository.java)
- [`RestingOrderRepository.java`](services/matching-engine/src/main/java/com/tradestream/matching_engine/persistence/RestingOrderRepository.java)

`RestingOrderRepository` exposes:

- `findAllActive()` for warm-start
- `updateStatus(...)`

The interesting detail is that `updateStatus(...)` exists but is not used in the visible service code. `MatchingService.cancel(...)` and matching updates both call `save(...)` on entities instead.

That suggests either:

- a partially abandoned optimization path, or
- a repository method added speculatively for later use

The evidence is not strong enough to say which, so the safest statement is that the repository API was slightly broader than the paths exercised by the first implementation.

## The first README mostly matched the code, but not perfectly

Because [`services/matching-engine/README.md`](services/matching-engine/README.md) is so detailed in the same commit, it is worth tracing the visible mismatches rather than treating it as ground truth.

### The README presents order statuses as a clean modeled set, but code stores them as raw strings

The README defines:

- `ACTIVE`
- `PARTIALLY_FILLED`
- `FILLED`
- `CANCELED`

The code does use those same strings, but there is no status enum enforcing them. That means the conceptual model and the implementation vocabulary align, while the type safety does not.

### The README’s cancellation rule is stricter than the service method

The README says cancellation applies if the order is present and still active/partial.

`MatchingService.cancel(...)` simply:

- loads the row if present
- sets status to `CANCELED`
- saves it
- removes it from the book

There is no explicit guard on current status in that method. The runtime effect may still be acceptable in common cases, but the code path is less restrictive than the narrative wording.

### The README frames “exactly-once considerations” clearly, and the code supports that limitation

The README explicitly says DB + Kafka are not wrapped in a single atomic transaction and there is a crash window where replay could re-publish trades.

That is consistent with the code. `MatchingService.handleIncoming(...)` publishes trades during matching and persists order-state changes in the same transactional method, but there is no outbox or visible Kafka transaction orchestration in this chunk.

So on this point the README is not overselling; it is accurately naming a limitation already present in the first implementation.

### The README and runtime config disagree with the Dockerfile’s exposed port

The README documents the health endpoint at port `8086`, and compose/config both reinforce `8086`. Only the Dockerfile still exposes `8085`.

This looks like a stale copy-forward from another service rather than an intentional choice, but that specific intent is not directly provable from the chunk. The evidence-bound conclusion is only that the mismatch existed on day one.

## Test coverage at introduction time was minimal despite substantial matching logic

[`MatchingEngineApplicationTests.java`](services/matching-engine/src/test/java/com/tradestream/matching_engine/MatchingEngineApplicationTests.java) contains only:

- `contextLoads()`

That means this commit introduced non-trivial behavior around:

- price-time priority
- FOK pre-checks
- IOC cancellation
- warm-start loading
- durable message deduplication
- DLT routing

without visible behavioral tests in the same chunk.

This is an important part of the chronology because the service was introduced as operationally ambitious and heavily documented, but the concrete automated verification evidence in the patch remained at application-startup level only.

## The committed build output also exposed Gradle deprecation warnings

[`services/matching-engine/build/reports/problems/problems-report.html`](services/matching-engine/build/reports/problems/problems-report.html) includes Gradle deprecation warnings such as use of deprecated Gradle APIs scheduled for removal in Gradle 9.0.

Those warnings are not specific to matching logic, but they do show the build was not perfectly clean even when the service first landed. This is build-environment evidence rather than business-logic evidence, so it should not be overstated, but it is part of the first committed state.

## The resulting before -> after shift in this chunk was substantial

Before commit `870d88c8a2a848514341adce65b43d4082217ced`, the chunk evidence shows `matching-engine` only as a compose placeholder excluded behind `profiles: ["later"]`.

After the commit, the repository contained a functioning first implementation with:

- a compose-activated service
- a dedicated Postgres instance
- a persisted `resting_orders` model
- recovery of active orders into in-memory books
- FIFO-by-price order books per ticker
- GTC / IOC / FOK handling
- Kafka consumers for order placement and cancellation
- idempotency via `processed_messages`
- trade publication to Kafka
- retry and dead-letter handling
- a long operator/developer README explaining the intended operational model

At the same time, the first version already carried visible rough edges in the same evidence:

- Dockerfile port `8085` vs runtime `8086`
- cancellation implementation looser than README wording
- string-based statuses instead of enum-enforced state
- only a context-load test despite substantial stateful matching behavior

Those rough edges are not secondary details. They are part of how the `matching-engine` entered the codebase in this chunk: ambitious, broadly wired into the stack, and clearly meant to be a real internal engine, but still with first-pass inconsistencies between code, docs, and packaging.
