# 📌 The Ultimate Source of Truth: API Gateway

---

## 1. One-liner

A secure, resilient, reactive API Gateway that centralizes routing, enforces PS256 JWT authentication, and safeguards all ingress traffic with rate limiting, circuit breakers, and unified observability for a trading microservices platform.

---

## 2. Executive Summary (for recruiters)

* Built a **fault-tolerant API Gateway** (Spring Cloud Gateway, Java 21) that serves as the single entry point for 5+ trading microservices.
* Centralized **JWT-based authentication** at the edge (PS256 enforcement), decoupling downstream services from cross-cutting security concerns.
* Strengthened resilience by integrating **Resilience4j circuit breakers** with declarative fallbacks, preventing cascading failures.
* Secured critical endpoints with **Redis-backed IP rate limiting**, mitigating brute-force abuse on login endpoints.
* Delivered **operational visibility** with X-Request-Id tracing, Actuator metrics, and standardized JSON fallbacks.
* Designed with **declarative YAML routing** → clean mapping of external APIs to internal services via path rewriting, header manipulation, and filters.

---

## 3. What this service does

* ✅ **Acts as a single entry point** for all client traffic into the platform.
* 🔐 **Secures downstream services** via PS256 JWT token validation (OAuth2 Resource Server).
* 🔀 **Routes requests dynamically** using path predicates and filters (`RewritePath`, `StripPrefix`).
* 🛡 **Protects the system** with Resilience4j circuit breakers, global fallback, and fast-fail behavior.
* 🚦 **Applies IP-based rate limiting** (Redis token bucket) for login/refresh endpoints.
* 🔧 **Injects `X-Request-Id` headers** for distributed tracing.
* 🌐 **Manages CORS globally** with consistent policy across environments.
* 📊 **Exposes operational telemetry** via Actuator endpoints (`/health`, `/metrics`, `/gateway`).

---

## 4. Tech Stack & Key Choices

| Technology                        | Purpose                   | Why                                                           |
| --------------------------------- | ------------------------- | ------------------------------------------------------------- |
| Java 21 + Spring Boot 3           | Runtime & framework       | Reactive, modern ecosystem, long-term support                 |
| Spring Cloud Gateway              | API gateway framework     | Reactive, declarative routing, first-class Spring integration |
| Spring Security (OAuth2 Resource) | JWT validation            | Industry-standard, flexible, PS256 signature enforcement      |
| Resilience4j                      | Circuit breakers          | Reactive, lightweight, isolates failures                      |
| Spring Data Redis (Reactive)      | Rate limiting state store | Low-latency token bucket algo, distributed-safe               |
| Docker                            | Containerization          | Consistent deployment across dev/stage/prod                   |
| Maven                             | Build & dependency mgmt   | Standard for Spring ecosystem                                 |
| Actuator                          | Observability             | Health, metrics, route introspection                          |
| Lombok                            | Boilerplate reduction     | Cleaner filter/config classes                                 |

**Design rationale highlights:**

* **JWT validation at the edge** → Zero-trust boundary, downstreams don’t duplicate auth logic.
* **Declarative routing in YAML** → Easy to evolve without redeploying.
* **Circuit breakers per domain** → Isolates failures of orders, transactions, portfolio, and market data services.
* **X-Request-Id injection** → Traceability across distributed logs.
* **Rate limiting on auth endpoints** → Protects login service from brute-force or abuse.

---

## 5. Architecture at a glance

### Flow 1: Authenticated Request (Place Order)

1. Client sends `POST /api/orders` with `Authorization: Bearer <JWT>`.
2. Gateway validates token signature (PS256) via public key.
3. Route predicate matches `/api/orders/**`.
4. Filters apply:

   * `RewritePath` → `/orders/**`
   * `RequestIdFilter` → injects `X-Request-Id`.
5. Request proxied to `orders-service`.
6. If failures breach threshold, `ordersCB` circuit breaker opens → `/fallback` returns degraded JSON response.

### Flow 2: Public Request with Rate Limiting (Login)

1. Client sends `POST /api/auth/login`.
2. `permitAll` rule allows bypass of JWT validation.
3. `RequestRateLimiter` uses Redis + `ipKeyResolver` to enforce quota (10 req/s, burst 20).
4. On pass → request rewritten to `/login` → proxied to `auth-service`.
5. On fail → gateway responds with `429 Too Many Requests`.

---

## 6. Rules & Invariants

* All routes must pass JWT validation unless explicitly whitelisted (`/api/auth/login`, `/api/auth/refresh`, `/api/users/register`, `/actuator/health`).
* JWTs must be PS256-signed.
* `X-Request-Id` is always present (injected if missing).
* Login endpoints are rate-limited per IP.
* Circuit breakers always return fast-fail degraded JSON, never client timeouts.
* Cookie headers are stripped from all inbound requests.

---

## 7. Data Model

* **Stateless service** → no DB.
* **Redis** → ephemeral store for rate limiting buckets.
* **JWT Public Key** → loaded from mounted secrets for PS256 validation.

---

## 8. Configuration (env)

| Key                       | Default                                                                        | Notes                          |
| ------------------------- | ------------------------------------------------------------------------------ | ------------------------------ |
| `SERVER_PORT`             | 8080                                                                           | Gateway HTTP port              |
| `JWT_PUBLIC_KEY_LOCATION` | file:/run/secrets/jwt\_public.pem                                              | Path to PS256 public key       |
| `AUTH_BASE_URL`           | [http://auth-service:8082](http://auth-service:8082)                           | Auth service base              |
| `REG_BASE_URL`            | [http://user-registration-service:8081](http://user-registration-service:8081) | User registration service base |
| `ORDERS_BASE_URL`         | [http://orders-service:8085](http://orders-service:8085)                       | Orders service base            |
| `SPRING_DATA_REDIS_HOST`  | redis                                                                          | Redis host for rate limiting   |
| `SPRING_DATA_REDIS_PORT`  | 6379                                                                           | Redis port                     |

---

## 9. Operations & Runbook

**Build & run:**

```bash
./mvnw clean package -DskipTests
docker build -t api-gateway .
docker run -p 8080:8080 --env-file .env api-gateway
```

**Health & diagnostics:**

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/gateway/routes
curl http://localhost:8080/actuator/metrics
```

**Troubleshooting**

| Symptom                    | Likely Cause                    | Fix                                   |
| -------------------------- | ------------------------------- | ------------------------------------- |
| 401 Unauthorized           | Invalid/expired JWT             | Refresh token, verify PS256 key       |
| 429 Too Many Requests      | Rate limit exceeded             | Back off; expected behavior           |
| 503 degraded JSON response | Circuit breaker open            | Check downstream service health       |
| 404 for expected route     | Misconfigured `application.yml` | Verify `Path` + `RewritePath`         |
| 500 JWT validation error   | Public key missing/invalid      | Check `JWT_PUBLIC_KEY_LOCATION` mount |
| Missing `X-Request-Id`     | Filter misconfigured            | Ensure `RequestIdFilter` registered   |

---

## 10. Extensibility

* Add new routes declaratively in `application.yml` with no code changes.
* Add RBAC enforcement via a custom filter inspecting JWT `scope`/`roles`.
* Support **per-user rate limiting** by resolving JWT subject instead of IP.
* Introduce request/response logging filters for observability.
* Integrate OpenTelemetry tracing for distributed spans.

---

## 11. Where this fits in the bigger system

The API Gateway is the **front door** of the trading platform.
It sits between external clients and internal microservices (orders, portfolio, transactions, market data, auth).
It standardizes ingress policy, **isolates failures**, and **enforces zero-trust security** at the perimeter, enabling downstream services to focus solely on business logic.

---

## 12. Resume/CV Content (Copy-Paste Ready)

**Impact Bullets**

* Architected and implemented a **secure, reactive API Gateway** (Spring Cloud Gateway, Java 21) as the unified entry point for a trading platform.
* Enforced **PS256 JWT validation** at the edge, decoupling authentication logic from downstream services.
* Enhanced resilience with **Resilience4j circuit breakers and JSON fallbacks**, preventing cascading outages.
* Secured login endpoints with **Redis-backed IP rate limiting**, mitigating brute-force abuse.
* Delivered operational visibility with **X-Request-Id correlation, Actuator telemetry, and Dockerized deployments**.

**Scope/Scale (fill in)**

* Traffic volume: \~N requests/sec peak.
* Latency overhead: \<X ms P99.
* Routed services: Z+ downstream services.

**Tech Stack Summary**
Java 21, Spring Boot 3, Spring Cloud Gateway, Spring Security (OAuth2, JWT), Project Reactor, Redis, Resilience4j, Docker, Maven, Actuator

---

## 13. Cover-letter paragraph

> I recently developed a **production-grade API Gateway** using Spring Cloud Gateway and Java 21 to secure and centralize ingress for a trading microservices platform. The gateway enforces PS256 JWT authentication, rate limiting, and circuit breaker protections, while exposing operational metrics and maintaining resilience under load. By handling these cross-cutting concerns at the edge, downstream services remain simpler, more focused, and more reliable. I bring this same rigor and system-level thinking to \<Company/Team>.

---

## 14. Interview Talking Points

* Why Spring Cloud Gateway vs alternatives (NGINX, Kong, Envoy).
* Reactive stack benefits (Project Reactor vs servlet stack).
* JWT validation flow: PS256 enforcement, key management, scope mapping.
* Circuit breaker states (CLOSED, OPEN, HALF-OPEN) and fallback strategies.
* Route Predicates vs Filters in Spring Cloud Gateway.
* Declarative YAML vs programmatic Java route configuration.
* X-Request-Id filter → distributed tracing best practices.

---

## 15. Cheat Sheet (for yourself)

**Login & grab access token**

```bash
ACCESS=$(curl -sS -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"S3cureP@ss!"}' | jq -r .access_token)
```

**Place order with JWT**

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $ACCESS" \
  -H "Content-Type: application/json" \
  -d '{"userId":"<uuid>","ticker":"AAPL","side":"BUY","type":"LIMIT","timeInForce":"GTC","quantity":10,"price":150.5}'
```

**Check routes and metrics**

```bash
curl http://localhost:8080/actuator/gateway/routes | jq .
curl http://localhost:8080/actuator/metrics
```

**YAML route snippet (Orders)**

```yaml
- id: orders
  uri: ${ORDERS_BASE_URL:http://orders-service:8085}
  predicates:
    - Path=/api/orders/**
  filters:
    - StripPrefix=1
    - RewritePath=/api/orders/(?<p>.*),/orders/${p}
    - CircuitBreaker=name=ordersCB,fallbackUri=forward:/fallback
```

---

## 16. Routes & Usage (Expanded Reference)

### Gateway Base URL (local)

`http://localhost:8080`

---

### Quick map

| Area         | Gateway route                                | Method(s) |   Auth  | Rewrites to                              | Backing service                    |
| ------------ | -------------------------------------------- | --------: | :-----: | ---------------------------------------- | ---------------------------------- |
| Auth         | `/api/auth/login`                            |      POST |    No   | `/login`                                 | `authentication-service` (8082)    |
| Auth         | `/api/auth/refresh`                          |      POST |   No\*  | `/refresh`                               | `authentication-service`           |
| Users        | `/api/users/register`                        |      POST |    No   | `/register`                              | `user-registration-service` (8081) |
| Orders       | `/api/orders`                                |      POST | **Yes** | `/orders`                                | `orders-service` (8085)            |
| Orders       | `/api/orders/{id}`                           |       GET | **Yes** | `/orders/{id}`                           | `orders-service`                   |
| Orders       | `/api/orders/{id}/cancel`                    |      POST | **Yes** | `/orders/{id}/cancel`                    | `orders-service`                   |
| Transactions | `/api/transactions/**`                       |       GET | **Yes** | (no rewrite)                             | `transaction-processor` (8084)     |
| Portfolio    | `/api/portfolio/{userId}/positions`          |       GET | **Yes** | `/portfolio/{userId}/positions`          | `portfolio-service` (8087)         |
| Portfolio    | `/api/portfolio/{userId}/positions/{ticker}` |       GET | **Yes** | `/portfolio/{userId}/positions/{ticker}` | `portfolio-service`                |
| Portfolio    | `/api/portfolio/{userId}/summary`            |       GET | **Yes** | `/portfolio/{userId}/summary`            | `portfolio-service`                |
| Market Data  | `/api/market-data/candles/{ticker}`          |       GET | **Yes** | `/candles/{ticker}`                      | `market-data-consumer` (8083)      |
| Market Data  | `/api/market-data/candles/{ticker}/latest`   |       GET | **Yes** | `/candles/{ticker}/latest`               | `market-data-consumer`             |
| Ops          | `/actuator/health`                           |       GET |    No   | –                                        | gateway itself                     |
| Ops          | `/actuator/info`                             |       GET |    No   | –                                        | gateway itself                     |
| Ops          | `/actuator/gateway`                          |       GET |    No   | –                                        | gateway itself                     |
| Ops          | `/actuator/metrics`                          |       GET |    No   | –                                        | gateway itself                     |

\* The refresh endpoint expects a valid refresh token in the body; it does not use the bearer token.

---

### Authentication

Gateway is configured as a **JWT resource server** using your PS256 public key.

* **Login** → exchange username/password for tokens.
* **Refresh** → exchange refresh token for a new access token.
* All protected routes require: `Authorization: Bearer <access_token>`.

#### POST `/api/auth/login`

**Body**

```json
{
  "username": "alice",
  "password": "S3cureP@ss!"
}
```

**Response 200**

```json
{
  "access_token": "<JWT>",
  "refresh_token": "<JWT>",
  "token_type": "Bearer",
  "user_id": "<uuid>"
}
```

> Rate limited (Redis) to **10 req/s**, burst **20 per IP**.

---

#### POST `/api/auth/refresh`

**Body**

```json
{
  "refreshToken": "<JWT>"
}
```

**Response 200**

```json
{
  "access_token": "<new-JWT>",
  "refresh_token": "<same-JWT>"
}
```

---

### User Registration

#### POST `/api/users/register`

**Body**

```json
{
  "username": "alice",
  "password": "S3cureP@ss!"
}
```

**Responses**

* `200 OK`: User created.
* `409 Conflict`: Username already exists.

---

### Orders API

Gateway → `orders-service`

#### POST `/api/orders`

**Body (`PlaceOrderRequest`)**

```json
{
  "userId": "<uuid>",
  "ticker": "AAPL",
  "side": "BUY",
  "type": "LIMIT",
  "timeInForce": "GTC",
  "quantity": 10,
  "price": 150.50
}
```

**Response 202 (`OrderResponse`)**

```json
{
  "id": "<uuid>",
  "userId": "<uuid>",
  "ticker": "AAPL",
  "side": "BUY",
  "type": "LIMIT",
  "timeInForce": "GTC",
  "quantity": 10,
  "price": 150.5,
  "status": "NEW",
  "filledQuantity": 0,
  "remainingQuantity": 10,
  "lastFillPrice": null,
  "createdAt": "2025-09-05T00:27:51Z",
  "updatedAt": "2025-09-05T00:27:51Z"
}
```

#### GET `/api/orders/{id}`

**Response 200:**

```json
{
  "id": "<uuid>",
  "ticker": "AAPL",
  "side": "BUY",
  "quantity": 10,
  "status": "NEW",
  "filledQuantity": 0
}
```

#### POST `/api/orders/{id}/cancel`

**Response 200:**

```json
{
  "id": "<uuid>",
  "status": "CANCELED",
  "updatedAt": "2025-09-05T00:30:00Z"
}
```

---

### Transactions API

Gateway → `transaction-processor`

#### GET `/api/transactions`

**Query params:** `page`, `size`, `sort`

**Response 200**

```json
{
  "content": [],
  "pageable": { "pageNumber": 0, "pageSize": 1 },
  "totalPages": 0,
  "totalElements": 0,
  "last": true,
  "size": 1,
  "number": 0,
  "sort": { "sorted": true },
  "first": true,
  "empty": true
}
```

---

### Portfolio API

Gateway → `portfolio-service`

#### GET `/api/portfolio/{userId}/positions`

**Response 200**

```json
[
  {
    "ticker": "AAPL",
    "quantity": 10,
    "avgCost": 150.5,
    "realizedPnl": 0,
    "lastPrice": null,
    "unrealizedPnl": null,
    "updatedAt": "2025-09-05T00:00:00Z"
  }
]
```

#### GET `/api/portfolio/{userId}/positions/{ticker}`

**Response 200**

```json
{
  "ticker": "AAPL",
  "quantity": 10,
  "avgCost": 150.5,
  "realizedPnl": 0,
  "lastPrice": 152.0,
  "unrealizedPnl": 15.0,
  "updatedAt": "2025-09-05T00:10:00Z"
}
```

**404** if not found.

#### GET `/api/portfolio/{userId}/summary`

**Response 200**

```json
{
  "realizedPnl": 0,
  "unrealizedPnl": 15.0,
  "marketValue": 1520.0,
  "totalPnl": 15.0
}
```

---

### Market Data API

Gateway → `market-data-consumer`

#### GET `/api/market-data/candles/{ticker}`

**Query params**

* `interval` (default `1m`)
* `limit` (default `100`, max `1000`)

**Response 200**

```json
[
  {
    "ticker": "AAPL",
    "interval": "1m",
    "open": 150.0,
    "high": 152.0,
    "low": 149.5,
    "close": 151.0,
    "volume": 10000,
    "timestamp": "2025-09-05T00:01:00Z"
  }
]
```

#### GET `/api/market-data/candles/{ticker}/latest`

**Query params:** `interval` (default `1m`)

**Response 200**

```json
{
  "ticker": "AAPL",
  "interval": "1m",
  "open": 150.0,
  "high": 152.0,
  "low": 149.5,
  "close": 151.0,
  "volume": 10000,
  "timestamp": "2025-09-05T00:01:00Z"
}
```

**404** if no candle exists for `(ticker, interval)`.

---

### Curl Quick Start

```bash
# 1) register
curl -sS -X POST http://localhost:8080/api/users/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"S3cureP@ss!"}'

# 2) login
ACCESS=$(curl -sS -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"S3cureP@ss!"}' | jq -r .access_token)

# 3) place an order
curl -sS -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $ACCESS" -H 'Content-Type: application/json' \
  -d '{"userId":"<uuid>","ticker":"AAPL","side":"BUY","type":"LIMIT","timeInForce":"GTC","quantity":10,"price":150.5}' | jq .

# 4) fetch latest candle
curl -sS -H "Authorization: Bearer $ACCESS" \
  "http://localhost:8080/api/market-data/candles/AAPL/latest?interval=1m" | jq .
```

---

### Operational Notes

* **Security:** JWT resource server, PS256.
* **Headers:** `Cookie` removed by default.
* **CORS:** Open in dev, tighten in prod.
* **Rate limiting:** Login only, requires Redis.
* **Circuit breakers:** `ordersCB`, `txCB`, `portfolioCB`, `mdCB`.
* **Actuator:** `/actuator/health`, `/info`, `/gateway`, `/metrics`.

---

### Behind the Gateway (debugging)

* `orders-service`: `http://orders-service:8085/orders`
* `transaction-processor`: `http://transaction-processor:8084`
* `portfolio-service`: `http://portfolio-service:8087/portfolio`
* `market-data-consumer`: `http://market-data-consumer:8083/candles`
* `authentication-service`: `http://auth-service:8082`
* `user-registration-service`: `http://user-registration-service:8081`

---
