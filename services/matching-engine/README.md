# 📌 TradeStream Matching Engine — Source of Truth (Hybrid Master Doc)

---

## 1. One-liner

High-performance, fault-tolerant matching engine that ingests real-time buy/sell orders from Kafka, enforces exchange invariants with a price–time priority algorithm, and publishes trade executions to downstream services.

---

## 2. Executive Summary (for recruiters)

* Built the **core execution engine** of a distributed trading platform, designed for **low latency, high throughput, and fault tolerance**.
* **Consumes** order placement and cancellation events from Kafka, applies **price–time priority** matching in-memory, and **publishes trade executions** to downstream services.
* Ensures **correctness and idempotency** with a PostgreSQL-backed deduplication ledger (`processed_messages`).
* Implements **resilient error handling**: retries with exponential backoff, Dead Letter Queue isolation, and operational metrics.
* Delivered a **production-grade microservice** with warm-start recovery, containerization, and schema migrations for zero-downtime deploys.

---

## 3. What this service does

* ✅ **Consumes Kafka events**: `order.placed.v1`, `order.cancelled.v1`.
* 📚 **Maintains in-memory order books** (per ticker) using **priority queues** for low-latency matching.
* 🤝 **Executes trades** via price–time priority (best price, then FIFO).
* 📬 **Publishes `trade.executed.v1` events** for clearing, portfolio, and settlement systems.
* 💾 **Persists state** of active orders to PostgreSQL for recovery.
* 🛡 **Guarantees idempotency** using a deduplication ledger to prevent duplicate processing.

---

## 4. Tech Stack & Key Choices

| Technology              | Purpose                  | Rationale                                                         |
| ----------------------- | ------------------------ | ----------------------------------------------------------------- |
| Java 17 + Spring Boot   | Service framework        | Modern ecosystem, excellent Kafka + JPA support                   |
| Apache Kafka (Redpanda) | Event backbone           | Partitioned, durable, scalable message bus                        |
| PostgreSQL + Flyway     | Persistence + migrations | ACID compliance, version-controlled schema evolution              |
| In-Memory PriorityQueue | Order book structure     | O(log n) insert/remove, O(1) peek — ideal for price–time priority |
| Spring Kafka            | Kafka integration        | Simplifies consumer/producer wiring + DLQ handling                |
| Docker                  | Containerization         | Consistent runtime across environments                            |

**Design rationale highlights**

* **Stateful in-memory design** → DB is a durable log, not a bottleneck for matching loop.
* **Processed message ledger** → enforces idempotency across at-least-once Kafka delivery.
* **DLQ integration** → ensures poison-pill messages don’t halt processing.

---

## 5. Architecture at a glance

### Flow 1: Startup & Recovery

1. On boot, queries `resting_orders` (ACTIVE/PARTIALLY\_FILLED).
2. Rebuilds in-memory order books per ticker.
3. Kafka consumers start consuming new events.

### Flow 2: Processing a New Order

1. `OrderPlacedConsumer` receives event.
2. **Idempotency check** against `processed_messages`.
3. Event transformed into `RestingOrder`.
4. **Matching loop** executes trades against book:

   * Create `TradeExecutedEvent`.
   * Publish to Kafka.
   * Update resting order state in DB.
5. If unfilled:

   * IOC → cancel remainder.
   * FOK → reject unless fully fillable.
   * MARKET → cancel remainder.
   * LIMIT (GTC) → persist in book + DB.
6. Record `(topic, messageId)` in ledger.

### Flow 3: Cancellations

* `OrderCancelledConsumer` receives event.
* Deduplication check.
* Cancels order in DB and removes from in-memory book.

---

## 6. Rules & Invariants

* Only **LIMIT orders** can rest in the book.
* **MARKET, IOC, FOK** → execute immediately, never persist.
* **Price-time priority**:

  * Best price wins (highest bid / lowest ask).
  * FIFO among same-price orders.
* Idempotency enforced by `(topic, messageId)` uniqueness.
* Warm-start rebuilds all active orders into in-memory books.

---

## 7. Data Model

**resting\_orders**

* PK: `id (UUID)`
* Key fields: `ticker`, `side`, `price`, `remaining_quantity`, `status`, `created_at`
* Purpose: durable backing store for active/partially filled orders.

**processed\_messages**

* PK: `id (BIGSERIAL)`
* Unique `(topic, message_id)`
* Purpose: idempotency ledger, ensures exactly-once semantics.

---

## 8. Configuration (env)

| Variable                       | Purpose            | Default              |
| ------------------------------ | ------------------ | -------------------- |
| SERVER\_PORT                   | Service port       | 8086                 |
| SPRING\_DATASOURCE\_URL        | JDBC URL           | –                    |
| SPRING\_DATASOURCE\_USERNAME   | DB user            | –                    |
| SPRING\_DATASOURCE\_PASSWORD   | DB pass            | –                    |
| KAFKA\_BOOTSTRAP\_SERVERS      | Kafka brokers      | `redpanda:9092`      |
| KAFKA\_CONSUMER\_GROUP         | Kafka group        | `matching-engine`    |
| KAFKA\_TOPIC\_ORDER\_PLACED    | Order placed topic | `order.placed.v1`    |
| KAFKA\_TOPIC\_ORDER\_CANCELLED | Cancel topic       | `order.cancelled.v1` |
| KAFKA\_TOPIC\_TRADE\_EXECUTED  | Trade topic        | `trade.executed.v1`  |

---

## 9. Operations & Runbook

**Build & run:**

```bash
./gradlew clean build -x test
docker build -t matching-engine .
docker run -p 8086:8086 --env-file .env matching-engine
```

**Health check:**

```bash
curl http://localhost:8086/actuator/health
```

**Troubleshooting**

| Symptom               | Cause                                    | Fix                                          |
| --------------------- | ---------------------------------------- | -------------------------------------------- |
| Orders not matching   | No opposing liquidity / consumer lag     | Check Kafka lag, inspect `resting_orders`    |
| Duplicate trades      | Ledger misconfigured                     | Check `processed_messages` unique constraint |
| Service fails startup | DB unavailable / Flyway migration failed | Verify DB, check `flyway_schema_history`     |
| High DLQ volume       | Poison pill / unhandled transient error  | Inspect DLT messages to isolate producer     |

---

## 10. Extensibility

* Add **advanced order types**: stop-loss, iceberg.
* **Market data feeds**: broadcast book depth snapshots.
* **Horizontal scaling**: shard by ticker.
* **Performance instrumentation**: Prometheus metrics (match latency, depth, lag).

---

## 11. Where this fits in the bigger system

* **Orders Service** → produces placement & cancel events.
* **Matching Engine** → consumes, applies matching logic, produces trade executions.
* **Downstream services** (Portfolio, Clearing, Risk) → consume `trade.executed.v1`.

---

## 12. Resume/CV Content (Copy-Paste Ready)

**Impact Bullets**

* Built a **high-performance, stateful matching engine** serving as the core execution venue in a distributed trading platform.
* Designed a **low-latency, priority-queue–based order book** enforcing price–time priority, supporting multiple order types.
* Implemented **idempotency ledger** for exactly-once semantics across Kafka’s at-least-once delivery.
* Engineered **resilient DLQ strategy** with exponential backoff, retries, and poison-pill isolation.
* Operationalized with **Flyway migrations, Docker, warm-start recovery, and observability hooks**.

**Scope/Scale (to fill in with real numbers):**

* Throughput: \~N orders/sec.
* Latency: P99 < X ms.
* Recovery: rebuild Y million orders in < Z sec.

**Tech Stack Summary**
Java 17, Spring Boot, Spring Kafka, JPA/Hibernate, PostgreSQL, Flyway, Docker, PriorityQueue.

**Cover-letter paragraph**
I engineered a **mission-critical matching engine** that processes orders in real time, guarantees exactly-once semantics, and enforces exchange-grade matching rules. Built with Java 17, Spring Boot, and Kafka, it demonstrates expertise in **stateful systems design, transactional correctness, and fault-tolerant event-driven architectures**.

---

## 13. Interview Talking Points

* Stateful vs stateless designs → trade-offs in latency and recovery.
* Idempotency → role of `processed_messages` in enforcing correctness.
* PriorityQueue complexity → O(log n) operations.
* Startup recovery → rebuilding books from `resting_orders`.
* Error handling → poison-pill detection, DLQ strategy.
* Concurrency → Kafka partition guarantees simplify synchronization.

---

## 14. Cheat Sheet (for yourself)

**Sample OrderPlacedEvent**

```json
{
  "orderId": "uuid",
  "userId": "uuid",
  "ticker": "AAPL",
  "side": "BUY",
  "orderType": "LIMIT",
  "timeInForce": "GTC",
  "price": 150.00,
  "quantity": 10
}
```

**Kafka**

```bash
# Produce order
kcat -b localhost:9092 -t order.placed.v1 -P -l order.json

# Consume trades
kcat -b localhost:9092 -t trade.executed.v1 -C -q
```

**DB Queries**

```sql
-- Active orders
SELECT * FROM resting_orders WHERE ticker='AAPL' AND status IN ('ACTIVE','PARTIALLY_FILLED');

-- Dedup ledger
SELECT * FROM processed_messages WHERE topic='order.placed.v1' AND message_id='uuid';
```

**Restart service**

```bash
docker restart matching-engine
```

---

