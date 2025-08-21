# 📌 The Ultimate Source of Truth: **Portfolio Service**

---

## 1) One-liner — A single, impactful sentence.

Event-driven projection service that consumes **transaction records** from Kafka and maintains **authoritative portfolio positions & realized PnL** in Postgres with strict **idempotency** and **pessimistic concurrency control**.

---

## 2) Executive Summary (for recruiters) — High-level business value + key technical achievements

* Built a **transactional core** that projects trade transactions into **per-user positions** (quantity, average cost, realized PnL) with **exactly-once effects** over an **at-least-once** Kafka pipeline.
* Implements **message de-duplication** via a `(topic, message_id)` ledger and **manual acks** for deterministic processing; recovers cleanly with **exponential backoff** and **dead-letter topics**.
* Enforces portfolio **calculation invariants** (weighted-average cost, clamped sells, null avgCost at flat) at both code and schema levels (NUMERIC precision, PKs).
* Exposes **read APIs** for positions and portfolio summary, engineered for ops safety (Flyway migrations, health probes, containerized builds).

---

## 3) What this service does — Responsibilities

* 🎧 **Consumes** `transaction.recorded.v1` events and applies BUY/SELL effects to positions.
* 🧮 **Maintains**: position quantity, weighted average cost (WAC), realized PnL; resets avgCost when flat.
* 🧱 **Guarantees idempotency** using `processed_messages` ledger keyed by `(topic, message_id)`; falls back to **synthetic stable IDs** when header missing.
* 🔒 **Serializes writes per position** with **pessimistic row locks** to avoid race conditions.
* 🌐 **Exposes REST read endpoints**:

  * `GET /portfolio/{userId}/positions`
  * `GET /portfolio/{userId}/positions/{ticker}`
  * `GET /portfolio/{userId}/summary`
* 🛟 **Operational robustness**: backoff + DLT on poison messages; health endpoints for k8s/docker.

---

## 4) Tech Stack & Key Choices — Table + design rationale

| Technology                  | Purpose                        | Why this choice                                                              |
| --------------------------- | ------------------------------ | ---------------------------------------------------------------------------- |
| **Java 17 + Spring Boot**   | Service framework              | Mature ecosystem, records/validation, actuator, easy Kafka & JPA integration |
| **Spring Kafka**            | Kafka consumer, error handling | Manual ack control, `DefaultErrorHandler` with backoff & DLT integration     |
| **Apache Kafka / Redpanda** | Event backbone                 | Partitioned scalability, durable at-least-once delivery                      |
| **PostgreSQL + Flyway**     | Persistence & schema evolution | ACID, precise `NUMERIC(18,8)`, versioned migrations                          |
| **JPA/Hibernate**           | ORM with pessimistic locks     | `SELECT … FOR UPDATE` semantics via repository method                        |
| **Docker**                  | Reproducible runtime           | One-step build & run in compose                                              |
| **Actuator**                | Health & metrics               | Liveness/readiness probes enabled                                            |
| **(Optional) Redis**        | Read caching                   | Hooked via Spring Cache if enabled (not required for correctness)            |

**Design rationale highlights**

* **Idempotency ledger**: `processed_messages(topic, message_id)` uniquely records processed events; safe replays and crash recovery.
* **Manual acks + concurrency=1**: Deterministic ordering per consumer; ack only after commit for end-to-end exactly-once effect.
* **Pessimistic locking over optimistic**: Avoids retry storms under concurrent fills for the same `(userId, ticker)`.
* **WAC math**: BUY recalculates weighted average at 8-dp; SELL realizes PnL and **never increases quantity** beyond existing long (sell clamped).
* **DLT by convention**: `topic.DLT` routing centralizes investigation of poison payloads.

---

## 5) Architecture at a glance — Primary flows (API calls, events, etc.)

### Flow A: Apply a Transaction (Kafka → DB)

1. Consumer receives `TransactionRecordedEvent` (may have `eventId` header or payload field).
2. **Message ID resolution**: prefer `eventId`; else parse Kafka header; else synthesize stable UUID from `topic|partition|offset`.
3. **Idempotency check**: `processed_messages` existence ⇒ **ignore** & ack.
4. **Lock target position**: `SELECT … FOR UPDATE` on `(userId, ticker)` or initialize new row.
5. **Project**:

   * **BUY** → `newQty = qty + Δ`, `avg = (qty*avg + Δ*price)/newQty` (8 dp, HALF\_UP).
   * **SELL** → `sellQty = min(Δ, max(qty,0))`, `realizedPnL += (price - avg)*sellQty`, `qty -= sellQty`, `avg=null` if flat.
6. **Commit** `positions` & insert into `processed_messages`.
7. **Ack** the Kafka message.

### Flow B: Read Portfolio (API → DB)

1. Client calls one of the `/portfolio` endpoints.
2. Service returns JSON with positions and/or summarized realized PnL (unrealized/market value are `null` by design in MVP).

### Flow C: Error Path (Retries → DLT)

1. Transient exception ⇒ **ExponentialBackOff** (200ms → 5s, 5 retries).
2. `IllegalArgumentException` or exhausted retries ⇒ publish to **`<source-topic>.DLT`** and ack to avoid hot-looping.

---

## 6) Rules & Invariants — Core business rules

* **BUY** increases quantity and recomputes **weighted average cost**; precision **`NUMERIC(18,8)`**; rounding **HALF\_UP**.
* **SELL** cannot exceed existing long quantity (shorting not yet supported); realizes PnL at `price - avgCost`.
* **Flat position ⇒ `avgCost = null`** to avoid stale cost carryover.
* Each Kafka message is **applied at most once per topic** (ledger uniqueness).
* Unknown `side` or missing identifier ⇒ **not retryable** (sent to DLT).
* `updated_at` is system-managed on every write.

---

## 7) Data Model — Main tables, keys, purpose

**positions**

* **PK**: `(user_id UUID, ticker VARCHAR(16))`
* **Columns**: `quantity NUMERIC(18,8)`, `avg_cost NUMERIC(18,8) NULL`, `realized_pnl NUMERIC(18,8)`, `updated_at TIMESTAMPTZ`
* **Indexes**: `ix_positions_user (user_id)`
* **Purpose**: Authoritative per-user per-ticker position & realized PnL.

**processed\_messages**

* **PK**: `id BIGSERIAL`
* **UK**: `uk_processed_topic_msgid (topic, message_id UUID)`
* **Columns**: `topic VARCHAR(200)`, `message_id UUID`, `received_at TIMESTAMPTZ`
* **Purpose**: Idempotency ledger to guarantee once-only projection per topic.

---

## 8) Configuration (env) — Critical environment variables

| Key                                                         | Default                                        | Notes                                             |
| ----------------------------------------------------------- | ---------------------------------------------- | ------------------------------------------------- |
| `SERVER_PORT`                                               | `8087`                                         | REST port                                         |
| `SPRING_DATASOURCE_URL`                                     | `jdbc:postgresql://localhost:5432/portfoliodb` | JDBC URL                                          |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | `portfoliouser` / `portfoliopass`              | DB creds                                          |
| `SPRING_JPA_HIBERNATE_DDL_AUTO`                             | `validate`                                     | Enforce Flyway over auto-DDL                      |
| `SPRING_FLYWAY_ENABLED` / `SPRING_FLYWAY_LOCATIONS`         | `true` / `classpath:db/migration`              | Migrations                                        |
| `KAFKA_BOOTSTRAP_SERVERS`                                   | `localhost:9092`                               | Broker                                            |
| `KAFKA_CONSUMER_GROUP`                                      | `portfolio-svc`                                | Consumer group id                                 |
| `KAFKA_TOPIC_TRANSACTION_RECORDED`                          | `transaction.recorded.v1`                      | Inbound events                                    |
| `KAFKA_TOPIC_TRADE_EXECUTED`                                | `trade.executed.v1`                            | Present for ecosystem parity (not consumed here)  |
| `SPRING_CACHE_TYPE`                                         | `none`                                         | Set to `redis` to enable optional caching         |
| `REDIS_HOST` / `REDIS_PORT`                                 | `localhost` / `6379`                           | Only if caching is enabled                        |
| `ORDERS_BASE_URL`                                           | `http://orders-service:8085`                   | Reserved for cross-service lookups (not required) |

---

## 9) Operations & Runbook — Run commands, health checks, troubleshooting

**Build & run locally**

```bash
./gradlew clean build -x test
java -jar build/libs/portfolio-service-0.0.1-SNAPSHOT.jar
```

**Docker**

```bash
docker build -t tradestream-portfolio-service .
docker run -p 8087:8087 --env-file .env tradestream-portfolio-service
```

**Health checks**

```bash
curl -s http://localhost:8087/actuator/health
```

**Troubleshooting**

| Symptom                          | Likely Cause                                       | Fix                                                                               |
| -------------------------------- | -------------------------------------------------- | --------------------------------------------------------------------------------- |
| Positions don’t update           | Consumer not connected to Kafka / wrong topic      | Verify `KAFKA_BOOTSTRAP_SERVERS`, topic name; check logs for partitions assigned  |
| Repeated PnL/qty drift           | Ledger table missing or unique constraint disabled | Ensure Flyway `V1__init.sql` applied; verify `uk_processed_topic_msgid` exists    |
| Messages pile up, then DLT grows | Poison payload or unknown `side`                   | Inspect `<topic>.DLT`, correct producer, redeploy; these are marked non-retryable |
| Stuck offsets / no commits       | Exception before ack                               | Check error logs; fix root cause or allow DLT flow; verify manual ack is invoked  |
| Avg cost not resetting at zero   | App crash before commit                            | Reprocess after restart; ledger prevents double-apply; confirm successful commit  |
| 404 on `positions/{ticker}`      | Position never created                             | Expected until first trade; use list endpoint or seed test data                   |

---

## 10) Extensibility — Future improvements & scaling

* **Short selling & borrow accounting** (negative quantities, realized PnL rules).
* **Unrealized PnL & market value** by integrating a **price feed** cache (Redis) and periodic marks.
* **Corporate actions** (splits/dividends) via event-sourced adjustments.
* **Partition-aware consumers** (key by `userId` or `ticker`) for horizontal scaling.
* **Outbox/CDC** to emit `position.updated` events for downstream analytics.
* **Multi-currency** positions with FX conversion layers.
* **Backfill & replay tooling** with time-bounded reprocessing guarded by the ledger.

---

## 11) Where this fits in the bigger system — Broader role

The **Portfolio Service** is the **read-side projection** for user holdings: upstream, the **Transaction Processor** turns trade executions into normalized **transaction.recorded** events; this service applies them into **authoritative positions** and exposes read APIs used by gateway/UI, reporting, and risk components.

---

## 12) Resume/CV Content (Copy-Paste Ready)

### Impact Bullets (3–5)

* Delivered an **event-driven portfolio projector** (Java 17, Spring Boot, Kafka, Postgres) that maintains authoritative positions & realized PnL with **idempotent processing**.
* Implemented **manual-ack Kafka consumption** with **pessimistic row locks** and a `(topic, message_id)` ledger to guarantee once-only effects under at-least-once delivery.
* Engineered **dead-letter routing** with **exponential backoff** and non-retryable classification for validation errors, improving MTTR for poison messages.
* Exposed **REST portfolio APIs** and hardened ops with **Flyway migrations** and **Actuator health probes**; packaged for reproducible deployment via **Docker**.

### Scope/Scale (placeholders)

* Throughput: \~**N** tx/sec peak; sustained **M**/day
* Latency: **P99 < X ms** from Kafka receipt to DB commit
* Data: **Z** users, **T** tickers, **K** positions projected
* Reliability: **0** duplicate effects across **R** replays/restarts

### Tech Stack Summary (single line)

**Java 17, Spring Boot, Spring Kafka, PostgreSQL, Flyway, JPA/Hibernate, Docker, Actuator (Redis optional)**

### Cover-letter paragraph

> I built a **portfolio projection service** that turns transaction events into authoritative positions with **strong idempotency and concurrency guarantees**. Using **Java 17, Spring Boot, Kafka, and Postgres**, I designed deterministic processing (manual acks, pessimistic locks), a clean idempotency ledger, and operational guardrails (DLT, backoff, Flyway, Actuator). The result is a reliable **read-side** that scales and simplifies downstream reporting—rigor I’m excited to apply at \<Company/Team>.

### Interview Talking Points — Deep dives

* **Idempotency strategy**: why `(topic, message_id)` beats payload-level heuristics; synthetic IDs for missing headers.
* **Locking choice**: pessimistic vs optimistic for hot positions; avoiding retry storms.
* **Numerics**: `NUMERIC(18,8)`, rounding, overflow considerations; resetting `avgCost` when flat.
* **Error handling**: backoff, **DLT**, and non-retryable classification; avoiding consumer hot loops.
* **Partitioning & ordering**: consumer concurrency=1 vs horizontal scaling by key.
* **Replay safety**: how the ledger enables backfills and reprocessing without double-apply.
* **Extensibility**: adding shorts, corporate actions, and unrealized PnL.

---

## 13) Cheat Sheet (for yourself) — Quick reference

**Run service**

```bash
./gradlew clean build -x test
java -jar build/libs/portfolio-service-0.0.1-SNAPSHOT.jar
# or Docker
docker build -t tradestream-portfolio-service .
docker run -p 8087:8087 --env-file .env tradestream-portfolio-service
```

**Health**

```bash
curl -s http://localhost:8087/actuator/health
```

**List positions**

```bash
curl -s "http://localhost:8087/portfolio/<user-uuid>/positions" | jq
```

**Get a single position**

```bash
curl -s "http://localhost:8087/portfolio/<user-uuid>/positions/AAPL" | jq
```

**Portfolio summary**

```bash
curl -s "http://localhost:8087/portfolio/<user-uuid>/summary" | jq
```

**Produce a sample TransactionRecorded event (kcat)**

```bash
cat <<'JSON' > /tmp/tx.json
{
  "eventId": "11111111-1111-1111-1111-111111111111",
  "tradeId": "22222222-2222-2222-2222-222222222222",
  "orderId": "33333333-3333-3333-3333-333333333333",
  "userId":  "44444444-4444-4444-4444-444444444444",
  "side": "BUY",
  "ticker": "AAPL",
  "quantity":  "10.00000000",
  "price":     "150.50000000",
  "executedAt":"1755513800.000000000",
  "version": 1
}
JSON

kcat -b localhost:9092 -t transaction.recorded.v1 -P -l /tmp/tx.json
```

**Consume live (kcat)**

```bash
kcat -b localhost:9092 -t transaction.recorded.v1 -C -q
kcat -b localhost:9092 -t transaction.recorded.v1.DLT -C -q   # inspect dead letters
```

**DB sanity checks**

```sql
-- positions
SELECT user_id, ticker, quantity, avg_cost, realized_pnl, updated_at
FROM positions
WHERE user_id = '44444444-4444-4444-4444-444444444444';

-- processed_messages ledger
SELECT topic, message_id, received_at
FROM processed_messages
ORDER BY received_at DESC
LIMIT 50;
```

**Reset consumer group to replay (dev only)**

```bash
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group portfolio-svc --topic transaction.recorded.v1 --reset-offsets --to-earliest --execute
```

**Key classes (grep anchors)**

* `TransactionRecordedConsumer` — listener, manual ack, message-ID resolution
* `PortfolioProjector` — transactional projector, locking, math & ledger writes
* `PositionRepository` — `lockByUserAndTicker` (pessimistic write)
* `ProcessedMessageRepository` — idempotency existence check
* `KafkaDlqConfig` / `ListenerFactoryConfig` — DLT & error handling wiring

---

