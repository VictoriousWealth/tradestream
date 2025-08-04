# Market Data Consumer Service

This microservice ingests market data events (e.g. stock price updates), validates them, and persists them into a PostgreSQL database.

---

## Features

- ✅ Exposes REST API to receive market data events
- ✅ Validates input with Jakarta Bean Validation
- ✅ Stores data in PostgreSQL using Spring Data JPA
- ✅ Database migrations and seed data via Flyway
- ✅ Dockerized and orchestrated with Docker Compose / Swarm
- ✅ Structured validation error responses
- ✅ Tested via `curl` and raw SQL inspection

---

## Tech Stack

- ![Java](https://img.shields.io/badge/Java-21-blue?logo=java\&logoColor=white) 
- ![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.0-green?logo=spring-boot)
- ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue?logo=postgresql)
- ![Flyway](https://img.shields.io/badge/Flyway-Database_Migrations-red?logo=flyway\&logoColor=white)
- ![Docker](https://img.shields.io/badge/Docker-Containerized-blue?logo=docker)
- ![Docker Compose](https://img.shields.io/badge/Docker--Compose-Orchestration-2496ED?logo=docker\&logoColor=white)

---

##� API Specification

### POST `/api/stock/event`

Ingests a new market data event.

#### Request Body (JSON)

```json
{
  "ticker": "AAPL",
  "name": "Apple Inc.",
  "price": 196.20,
  "volume": 10000000,
  "date": "2025-08-04"
}
````

#### Response

* `202 Accepted`: Event was accepted and stored
* `400 Bad Request`: Validation failed

---

## Validation Rules

| Field  | Constraints                |
| ------ | -------------------------- |
| ticker | Not null, 1–10 characters  |
| name   | Not null, 1–255 characters |
| price  | Not null, must be positive |
| volume | Must be positive           |
| date   | Not null                   |

---

## Running Locally

### 1. Build the Docker images

```bash
docker build -t market-data-consumer:dev ./market-data-consumer
```

### 2. Start services

```bash
docker stack deploy -c docker-compose.yml tradestream
```

### 3. Test the endpoint

```bash
curl -X POST http://localhost:8083/api/stock/event \
  -H "Content-Type: application/json" \
  -d '{
    "ticker": "AAPL",
    "name": "Apple Inc.",
    "price": 196.20,
    "volume": 10000000,
    "date": "2025-08-04"
  }'
```

---

## Database Schema

Created via Flyway (`V1__init_schema.sql`, etc):

```sql
CREATE TABLE stock_data (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    ticker VARCHAR(10) NOT NULL,
    date DATE NOT NULL,
    open DECIMAL(18, 4) NOT NULL,
    high DECIMAL(18, 4) NOT NULL,
    low DECIMAL(18, 4) NOT NULL,
    close DECIMAL(18, 4) NOT NULL,
    volume BIGINT NOT NULL,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    CONSTRAINT uq_ticker_date UNIQUE (ticker, date)
);
```

---

## Seed Data

Seeded automatically by Flyway (`V4__seed_stock_data.sql`):

```sql
INSERT INTO stock_data (...) VALUES
  ('AAPL', ...),
  ('TSLA', ...);
```

---

## Folder Structure

```
.
├── market-data-consumer/
│   ├── src/main/java/com/tradestream/market_data_consumer/
│   │   ├── stock_data/
│   │   │   ├── StockData.java
│   │   │   ├── MarketDataEvent.java
│   │   │   ├── StockDataController.java
│   │   │   └── ValidationExceptionHandler.java
│   └── resources/db/migration/
│       ├── V1__init_schema.sql
│       ├── V2__add_timestamps_to_stock_data.sql
│       ├── V3__create_extension_pgcrypto.sql
│       └── V4__seed_stock_data.sql
```

---
