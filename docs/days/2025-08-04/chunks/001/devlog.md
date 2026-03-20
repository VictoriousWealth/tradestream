# Devlog

## 2025-08-04 23:21:50 +0100 — commit `e568e24694213707c17f4f1ed05040d5649e26ef`

This chunk is anchored by a large service-bootstrap commit that turned `market-data-consumer` from a directory referenced in compose into a full Spring Boot application with its own build, runtime, schema, controller layer, service layer, documentation, and deployment wiring.

The changed-file set shows the scope clearly:

- service scaffolding under [`market-data-consumer/`](market-data-consumer)
- runtime/deploy changes in [`docker-compose.yml`](docker-compose.yml) and [`automated_scripts/dev-deploy.sh`](automated_scripts/dev-deploy.sh)
- one documentation fix in [`user-registration-service/README.md`](user-registration-service/README.md)

### Deployment script expanded from two services to three

[`automated_scripts/dev-deploy.sh`](automated_scripts/dev-deploy.sh) previously iterated over only:

- `auth-service`
- `user-registration-service`

After this commit it also included:

- `market-data-consumer`

in both `SERVICE_KEYS` and `SERVICE_DIRS`.

This is a small patch, but it marks a real deployment boundary change. Before the commit, the repo’s scripted update workflow only knew how to build and force-update auth and registration. After it, market data joined that same operational path, meaning it was being treated as a first-class deployable service rather than an unfinished side project.

### Compose service definitions gained `deploy.replicas`

[`docker-compose.yml`](docker-compose.yml) did not add the market-data service in this chunk, because that happened earlier. Instead, this commit added:

- `deploy:`
  - `replicas: 1`

to:

- `user-registration-service`
- `auth-service`
- `market-data-consumer`

That is a notable shift in orchestration intent. Before this commit, the compose file defined build/runtime settings but not explicit replica counts. After it, the three services were described in a way that fits Swarm-style deployment semantics more directly. This aligns with the `docker stack deploy` language used in the new market-data README added in the same commit.

## `market-data-consumer` was introduced as a complete Gradle/Spring Boot project

The largest part of the patch created the entire `market-data-consumer` service structure:

- [`.gitattributes`](market-data-consumer/.gitattributes)
- [`.gitignore`](market-data-consumer/.gitignore)
- [`build.gradle`](market-data-consumer/build.gradle)
- [`Dockerfile`](market-data-consumer/Dockerfile)
- Gradle wrapper files
- [`settings.gradle`](market-data-consumer/settings.gradle)
- application bootstrap class
- domain, controller, repository, service, validation handler
- application properties
- four Flyway migrations
- context-load test
- service README

This was not an incremental feature addition. It was the first full application implementation for this service in the evidence.

### Build, runtime, and packaging choices

[`market-data-consumer/build.gradle`](market-data-consumer/build.gradle) established the service as:

- Spring Boot `3.5.4`
- Java toolchain `17`
- Web + Data JPA + Validation
- Flyway core and PostgreSQL-specific Flyway support
- PostgreSQL runtime driver
- Lombok

The [`Dockerfile`](market-data-consumer/Dockerfile) follows the same pattern used by the other Java services in the repo at this stage:

- base image `eclipse-temurin:17-jdk`
- copy full service directory
- run `./gradlew clean build -x test`
- expose `8083`
- run the built JAR

So before this commit, there was no complete build-and-run contract for market data in the repo evidence. After it, the service had a standardized Gradle-and-Docker scaffold aligned with the auth and registration services.

## The first implementation was REST-ingest plus Postgres persistence, not stream consumption

Despite the service name `market-data-consumer`, the actual implementation introduced in this commit is HTTP-ingest based rather than Kafka-consumer based.

### Application entrypoint

[`market-data-consumer/src/main/java/com/tradestream/market_data_consumer/MarketDataConsumerApplication.java`](market-data-consumer/src/main/java/com/tradestream/market_data_consumer/MarketDataConsumerApplication.java) introduced the service bootstrap class and nothing more. The real behavior appears in the `stock_data` package.

### Input model: `MarketDataEvent`

[`market-data-consumer/src/main/java/com/tradestream/market_data_consumer/stock_data/MarketDataEvent.java`](market-data-consumer/src/main/java/com/tradestream/market_data_consumer/stock_data/MarketDataEvent.java) defined the inbound request shape as a Java record with validation:

- `ticker`: not null, size 1–10
- `name`: not null, size 1–255
- `price`: not null, positive
- `volume`: positive
- `date`: not null

That model matches the REST API described in the README and makes it clear that, at this point, the “consumer” was consuming JSON over HTTP rather than message-broker events.

### Persistence model: one row per ticker/date OHLCV aggregate

[`market-data-consumer/src/main/java/com/tradestream/market_data_consumer/stock_data/StockData.java`](market-data-consumer/src/main/java/com/tradestream/market_data_consumer/stock_data/StockData.java) introduced the persisted entity.

The entity contains:

- `id`
- `name`
- `ticker`
- `date`
- `open`
- `high`
- `low`
- `close`
- `volume`
- `createdAt`
- `updatedAt`

with a unique constraint on:

- `(ticker, date)`

This is a crucial architectural detail. The service did not begin as a raw event store. It began as a daily OHLCV projection keyed by ticker and date, with one row being updated over time as more events arrive.

### Repository and service logic

[`StockDataRepository`](market-data-consumer/src/main/java/com/tradestream/market_data_consumer/stock_data/StockDataRepository.java) exposes:

- `findByTickerAndDate`

and [`StockDataService`](market-data-consumer/src/main/java/com/tradestream/market_data_consumer/stock_data/StockDataService.java) uses it to implement the core aggregation behavior.

The service logic is:

1. extract `ticker`, `date`, `price`, and `volume` from the incoming event
2. look up existing row by `ticker + date`
3. if none exists:
   - create a new row
   - set `open`, `high`, `low`, and `close` all to the incoming price
   - set `volume` to the incoming volume
4. if a row exists:
   - update `high` using `max`
   - update `low` using `min`
   - set `close` to the new price
   - increment `volume`
5. save the row

This is the actual business behavior introduced in the chunk. Before the commit, no evidence-backed implementation existed here. After it, the service could act as an OHLCV accumulator driven by validated REST payloads.

That also means the first version had no separate event log, no intraday bucket granularity beyond the `date` field, and no explicit asynchronous broker consumption. The “consumer” label was aspirational or conceptual at this point; the code path is synchronous HTTP ingestion.

### Controller and error contract

[`StockDataController`](market-data-consumer/src/main/java/com/tradestream/market_data_consumer/stock_data/StockDataController.java) exposes:

- `POST /api/stock/event`

and returns:

- `202 Accepted`

after delegating to the service.

[`ValidationExceptionHandler`](market-data-consumer/src/main/java/com/tradestream/market_data_consumer/stock_data/ValidationExceptionHandler.java) catches `MethodArgumentNotValidException` and returns a `400` JSON map of field errors.

This gives the service a narrow but real contract:

- one ingest endpoint,
- validated input,
- structured validation failures.

## Database schema and seed data were built around the same daily-aggregate model

The Flyway migrations are important because they show how the Java model was intended to map to the database from day one.

### `V1__init_schema.sql`

[`V1__init_schema.sql`](market-data-consumer/src/main/resources/db/migration/V1__init_schema.sql) created `stock_data` with:

- UUID primary key
- `name`, `ticker`, `date`
- OHLC fields
- `volume`
- unique constraint on `(ticker, date)`

### `V2__add_timestamps_to_stock_data.sql`

[`V2__add_timestamps_to_stock_data.sql`](market-data-consumer/src/main/resources/db/migration/V2__add_timestamps_to_stock_data.sql) added:

- `created_at`
- `updated_at`

### `V3__create_extension_pgcrypto.sql`

[`V3__create_extension_pgcrypto.sql`](market-data-consumer/src/main/resources/db/migration/V3__create_extension_pgcrypto.sql) enabled:

- `pgcrypto`

### `V4__seed_stock_data.sql`

[`V4__seed_stock_data.sql`](market-data-consumer/src/main/resources/db/migration/V4__seed_stock_data.sql) inserted sample rows for:

- `AAPL`
- `TSLA`

using `gen_random_uuid()`, timestamps, and daily OHLCV values.

The before-to-after evolution here is coherent:

- before: no schema for market data existed in this service directory
- after: the service had a staged migration history and test seed data matching the entity shape

The use of `pgcrypto` is specifically tied to UUID generation in seed data via `gen_random_uuid()`.

## Configuration reflected a DB-backed HTTP service

[`application.properties`](market-data-consumer/src/main/resources/application.properties) set:

- `spring.application.name=market-data-consumer`
- default port `8083`
- datasource URL/user/password from environment
- JPA validate mode
- SQL logging enabled
- Flyway enabled and configured

That reinforces the implementation character of the service:

- database-backed,
- schema-validated,
- verbose enough for development-time SQL inspection.

No security configuration appears in this chunk, and no broker client dependencies or broker settings appear in the build or properties files. That absence matters because it bounds what the service actually did in its first commit.

## The initial README documented the service as if it were already fully coherent

[`market-data-consumer/README.md`](market-data-consumer/README.md) was added in the same bootstrap commit and described the service as:

- ingesting market data events,
- validating them,
- persisting them,
- exposing a REST API,
- using PostgreSQL + Flyway,
- being Dockerized and orchestrated with Compose/Swarm,
- tested via `curl` and SQL inspection.

It also documented:

- `POST /api/stock/event`
- request and response shape
- validation rules
- `docker build`
- `docker stack deploy -c docker-compose.yml tradestream`
- the schema and seed data
- the project folder structure

This README is mostly aligned with the actual code. The main point of drift is naming: the service is called a “consumer,” but no broker-consumer code is introduced here. Everything observable is synchronous REST ingestion.

## 2025-08-04 23:27:44 +0100 — commit `94c4eafa89a5c490dbd4cf59faf657d0ed8b3f1f`

The first follow-up commit edited only [`market-data-consumer/README.md`](market-data-consumer/README.md). This was a formatting pass, not a feature change.

### Emoji-heavy headings were stripped back

Section titles were simplified:

- `# 📈 Market Data Consumer Service` → `# Market Data Consumer Service`
- `## 🚀 Features` → `## Features`
- `## 🔧 Tech Stack` → `## Tech Stack`
- and similar removals for validation, running locally, database schema, seed data, folder structure.

### Badge block was turned into a bullet list

The standalone badge lines under “Tech Stack” were converted into markdown bullets, and `Docker Compose` was added explicitly as a badge item.

### One heading was accidentally corrupted

The “API Specification” heading became:

- `##� API Specification`

This is a clear regression introduced by the edit. Before the commit, the heading rendered correctly with an emoji. After it, the heading contained a malformed leading character.

That means the commit was not a pure cleanup. It simplified the README styling but introduced a visible encoding/rendering error.

## 2025-08-04 23:29:39 +0100 — commit `bc3a7c1f7339db5a5ade3c5a3f86598a0c9cee1e`

This commit did not touch market-data at all; it fixed a rendering issue in [`user-registration-service/README.md`](user-registration-service/README.md).

The screenshots section previously contained:

```bash
![./docs/example-curl-request-to-microservice.png]
```

inside a fenced code block. After the change, it became:

- `![](./docs/example-curl-request-to-microservice.png)`

Before this commit, the README displayed the markdown image syntax as literal code rather than rendering the image. After it, the screenshot could actually display inline.

This is a small doc fix, but it belongs in the chunk chronology because it shows the same pattern as the market-data README edits: newly introduced service docs were being immediately cleaned up for rendering correctness.

## 2025-08-04 23:33:00 +0100 — commit `cc56288175b410ade0a7d771ce0a7e41f46ad8fd`

The final commit in the chunk fixed the encoding regression introduced in `94c4eafa89a5c490dbd4cf59faf657d0ed8b3f1f`.

In [`market-data-consumer/README.md`](market-data-consumer/README.md), the broken heading:

- `##� API Specification`

was corrected to:

- `## API Specification`

This completes the immediate README stabilization cycle:

1. bootstrap the service and write a first README,
2. simplify heading/badge style,
3. accidentally corrupt one heading,
4. repair the heading.

## Overall evolution in the chunk

The main before-to-after change is that `market-data-consumer` became a concrete, runnable, database-backed microservice instead of just a name in the system.

Before `e568e24694213707c17f4f1ed05040d5649e26ef`, the service did not have, within this chunk’s evidence:

- a build definition,
- Docker packaging,
- application bootstrap class,
- controller/service/repository/entity code,
- Flyway schema history,
- service README,
- deployment-script participation.

After it, the repository had:

- a full Gradle/Spring Boot service scaffold,
- a REST ingest endpoint at `POST /api/stock/event`,
- daily OHLCV aggregation persisted by `ticker + date`,
- Flyway-managed schema and seed data,
- deployment-script support,
- compose definitions with explicit replica counts.

The three follow-up commits then focused only on documentation rendering:

- removing some emoji-heavy heading style from the market-data README,
- fixing a user-registration screenshot embed,
- correcting a broken “API Specification” heading introduced by the README cleanup.

The evidence does not support claiming Kafka consumption, quote-stream handling, or multi-interval candle aggregation in this first version. The service name suggests a future direction, but the implementation added in this chunk is a validated REST ingest path that maintains one daily aggregate row per ticker/date in PostgreSQL.
