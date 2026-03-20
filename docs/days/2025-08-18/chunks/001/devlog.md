# Devlog

## 2025-08-18 04:45:40 +0100 — commit `2e162b875b31545293727cbb6fd2316d35a9d0cf`

This chunk is a single large integration-and-hardening commit that did four distinct things at once:

- captured a benchmark run and checked the artifacts into `bench_out/`
- standardized Docker build patterns across the Java services
- introduced `portfolio-service` as a new scaffolded deployable
- rewrote `transaction-processor` from a synchronous portfolio-mutating service into an append-only trade journal consumer with Kafka output and query endpoints

The changed-file set makes that shape explicit:

- benchmarking files and runner at repo root
- compose updates in [`docker-compose.yml`](docker-compose.yml)
- modified Dockerfiles across existing services
- a new full [`services/portfolio-service/`](services/portfolio-service) tree
- a large replacement of `transaction-processor` source files under [`services/transaction-processor/`](services/transaction-processor)
- smaller compatibility/documentation changes in `orders-service` and `market-data-consumer`

This was not a narrow bugfix commit. It changed how the project was packaged, how one service was measured, how another service was conceptualized, and how the trade-processing path was structured.

## The commit began by adding a concrete benchmark harness and preserving the output

Before this commit, there is no evidence in this chunk of a checked-in benchmark runner or saved benchmark snapshots. After `2e162b875b31545293727cbb6fd2316d35a9d0cf`, the repo gained:

- [`bench.sh`](bench.sh)
- [`bench_out/RESUME_SUMMARY.md`](bench_out/RESUME_SUMMARY.md)
- [`bench_out/kafka.txt`](bench_out/kafka.txt)
- [`bench_out/market_raw.txt`](bench_out/market_raw.txt)
- [`bench_out/market_summary.txt`](bench_out/market_summary.txt)
- [`bench_out/orders_raw.txt`](bench_out/orders_raw.txt)
- [`bench_out/orders_summary.txt`](bench_out/orders_summary.txt)
- [`bench_out/redis_info.txt`](bench_out/redis_info.txt)
- [`bench_out/redis_summary.txt`](bench_out/redis_summary.txt)

and also a few zero-byte placeholder files:

- `bench_out/market_lat_ms.txt`
- `bench_out/orders_hey.txt`
- `bench_out/orders_lat_ms.txt`

### `bench.sh` encoded the benchmark methodology directly in the repo

[`bench.sh`](bench.sh) does not rely on an external load-test tool like `hey`. Instead it builds a small shell-based load harness around `curlimages/curl` containers running on the compose private network.

The script:

- detects the compose `private_net`
- health-checks `orders-service` and `market-data-consumer`
- seeds one crossing trade to warm `AAPL` latest-candle reads
- drives POST load against `http://orders-service:8085/orders`
- drives GET load against `http://market-data-consumer:8083/candles/AAPL/latest?interval=1m`
- summarizes p50/p95 and a coarse concurrency-based RPS estimate
- snapshots Redis stats
- snapshots Kafka topics and consumer groups via `rpk`
- writes a résumé-oriented markdown summary

This is important because the benchmark artifact is not just a pasted result. The method used to produce it is now reproducible from within the repo.

### The checked-in benchmark results became part of the repo narrative

[`bench_out/RESUME_SUMMARY.md`](bench_out/RESUME_SUMMARY.md) records:

- Orders API: `~1629 req/s`, p50 `11 ms`, p95 `26 ms` for `500` POST requests at `20` concurrency
- Market Data Latest: `~90334 req/s`, p50 `3 ms`, p95 `14 ms` for `20000` GET requests at `400` concurrency
- Redis cache: `hits=39736 | misses=265 | hit_rate=99.3%`

[`bench_out/kafka.txt`](bench_out/kafka.txt) also captures the state of the broker at the time of the benchmark:

- one-broker Redpanda cluster
- topics including `order.placed.v1`, `order.cancelled.v1`, `trade.executed.v1`, `transaction.recorded.v1`
- stable consumer groups for:
  - `matching-engine`
  - `md-consumer`
  - `txproc-journal`
- all with zero lag at capture time

That is useful development-history evidence because it shows the new `transaction.recorded.v1` topic and `txproc-journal` group were not merely configured in code in this commit; they were part of a live benchmarked stack snapshot.

### The benchmark runner also shaped the repo’s self-presentation

`bench.sh` explicitly writes résumé-ready bullets into [`bench_out/RESUME_SUMMARY.md`](bench_out/RESUME_SUMMARY.md). That means this commit was partly about measuring the system and partly about making those measurements portable into portfolio/recruiter material.

The evidence here is direct, not inferred: the generated markdown literally contains “Suggested bullets”.

## Compose changed to expose a broader runtime graph and to formalize new topic/env conventions

[`docker-compose.yml`](docker-compose.yml) changed in three separate ways.

### Matching-engine gained duplicate topic configuration under Spring-style property names

The `matching-engine` service already had:

- `KAFKA_TOPIC_ORDER_PLACED`
- `KAFKA_TOPIC_ORDER_CANCELLED`
- `KAFKA_TOPIC_TRADE_EXECUTED`

This commit adds parallel variables:

- `TRADESTREAM_TOPICS_ORDERPLACED`
- `TRADESTREAM_TOPICS_ORDERCANCELLED`
- `TRADESTREAM_TOPICS_TRADEEXECUTED`

That suggests the developer was making topic names available through both the existing env naming convention and the `tradestream.topics.*` property namespace expected by parts of the Spring configuration.

The evidence does not prove which exact component required the duplicated variables, but the before -> after change is clear:

- before: only `KAFKA_TOPIC_*` environment variables were supplied
- after: compose also injects a direct `TRADESTREAM_TOPICS_*` form for matching-engine

### Transaction-processor changed identity and dependencies in compose

The `transaction-processor` service changed:

- image name from `transaction-processor:dev` to `tradestream-transaction-processor:dev`
- consumer group from `pf-consumer` to `txproc-journal`
- added `KAFKA_TOPIC_TRANSACTION_RECORDED=transaction.recorded.v1`
- added `ORDERS_BASE_URL=http://orders-service:8085`
- added dependency on `orders-service`
- removed `MARKET_SERVICE_URL`

This is a major clue about the service’s internal redesign.

Before this commit, the compose contract suggests transaction-processor still expected to call into market-data directly and consumed trades under a portfolio-oriented consumer-group identity. After the commit, it is repositioned as:

- a journal consumer of `trade.executed.v1`
- a producer of `transaction.recorded.v1`
- a service that enriches trade events via `orders-service`

### Portfolio-service became a real compose member with its own Postgres

This commit added a new `portfolio-service` block and a new `portfolio_postgres` datastore block.

`portfolio-service` gained:

- build context and image
- port `8087`
- dedicated Postgres connection
- Kafka bootstrap and two topics:
  - `trade.executed.v1`
  - `transaction.recorded.v1`
- its own consumer group `portfolio-svc`
- `ORDERS_BASE_URL`
- Redis-related env vars
- actuator healthcheck

`portfolio_postgres` gained:

- `portfoliodb`
- `portfoliouser`
- `portfoliopass`
- persistent volume `portfolio_postgres_data`

The key chronological point is that `portfolio-service` was not only added as source code. It was immediately wired into compose with its own datastore and runtime contract.

## End-to-end scenarios were widened so the transaction journal became part of the acceptance path

[`e2e_scenarios.sh`](e2e_scenarios.sh) already existed before this commit. This patch does not replace its basic trade/matching checks; it extends them to include `transaction-processor`.

### Service addressing shifted from container names to service DNS names

The script changed defaults from container-specific names like:

- `tradestream-orders-service-1`
- `tradestream-matching-engine-1`
- `tradestream-market-data-consumer-1`

to compose service DNS names:

- `orders-service`
- `matching-engine`
- `market-data-consumer`
- new `transaction-processor`

This is an operational stabilization change. Before, the script assumed a very specific compose naming pattern. After, it targeted stable service names on the Docker network.

### Transaction-processor health and lag checks were added

The script now includes:

- `wait_tx_health()`
- `wait_txproc_caught_up()`
- `tx_count_user_ticker()`
- `tx_list_user_ticker()`

and brings up additional services:

- `transaction_postgres`
- `transaction-processor`

This turns the script from “trade pipeline through matching and market data” into “trade pipeline through matching, market data, and transaction journaling”.

### Each trade scenario now asserts transaction-ledger side effects

For scenarios like:

- partial fill
- IOC partial
- market order
- recovery after restart

the script now waits for `txproc-journal` to catch up on `trade.executed.v1` and then verifies:

- one buyer ledger row exists for the ticker
- one seller ledger row exists for the ticker

For scenarios with no trade:

- IOC no liquidity
- FOK insufficient liquidity
- cancel before crossing

the script now asserts zero ledger rows.

It also adds an explicit idempotency check against the ledger after replaying a duplicate trade message.

This is strong evidence about how the developer now expected the system to behave:

- transaction-processor is no longer incidental
- it is part of the correctness contract for trade execution scenarios

## Docker build patterns were standardized across the Java services

Five Dockerfiles changed in parallel:

- [`services/authentication-service/Dockerfile`](services/authentication-service/Dockerfile)
- [`services/market-data-consumer/Dockerfile`](services/market-data-consumer/Dockerfile)
- [`services/matching-engine/Dockerfile`](services/matching-engine/Dockerfile)
- [`services/orders-service/Dockerfile`](services/orders-service/Dockerfile)
- [`services/transaction-processor/Dockerfile`](services/transaction-processor/Dockerfile)
- [`services/user-registration-service/Dockerfile`](services/user-registration-service/Dockerfile)

The new pattern is consistent:

1. set `WORKDIR /app`
2. copy `gradlew` and `gradle/` first
3. install `curl` and `unzip`
4. `chmod +x gradlew`
5. clear `/root/.gradle/wrapper/dists`
6. copy the rest of the source tree
7. run `./gradlew dependencies --no-daemon || true`
8. run `./gradlew clean build -x test --no-daemon`

Before this commit, the Dockerfiles were simpler one-pass builds:

- copy everything
- optionally install `curl`
- build immediately

After the commit, they are deliberately structured for better layer caching around Gradle wrapper/dependency download.

### Matching-engine’s port mismatch was corrected here

[`services/matching-engine/Dockerfile`](services/matching-engine/Dockerfile) changed:

- `EXPOSE 8085` -> `EXPOSE 8086`

That closes the mismatch introduced in the initial matching-engine bootstrap, where runtime config and compose healthchecks used `8086` but the Dockerfile still exposed `8085`.

So this commit did not only standardize build caching; it also fixed an older packaging inconsistency.

## Orders-service event serialization was tightened for time handling

Three `orders-service` files changed:

- [`services/orders-service/src/main/java/com/tradestream/orders_service/events/OrderCancelledEvent.java`](services/orders-service/src/main/java/com/tradestream/orders_service/events/OrderCancelledEvent.java)
- [`services/orders-service/src/main/java/com/tradestream/orders_service/events/OrderPlaced.java`](services/orders-service/src/main/java/com/tradestream/orders_service/events/OrderPlaced.java)
- [`services/orders-service/src/main/resources/application.yml`](services/orders-service/src/main/resources/application.yml)

### Event timestamps were forced into string-form JSON

Both event classes added:

- `@JsonFormat(shape = JsonFormat.Shape.STRING)` on their `Instant timestamp` field

Before this patch, the event definitions left timestamp formatting to default Jackson behavior. After it, the event payloads explicitly require string-shaped timestamp serialization.

### Application-level Jackson settings were aligned with UTC string timestamps

[`application.yml`](services/orders-service/src/main/resources/application.yml) and its generated `bin/main` copy added:

- `spring.jackson.time-zone: UTC`
- `spring.jackson.serialization.write-dates-as-timestamps: false`

This is a coherent before -> after shift:

- before: time serialization behavior depended on defaults
- after: orders-service explicitly serialized dates in UTC ISO-style form rather than timestamp numbers

Given the surrounding event-driven architecture, this looks like a compatibility hardening step for downstream consumers and for reproducible event payloads.

## Market-data-consumer was repositioned in documentation and slightly expanded in build dependencies

Two files matter here:

- [`services/market-data-consumer/README.md`](services/market-data-consumer/README.md)
- [`services/market-data-consumer/build.gradle`](services/market-data-consumer/build.gradle)

### The README was rewritten from a service README into a “source of truth” document

Before the patch, the README was a narrower service overview focused on:

- consuming executed trade events
- aggregating OHLCV
- Redis caching
- REST API usage

After this commit it became a much longer document framed as:

- “Market Data Consumer — Source of Truth”

and expanded to cover:

- recruiter-facing executive summary
- architecture and contract details
- data model and cache model
- operations/runbook content
- CV bullets, cover-letter paragraph, and interview talking points
- root README snippet suggestions

This is not just a documentation polish change. It changes the purpose of the file from internal service README toward portfolio/recruiter-facing canonical explanation, similar in spirit to the benchmark résumé summary added in the same commit.

### Build file added an unnecessary production-scoped test starter

[`services/market-data-consumer/build.gradle`](services/market-data-consumer/build.gradle) added:

- `implementation 'org.springframework.boot:spring-boot-starter-test'`

The evidence only proves the dependency was added under `implementation`, not under `testImplementation`.

That is likely unintentional or at least unusual, because it pulls test support into the runtime classpath. The patch itself does not show whether this caused issues, but it is a concrete change worth recording because it changed the service’s dependency surface in a non-standard way.

## Portfolio-service was introduced as a scaffolded runtime participant, not yet as a feature-complete service

This commit created the entire [`services/portfolio-service/`](services/portfolio-service) tree:

- Gradle wrapper and config
- `.gitattributes` / `.gitignore`
- [`build.gradle`](services/portfolio-service/build.gradle)
- [`Dockerfile`](services/portfolio-service/Dockerfile)
- [`settings.gradle`](services/portfolio-service/settings.gradle)
- [`PortfolioServiceApplication.java`](services/portfolio-service/src/main/java/com/tradestream/portfolio_service/PortfolioServiceApplication.java)
- [`application.yml`](services/portfolio-service/src/main/resources/application.yml)
- context-load test

### The build and config show intended responsibilities more clearly than the code does

`build.gradle` includes:

- actuator
- cache
- JPA
- Redis
- validation
- web
- Flyway
- Kafka

`application.yml` configures:

- Postgres
- Redis
- Kafka consumer group `portfolio-svc`
- default inbound Kafka type `com.tradestream.portfolio_service.consumer.TransactionRecordedEvent`
- topics:
  - `tradeExecuted`
  - `transactionRecorded`
- server port `8087`

However, the only Java source added is the Spring Boot application class with `@EnableKafka`.

That means before -> after:

- before: no portfolio-service project existed in this chunk
- after: a deployable scaffold with runtime configuration existed, but no business-layer consumers, entities, repositories, or controllers are evidenced in this commit

So `portfolio-service` entered the repo as infrastructure and intent first, not as a feature-complete implementation.

## Transaction-processor was substantially repurposed into an append-only transaction journal

The most important change in this chunk is the replacement of `transaction-processor`’s internal model.

Before this commit, the deleted files show a very different service:

- it had synchronous HTTP-style transaction creation endpoints
- it mutated a local `portfolio` table
- it called a `MarketDataClient`
- it validated tickers against market-data
- it stored signed transaction quantities (`BUY` positive, `SELL` negative)

The deleted files include:

- [`market_data/MarketDataClient.java`](services/transaction-processor/src/main/java/com/tradestream/transaction_processor/market_data/MarketDataClient.java)
- [`market_data/MarketDataEvent.java`](services/transaction-processor/src/main/java/com/tradestream/transaction_processor/market_data/MarketDataEvent.java)
- [`portofolio/Portfolio.java`](services/transaction-processor/src/main/java/com/tradestream/transaction_processor/portofolio/Portfolio.java)
- [`portofolio/PortfolioRepository.java`](services/transaction-processor/src/main/java/com/tradestream/transaction_processor/portofolio/PortfolioRepository.java)
- the entire older `transaction/` package with request/controller/service/repository

After `2e162b875b31545293727cbb6fd2316d35a9d0cf`, the service is rebuilt around Kafka-consumed trade executions and append-only journal rows.

### The service became Kafka-enabled at the application level

[`TransactionProcessorApplication.java`](services/transaction-processor/src/main/java/com/tradestream/transaction_processor/TransactionProcessorApplication.java) added:

- `@EnableKafka`

That is a small but symbolic change: the service is no longer primarily an HTTP write endpoint. Kafka consumption is now central enough to enable explicitly at application bootstrap.

### Build dependencies shifted toward messaging and integration testing

[`build.gradle`](services/transaction-processor/build.gradle) now includes:

- `spring-kafka`
- `spring-boot-starter-actuator`
- configuration-processor
- Kafka test support
- Testcontainers for JUnit, PostgreSQL, and Kafka

Before this commit, the visible dependency surface was more limited and did not include the Kafka and Testcontainers stack now present.

So the service’s intended development and testing model broadened from simple REST/JPA work toward event-driven integration testing.

### Incoming trade execution became the write-side entrypoint

New files:

- [`consumer/TradeExecutedConsumer.java`](services/transaction-processor/src/main/java/com/tradestream/transaction_processor/consumer/TradeExecutedConsumer.java)
- [`consumer/TradeExecutedEvent.java`](services/transaction-processor/src/main/java/com/tradestream/transaction_processor/consumer/TradeExecutedEvent.java)

The new DTO contains:

- `tradeId`
- `buyOrderId`
- `sellOrderId`
- `ticker`
- `price`
- `quantity`
- `timestamp`

and the consumer simply hands the Kafka record to:

- `transactionService.processTrade(record.topic(), record.key(), event)`

This matches the trade-execution shape that had already emerged elsewhere in the system. Before this commit, transaction-processor did not consume this event form. After it, that became its primary write path.

### User IDs are now resolved from orders-service rather than carried in the event

[`OrdersServiceClient.java`](services/transaction-processor/src/main/java/com/tradestream/transaction_processor/client/OrdersServiceClient.java) is new and does one thing:

- fetch `/orders/{id}` from orders-service
- extract `userId`

This is a significant design choice.

Before the rewrite, the service managed transactions directly from a user-facing request path and already had the acting `userId`. After the rewrite, it consumes a trade event that contains order IDs but not participant user IDs, so it enriches by calling orders-service.

That means the new journal model depends on orders-service availability for enrichment, which is why compose now injects `ORDERS_BASE_URL` and adds `orders-service` as a dependency.

### Idempotency became explicit and Kafka-topic-scoped

[`domain/ProcessedMessage.java`](services/transaction-processor/src/main/java/com/tradestream/transaction_processor/domain/ProcessedMessage.java) introduces:

- embedded key `(topic, message_id)`
- `processed_at`

and [`repo/ProcessedMessageRepository.java`](services/transaction-processor/src/main/java/com/tradestream/transaction_processor/repo/ProcessedMessageRepository.java) persists it.

This closely mirrors idempotency patterns already visible in other services.

`TransactionService.processTrade(...)` uses:

- `new ProcessedMessage.Key(topic, event.getTradeId().toString())`

as the dedup key.

So before -> after:

- before: no such Kafka-message dedup table is evidenced for transaction-processor
- after: trade processing is explicitly guarded by `(topic, tradeId)` idempotency

### The transaction ledger model changed from signed trade deltas to two append-only rows per trade

New [`domain/Transaction.java`](services/transaction-processor/src/main/java/com/tradestream/transaction_processor/domain/Transaction.java) stores:

- synthetic UUID `id`
- `tradeId`
- `orderId`
- `userId`
- `side` enum (`BUY` / `SELL`)
- `ticker`
- `quantity` as a strictly positive `int`
- `price`
- `executedAt`

with unique constraint:

- `uq_trade_participant (trade_id, user_id, side)`

This is a major modeling change.

The deleted older `transaction/Transaction.java` stored:

- one row with signed `quantity`
- a `TransactionType`
- `createdAt`

The new model instead stores one row per participant leg, both with positive quantities and explicit side.

`TransactionService.processTrade(...)` makes that explicit:

1. resolve buyer user ID from `buyOrderId`
2. resolve seller user ID from `sellOrderId`
3. save a buyer transaction row
4. save a seller transaction row
5. mark the Kafka message processed
6. publish one `transaction.recorded.v1` event per saved journal row

So before this commit, transaction-processor managed portfolio mutations and single-request transaction history. After it, it became a journaler of executed trades into participant-specific ledger rows.

### Downstream publication became part of the service contract

New files:

- [`producer/TransactionRecordedEvent.java`](services/transaction-processor/src/main/java/com/tradestream/transaction_processor/producer/TransactionRecordedEvent.java)
- [`producer/TransactionRecordedProducer.java`](services/transaction-processor/src/main/java/com/tradestream/transaction_processor/producer/TransactionRecordedProducer.java)

The event includes:

- `eventId`
- `tradeId`
- `orderId`
- `userId`
- `side`
- `ticker`
- `quantity`
- `price`
- `executedAt`
- `version`

and the producer keys messages as:

- `tradeId:userId:side`

to preserve participant-specific ordering if needed.

This shows transaction-processor no longer terminates the flow. It now emits a normalized journal event that another service, likely the newly scaffolded `portfolio-service`, can consume.

### Query APIs replaced the older command-style REST endpoints

New files:

- [`api/TransactionDto.java`](services/transaction-processor/src/main/java/com/tradestream/transaction_processor/api/TransactionDto.java)
- [`api/TransactionQueryController.java`](services/transaction-processor/src/main/java/com/tradestream/transaction_processor/api/TransactionQueryController.java)
- [`repo/TransactionRepository.java`](services/transaction-processor/src/main/java/com/tradestream/transaction_processor/repo/TransactionRepository.java)

The controller now exposes paged read endpoints:

- `GET /api/transactions/{userId}`
- `GET /api/transactions/{userId}/ticker/{ticker}`
- `GET /api/transactions/{userId}/since?iso=...`

with:

- page/size controls
- bounded size max `500`
- configurable sort, defaulting to `executedAt DESC`

The deleted controller in the old `transaction/` package had a very different shape:

- `POST /api/transactions/{userId}` to create transactions
- `GET /api/transactions/portfolio/{userId}`
- `GET /api/transactions/history/{userId}`

So the public API shifted from:

- write-side command endpoints plus local portfolio/history views

to:

- pure journal-query endpoints over append-only trade-derived transactions

### Schema migration aligned with the new journal model

[`src/main/resources/db/migration/V1__init_schema.sql`](services/transaction-processor/src/main/resources/db/migration/V1__init_schema.sql) was heavily rewritten.

Before, it created:

- `transactions`
- `portfolio`

with indexes optimized for that older model.

After the patch it creates:

- `processed_messages` with PK `(topic, message_id)`
- `transactions` with columns:
  - `id`
  - `trade_id`
  - `order_id`
  - `user_id`
  - `side`
  - `ticker`
  - `quantity`
  - `price`
  - `executed_at`
- unique constraint `uq_trade_participant`
- indexes:
  - `(user_id, executed_at DESC)`
  - `(user_id, ticker, executed_at DESC)`
  - `(trade_id)`

That schema makes the service’s new purpose explicit:

- durable Kafka deduplication
- append-only per-user trade journal
- efficient read-side queries by user, ticker, and time

The entire `portfolio` table disappears from the migration in this commit.

### Application config was rewritten to match the new service identity

[`src/main/resources/application.yml`](services/transaction-processor/src/main/resources/application.yml) changed from a generic service-plus-market-client configuration to a transaction-journal-specific configuration.

The after-state includes:

- `spring.application.name: transaction-processor`
- Kafka consumer group default `transaction_processor-journal`
- JSON default type `consumer.TradeExecutedEvent`
- producer JSON serializer
- graceful lifecycle timeout
- `server.port: 8084`
- actuator exposure `health,info,metrics`
- `orders.baseUrl`
- topic config:
  - `tradeExecuted`
  - `transactionRecorded`

and removes the earlier `market.baseUrl`.

This aligns with the deleted `MarketDataClient` and the new `OrdersServiceClient`.

## The market-data consumer README and the benchmark artifacts together repositioned the repo toward performance-and-operability storytelling

This commit repeatedly turns implementation details into résumé/interview material:

- `bench.sh` writes résumé bullets
- `bench_out/RESUME_SUMMARY.md` captures concrete throughput/latency claims
- `market-data-consumer/README.md` becomes a source-of-truth plus recruiter-facing document

That is not incidental documentation churn. The same commit that restructured runtime services also made the repo more legible as a portfolio artifact.

## The chronological before -> after shape of `2e162b875b31545293727cbb6fd2316d35a9d0cf` is therefore clear

Before this commit, the project already had:

- working orders, matching, and market-data services
- a first-cut transaction-processor with local portfolio mutation
- no portfolio-service project in this chunk
- no checked-in benchmark runner/results
- less standardized Java Docker build pipelines

After the commit:

- benchmark tooling and measured outputs were versioned into the repo
- Java service Dockerfiles were reworked for cached Gradle dependency builds
- matching-engine’s Docker port metadata was corrected
- orders-service event timestamps were normalized to UTC string serialization
- end-to-end scenarios now validated transaction-journal side effects
- transaction-processor became a Kafka-driven append-only transaction journal with:
  - idempotent trade consumption
  - orders-service enrichment
  - buyer/seller leg journaling
  - query endpoints
  - downstream `transaction.recorded.v1` publication
- portfolio-service entered as a full deployable scaffold with compose wiring, Redis/Postgres/Kafka config, and health checks, but without business logic yet

The strongest single engineering move in the chunk is the transaction-processor rewrite. The strongest operational move is the benchmark capture plus Dockerfile standardization. The strongest architectural move is the split between:

- `transaction-processor` as journal writer
- `portfolio-service` as a newly introduced future read/projection consumer

That split is not fully implemented by the end of the chunk, but the commit makes the direction visible in both code and compose wiring.
