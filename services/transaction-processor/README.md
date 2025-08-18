# 📌 The Ultimate Source of Truth: Transaction Processor Service

---

## One-liner

Event-driven microservice that journals executed trades into an immutable, per-user transaction ledger with strict idempotency, then publishes normalized transaction events for downstream consumers.

---

## Executive Summary (for recruiters)

* Built a **transaction journaling service** that records each trade **exactly once** for both buyer and seller, forming the transactional core for portfolios and reporting.
* Guarantees correctness via a **persisted idempotency ledger** (`processed_messages`) in an at-least-once Kafka environment, plus DLQ with exponential backoff.
* Emits **clean, versioned events** (`transaction.recorded.v1`) enabling real-time portfolio updates and analytics.
* Enforces **data integrity** with DB constraints, DECIMAL precision for quantity/price, and unique `(trade_id, user_id, side)`.
* Operates reliably with **Flyway migrations**, **health probes**, and **containerized** deployment alongside Redpanda and Postgres.

---

## What this service does

* 🎧 **Consumes** `trade.executed.v1` from Kafka (Redpanda).
* 🔗 **Resolves user identities** by calling Orders Service for each `orderId`.
* 🧾 **Persists two transactions per trade** (BUY & SELL) in an append-only ledger.
* 🛡 **Prevents duplicates** via `processed_messages (topic, message_id)` idempotency check.
* 📬 **Publishes** `transaction.recorded.v1` after each journaled transaction.
* 🔍 **Exposes REST** read-model queries by user, ticker, and time (paginated & sortable).

---

## Tech Stack & Key Choices

| Technology                      | Purpose                        | Rationale                                               |
| ------------------------------- | ------------------------------ | ------------------------------------------------------- |
| **Java 17 + Spring Boot**       | Service framework              | Modern records/DTOs, validation, DI, actuator endpoints |
| **Spring Kafka**                | Kafka consumer/producer        | DLQ + backoff, JSON serde, listener lifecycle           |
| **PostgreSQL 15 + Flyway**      | Persistence & schema evolution | ACID, strong constraints, versioned migrations          |
| **Spring Data JPA (Hibernate)** | ORM + repositories             | Concise CRUD, pagination, sorting                       |
| **Redpanda (Kafka-compatible)** | Messaging backbone             | Simple local ops, at-least-once delivery                |
| **Docker / Compose**            | Packaging & orchestration      | Reproducible environments, isolated networks            |
| **Lombok**                      | Boilerplate reduction          | Clean domain and DTOs                                   |
| **Actuator**                    | Ops visibility                 | Health/readiness probes                                 |

### Design rationale highlights

* **Idempotent journal**: `(topic, message_id)` in `processed_messages` ensures each trade is applied once (even on replays).
* **Append-only ledger**: Immutable per-user records provide traceability and auditability; adjustments use future reversal entries (extensible).
* **Decimal precision**: `quantity` & `price` are `DECIMAL(18,6)` to avoid rounding errors (migrated in V2).
* **DLQ + backoff**: Non-transient errors go to `<topic>.DLT`; transient errors retry with exponential backoff.
* **Separation of concerns**: Kafka listener → service layer → repo; outbound publisher isolated and keyed for ordered delivery.

---

## Architecture at a glance

### Primary flow: Trade execution → Journal → Outbound event

1. **Consume** `trade.executed.v1` (JSON with `tradeId`, `buyOrderId`, `sellOrderId`, `ticker`, `price`, `quantity`, `timestamp`).
2. **Idempotency check**: If `(topic, tradeId)` exists in `processed_messages`, **skip**.
3. **Resolve users**: Call Orders Service for `buyOrderId` and `sellOrderId` → obtain `buyerUserId`, `sellerUserId`.
4. **Persist** two rows in `transactions` (BUY for buyer, SELL for seller) with `DECIMAL` price/quantity and `executed_at`.
5. **Record processed** `(topic, tradeId)` with `processed_at` timestamp.
6. **Publish** two `transaction.recorded.v1` events (buyer & seller views), keyed `"tradeId:userId:side"`.

### Read-model flow: REST queries

1. Client calls:

   * `GET /api/transactions/{userId}` (page/sort)
   * `GET /api/transactions/{userId}/ticker/{ticker}`
   * `GET /api/transactions/{userId}/since?iso=<Instant>`
2. Repositories use indexes to return paginated, sorted `TransactionDto` pages.

---

## Rules & Invariants

* **Two rows per trade**: one BUY for buyer, one SELL for seller.
* **Uniqueness**: `(trade_id, user_id, side)` is unique (enforced in DB).
* **Positive amounts**: `quantity > 0`, `price > 0`.
* **Immutability**: Transactions are append-only; no updates after commit.
* **Idempotency**: Events are applied at most once via `processed_messages` guard.
* **Strict typing**: `quantity`, `price` are `DECIMAL(18,6)`; `executed_at` is UTC.
* **User resolution required**: If Orders lookup fails, processing is aborted and message is retried/parked in DLQ.

---

## Data Model

**transactions**

* **PK**: `id (UUID)`
* **Columns**: `trade_id (UUID)`, `order_id (UUID)`, `user_id (UUID)`, `side (BUY|SELL)`, `ticker (VARCHAR32)`, `quantity DECIMAL(18,6)`, `price DECIMAL(18,6)`, `executed_at TIMESTAMPTZ`
* **Unique**: `(trade_id, user_id, side)` (**uq\_trade\_participant**)
* **Indexes**:

  * `(user_id, executed_at DESC)` for time-sorted history
  * `(user_id, ticker, executed_at DESC)` for symbol-scoped history
  * `(trade_id)` for traceability

**processed\_messages**

* **PK**: `(topic TEXT, message_id TEXT)`
* **Columns**: `processed_at TIMESTAMPTZ DEFAULT now()`
* **Purpose**: persistent idempotency ledger for Kafka consumer

---

## Configuration (env)

| Key                                | Default                      | Notes                                                             |
| ---------------------------------- | ---------------------------- | ----------------------------------------------------------------- |
| `SERVER_PORT`                      | `8084`                       | REST port                                                         |
| `SPRING_DATASOURCE_URL`            | –                            | e.g., `jdbc:postgresql://transaction_postgres:5432/transactiondb` |
| `SPRING_DATASOURCE_USERNAME`       | –                            | `transactionuser`                                                 |
| `SPRING_DATASOURCE_PASSWORD`       | –                            | `transactionpass`                                                 |
| `SPRING_JPA_HIBERNATE_DDL_AUTO`    | `validate`                   | Migrations driven by Flyway                                       |
| `SPRING_FLYWAY_ENABLED`            | `true`                       | Enable/disable Flyway                                             |
| `SPRING_FLYWAY_LOCATIONS`          | `classpath:db/migration`     | Migration path                                                    |
| `KAFKA_BOOTSTRAP_SERVERS`          | `redpanda:9092`              | Broker endpoint (inside Compose net)                              |
| `KAFKA_CONSUMER_GROUP`             | `txproc-journal`             | Consumer group                                                    |
| `KAFKA_TOPIC_TRADE_EXECUTED`       | `trade.executed.v1`          | Inbound                                                           |
| `KAFKA_TOPIC_TRANSACTION_RECORDED` | `transaction.recorded.v1`    | Outbound                                                          |
| `ORDERS_BASE_URL`                  | `http://orders-service:8085` | Service discovery via container name                              |

**Service ↔ Container ↔ Port (for quick reference)**

| Service                 | Container name                      | Port |
| ----------------------- | ----------------------------------- | ---- |
| Transaction Processor   | `tradestream-transaction-processor` | 8084 |
| Orders Service          | `orders-service`                    | 8085 |
| Matching Engine         | `matching-engine`                   | 8086 |
| Portfolio Service       | `portfolio-service`                 | 8087 |
| Market Data Consumer    | `market-data-consumer`              | 8083 |
| Redpanda (Kafka broker) | `redpanda`                          | 9092 |
| Transaction Postgres    | `transaction_postgres`              | 5432 |

---

## Operations & Runbook

### Build & run locally (outside Compose)

```bash
./gradlew clean build -x test
docker build -t transaction-processor .
docker run -p 8084:8084 --env-file .env transaction-processor
```

### Health checks (from host via Compose network)

> Use `docker exec` so requests originate inside `private_net`.

```bash
docker exec -it tradestream-transaction-processor curl -s \
  http://transaction-processor:8084/actuator/health
```

### Logs

```bash
docker logs -f tradestream-transaction-processor
```

### Kafka (listen/send) from host via container

```bash
docker exec -it tradestream-transaction-processor kcat -b redpanda:9092 \
  -t trade.executed.v1 -C -q
docker exec -it tradestream-transaction-processor kcat -b redpanda:9092 \
  -t transaction.recorded.v1 -C -q
```

### Troubleshooting

| Symptom                             | Likely Cause                                    | Fix                                                                |
| ----------------------------------- | ----------------------------------------------- | ------------------------------------------------------------------ |
| Duplicate transactions observed     | Missing/failed insert into `processed_messages` | Verify Flyway V1 applied; check consumer logs & DB                 |
| No transactions after trades        | Orders Service lookup failing                   | Check `orders-service` health/logs; network; RestTemplate timeouts |
| Poison messages / consumer stuck    | Invalid payload shape/version                   | Inspect DLQ: `trade.executed.v1.DLT`; upgrade deserializer or map  |
| API returns empty pages             | Wrong `userId` or filters                       | Re-check query params; validate UUID casing                        |
| Quantity precision seems wrong      | V2 migration not applied                        | Confirm `V2__quantity_decimal.sql` executed                        |
| Intermittent 5xx from Orders client | Upstream latency/timeouts                       | Review RestTemplate timeouts (2s/3s), consider retries/backoff     |

---

## Extensibility

* **Reversals/adjustments**: Add compensating entries for corrections (keep append-only).
* **Fees & settlement**: Extend event and schema with commission, venue, settlement date.
* **Sharding/partitioning**: Partition by `user_id` for DB scaling; partition Kafka by user for ordered per-user processing.
* **GraphQL/aggregation**: Add GraphQL read-model for richer client queries.
* **Caching**: Add Redis for hot path queries; optional L1/L2 caches in portfolio service already scaffolded.
* **Observability**: Add metrics for consumer lag, processing latency, and event publish rates.

---

## Where this fits in the bigger system

Within the **Tradestream** microservices, the Transaction Processor is the **authoritative journal** downstream of the Matching Engine. It listens to `trade.executed.v1`, resolves `userId` via Orders Service, writes immutable per-user records, and publishes `transaction.recorded.v1` consumed by **Portfolio Service** and analytics. It is the **bridge** from raw trade executions to user-centric financial records, ensuring consistency, auditability, and downstream readiness.

---

## Resume/CV Content (Copy-Paste Ready)

### Impact Bullets

* Delivered a **fault-tolerant transaction journal** (Java 17, Spring Boot, Kafka, Postgres) recording each trade exactly once for buyer and seller.
* Implemented **idempotent consumption** using a persisted `(topic, message_id)` ledger with DLQ and exponential backoff to handle poison messages.
* Published **normalized transaction events** (`transaction.recorded.v1`) enabling real-time portfolio updates and analytics.
* Enforced **strong invariants** (unique `(trade_id, user_id, side)`, DECIMAL precision, append-only model) with Flyway migrations and DB constraints.
* Operationalized with **Actuator health checks**, containerized builds, and indexed read APIs with pagination/sorting.

### Scope/Scale (placeholders)

* Throughput: \~**N** trades/sec journaled
* End-to-end latency (consume → DB → publish): **X** ms P95
* Data volume: **Z** million transactions stored
* API latency (P99): **Y** ms for paginated queries

### Tech Stack Summary

Java 17, Spring Boot, Spring Kafka, Spring Data JPA (Hibernate), PostgreSQL 15, Flyway, Redpanda (Kafka), Docker/Compose, Lombok, Actuator

### Cover-letter paragraph

> I engineered a **transaction processing microservice** that guarantees immutability and exactly-once recording of trade executions in a high-integrity ledger. The service consumes Kafka events, resolves users via the Orders Service, persists BUY/SELL entries with DECIMAL precision, and publishes normalized events consumed by portfolios and analytics. Built with **Java 17, Spring Boot, Kafka, and Postgres**, it emphasizes idempotency, schema evolution, and operational resilience—the same rigor I’d bring to \<Company/Team>.

### Interview Talking Points

* **Idempotency strategy**: Why a persisted `(topic, tradeId)` table beats in-memory dedupe.
* **DLQ & retries**: Exponential backoff, non-retryable exceptions, and reprocessing from DLT.
* **Ledger design**: Append-only vs. mutable balances; traceability and reversals.
* **Decimal precision**: Migration from `INT` to `DECIMAL(18,6)` for quantity; price precision rationale.
* **Event versioning**: `transaction.recorded.v1` evolution strategy.
* **Failure domains**: Orders Service dependency, timeouts, and fallback considerations.
* **Indexes & pagination**: Read-model performance characteristics.

---

## Cheat Sheet (for yourself)

### Container/service quick map (copy-paste helpers)

```bash
# shells
docker exec -it tradestream-transaction-processor sh
docker exec -it transaction_postgres psql -U transactionuser -d transactiondb
```

### Health & metrics

```bash
docker exec -it tradestream-transaction-processor curl -s \
  http://transaction-processor:8084/actuator/health
```

### Kafka (listen from host via container)

```bash
docker exec -it tradestream-transaction-processor kcat -b redpanda:9092 \
  -t trade.executed.v1 -C -q
docker exec -it tradestream-transaction-processor kcat -b redpanda:9092 \
  -t transaction.recorded.v1 -C -q
```

### REST queries (from host via container)

```bash
# All transactions for a user (sorted desc by executedAt)
docker exec -it tradestream-transaction-processor curl -s \
  "http://transaction-processor:8084/api/transactions/{userId}?page=0&size=20&sort=executedAt,desc"

# By ticker
docker exec -it tradestream-transaction-processor curl -s \
  "http://transaction-processor:8084/api/transactions/{userId}/ticker/AAPL"

# Since timestamp
docker exec -it tradestream-transaction-processor curl -s \
  "http://transaction-processor:8084/api/transactions/{userId}/since?iso=2025-01-01T00:00:00Z"
```

### SQL snippets

```sql
-- Top recent transactions
SELECT user_id, side, ticker, quantity, price, executed_at
FROM transactions
WHERE user_id = '<uuid>'
ORDER BY executed_at DESC
LIMIT 50;

-- Verify idempotency record exists for a trade
SELECT *
FROM processed_messages
WHERE topic='trade.executed.v1' AND message_id='<tradeId>';

-- Check uniqueness invariant
SELECT trade_id, user_id, side, COUNT(*)
FROM transactions
GROUP BY 1,2,3
HAVING COUNT(*) > 1;
```

### Sample payloads

**Inbound event: `trade.executed.v1`**

```json
{
  "tradeId": "11111111-2222-3333-4444-555555555555",
  "buyOrderId": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
  "sellOrderId": "ffffffff-1111-2222-3333-444444444444",
  "ticker": "AAPL",
  "price": 150.420000,
  "quantity": 10.500000,
  "timestamp": "2025-01-02T15:04:05Z"
}
```

**Outbound event: `transaction.recorded.v1`** (one per participant)

```json
{
  "eventId": "99999999-8888-7777-6666-555555555555",
  "tradeId": "11111111-2222-3333-4444-555555555555",
  "orderId": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
  "userId": "deadbeef-dead-beef-dead-beefdeadbeef",
  "side": "BUY",
  "ticker": "AAPL",
  "quantity": 10.500000,
  "price": 150.420000,
  "executedAt": "2025-01-02T15:04:05Z",
  "version": 1
}
```

### DLQ inspection & replay (manual)

```bash
# Inspect dead-lettered records
docker exec -it tradestream-transaction-processor kcat -b redpanda:9092 \
  -t trade.executed.v1.DLT -C -q

# (Optional) Pipe DLT records back to original topic after fix (use with care)
docker exec -it tradestream-transaction-processor sh -lc '
  kcat -b redpanda:9092 -t trade.executed.v1.DLT -C -q -o beginning | \
  kcat -b redpanda:9092 -t trade.executed.v1 -P
'
```

### Orders Service dependency probe

```bash
docker exec -it tradestream-transaction-processor curl -s \
  http://orders-service:8085/actuator/health
```

---

### Appendix: Notable code elements (for interviews)

* **`ProcessedMessage` entity**: `(topic, message_id)` PK; guarantees idempotency.
* **`TradeExecutedConsumer`**: `@KafkaListener` with per-record ack; delegates to service.
* **`TransactionService#processTrade`**: full execution path (idempotency check → user resolution → two inserts → processed marker → two outbound events).
* **DLQ config**: `DeadLetterPublishingRecoverer` sends to `<topic>.DLT`; `DefaultErrorHandler` with exponential backoff; `IllegalArgumentException` marked non-retryable.
* **Migrations**: `V1__init_schema.sql` creates baseline; `V2__quantity_decimal.sql` migrates `quantity` `INT → DECIMAL(18,6)`.

---

