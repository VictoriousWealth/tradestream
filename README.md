# TradeStream — Distributed Trading Platform

> Event-driven trading system with Spring/Java microservices, Kafka/Redpanda, PostgreSQL, Redis, and Docker. This root README is the entry for recruiters and engineers: what’s here **today**, how to run it, and where to look next.

<p align="center">
  <a href="https://github.com/VictoriousWealth/tradestream">
    <img src="https://img.shields.io/github/repo-size/VictoriousWealth/tradestream" alt="Repo Size">
  </a>
  <a href="https://github.com/VictoriousWealth/tradestream/issues">
    <img src="https://img.shields.io/github/issues/VictoriousWealth/tradestream" alt="Open Issues">
  </a>
  <a href="https://github.com/VictoriousWealth/tradestream/pulls">
    <img src="https://img.shields.io/github/issues-pr/VictoriousWealth/tradestream" alt="Pull Requests">
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/LICENSE-CC%20BY--NC%204.0-blue.svg" alt="License">
  </a>
  <a href="https://github.com/VictoriousWealth/tradestream/commits/main">
    <img src="https://img.shields.io/github/last-commit/VictoriousWealth/tradestream" alt="Last Commit">
  </a>
</p>

---

## TL;DR

* **Edge security:** Spring Cloud **API Gateway** (Java 21) validates **PS256 JWT** at the perimeter, injects `X-Request-Id`, applies **Resilience4j** circuit breakers, and **Redis** IP rate limits **login**.
* **Order → Trade pipeline:** **Orders Service** → **Matching Engine** (price–time priority) → **Transaction Processor** (immutable journal).
* **Read models:** **Portfolio Service** projects positions & realized PnL. **Market Data Consumer** aggregates OHLCV and keeps “latest” hot in Redis.
* **Everything is containerized**; **Flyway** manages schemas; **Actuator** provides health/metrics.

> This repo is **educational/portfolio**—it simulates a trading stack; it does **not** process real money.

---

## What’s the current status?

| Component                   | Path                                  | Status | Notes                                                                                    |
| --------------------------- | ------------------------------------- | :----: | ---------------------------------------------------------------------------------------- |
| **API Gateway**             | `api-gateway/`                        |    ✅   | PS256 JWT verification, Redis rate limit (login), circuit breakers, CORS, tracing header |
| **User Registration**       | `services/user-registration-service/` |    ✅   | Internal-only `/register` gated by `X-Internal-Caller: api-gateway`, BCrypt, Postgres    |
| **Authentication Service**  | `services/authentication-service/`    |    ✅   | `/login` issues **PS256**-signed access+refresh; `/refresh` is **internal-only**         |
| **Orders Service**          | `services/orders-service/`            |    ✅   | REST + Kafka producer/consumer, pessimistic locks, idempotent fill tracking              |
| **Matching Engine**         | `services/matching-engine/`           |    ✅   | Price–time priority, idempotency ledger, DLQ                                             |
| **Transaction Processor**   | `services/transaction-processor/`     |    ✅   | Journals `trade.executed.v1` → emits `transaction.recorded.v1`, REST queries             |
| **Portfolio Service**       | `services/portfolio-service/`         |    ✅   | Projects transactions → positions & realized PnL; pessimistic locking + ledger           |
| **Market Data Consumer**    | `services/market-data-consumer/`      |    ✅   | Kafka→Postgres OHLCV; Redis-cached “latest” with precise eviction                        |
| **Docs: recruiter summary** | `cvreadme.md`                         |    ✅   | Hybrid, recruiter-friendly project overview                                              |
| **Kubernetes/Terraform**    | —                                     |   🔜   | Planned future work; not present in this repo                                            |
| **RabbitMQ option**         | —                                     |    ❌   | **Not used**—message bus is **Kafka/Redpanda only**                                      |
| **JWE (encrypted JWTs)**    | —                                     |    ❌   | **Not used**—project uses **signed JWT (PS256)** only                                    |

Legend: ✅ present & working (dev), 🔜 planned, ❌ not applicable.

---

## Architecture at a glance

```
Clients
  │
  ▼
API Gateway (8080)  — PS256 JWT, Circuit Breakers, Redis Rate Limit (login), X-Request-Id
  │
  ├─ /api/users/register  → user-registration-service (8081) [internal header required]
  ├─ /api/auth/*          → authentication-service (8082)    [/login public, /refresh internal-only]
  ├─ /api/orders/*        → orders-service (8085)
  ├─ /api/transactions/*  → transaction-processor (8084)
  ├─ /api/portfolio/*     → portfolio-service (8087)
  └─ /api/market-data/*   → market-data-consumer (8083)

Kafka/Redpanda (9092)
  ├─ order.placed.v1 → Matching Engine (8086) → trade.executed.v1
  └─ trade.executed.v1 → Transaction Processor → transaction.recorded.v1 → Portfolio Service
```

---

## Tech stack (actual)

* **Language/Frameworks:** Java 17/21, Spring Boot 3, Spring Cloud Gateway, Project Reactor
* **Messaging:** Kafka-compatible **Redpanda**
* **Datastores:** PostgreSQL (per service, **Flyway** migrations), **Redis** (rate limit + market latest)
* **Resilience/Obs:** Resilience4j circuit breakers, Spring **Actuator**
* **Packaging:** Docker & **Docker Compose** (local dev)
* **Auth:** **JWT (PS256)** at the gateway; private key in Auth service; public key at Gateway
* **CI/CD/Cloud:** local-first; cloud/IaC (Kubernetes/Terraform) are **planned**, not in-repo

---

## Run it locally

### Prereqs

* Docker & Docker Compose
* `openssl` (for JWT keypair)
* `jq` (nice to have)
* (optional) `kcat` for Kafka testing

### 1) Generate PS256 keys

```bash
mkdir -p secrets
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out secrets/jwt_private.pem
openssl rsa -in secrets/jwt_private.pem -pubout -out secrets/jwt_public.pem
```

### 2) Bring everything up

```bash
docker compose up -d --build
```

### 3) Health check

```bash
curl -s http://localhost:8080/actuator/health
# {"status":"UP"}
```

---

## Quickstart (Gateway-first)

### Register → Login

```bash
curl -sS -X POST http://localhost:8080/api/users/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"S3cureP@ss!"}'

ACCESS=$(curl -sS -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"S3cureP@ss!"}' | jq -r .access_token)
echo "ACCESS=$ACCESS"
```

### Place an order

```bash
curl -sS -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' \
  -d '{"userId":"<uuid>","ticker":"AAPL","side":"BUY","type":"LIMIT","timeInForce":"GTC","quantity":10,"price":150.5}' | jq .
```

### Portfolio & Market Data

```bash
curl -sS -H "Authorization: Bearer $ACCESS" \
  http://localhost:8080/api/portfolio/<uuid>/positions | jq .

curl -sS -H "Authorization: Bearer $ACCESS" \
  "http://localhost:8080/api/market-data/candles/AAPL/latest?interval=1m" | jq .
```

> Handy scripts live at repo root: `e2e_trade_pipeline_test.sh`, `e2e_portfolio_service.sh`, `gateway_smoke.sh`.

---

## Gateway routes (reality-checked)

| Area         | Route                                            | Auth | Rewrites to…                                 |
| ------------ | ------------------------------------------------ | :--: | -------------------------------------------- |
| Auth         | `POST /api/auth/login`                           |  No  | `/login` → authentication-service (8082)     |
| Auth         | `POST /api/auth/refresh`                         |  No  | `/refresh` (internal-only header enforced)   |
| Users        | `POST /api/users/register`                       |  No  | `/register` → user-registration (8081)       |
| Orders       | `POST /api/orders`                               |  Yes | `/orders` → orders-service (8085)            |
| Orders       | `GET /api/orders/{id}`                           |  Yes | `/orders/{id}`                               |
| Orders       | `POST /api/orders/{id}/cancel`                   |  Yes | `/orders/{id}/cancel`                        |
| Transactions | `GET /api/transactions/**`                       |  Yes | (no rewrite) → transaction-processor (8084)  |
| Portfolio    | `GET /api/portfolio/{userId}/positions[...]`     |  Yes | `/portfolio/...` → portfolio-service (8087)  |
| Market Data  | `GET /api/market-data/candles/{ticker}[/latest]` |  Yes | `/candles/...` → market-data-consumer (8083) |
| Ops          | `/actuator/*`                                    |  No  | Gateway itself                               |

* **Login is IP rate-limited via Redis** (token-bucket).
* **Circuit breakers** return predictable JSON fallbacks.
* **`X-Request-Id`** injected if missing; cookies stripped.

---

## Security model (current)

* **JWT at edge:** Gateway is a **resource server** validating **PS256** signatures with the public key.
* **Internal caller gates:**

  * `authentication-service` `/refresh` requires `X-Internal-Caller: api-gateway`.
  * `user-registration-service` `/register` requires the same header (added by the Gateway).
* **No JWE** (encryption) — **signed** JWTs only.
* **Rate limiting:** Redis-backed rate limit on login endpoints.
* **CORS:** permissive in dev; tighten in prod (config in Gateway).

---

## Troubleshooting (real problems you’ll see)

| Symptom                          | Likely cause                           | Fix                                             |
| -------------------------------- | -------------------------------------- | ----------------------------------------------- |
| `401` at gateway                 | Missing/expired/invalid JWT (PS256)    | Re-login; verify public key mount               |
| `429` on `/api/auth/login`       | Rate limit tripped                     | Back off; expected behavior                     |
| Gateway degraded JSON (`503`)    | Circuit breaker open on downstream     | Check downstream `/actuator/health`             |
| Orders don’t execute             | Matching Engine or Kafka not consuming | Inspect topics/consumer logs                    |
| Duplicate fills / position drift | Ledger/migration missing               | Ensure Flyway applied; check unique constraints |
| Stale market “latest”            | Quiet feed; TTL not expired            | Next trade will evict → fresh read              |
| `/register` returns `403`        | Missing internal-caller header         | Ensure Gateway injects `X-Internal-Caller`      |

---

## Repo layout (trimmed)

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
├── secrets/                       # jwt_private.pem / jwt_public.pem
├── cvreadme.md                    # recruiter-facing overview
├── e2e_*.sh / gateway_smoke.sh
└── bench_out/
```

**Per-service READMEs (sources of truth):**

* `api-gateway/README.md`
* `services/authentication-service/README.md`
* `services/user-registration-service/README.md`
* `services/orders-service/README.md`
* `services/matching-engine/README.md`
* `services/transaction-processor/README.md`
* `services/market-data-consumer/README.md`

---

## License

This project is licensed under **CC BY-NC 4.0**. See [`LICENSE`](LICENSE).

---

