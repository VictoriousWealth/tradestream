# Matching Engine Microservice — Developer/Operator Guide

*Last updated: 2025-08-11 (Europe/London)*

This document explains **exactly** what the Matching Engine does and how to run, operate, and evolve it—without reading any code. It covers behavior, event contracts, storage, recovery, ops, and testing.

---

## What this service does (in one breath)

The **Matching Engine** consumes **orders** from Kafka, maintains **in-memory order books** per ticker with **price-time priority**, applies **time-in-force** (GTC/IOC/FOK), generates **trades** when prices cross, persists **resting orders** for recovery, ensures **idempotent** processing of incoming messages, and publishes **trade events** to Kafka for downstream services (market data aggregation and portfolio updates). It also ships with **retry + dead-letter topics (DLT)** for bad events.

---

# 1) Responsibilities & Scope

### Responsibilities

* Consume:

  * `order.placed.v1` — new orders
  * `order.cancelled.v1` — cancellations
* Keep an **in-memory order book** per ticker (bids/asks) using **price-time priority**.
* Execute trades when an incoming order **crosses** the best opposite price.
* Apply **TIF rules**:

  * **GTC**: rest unfilled quantity in the book.
  * **IOC**: match immediately; cancel remainder.
  * **FOK**: only execute if **fully** fillable immediately; otherwise cancel (no partials).
* Publish executed trades to `trade.executed.v1`.
* Persist resting orders in Postgres; warm-load on startup for continuity.
* Use an **idempotency table** to avoid reprocessing the same Kafka message.
* **Retry** transient consumer failures; send irrecoverable events to **DLT**.

### Out of scope

* REST API for orders (that’s the Orders Service).
* User auth, balances, holdings (Auth & Transaction Processor).
* External market connectivity (this is a pure internal engine).

---

# 2) Event Contracts

All events are JSON. The engine does **not** require Spring type headers; it expects the body to match the DTO shapes below.

## 2.1 Inputs

### A) `order.placed.v1`

```json
{
  "orderId": "uuid",
  "userId": "uuid",
  "ticker": "AAPL",
  "side": "BUY",              // BUY | SELL
  "orderType": "LIMIT",       // LIMIT | MARKET
  "timeInForce": "GTC",       // GTC | IOC | FOK
  "price": 150.25,            // required for LIMIT; null for MARKET
  "quantity": 100.0           // > 0
}
```

### B) `order.cancelled.v1`

```json
{ "orderId": "uuid" }
```

## 2.2 Outputs

### A) `trade.executed.v1`

```json
{
  "tradeId": "uuid",
  "buyOrderId": "uuid",
  "sellOrderId": "uuid",
  "ticker": "AAPL",
  "price": 150.25,
  "quantity": 50.0,
  "timestamp": "2025-08-11T12:34:56Z"
}
```

**Partitioning/keying:** All published trades are keyed by **ticker**. This keeps same-ticker events on the same partition for downstream consumers.

---

# 3) Matching Rules (the heart)

## 3.1 Price-time priority

* **Price**: Best price first

  * **Bids** (BUY): higher price is better.
  * **Asks** (SELL): lower price is better.
* **Time**: FIFO within the same price level, using submission time.

## 3.2 Market vs Limit

* **MARKET**: matches at the **resting** order’s price. Any **remainder never rests** and is canceled.
* **LIMIT**: matches if the limit is marketable; remainder behavior depends on TIF.

## 3.3 Time-in-Force

* **GTC (Good-Till-Cancel)**

  * Remainder **rests** in the book (LIMIT only).
* **IOC (Immediate-Or-Cancel)**

  * Match whatever is immediately available; **cancel** remainder (never rests).
* **FOK (Fill-Or-Kill)**

  * **Pre-check** book depth: if the full quantity **cannot** be filled **at acceptable prices now**, **reject** (no partial, no rest).

## 3.4 Cancel semantics

* On `order.cancelled.v1`, if the order is present in the book/DB and still active/partial, it’s marked `CANCELED` and **removed from the in-memory book**.

---

# 4) State Model & Persistence

## 4.1 Order statuses

* `ACTIVE` — resting with full original quantity available.
* `PARTIALLY_FILLED` — resting with some remainder.
* `FILLED` — fully executed; no longer in the book.
* `CANCELED` — canceled; no longer in the book.

## 4.2 Database schema (PostgreSQL)

### Table: `resting_orders`

* `id UUID PK`
* `user_id UUID NOT NULL`
* `ticker VARCHAR(16) NOT NULL`
* `side VARCHAR(4) NOT NULL` — `BUY` | `SELL`
* `order_type VARCHAR(10) NOT NULL` — `LIMIT` | `MARKET` (market never rests, but historical correctness)
* `time_in_force VARCHAR(10) NOT NULL` — `GTC` | `IOC` | `FOK`
* `price NUMERIC(18,8)` — **null only for MARKET** (never in-book)
* `original_quantity NUMERIC(18,8) NOT NULL`
* `remaining_quantity NUMERIC(18,8) NOT NULL`
* `status VARCHAR(16) NOT NULL`
* `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
* `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`

**Index:** `(ticker, side, status)` to load active/partial orders quickly at startup.

### Table: `processed_messages`

* `message_id UUID PK` — used to ensure idempotency of consumed Kafka events.
* `received_at TIMESTAMPTZ NOT NULL DEFAULT now()`

## 4.3 Warm-start (recovery)

On startup, the engine queries `resting_orders` for `ACTIVE`/`PARTIALLY_FILLED` and reconstructs in-memory order books per ticker.

---

# 5) Idempotency & Exactly-Once Considerations

## 5.1 Incoming events

* The engine treats a Kafka header `eventId` as the **idempotency key**.
  If absent, it falls back to `orderId`.
* If the `message_id` exists in `processed_messages`, the event is **skipped safely**.

## 5.2 Trade publication

* Trades are published after matches are computed.
* This MVP uses **idempotent consumption**; it does **not** wrap DB + Kafka in a single transaction. In the tiny crash window “publish succeeded, DB write fails”, a replay could re-publish a trade. Downstream services (like Orders Service fill applier) should also be idempotent on `tradeId` or via their own dedup tables.

---

# 6) Error Handling, Retries & DLT

## 6.1 Deserialization & listener errors

* Consumers use `ErrorHandlingDeserializer` to catch JSON/type errors without crashing the container.
* A **DefaultErrorHandler** retries with exponential backoff (configurable).
  After retries, the record is sent to the **dead-letter topic**.

## 6.2 DLT topics

* For each source topic `X`, the DLT is `X.DLT`.
  (e.g., `order.placed.v1.DLT`, `order.cancelled.v1.DLT`)
* The engine also includes a **bytes-based DLT logger** that consumes DLT payloads as raw bytes to avoid nested `.DLT.DLT` loops and prints readable diagnostics (exception class/message, original topic/offset, key, value).

## 6.3 Operations on DLT

* You can `rpk topic consume …DLT` to audit failures.
* To **replay**: decode the value (base64), fix JSON, re-publish to the original topic with the same key.
* Recommended to set a retention on DLT topics (e.g., 7 days).

---

# 7) Interactions with Other Services

| Service                   | Direction | Contract/Notes                                                                 |
| ------------------------- | --------- | ------------------------------------------------------------------------------ |
| **Orders Service**        | In        | Produces `order.placed.v1` and `order.cancelled.v1`.                           |
| **Market Data Consumer**  | Out       | Consumes `trade.executed.v1` to build OHLCV candles and cache latest in Redis. |
| **Transaction Processor** | Out       | Consumes `trade.executed.v1` to update user balances/positions.                |
| **API Gateway**           | —         | Not connected to the engine directly (gateway fronts Orders Service).          |
| **Postgres (matchingdb)** | Both      | Persists resting orders and processed message IDs.                             |
| **Kafka (Redpanda)**      | Both      | Event transport; topics described above.                                       |

---

# 8) Configuration Reference

All values are provided via environment variables (Docker Compose) and defaulted in `application.yml`.

**Kafka**

* `KAFKA_BOOTSTRAP_SERVERS` (default `redpanda:9092`)
* `KAFKA_CONSUMER_GROUP` (default `matching-engine`)
* Topics:

  * `KAFKA_TOPIC_ORDER_PLACED` (default `order.placed.v1`)
  * `KAFKA_TOPIC_ORDER_CANCELLED` (default `order.cancelled.v1`)
  * `KAFKA_TOPIC_TRADE_EXECUTED` (default `trade.executed.v1`)

**Database**

* `SPRING_DATASOURCE_URL` (e.g., `jdbc:postgresql://matching_postgres:5432/matchingdb`)
* `SPRING_DATASOURCE_USERNAME`
* `SPRING_DATASOURCE_PASSWORD`
* Flyway is enabled, migrations at `classpath:db/migration`.

**Server**

* `SERVER_PORT` (default `8086`)

**Actuator**

* `/actuator/health`, `/actuator/info`, `/actuator/metrics` are exposed.

---

# 9) Deployment & Runtime

## 9.1 Containers & dependencies

* Requires:

  * `matching_postgres` (Postgres 15)
  * `redpanda` (Kafka-compatible broker)
* Start:

  ```bash
  docker compose up -d matching_postgres redpanda matching-engine
  ```

## 9.2 Health

* Liveness/health: `GET http://matching-engine:8086/actuator/health`
  (In Compose, there’s a healthcheck using curl.)

---

# 10) Operational Runbook

## 10.1 Common issues

**A) Flyway checksum mismatch at startup**

* Happens if you edited a migration already applied.
* Dev fixes:

  * Reset the DB volume for `matching_postgres`, or
  * `UPDATE flyway_schema_history SET checksum = <new> WHERE version='1';`, or
  * Revert V1 and add changes in a new `V2__...sql`.

**B) Kafka deserialization “No type information in headers”**

* Ensure listeners set a **default type** and `spring.json.use.type.headers=false` (already configured).
* For old/poison records, use DLT (already configured).

**C) Poison messages loop**

* The DefaultErrorHandler publishes to `X.DLT` after retries.
* Ensure the DLT logger consumes **bytes**, not JSON (already configured), to avoid `.DLT.DLT`.

**D) Skip past old bad records**

* Change consumer group (dev-friendly), or

  ```bash
  rpk group seek matching-engine --to end --topics order.placed.v1,order.cancelled.v1
  ```

## 10.2 DLT operations

**Create DLT topics** (once):

```bash
rpk topic create order.placed.v1.DLT order.cancelled.v1.DLT
```

**Decode a DLT payload**:

```bash
rpk topic consume order.placed.v1.DLT -n 1 -f '%v' | sed 's/^"//; s/"$//' | base64 -d
```

**Replay a fixed event**:

```bash
rpk topic produce order.placed.v1 -k AAPL <<'JSON'
{ ... corrected JSON ... }
JSON
```

---

# 11) Smoke Tests (rpk)

**1) Place SELL (rests)**

```bash
rpk topic produce order.placed.v1 -k AAPL <<'JSON'
{"orderId":"11111111-1111-1111-1111-111111111111","userId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","ticker":"AAPL","side":"SELL","orderType":"LIMIT","timeInForce":"GTC","price":150.25,"quantity":50}
JSON
```

**2) Place BUY (crosses)**

```bash
rpk topic produce order.placed.v1 -k AAPL <<'JSON'
{"orderId":"22222222-2222-2222-2222-222222222222","userId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","ticker":"AAPL","side":"BUY","orderType":"LIMIT","timeInForce":"GTC","price":150.25,"quantity":50}
JSON
```

**3) Observe trade**

```bash
rpk topic consume trade.executed.v1 -n 1
```

**4) Produce a poison message (goes to DLT)**

```bash
rpk topic produce order.placed.v1 -k AAPL <<'JSON'
{"orderId":"aaaaaaaa-...","userId":"bbbbbbbb-...","ticker":"AAPL","side":"BYYY","orderType":"LIMIT","timeInForce":"GTC","price":150.25,"quantity":10}
JSON
rpk topic consume order.placed.v1.DLT -n 1
```

---

# 12) Observability

* **Actuator health/metrics** are available; system metrics (JVM, Kafka client) are exposed via Micrometer defaults.
* DLT consumer prints failures with:

  * exception FQCN & message,
  * original topic/partition/offset,
  * key,
  * raw payload (UTF-8).

*Optional next steps*: add structured logging (JSON), custom metrics (matches/sec, book depth, lag), and alerts on DLT volume.

---

# 13) Performance & Scalability

* **In-memory** order books per ticker: O(log N) insert/remove (priority queues).
* **Partitioning:** Trades are keyed by **ticker**; you can scale consumers horizontally if input topics are partitioned by ticker as well (ensure all orders for a ticker land on the same instance).
* **State recovery:** Warm-start from DB; consider periodic snapshots or changelog replay if you later shard across many instances.

---

# 14) Security

* No external HTTP interfaces besides Actuator; run on a private network only.
* Kafka & Postgres creds are injected via environment variables.
* If exposing Actuator beyond the cluster, secure it (basic auth, network policy).

---

# 15) Limitations & Future Enhancements

* **Exactly-once** across DB + Kafka is **not** enabled. For hard guarantees, add:

  * transactional producer + DB transaction choreography, or
  * an **outbox** table + Debezium.
* **Schema governance** (Avro/JSON-Schema + registry) recommended for evolvability.
* **Advanced order types** (iceberg, stop, hidden) are not implemented.
* **Validation** (tick size, lot size) assumed upstream in Orders Service.

---

## Appendix A — Topic Naming (defaults)

* Orders in: `order.placed.v1`, `order.cancelled.v1`
* Trades out: `trade.executed.v1`
* Dead-letters: `order.placed.v1.DLT`, `order.cancelled.v1.DLT`

## Appendix B — Data types & precision

* Prices/quantities use `NUMERIC(18,8)` in DB; timestamps are UTC (`TIMESTAMPTZ`).

---
