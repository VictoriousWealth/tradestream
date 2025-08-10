## **Orders Service — Developer Handbook**

**Version:** 1.0
**Audience:** Developers maintaining/extending Orders Service
**Last Updated:** 2025-08-10

---

### **1. Service Overview**

Orders Service is responsible for:

* Accepting and validating new market/limit orders from authenticated users.
* Persisting orders in PostgreSQL.
* Publishing `OrderPlaced` events to Kafka for the Matching Engine.
* Handling order cancellations.
* Consuming `TradeExecuted` events from Kafka and applying fills to existing orders.

It acts as the **entry point for all order activity** in the TradeStream platform and maintains authoritative order state.

---

### **2. Position in System Architecture**

**Upstream:**

* API Gateway → forwards `/orders` requests to Orders Service (authenticated and rate-limited).

**Downstream:**

* Kafka (Redpanda) → publishes `order.placed.v1` and `order.cancelled.v1`.
* Matching Engine Service → receives `OrderPlaced` events.
* Market Data & Portfolio services → react to `TradeExecuted` events published by Matching Engine.

**External Dependencies:**

* PostgreSQL (ordersdb)
* Kafka broker (Redpanda)

---

### **3. REST API**

| Method | Path                  | Body (JSON)         | Description              | Response                       |
| ------ | --------------------- | ------------------- | ------------------------ | ------------------------------ |
| POST   | `/orders`             | `PlaceOrderRequest` | Place a new order        | `OrderResponse` (202 Accepted) |
| GET    | `/orders/{id}`        | —                   | Retrieve order details   | `OrderResponse`                |
| POST   | `/orders/{id}/cancel` | —                   | Cancel an existing order | `Order` (200 OK)               |

**Validation rules:**

* `userId`: UUID, required.
* `ticker`: String ≤ 16 chars, required.
* `side`: BUY or SELL.
* `type`: MARKET or LIMIT.
* `timeInForce`: IOC / FOK / GTC / DAY.
* `quantity`: > 0.000001.
* `price`: Required for LIMIT; must be null for MARKET.

**Error handling:**

* Invalid arguments → 400 with `{"code":"BAD_REQUEST","message":"..."}`
* State violations (e.g., canceling non-NEW order) → 409 with `{"code":"CONFLICT","message":"..."}`
* Order not found → 400 BAD\_REQUEST

---

### **4. Event Contracts**

**Published:**

* **OrderPlaced** (`order.placed.v1`) — sent after successful order creation.
* **OrderCancelledEvent** (`order.cancelled.v1`) — sent after successful cancel of a NEW order.

**Consumed:**

* **TradeExecuted** (`trade.executed.v1`) — updates orders with partial or full fills, idempotent via `ingested_trades` table.

---

### **5. Internal Architecture**

**Key Components:**

* **OrdersController** — REST layer for request handling.
* **OrderService** — Business logic for placing, retrieving, and canceling orders.
* **OrderProducer** — Publishes `OrderPlaced` events to Kafka.
* **TradeExecutedConsumer** — Kafka listener to update order state from fills.
* **OrderRepository** — Spring Data JPA repository for `Order` entity.
* **IngestedTradeRepository** — Idempotency tracking for processed trades.

---

### **6. Domain Model**

**Order**

* Tracks order lifecycle (`NEW`, `CANCELED`, `PARTIALLY_FILLED`, `FILLED`, `REJECTED`, `EXPIRED`).
* Supports optimistic locking (`@Version`) and pessimistic row locking for updates.
* Maintains `filledQuantity`, `remainingQuantity` (computed), and `lastFillPrice`.

---

### **7. Database Schema**

**Tables:**

* `orders`
* `ingested_trades`

**Migrations:**

* `V1__init_orders.sql` — base table with UUID PK, indexes on `user_id` and `ticker`.
* `V2__order_fill_tracking.sql` — adds `filled_quantity` and `last_fill_price`.
* `V3__ingested_trades.sql` — idempotency table for processed trades.

---

### **8. Configuration**

**Environment Variables:**

* `SERVER_PORT`
* `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
* `SPRING_JPA_HIBERNATE_DDL_AUTO`
* `KAFKA_BOOTSTRAP_SERVERS`
* `KAFKA_TOPIC_ORDER_PLACED`, `KAFKA_TOPIC_ORDER_CANCELLED`, `KAFKA_TOPIC_TRADE_EXECUTED`

---

### **9. Deployment**

**Dockerfile**:

* Builds Spring Boot app with Gradle, runs on Temurin JDK 17.
* Healthcheck via `curl` to `/actuator/health`.

**Dependencies in docker-compose:**

* `orders_postgres`
* `redpanda` (Kafka broker)

---

### **10. Development Workflow**

**Run locally:**

```bash
./gradlew bootRun
```

**Run in Docker:**

```bash
docker-compose up orders-service orders_postgres redpanda
```

**Test:**

```bash
./gradlew test
```

**Reset DB:**
Drop & recreate via Flyway migrations.

---
