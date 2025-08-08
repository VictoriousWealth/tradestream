# 📈 Market Data Consumer – TradeStream

## **Overview**

The **Market Data Consumer** microservice is responsible for:

* Consuming **executed trade events** from Kafka.
* Aggregating these trades into **OHLCV candles** across multiple time intervals.
* Persisting candles to **PostgreSQL**.
* Caching the **latest candle per (ticker, interval)** in **Redis** for ultra-fast retrieval.
* Serving aggregated market data via REST APIs.

This service **does not**:

* Accept or manage orders.
* Execute trades.
* Publish data to Kafka.
* Act as the API gateway for the entire system.

It is purely **read-focused** with real-time aggregation from the trade event stream.

---

## **Architecture & Flow**

### **Event Ingestion**

1. Trades are produced to the Kafka topic `trade.executed.v1`.

2. Each message contains:

   ```json
   {
     "tradeId": "t-123",
     "orderId": "o-456",
     "userId": "u-789",
     "ticker": "AAPL",
     "price": 201.5,
     "quantity": 10,
     "side": "BUY",
     "timestamp": "2025-08-08T18:31:30Z"
   }
   ```

3. The consumer:

   * Deduplicates based on `tradeId` to ensure idempotency.
   * Aggregates into candles for intervals: **1m, 5m, 1h, 1d**.
   * Uses UPSERT to maintain the correct OHLCV data for each interval.

---

### **Aggregation Logic**

For each new trade:

* **Open** price = first trade in the bucket.
* **High** price = max price seen in the bucket.
* **Low** price = min price seen in the bucket.
* **Close** price = last trade in the bucket.
* **Volume** = sum of quantities.

---

### **Caching**

* **Cache Name**: `latest`
* **Key Format**: `market:latest::<interval>:<TICKER>`

  * Example: `market:latest::1m:AAPL`
* **TTL**: 10 minutes (configurable).
* **Eviction**: When a new trade is processed, the corresponding `(interval, ticker)` key is removed from Redis so the next read repopulates it.

---

## **Technologies Used**

| Layer            | Technology                                                   |
| ---------------- | ------------------------------------------------------------ |
| Language         | Java 17                                                      |
| Framework        | Spring Boot 3.5                                              |
| Messaging        | Apache Kafka (via Spring Kafka)                              |
| Database         | PostgreSQL 15 (via Spring Data JPA & Flyway for migrations)  |
| Caching          | Redis (Spring Data Redis, JSON serialization with type info) |
| Build Tool       | Gradle                                                       |
| Containerization | Docker + Docker Compose                                      |
| Metrics/Health   | Spring Boot Actuator                                         |

---

## **REST API**

### **Get Latest Candle**

```
GET /candles/{ticker}/latest?interval={interval}
```

#### Path Parameters:

* `ticker` – Stock ticker (e.g., `AAPL`)

#### Query Parameters:

* `interval` – Candle interval, one of: `1m`, `5m`, `1h`, `1d`

#### Example:

```bash
curl http://localhost:8083/candles/AAPL/latest?interval=1m
```

#### Example Response:

```json
{
  "id": "16f5b3b2-5db6-476e-b2bc-1cb4e7ea7a31",
  "ticker": "AAPL",
  "interval": "1m",
  "bucketStart": "2025-08-08T18:31:00Z",
  "open": 201.5,
  "high": 202.3,
  "low": 201.5,
  "close": 202.3,
  "volume": 30.0,
  "updatedAt": "2025-08-08T18:38:42.599208Z"
}
```

* **First request**: hits PostgreSQL, stores result in Redis.
* **Subsequent requests**: served from Redis (millisecond latency).

---

## **Expected Inputs**

* Kafka Topic: `trade.executed.v1`
* Message format: `TradeExecuted` DTO (as shown above).
* Each trade must include:

  * `tradeId` (unique per trade)
  * `ticker`
  * `price`
  * `quantity`
  * `timestamp` (UTC)

---

## **Expected Outputs**

* Persisted candle rows in PostgreSQL.
* Latest candle in Redis under `market:latest::<interval>:<TICKER>`.
* REST JSON responses for latest candles.

---

## **Running the Service**

### **Local with Docker Compose**

```bash
docker compose up -d
```

This starts:

* Market Data Consumer service
* PostgreSQL
* Redis
* Kafka (Redpanda)

---

### **Testing the Pipeline**

**1. Produce a trade event:**

```bash
docker exec -i $(docker ps -qf name=redpanda) \
  rpk topic produce trade.executed.v1 -k AAPL <<'JSON'
{"tradeId":"t-100","orderId":"o-100","userId":"u-1","ticker":"AAPL","price":201.5,"quantity":10,"side":"BUY","timestamp":"2025-08-08T18:31:30Z"}
JSON
```

**2. Fetch the latest candle:**

```bash
curl http://localhost:8083/candles/AAPL/latest?interval=1m
```

**3. Check Redis cache:**

```bash
docker compose exec redis redis-cli KEYS 'market:*'
docker compose exec redis redis-cli GET 'market:latest::1m:AAPL'
```

---

## **Operational Notes**

* If Redis is unavailable, reads will still work but be slower (DB-only).
* Cache TTL can be adjusted in `RedisCacheConfig`.
* Supported intervals are hardcoded in `AggregationService` but can be made configurable.
* `cache-null-values=false` ensures nonexistent candles are not cached.

---

This README should give **any developer, tester, or devops engineer** a clear mental model of:

* What this service does.
* How it does it.
* How to run and test it.
* What inputs and outputs to expect.

---
