# API Design (Current Implementation)

This document mirrors the live service routes and gateway behavior as implemented.

---

## Gateway Routes (External)

| Area         | Gateway Route                                    | Auth | Rewrites to…                                 |
| ------------ | ------------------------------------------------ | :--: | -------------------------------------------- |
| Auth         | `POST /api/auth/login`                           |  No  | `/login` → authentication-service (8082)     |
| Auth         | `POST /api/auth/refresh`                         |  No  | `/refresh` → authentication-service (8082)   |
| Users        | `POST /api/users/register`                       |  No  | `/register` → user-registration (8081)       |
| Orders       | `POST /api/orders`                               |  Yes | `/orders` → orders-service (8085)            |
| Orders       | `GET /api/orders/{id}`                           |  Yes | `/orders/{id}`                               |
| Orders       | `POST /api/orders/{id}/cancel`                   |  Yes | `/orders/{id}/cancel`                        |
| Transactions | `GET /api/transactions/**`                       |  Yes | (no rewrite) → transaction-processor (8084)  |
| Portfolio    | `GET /api/portfolio/{userId}/positions[...]`     |  Yes | `/portfolio/...` → portfolio-service (8087)  |
| Market Data  | `GET /api/market-data/candles/{ticker}[/latest]` |  Yes | `/candles/...` → market-data-consumer (8083) |

Notes:
* **Login** is IP rate-limited via Redis (10 r/s, burst 20).
* **`/api/auth/refresh`** and **`/api/users/register`** are **internal-only** at the downstream services via `X-Internal-Caller: api-gateway`.

---

## Service Endpoints (Internal)

### Authentication Service (8082)
* `POST /login` → issues access + refresh tokens (PS256).
* `POST /refresh` → requires `X-Internal-Caller: api-gateway`.

### User Registration Service (8081)
* `POST /register` → requires `X-Internal-Caller: api-gateway`.

### Orders Service (8085)
* `POST /orders` → place order (accepted).
* `GET /orders/{id}` → order status.
* `POST /orders/{id}/cancel` → cancel order.

### Transaction Processor (8084)
* `GET /api/transactions/{userId}`
* `GET /api/transactions/{userId}/ticker/{ticker}`
* `GET /api/transactions/{userId}/since?iso=...`

### Portfolio Service (8087)
* `GET /portfolio/{userId}/positions`
* `GET /portfolio/{userId}/positions/{ticker}`
* `GET /portfolio/{userId}/summary`

### Market Data Consumer (8083)
* `GET /candles/{ticker}?interval=1m&limit=100`
* `GET /candles/{ticker}/latest?interval=1m`

---

## Events (Kafka/Redpanda)

* `order.placed.v1` → emitted by Orders Service.
* `trade.executed.v1` → emitted by Matching Engine.
* `transaction.recorded.v1` → emitted by Transaction Processor.
