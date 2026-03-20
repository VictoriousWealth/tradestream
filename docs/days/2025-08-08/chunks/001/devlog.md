# Devlog

## 2025-08-08 08:38:13 +0100 — commit `b0f420ff5dda4b6cdf7ec293b2a86a59d031677c`

This chunk opens with a large architectural expansion commit that did three things in parallel:

- introduced a brand-new `transaction-processor` service,
- upgraded `market-data-consumer` from write-only ingest to a service other services could query,
- extended deployment wiring so the new service could run alongside auth, registration, and market data.

The changed-file list shows the breadth of the move. This was not a one-file feature tweak; it was the addition of a new microservice plus supporting API and infrastructure changes in adjacent services.

### Deployment topology expanded again

[`automated_scripts/dev-deploy.sh`](automated_scripts/dev-deploy.sh) added:

- `transaction-processor`

to both the `SERVICE_KEYS` and `SERVICE_DIRS` arrays.

Before this commit, the scripted Swarm update loop knew about:

- `auth-service`
- `user-registration-service`
- `market-data-consumer`

After it, `transaction-processor` became part of the same operational update flow.

[`docker-compose.yml`](docker-compose.yml) also gained a full `transaction-processor` service definition:

- build context `./transaction-processor`
- port mapping `8084:8084`
- dependency on:
  - `transaction_postgres`
  - `market-data-consumer`
- datasource environment for `transactiondb`
- `MARKET_SERVICE_URL=http://market-data-consumer:8083`
- `deploy.replicas: 1`

and a new dedicated database container:

- `transaction_postgres`

with:

- database `transactiondb`
- user `transactionuser`
- password `transactionpass`
- host port `5434`
- persistent volume `transaction_postgres_data`

This is the clearest before-to-after infrastructure change in the chunk. Before the commit, the local stack had no transaction-processing service and no transaction database. After it, the system topology had a new service/database pair, and that service was explicitly coupled to market-data-consumer over HTTP.

### Orders service was brought into the active runtime contract

The same compose diff removed:

- `profiles: ["later"]`

from `orders-service`.

That matters because it changes orders-service from an intentionally deferred compose component into part of the active stack definition. The environment block also gained explicit Kafka topic names for:

- `order.cancelled.v1`
- `trade.executed.v1`

in addition to `order.placed.v1`.

So even though the bulk of this chunk is about transaction processing, the deployment layer also made the broader trading pipeline more concrete. Before, orders-service was still marked as “later.” After, it was being wired as part of the currently intended runtime.

## `market-data-consumer` was upgraded from ingest-only to queryable latest-price service

The second major change inside `b0f420ff5dda4b6cdf7ec293b2a86a59d031677c` was the extension of `market-data-consumer`.

### DTO and mapper layer added

New files:

- [`StockDataDto`](services/market-data-consumer/src/main/java/com/tradestream/market_data_consumer/stock_data/StockDataDto.java)
- [`StockDataMapper`](services/market-data-consumer/src/main/java/com/tradestream/market_data_consumer/stock_data/StockDataMapper.java)

introduced a dedicated projection for exposing market data to other services. The DTO contains:

- `ticker`
- `name`
- `close`
- `date`

This is important because it formalized a boundary between the internal persistence entity and the data exposed to external callers. Before this commit, market-data-consumer only had the entity and ingest path. After it, it had a minimal read-model tailored for inter-service use.

### Repository grew latest-value queries

[`StockDataRepository`](services/market-data-consumer/src/main/java/com/tradestream/market_data_consumer/stock_data/StockDataRepository.java) gained:

- `findAllLatestStocks()` using a JPQL query grouping by ticker and taking max(date)
- `findTopByTickerOrderByDateDesc(String ticker)`

Before this commit, the repository only supported:

- `findByTickerAndDate`

which was enough for ingest/update logic but not enough for service-to-service read access. After it, the repository could serve “latest snapshot” queries across all tickers or by a specific ticker.

### Service layer added read-oriented methods

[`StockDataService`](services/market-data-consumer/src/main/java/com/tradestream/market_data_consumer/stock_data/StockDataService.java) was extended with:

- `getAllLatestStocks()`
- `getLatestByTicker(String ticker)`
- `getAllLatestStockDtos()`
- `getLatestStockDtoByTicker(String ticker)`

This changed the service from a write-only aggregate updater into a read/write component. The original ingest logic remained in place; these new methods layered query functionality on top.

### Controller exposed GET endpoints

[`StockDataController`](services/market-data-consumer/src/main/java/com/tradestream/market_data_consumer/stock_data/StockDataController.java) gained:

- `GET /api/stock`
- `GET /api/stock/{ticker}`

The first returns the latest DTO per ticker; the second returns the latest DTO for one ticker or `404` if absent.

Before this commit, the service only exposed:

- `POST /api/stock/event`

After it, market-data-consumer became a true dependency surface for other services. That is exactly what the new transaction-processor uses later in the same commit.

### TradeExecuted DTO precision changed from primitive numeric types to BigDecimal

[`services/market-data-consumer/src/main/java/com/tradestream/market_data_consumer/dto/TradeExecuted.java`](services/market-data-consumer/src/main/java/com/tradestream/market_data_consumer/dto/TradeExecuted.java) changed:

- `Double price` → `BigDecimal price`
- `Integer quantity` → `BigDecimal quantity`

This is a meaningful domain correction. Before the change, trade-execution values in market data used floating-point and integer types. After the change, both moved to `BigDecimal`, aligning better with financial precision requirements and with the growing use of decimal quantities elsewhere in the platform.

## `transaction-processor` was introduced as a new synchronous transaction and portfolio service

The largest implementation change in `b0f420ff5dda4b6cdf7ec293b2a86a59d031677c` is the full addition of `transaction-processor` as a Gradle/Spring Boot service.

### Project scaffold and build tooling

The service was added with the full standard scaffold:

- `.gitattributes`
- `.gitignore`
- `build.gradle`
- `Dockerfile`
- Gradle wrapper files
- `settings.gradle`
- Spring Boot application class
- properties
- Flyway migration
- test class

An empty [`pom.xml`](services/transaction-processor/pom.xml) was deleted, mirroring the repo’s wider move to Gradle for these services.

The build file shows the intended capabilities:

- Spring Web
- Spring Data JPA
- Spring Validation
- Flyway + PostgreSQL
- Lombok

Notably absent are Kafka client dependencies in this first version. That matters because the service is called `transaction-processor`, and later architecture docs will describe event-driven trade journaling, but the observable code in this commit is HTTP + database based rather than Kafka-consumer based.

### Market data integration was synchronous HTTP, not asynchronous events

[`RestTemplateConfig`](services/transaction-processor/src/main/java/com/tradestream/transaction_processor/config/RestTemplateConfig.java) provides a plain `RestTemplate` bean.

[`MarketDataClient`](services/transaction-processor/src/main/java/com/tradestream/transaction_processor/market_data/MarketDataClient.java) then uses that bean for two actions:

- `getStockByTicker(String ticker)` via `GET /api/stock/{ticker}`
- `publishMarketEvent(...)` via `POST /api/stock/event`

This is one of the most important evidence-based facts in the chunk. Before the later PRD rewrite, the actual implementation here is a synchronous service-to-service coupling:

- transaction-processor validates or enriches against market-data-consumer by REST,
- then posts a market event back to market-data-consumer after transaction processing.

There is no event broker involvement in this code path.

### Domain model introduced both transaction history and current holdings

The service added two persistence models:

- [`Transaction`](services/transaction-processor/src/main/java/com/tradestream/transaction_processor/transaction/Transaction.java)
- [`Portfolio`](services/transaction-processor/src/main/java/com/tradestream/transaction_processor/portofolio/Portfolio.java)

`Transaction` stores:

- `userId`
- `ticker`
- signed `quantity`
- `price`
- enum `type`
- `createdAt`

`Portfolio` stores:

- `userId`
- `ticker`
- `quantity`
- `updatedAt`

with a unique constraint on `(user_id, ticker)`.

This is a two-table model:

- immutable-ish transaction rows for history,
- one current row per user/ticker for holdings.

Before this commit, the repo had no implementation for transaction journaling or portfolio state under this service. After it, both concepts existed side by side.

### Service behavior: validate ticker, update holdings, write transaction, publish telemetry

[`TransactionService`](services/transaction-processor/src/main/java/com/tradestream/transaction_processor/transaction/TransactionService.java) defines the actual processing flow.

The method `processTransaction(...)` does the following:

1. fetch latest stock data by ticker from market-data-consumer,
2. reject unknown tickers with `IllegalArgumentException`,
3. reject non-positive buy/sell quantities,
4. convert sell quantities to negative signed values,
5. load or create the portfolio row for `(userId, ticker)`,
6. reject sells that would drive holdings below zero,
7. persist the updated portfolio quantity,
8. persist a transaction row,
9. attempt to publish a market-data event back to market-data-consumer,
10. swallow failures in that publish path except for printing to stderr.

This gives the service a clear first-cut role:

- it is both transaction intake and portfolio updater,
- it depends on market data as a ticker-validation source,
- it pushes trade telemetry back into market-data-consumer.

The last point is especially important. The service is not just reading from market data; it also uses market-data-consumer as the receiver of transaction-derived events. That makes the coupling bidirectional.

### Public API surface

[`TransactionController`](services/transaction-processor/src/main/java/com/tradestream/transaction_processor/transaction/TransactionController.java) exposes:

- `POST /api/transactions/{userId}`
- `GET /api/transactions/portfolio/{userId}`
- `GET /api/transactions/history/{userId}`

So before this commit, there was no transaction processing API. After it, the service offered:

- transaction submission,
- portfolio lookup,
- transaction-history lookup.

### Schema matches the first-cut domain model

[`V1__init_schema.sql`](services/transaction-processor/src/main/resources/db/migration/V1__init_schema.sql) creates:

- `transactions`
- `portfolio`

with indexes on:

- `transactions.user_id`
- `transactions.ticker`
- `portfolio.user_id`

This is consistent with the Java model and confirms that the service was not half-scaffolded; it was a full application with schema migration from the start.

## 2025-08-08 11:12:42 +0100 — commit `c72277b9bff3e54d0f5d434bc0e3328d81cde354`

The second commit is a major rewrite of [`docs/tradestream-prd.md`](docs/tradestream-prd.md). The diff is large, but the broad direction is clear even from the visible slice: the PRD was radically condensed and refocused around a matching-engine/event-driven trading architecture rather than the earlier broad planning-style document.

### Architecture section title changed to reflect the newer system center

In the table of contents, section 5 changed from:

- `High-Level Architecture (Updated with Authentication Service)`

to:

- `High-Level Architecture (Updated with Matching Engine)`

That alone captures the shift in system emphasis. Before, authentication service was treated as the key architectural update. After this commit, the matching engine became the defining organizing concept.

### Large amounts of generic planning prose were removed

The visible diff shows entire earlier sections being stripped out:

- title/document-control verbosity,
- long executive-summary prose,
- generic MVP/future-enhancement breakdowns,
- earlier AWS Lightsail / RabbitMQ / draft-style planning language.

Although the truncated diff does not expose every inserted replacement section in full, it clearly shows the PRD moving away from a long, portfolio-planning document and toward a shorter, more implementation-oriented architecture writeup.

### Version/date and project framing were updated

The replacement header visible in the diff shows:

- project renamed in the document control table to `TradeStream — Event-Driven Trading Simulation Platform`
- version changed to `2.0 (Upgraded Architecture)`
- date updated to `Fri, 08 August 2025`

That is a formal before-to-after re-baselining of the PRD. It marks the document not as a polished first PRD draft anymore, but as an upgraded architecture spec aligned with the newer service set and matching/trade pipeline.

### Evidence limitation on the rewritten PRD body

The diff excerpt available in this chunk clearly demonstrates that the document was extensively rewritten and heavily reduced, but not all replacement content is visible in the provided patch segment. So the precise wording of every new section cannot be reconstructed here. What is fully supported is:

- the PRD was aggressively condensed,
- its focus shifted toward an event-driven trading simulation architecture,
- matching engine replaced authentication service as the named architectural update anchor,
- and the old long-form planning narrative was removed in favor of a new version/date/project framing.

## Overall evolution across the chunk

The two commits work together:

1. `b0f420ff5dda4b6cdf7ec293b2a86a59d031677c` materially expands the codebase by:
   - adding a new `transaction-processor` service,
   - making `market-data-consumer` queryable by other services,
   - extending compose/deploy wiring,
   - activating more of the trading pipeline in local runtime.
2. `c72277b9bff3e54d0f5d434bc0e3328d81cde354` then rewrites the PRD so the architecture narrative catches up to the codebase’s newer center of gravity.

There is also a productive tension in the evidence:

- the implementation introduced in the first commit is still heavily synchronous and REST-coupled,
- while the PRD rewrite’s visible framing pushes the project toward an event-driven matching-engine-centric story.

That mismatch is itself part of the historical record. The code in this chunk shows the platform in transition: the architecture docs are being updated toward a more advanced trading-system design at the same time the concrete implementation still relies on direct HTTP interactions between services for transaction processing and market-data publication.
