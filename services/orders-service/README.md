# 📌 The Ultimate Source of Truth: Orders Service

---

## 1. One-liner

Event-driven microservice that ingests and validates buy/sell orders, persists lifecycle state in Postgres, and coordinates with a matching engine via Kafka for fills and cancellations.

---

## 2. Executive Summary (for recruiters)

* Designed and built a **high-throughput order management service** that validates, persists, and publishes new trading orders while consuming trade execution events to maintain a consistent state.
* Implements **idempotent fill tracking** using a database ledger to guarantee exactly-once application of trades in an at-least-once Kafka environment.
* Enforces critical **business invariants** at both API and DB layers (order type/price constraints, cancellation semantics, strict state machine transitions).
* Achieves a **clean separation of concerns**: REST controllers for ingress, service layer for logic, repositories for persistence, Kafka producers/consumers for async workflows.
* Engineered for **operational safety** with Flyway migrations, actuator health endpoints, pessimistic DB locking, and containerization with Docker.

---

## 3. What this service does

* ✅ **Exposes REST API** for order placement (`POST /orders`), retrieval (`GET /orders/{id}`), and cancellation (`POST /orders/{id}/cancel`).
* 📬 **Publishes to Kafka**:

  * `order.placed.v1` on validated order creation.
  * `order.cancelled.v1` when a NEW order is cancelled.
* 🎧 **Consumes from Kafka**:

  * `trade.executed.v1` to apply fills to open orders.
* 🔒 **Manages lifecycle state**: `NEW → PARTIALLY_FILLED → FILLED` or `CANCELED/REJECTED/EXPIRED`.
* 🛡 **Concurrency safety**: Pessimistic DB locks ensure race-free fill application.

---

## 4. Tech Stack & Key Choices

| Technology              | Purpose                        | Why                                                                           |
| ----------------------- | ------------------------------ | ----------------------------------------------------------------------------- |
| Java 17 + Spring Boot   | Microservice framework         | Modern ecosystem, validation, dependency injection, concise DTOs with Records |
| PostgreSQL + Flyway     | Persistence & schema evolution | ACID guarantees, UUID/TS support, versioned migrations                        |
| Apache Kafka (Redpanda) | Messaging backbone             | Decouples systems, partitioning by ticker preserves per-symbol order          |
| JPA/Hibernate           | ORM                            | Simplified persistence with explicit pessimistic locks                        |
| Docker                  | Containerization               | Consistent runtime across environments                                        |
| Lombok                  | Boilerplate reduction          | Clean domain models, DTOs                                                     |

**Design rationale highlights:**

* **Pessimistic locking** avoids retry loops under high contention (better than optimistic here).
* **Idempotency ledger** (`ingested_fills`) ensures correctness in at-least-once Kafka delivery.
* **Separate topics** (`placed` vs `cancelled`) allow consumer separation and scaling.

---

## 5. Architecture at a glance

### Flow 1: Placing a New Order (API → Kafka)

1. Client sends `POST /orders` with JSON payload.
2. Validation: LIMIT must include price; MARKET must not.
3. DB transaction begins → order persisted with `NEW` status.
4. On commit, `OrderPlaced` event published to `order.placed.v1` (keyed by ticker).
5. Client receives `202 ACCEPTED` with order details.

### Flow 2: Processing a Trade Execution (Kafka → DB)

1. Consumer receives `TradeExecuted` event.
2. Attempt `INSERT (orderId, tradeId)` into `ingested_fills`.

   * Fail = duplicate → stop.
   * Success = first time → proceed.
3. Lock order row (`SELECT … FOR UPDATE`).
4. Update `filledQuantity`, `lastFillPrice`, and status (FILLED or PARTIALLY\_FILLED).
5. Commit transaction (atomic update + idempotency record).

### Flow 3: Cancelling an Order (API → Kafka)

1. Client sends `POST /orders/{id}/cancel`.
2. Service enforces: only `NEW` orders may cancel.
3. Status updated to `CANCELED`.
4. Event published to `order.cancelled.v1`.

---

## 6. Rules & Invariants

* LIMIT orders must have `price`.
* MARKET orders must not have `price`.
* Only orders with status = `NEW` can be cancelled.
* Duplicate trade events are logged and ignored via `ingested_fills`.
* State machine is strict:

  * `NEW → PARTIALLY_FILLED → FILLED`
  * `NEW → CANCELED`
  * `NEW → REJECTED` (validation failure)
  * `NEW → EXPIRED` (future extension)

---

## 7. Data Model

**orders**

* PK: `id (UUID)`
* Columns: userId, ticker, side, type, tif, qty, price, filledQty, lastFillPrice, status, created\_at, updated\_at, version
* Indexes: `(user_id)`, `(ticker)`

**ingested\_fills**

* PK: `(order_id, trade_id)`
* Columns: ticker, ts
* Purpose: enforces idempotency of trade updates

---

## 8. Configuration (env)

| Key                           | Default              | Notes                         |
| ----------------------------- | -------------------- | ----------------------------- |
| `SERVER_PORT`                 | 8085                 | REST API port                 |
| `SPRING_DATASOURCE_URL`       | –                    | JDBC URL                      |
| `SPRING_DATASOURCE_USERNAME`  | –                    | DB user                       |
| `SPRING_DATASOURCE_PASSWORD`  | –                    | DB pass                       |
| `KAFKA_BOOTSTRAP_SERVERS`     | redpanda:9092        | Broker                        |
| `KAFKA_CONSUMER_GROUP`        | orders-exec-consumer | Consumer group for executions |
| `KAFKA_TOPIC_ORDER_PLACED`    | order.placed.v1      | Outbound                      |
| `KAFKA_TOPIC_ORDER_CANCELLED` | order.cancelled.v1   | Outbound                      |
| `KAFKA_TOPIC_TRADE_EXECUTED`  | trade.executed.v1    | Inbound                       |

---

## 9. Operations & Runbook

**Build & run:**

```bash
./gradlew clean build -x test
docker build -t orders-service .
docker run -p 8085:8085 --env-file .env orders-service
```

**Health check:**

```bash
curl http://localhost:8085/actuator/health
```

**Troubleshooting**

| Symptom                        | Likely Cause                            | Fix                                |
| ------------------------------ | --------------------------------------- | ---------------------------------- |
| 400 BAD\_REQUEST               | Invalid payload (e.g., price on MARKET) | Fix client input                   |
| Orders not executing           | Kafka broker/consumer down              | Check logs, verify Kafka health    |
| Duplicate fills applied        | Migration missing `ingested_fills`      | Ensure Flyway migration V4 applied |
| Cancel fails (409)             | Order status not `NEW`                  | Expected behavior                  |
| Unknown order in TradeExecuted | Late/out-of-order event                 | Logged + ignored safely            |

---

## 10. Extensibility

* Add **new order types** (stop-loss, iceberg) → extend domain enums, validation, events.
* Add **expiry policies** (auto-expire GTC orders).
* Integrate **gRPC/FIX gateway** for broker connectivity.
* Scale by **sharding Kafka partitions** across ticker ranges.

---

## 11. Where this fits in the bigger system

* **Orders Service**: authoritative order state.
* **Matching Engine**: consumes `order.placed.v1`, publishes `trade.executed.v1`.
* **Orders Service**: applies fills, updates state.
* **Market Data Consumer**: aggregates executions into OHLCV candles.

The Orders Service is the **single source of truth** for order lifecycle.

---

## 12. Resume/CV Content (Copy-Paste Ready)

**Impact Bullets**

* Built a **fault-tolerant order management microservice** (Java 17, Spring Boot, Kafka, Postgres) serving as the transactional core of a trading platform.
* Engineered an **idempotent Kafka consumer** with pessimistic locking and `ingested_fills` ledger to guarantee exactly-once trade application.
* Designed and enforced a **strict state machine** (NEW, PARTIALLY\_FILLED, FILLED, CANCELED, REJECTED) with validation at API and DB layers.
* Enabled resilient **event-driven integration** by producing lifecycle events and consuming trade executions via Kafka.
* Operationalized with **Flyway, Actuator, Docker**, and cached Gradle builds for predictable CI/CD.

**Scope/Scale (fill in with real numbers)**

* Throughput: \~N orders/sec peak
* API latency: P99 < X ms for `POST /orders`
* Fill processing latency: < Y ms from Kafka to DB commit
* Database: Z million orders managed

**Tech Stack Summary**
Java 17, Spring Boot, Spring Kafka, Spring Data JPA, PostgreSQL, Flyway, Docker, Lombok, Actuator

---

## 13. Cover-letter paragraph

> I recently engineered a **fault-tolerant orders microservice** that manages the complete lifecycle of trading orders. It validates and persists orders, publishes them to Kafka for matching, and consumes executions to apply fills with **idempotent guarantees**. Using **Java 17, Spring Boot, Kafka, and Postgres**, I emphasized concurrency control, schema evolution, and operational reliability. This project reflects the same rigor I’d bring to \<Company/Team>.

---

## 14. Interview Talking Points

* **Concurrency control:** Why pessimistic locking over optimistic.
* **Idempotency:** How `(order_id, trade_id)` ledger ensures exactly-once processing.
* **Kafka design:** Separate topics for placed vs cancelled orders.
* **System fit:** How Orders, Matching Engine, and Market Data Consumer interact.
* **Schema management:** Flyway migrations across environments.
* **Error handling:** REST error strategy and Kafka poison pill handling.
* **Extensibility:** How to add STOP\_LOSS or expiry logic.

---

## 15. Cheat Sheet (for yourself)

**Place LIMIT order**

```bash
curl -X POST http://localhost:8085/orders -H "Content-Type: application/json" -d '{
  "userId":"<uuid>",
  "ticker":"AAPL",
  "side":"BUY",
  "type":"LIMIT",
  "timeInForce":"GTC",
  "quantity":100,
  "price":150.5
}'
```

**Cancel order**

```bash
curl -X POST http://localhost:8085/orders/{id}/cancel
```

**Kafka listen (kcat)**

```bash
kcat -b localhost:9092 -t order.placed.v1 -C -q
```

**DB check**

```sql
SELECT id, status, quantity, filled_quantity FROM orders WHERE id='...';
SELECT * FROM ingested_fills WHERE order_id='...';
```

---
