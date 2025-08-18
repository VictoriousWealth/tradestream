# Market Data Consumer — Source of Truth

**One-liner:** Kafka→Postgres aggregator that turns `TradeExecuted` events into OHLCV candles (`1m/5m/1h/1d`), exposes a tiny REST API for reads, and keeps “latest” responses hot via Redis with precise cache eviction.

## Executive summary (for recruiters)

* Built a **real-time market data aggregator** that consumes trade events, performs **idempotent** upserts into Postgres, and serves **low-latency** reads via Redis caching and cache-on-write eviction.
* Designed for **event-time correctness** (UTC bucketing, handles out-of-order & late events) and **operational resilience** (Flyway migrations, health checks, deterministic consumer group behavior).
* Clean separation of concerns: Kafka listener → aggregation service → repositories → REST controller; cache layer is explicit and predictable.

---

## What this service does

* **Consumes**: `trade.executed.v1` (JSON over Kafka/Redpanda).
* **Idempotency**: `ingested_trades(trade_id)` ensures duplicates are skipped before any state change.
* **Aggregation**: computes **UTC** bucket starts for `1m`, `5m`, `1h`, `1d`; performs an atomic **UPSERT** (Postgres `ON CONFLICT`) to adjust OHLCV and volume.
* **Caching strategy**: latest candle per `(ticker, interval)` cached in Redis with TTL; **every successful upsert evicts** the exact cache key so the next read is fresh.
* **API**:

  * `GET /candles/{ticker}?interval=1m&limit=100` (recent, newest first; limit ≤1000)
  * `GET /candles/{ticker}/latest?interval=1m` (404 if none)

---

## Tech stack & key choices

* **Language/Framework**: Java 17, Spring Boot
* **Messaging**: Redpanda (Kafka-compatible)
* **Database**: Postgres (per-service instance), Flyway migrations
* **Cache**: Redis (type-safe Jackson serializer with default typing)
* **Containerization**: Docker, docker-compose; health checks via Spring Actuator
* **Networking**: service-to-service on `private_net` (compose), optional gateway exposure

Rationale:

* **Postgres UPSERT** keeps write path single-round-trip and atomic.
* **Event-time bucketing** preserves correctness under network jitter/out-of-order arrivals.
* **Cache-evict on write** keeps reads fast **and** correct without complex invalidation logic.

---

## Architecture at a glance

* **Listener**: `TradeExecutedListener` (`@KafkaListener`) → hands off to `AggregationService`.
* **AggregationService**:

  1. `ingested_trades.tryInsert(tradeId)` → if duplicate, **stop**.
  2. for each interval: compute bucket (UTC), call `candleRepo.upsertCandle(...)`.
  3. `CacheOps.evictLatest("{interval}:{TICKER}")`.
* **Repositories**:

  * `CandleRepository.upsertCandle(...)` (native SQL `INSERT ... ON CONFLICT DO UPDATE`).
  * `IngestedTradeRepository.tryInsert(...)` (idempotency).
* **Read path**:

  * `CandleQueryService.latest(...)` is `@Cacheable(cache="latest", key="{interval}:{TICKER}"`).
  * `recent(...)` hits DB directly (paged).
* **Controller**: validates interval, exposes `/candles/**`.

---

## Interfaces & contracts

### Kafka (input)

**Topic:** `trade.executed.v1`
**Payload (`TradeExecuted`)**:

```json
{
  "tradeId": "uuid", "orderId": "uuid", "userId": "uuid",
  "ticker": "AAPL", "price": 196.210000, "quantity": 10.000000, "side": "BUY",
  "timestamp": "2025-08-01T09:41:12.345Z"   // event time in UTC
}
```

**Rules:**

* `tradeId` must be unique per execution.
* `timestamp` is authoritative for bucketing (UTC).

### HTTP (output)

* `GET /candles/{ticker}?interval=1m&limit=100`
* `GET /candles/{ticker}/latest?interval=1m` → cached; eviction on new trade.

---

## Data model (Postgres)

* `candles`

  * **Unique**: `(ticker, interval, bucket_start)`
  * **Columns**: open/high/low/close `NUMERIC(18,6)`, volume `NUMERIC(20,6)`, updated\_at
  * **Index**: `(ticker, interval, bucket_start DESC)` (fast “latest”)
* `ingested_trades`

  * **PK**: `trade_id` (UUID), plus `(ticker, ts)` index for ops

**Schema management**: Flyway (`V0__enable_pgcrypto.sql`, `V1__create_candles_and_ingested_trades.sql`).

---

## Caching model (Redis)

* Cache name: `latest`
* **Key**: `"{interval}:{TICKER}"` (e.g., `1m:AAPL`)
* **Value**: `Candle` serialized with Jackson default typing (so it deserializes as `Candle`, not a Map)
* TTL: 10 minutes
* **Eviction**: after each successful candle upsert for all affected intervals.

---

## Configuration (env)

| Key                             | Default                                           | Notes                    |
| ------------------------------- | ------------------------------------------------- | ------------------------ |
| `SERVER_PORT`                   | 8083                                              | HTTP                     |
| `SPRING_DATASOURCE_URL`         | `jdbc:postgresql://market_postgres:5432/marketdb` |                          |
| `SPRING_DATASOURCE_USERNAME`    | `marketuser`                                      |                          |
| `SPRING_DATASOURCE_PASSWORD`    | `marketpass`                                      |                          |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `validate`                                        | use Flyway for schema    |
| `SPRING_FLYWAY_ENABLED`         | `true`                                            |                          |
| `KAFKA_BOOTSTRAP_SERVERS`       | `redpanda:9092`                                   |                          |
| `KAFKA_CONSUMER_GROUP`          | `md-consumer`                                     | change per env if needed |
| `KAFKA_TOPIC_TRADE_EXECUTED`    | `trade.executed.v1`                               |                          |
| `REDIS_HOST` / `REDIS_PORT`     | `redis` / `6379`                                  |                          |

---

## Operations & runbook

* **Bring up (service + deps):**
  `docker compose up -d --build market_postgres redis redpanda market-data-consumer`
* **Health:**
  `docker compose exec market-data-consumer curl -sf http://localhost:8083/actuator/health`
* **Logs:**
  `docker compose logs -f market-data-consumer`
* **Quick sanity:**

  * Produce a test trade to `trade.executed.v1`.
  * `curl "http://market-data-consumer:8083/candles/AAPL/latest?interval=1m"`

**Common gotchas**

| Symptom                   | Likely cause                   | Fix                                     |
| ------------------------- | ------------------------------ | --------------------------------------- |
| 404 on `/latest`          | no trades yet                  | publish a trade                         |
| stale “latest”            | quiet market → TTL not expired | expected; next trade evicts             |
| deserialization errors    | payload mismatch               | align fields/types with `TradeExecuted` |
| `gen_random_uuid()` error | pgcrypto missing               | ensure Flyway ran `V0__...`             |
| trade ignored             | duplicate `tradeId`            | send unique UUIDs                       |

---

## Extensibility (how to evolve fast)

* **Add interval (15m)**: add to `Interval`, update `Bucketizer`, include in `AggregationService.SUPPORTED` (no schema change).
* **Expose externally**: route via API Gateway (e.g., `/mdc/**`), enforce JWT, rate-limit.
* **Backfill**: publish historical `TradeExecuted` with original timestamps; service will fill historical buckets correctly.

---

# Resume/CV content (copy-paste ready)

## Impact bullets (choose 3–5)

* Built a **real-time market data aggregation service** (Java/Spring, Kafka, Postgres, Redis) that converts trade events into OHLCV candles across multiple intervals with **idempotent** upserts and **event-time** correctness (UTC bucketing).
* Achieved **sub-millisecond cache hits** for “latest” reads via Redis and **precise cache-on-write eviction**, reducing median read latency by **\~X%** vs. DB lookups (insert your measured number).
* Designed a **duplicate-safe pipeline** using an `ingested_trades` ledger (UUID keys) to guarantee at-least-once consumption without double counting.
* Implemented **atomic UPSERTs** with Postgres `ON CONFLICT` to maintain OHLCV integrity under concurrency with a single round trip.
* Operationalized with Docker Compose, **health checks**, Flyway migrations, and structured logging, enabling **one-command local bring-up** and predictable CI runs.

## Tech stack line

**Java 17, Spring Boot, Spring Kafka, Spring Data JPA, Postgres (Flyway), Redis, Docker, Redpanda/Kafka, Actuator**

## Scope/scale (fill in real numbers when you have them)

* Throughput: \~**N** msgs/sec peak; **M** avg
* Latency: **P** ms p50/p95 for GET `/latest`
* Data size: **X** million candles; **Y** GB over **Z** months
* Availability: **99.xx%** over **period**

---

# Cover-letter paragraph (customize per role)

> I recently designed and shipped a real-time **market data consumer** that aggregates Kafka trade events into OHLCV candles. The service is **idempotent** (duplicate-safe), **event-time correct** (UTC bucketing for late/out-of-order events), and provides **low-latency** reads via Redis with cache-on-write eviction. I used **Java 17/Spring**, **Postgres** (atomic UPSERTs), and **Redpanda/Kafka**, packaged with Docker and managed with Flyway migrations and health checks. This project mirrors the production concerns I enjoy—correctness, performance, and operability—and I’d be excited to bring the same rigor to \<Company/Team>.

---

# Interview talking points (quick recall)

* Why event-time (UTC) vs. processing-time bucketing.
* How `ingested_trades` enables **at-least-once** consumption without double counting.
* Why **UPSERT** (and its exact SQL) keeps OHLCV invariant correct under concurrency.
* Cache design: keys, TTL, and explicit eviction on write (why “eventual consistency” isn’t good enough for “latest”).
* Failure modes: Kafka deserialization, Flyway/pgcrypto, cache staleness; how each is detected and mitigated.

---

# “Cheat sheet” for yourself

**Bring up deps + service**

```bash
docker compose up -d --build market_postgres redis redpanda market-data-consumer
```

**Health**

```bash
docker compose exec market-data-consumer curl -sf http://localhost:8083/actuator/health
```

**Sample trade payload** (publish to `trade.executed.v1`)

```json
{"tradeId":"<uuid>","orderId":"<uuid>","userId":"<uuid>","ticker":"AAPL","price":196.21,"quantity":10.0,"side":"BUY","timestamp":"2025-08-01T09:41:12Z"}
```

**Query latest**

```bash
curl -s "http://market-data-consumer:8083/candles/AAPL/latest?interval=1m" | jq .
```

**Cache key format**

```
latest cache name; key = "{interval}:{TICKER}"  e.g., "1m:AAPL"
```

---

# Where this fits in the bigger system (one paragraph)

Part of a larger trading microservice suite: orders are placed via `orders-service`, matched by `matching-engine`, which publishes `trade.executed.v1`. This service listens and aggregates trades into candles for downstream reads (e.g., portfolio calculations, UIs). It lives on the **private network**, and endpoints can be safely exposed through the **API gateway** with JWT authentication if external access is needed.

---

# Optional: README snippet for the repo root

> **Market Data Consumer** — Aggregates `trade.executed.v1` Kafka events into OHLCV candles at `1m/5m/1h/1d` granularity. Idempotent via `ingested_trades` ledger and atomic Postgres UPSERT. Serves recent/latest candles over REST; latest is Redis-cached with precise eviction on new trades.
> **Stack:** Java 17, Spring Boot, Kafka (Redpanda), Postgres (Flyway), Redis, Docker.
> **Endpoints:** `/candles/{ticker}`, `/candles/{ticker}/latest` (interval query param).
> **Why it exists:** supports charting, analytics, and latency-sensitive reads without scanning raw trades.

---

## Tips for using this as your “single source of truth”

* Keep this page **versioned** alongside the code. Update when you change intervals, schema, cache TTL, or topics.
* After you gather real metrics (latency, throughput), **replace placeholders** above so your resume bullets have credible numbers.
* Link this doc from your repo root README so recruiters land here quickly.
* If you publish externally, add a short **OpenAPI snippet** and a **Postman collection** next to this doc.

---
