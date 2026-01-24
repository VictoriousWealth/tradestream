# TradeStream — High-Performance Trading Microservices Platform

> A production-minded, event-driven trading platform built with Java/Spring, Kafka/Redpanda, PostgreSQL, Redis, and Docker. This **recruiter-facing README** is the entry point for architecture, how to run, critical routes, and links into each service’s source of truth.

---

## TL;DR

* **Edge security:** Spring Cloud **API Gateway** validates **PS256 JWTs**, rate-limits login, injects `X-Request-Id`, and applies circuit-breaker fallbacks.
* **Order flow:** **Orders Service** validates & persists → **Matching Engine** executes (price-time) → emits `trade.executed.v1`.
* **Post-trade:** **Transaction Processor** journals trades → emits `transaction.recorded.v1`.
* **Read model:** **Portfolio Service** projects positions (qty, WAC, realized PnL).
* **Market data:** **Market Data Consumer** aggregates OHLCV; “latest” cached in Redis with precise eviction.

All services have Flyway migrations, Actuator health, and Dockerized builds. **Gateway is the only public entry.**

---

## Architecture Overview (CQRS + Event-Driven)

The platform follows **CQRS** and **event-driven** patterns. The **API Gateway** is the single, secure entry point.
Write paths (place/cancel order) produce events on **Kafka (Redpanda)**; read-side services consume those events to maintain projections (portfolios, OHLCV). This decouples components, improves fault tolerance, and allows independent scaling.

### High-Level Data Flow (Placing an Order)

```mermaid
sequenceDiagram
    participant Client
    participant API Gateway
    participant Orders Service
    participant Kafka
    participant Matching Engine
    participant Downstream Consumers

    Client->>+API Gateway: POST /api/orders (with JWT)
    API Gateway->>+Orders Service: Forward request
    Orders Service-->>-API Gateway: 202 Accepted
    API Gateway-->>-Client: 202 Accepted

    Orders Service->>+Kafka: Publish(order.placed.v1)
    Kafka-->>-Orders Service: Ack

    Matching Engine->>+Kafka: Consume(order.placed.v1)
    Note over Matching Engine: Price-time matching
    Matching Engine->>+Kafka: Publish(trade.executed.v1)
    Kafka-->>-Matching Engine: Ack

    Downstream Consumers->>+Kafka: Consume(trade.executed.v1)
    Note over Downstream Consumers: Tx Processor → Portfolio; Market Data aggregates candles
    Kafka-->>-Downstream Consumers: Ack
```

### System Map (ASCII)

```
Internet / Clients
        │
        ▼
┌──────────────────────────┐
│     API GATEWAY (8080)   │  PS256 JWT, Circuit Breakers, RL, X-Request-Id
└───────────┬──────────────┘
            │
   ┌────────┼──────────┬──────────────────────────┬───────────────┐
   ▼        ▼          ▼                          ▼               ▼
AUTH     USER-REG   ORDERS (8085)          TRANSACTION PROC    MARKET DATA
(8082)   (8081)     POST/GET/CANCEL        (8084) journal      CONSUMER (8083)
 /login    /register     │                 recorded.v1         OHLCV + Redis
 /refresh (internal)     │                      │                  │
                         ▼                      ▼                  │
                    KAFKA / REDPANDA (9092)  ←────────────────────┘
                         │
                         ▼
                  MATCHING ENGINE (8086)
                  price-time priority → trade.executed.v1
                         │
                         ▼
                   PORTFOLIO (8087)
                   positions + realized PnL
```

---

## Services & Ports

| Service                   | Port | Purpose                                                                                       | README                                         |
| ------------------------- | ---: | --------------------------------------------------------------------------------------------- | ---------------------------------------------- |
| **API Gateway**           | 8080 | JWT validation (PS256), routing, Redis rate limiting (login), circuit breakers, observability | `api-gateway/README.md`                        |
| **User Registration**     | 8081 | Internal-only `/register` with BCrypt; zero-trust via internal header                         | `services/user-registration-service/README.md` |
| **Authentication**        | 8082 | `/login` issues access+refresh; `/refresh` (internal-only) mints new access (PS256 signing)   | `services/authentication-service/README.md`    |
| **Market Data Consumer**  | 8083 | Aggregates trades → OHLCV; Redis-cached latest; REST reads                                    | `services/market-data-consumer/README.md`      |
| **Transaction Processor** | 8084 | Journals executed trades into immutable ledger; publishes `transaction.recorded.v1`           | `services/transaction-processor/README.md`     |
| **Orders Service**        | 8085 | Place/get/cancel orders; publishes `order.placed.v1`; applies fills idempotently              | `services/orders-service/README.md`            |
| **Matching Engine**       | 8086 | Price-time priority matching; publishes `trade.executed.v1`                                   | `services/matching-engine/README.md`           |
| **Portfolio Service**     | 8087 | Projects transactions → positions (qty, avgCost, realized PnL)                                | `services/portfolio-service/README.md`         |

> Infra: Redpanda (Kafka), Postgres (per service), Redis (rate limit + market “latest”) via `docker-compose.yml`.

---

## Gateway Route Map (Quick Reference)

| Area         | Gateway Route                                    | Auth | Rewrites to…                                 |
| ------------ | ------------------------------------------------ | :--: | -------------------------------------------- |
| Auth         | `POST /api/auth/login`                           |  No  | `/login` → authentication-service (8082)     |
| Auth         | `POST /api/auth/refresh`                         |  No  | `/refresh` (internal header enforced)        |
| Users        | `POST /api/users/register`                       |  No  | `/register` → user-registration (8081)       |
| Orders       | `POST /api/orders`                               |  Yes | `/orders` → orders-service (8085)            |
| Orders       | `GET /api/orders/{id}`                           |  Yes | `/orders/{id}`                               |
| Orders       | `POST /api/orders/{id}/cancel`                   |  Yes | `/orders/{id}/cancel`                        |
| Transactions | `GET /api/transactions/**`                       |  Yes | (no rewrite) → transaction-processor (8084)  |
| Portfolio    | `GET /api/portfolio/{userId}/positions[...]`     |  Yes | `/portfolio/...` → portfolio-service (8087)  |
| Market Data  | `GET /api/market-data/candles/{ticker}[/latest]` |  Yes | `/candles/...` → market-data-consumer (8083) |
| Ops          | `/actuator/*`                                    |  No  | Gateway self                                 |

* **Login is rate-limited per IP** (Redis).
* **Circuit breakers** return JSON fallbacks (no client timeouts).
* **`X-Request-Id`** is injected for traceability.

---

## Technology Stack

| Category         | Technology                                                 | Purpose                                              |
| ---------------- | ---------------------------------------------------------- | ---------------------------------------------------- |
| Backend          | Java 17 & 21, Spring Boot 3, Spring Cloud, Project Reactor | Core application frameworks; reactive gateway        |
| Messaging        | Apache Kafka (Redpanda)                                    | High-throughput, durable event bus                   |
| Database         | PostgreSQL, Spring Data JPA, Flyway                        | ACID persistence, ORM, versioned migrations          |
| Caching / Memory | Redis, Java PriorityQueue                                  | Market “latest” cache; in-memory order books         |
| Security         | Spring Security, **JWT (PS256)**                           | Centralized authentication/authorization at the edge |
| Resilience       | Resilience4j, DLQ pattern                                  | Circuit breakers, retries, dead-letter queues        |
| Packaging        | Docker, Docker Compose                                     | Consistent, isolated environments                    |
| Build & Ops      | Maven, Gradle, Spring Actuator, GitHub Actions             | Dependency mgmt, builds, health/metrics endpoints    |

---

## Getting Started

### Prerequisites

* Docker & Docker Compose
* Java 17+ (for local runs/IDE)
* OpenSSL
* `curl` & `jq` (handy)
* (Optional) `kcat` for Kafka testing

### Generate JWT Keys (PS256)

```bash
mkdir -p secrets
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out secrets/jwt_private.pem
openssl rsa -in secrets/jwt_private.pem -pubout -out secrets/jwt_public.pem
```

### Bring Up Everything (Dev)

```bash
docker compose up -d --build
```

Check health:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

---

## Quickstart (End-to-End via Gateway)

1. **Register → Login**

```bash
curl -sS -X POST http://localhost:8080/api/users/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"S3cureP@ss!"}'

ACCESS=$(curl -sS -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"S3cureP@ss!"}' | jq -r .access_token)
echo "ACCESS=$ACCESS"
```

2. **Place Order**

```bash
curl -sS -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $ACCESS" \
  -H 'Content-Type: application/json' \
  -d '{"userId":"<uuid>","ticker":"AAPL","side":"BUY","type":"LIMIT","timeInForce":"GTC","quantity":10,"price":150.5}' | jq .
```

3. **Portfolio & Market Data**

```bash
curl -sS -H "Authorization: Bearer $ACCESS" \
  http://localhost:8080/api/portfolio/<uuid>/positions | jq .

curl -sS -H "Authorization: Bearer $ACCESS" \
  "http://localhost:8080/api/market-data/candles/AAPL/latest?interval=1m" | jq .
```

> See helper scripts: `e2e_trade_pipeline_test.sh`, `e2e_portfolio_service.sh`, `gateway_smoke.sh`.

---

## Configuration (high-value envs)

* **Gateway**

  * `SERVER_PORT=8080`
  * `JWT_PUBLIC_KEY_LOCATION=file:/run/secrets/jwt_public.pem`
  * `SPRING_DATA_REDIS_HOST=redis` / `SPRING_DATA_REDIS_PORT=6379`
* **Auth**

  * `JWT_PRIVATE_KEY_PATH=/run/secrets/jwt_private.pem`
  * `JWT_PUBLIC_KEY_PATH=/run/secrets/jwt_public.pem`
  * `SPRING_DATASOURCE_URL=jdbc:postgresql://.../authdb`
* Each service has Flyway-managed Postgres config in `application.yml` and `docker-compose.yml`.

---

## Rules & Invariants (core guarantees)

* **Edge auth:** Gateway validates **PS256 JWT** for all non-whitelisted routes.
* **Idempotency ledgers:** Consumers persist processed message IDs to guarantee once-only effects under at-least-once delivery.
* **Orders state machine:** `NEW → PARTIALLY_FILLED → FILLED | CANCELED | REJECTED` (strict).
* **Portfolio math:** BUY recomputes **WAC**; SELL clamps to long qty; `avgCost` resets to `null` when flat.
* **Market data cache:** “Latest” candle cached in Redis; **each upsert evicts** the exact key → next read is fresh.
* **Headers:** `Cookie` stripped by Gateway; `X-Request-Id` injected if missing.

---

## Troubleshooting

| Symptom                               | Likely Cause                            | Fix                                             |
| ------------------------------------- | --------------------------------------- | ----------------------------------------------- |
| `401` at Gateway                      | Missing/expired/invalid JWT (PS256)     | Re-login; verify public key mount               |
| `429` on `/api/auth/login`            | Rate limit triggered                    | Back off (expected)                             |
| Gateway returns degraded JSON (`503`) | Circuit breaker open                    | Check downstream `/actuator/health`             |
| Orders not executing                  | Matching Engine/Kafka not consuming     | Inspect Kafka topics & ME logs                  |
| Duplicate fills or PnL drift          | Ledger/migrations missing               | Ensure Flyway applied; check unique constraints |
| `/latest` candle seems stale          | Quiet market + TTL not expired          | Expected until next trade evicts cache          |
| Registration returns `403`            | Missing internal header on internal hop | Ensure Gateway injects `X-Internal-Caller`      |

---

## Repository Layout (trimmed)

```
.
├── api-gateway/
├── services/
│   ├── authentication-service/
│   ├── user-registration-service/
│   ├── orders-service/
│   ├── matching-engine/
│   ├── transaction-processor/
│   └── market-data-consumer/
├── docker-compose.yml
├── secrets/                 # jwt_private.pem / jwt_public.pem
├── e2e_*.sh / gateway_smoke.sh
└── bench_out/
```

---

## Deep Dives (Sources of Truth)

* Gateway → `api-gateway/README.md`
* Authentication → `services/authentication-service/README.md`
* User Registration → `services/user-registration-service/README.md`
* Orders → `services/orders-service/README.md`
* Matching Engine → `services/matching-engine/README.md`
* Transaction Processor → `services/transaction-processor/README.md`
* Market Data Consumer → `services/market-data-consumer/README.md`

---

## License

See [`LICENSE`](LICENSE).
