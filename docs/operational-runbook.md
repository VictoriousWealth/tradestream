# Operational Runbook (Current Local Runtime)

Last reviewed against code/config on 2026-05-10.

This runbook is written for the repo’s current operating model: local Docker Compose with Redpanda, Postgres, Redis, and the seven application services.

---

## 1) Runtime topology you should assume

Public entry:
- `api-gateway` on `localhost:8080`

Private application services:
- `user-registration-service` on `8081`
- `auth-service` on `8082`
- `market-data-consumer` on `8083`
- `transaction-processor` on `8084`
- `orders-service` on `8085`
- `matching-engine` on `8086`
- `portfolio-service` on `8087`

Core dependencies:
- Redpanda on `9092`
- Redis on `6379`
- Postgres instances per service group

---

## 2) First checks when something looks wrong

1. Confirm the stack is up:
   - `docker compose ps`
2. Check gateway health:
   - `curl -s http://localhost:8080/actuator/health`
3. Check the downstream service health endpoints directly if a route is failing.
4. Confirm Redpanda and Redis are healthy in Compose.
5. Confirm the expected JWT keys exist under `secrets/`.

If auth is failing everywhere, check keys first.
If only one route family is failing, check the owning downstream service next.

---

## 3) Health endpoints

Gateway:
- `http://localhost:8080/actuator/health`

Private services:
- `http://localhost:8081/actuator/health`
- `http://localhost:8082/actuator/health`
- `http://localhost:8083/actuator/health`
- `http://localhost:8084/actuator/health`
- `http://localhost:8085/actuator/health`
- `http://localhost:8086/actuator/health`
- `http://localhost:8087/actuator/health`

Useful note:
- Compose health checks already use these routes, so a container that never becomes healthy usually points to config, dependency, migration, or startup failure.

---

## 4) Kafka topics and consumer groups that matter operationally

Topics:
- `order.placed.v1`
- `order.cancelled.v1`
- `trade.executed.v1`
- `transaction.recorded.v1`

Expected DLTs in current reviewed services:
- `order.placed.v1.DLT`
- `order.cancelled.v1.DLT`
- `trade.executed.v1.DLT`
- `transaction.recorded.v1.DLT`

Consumer groups commonly used in Compose/default config:
- `matching-engine`
- `orders-exec-consumer`
- `md-consumer`
- `txproc-journal`
- `portfolio-svc`

When debugging lag:
- `matching-engine` lag on `order.placed.v1` or `order.cancelled.v1` affects matching correctness.
- `txproc-journal` lag on `trade.executed.v1` delays transaction journaling and therefore portfolio updates.
- `portfolio-svc` lag on `transaction.recorded.v1` delays positions/PnL projection.

---

## 5) Symptom -> likely cause -> where to look

## `401` from gateway on protected routes

Likely causes:
- Access token missing.
- Access token expired.
- Public key mismatch between gateway and auth service.

Check:
- `secrets/jwt_public.pem`
- auth login still works
- gateway logs for JWT validation failures

## `403` on registration or refresh

Likely causes:
- Missing `X-Internal-Caller: api-gateway` header on the downstream hop.

Check:
- gateway route config for `/api/users/register` and `/api/auth/refresh`
- `authentication-service` and `user-registration-service` interceptor wiring

Important nuance:
- These routes are publicly permitted at the gateway layer, but still blocked downstream without the header.

## Login returns `429`

Likely cause:
- Gateway Redis rate limiter is working as designed.

Check:
- Redis health
- whether a test script is hammering `/api/auth/login`

## Orders are accepted but no trades occur

Likely causes:
- `matching-engine` unhealthy or lagging.
- Orders do not cross by price/time-in-force semantics.
- `order.placed.v1` not being consumed.

Check:
- `orders-service` health
- `matching-engine` health
- `matching-engine` consumer lag on `order.placed.v1`
- emitted order payloads and ticker keys

## Orders fill, but portfolio does not update

Likely causes:
- `transaction-processor` not consuming `trade.executed.v1`
- HTTP lookup from `transaction-processor` to `orders-service` failing
- `portfolio-service` lagging on `transaction.recorded.v1`

Check:
- `transaction-processor` health and logs
- `orders-service /orders/{id}` works for referenced order IDs
- `portfolio-svc` lag

## Market data stays stale

Likely causes:
- No new `trade.executed.v1` events for that ticker
- market-data consumer lag
- Redis latest cache not being evicted because no new upsert happened

Check:
- `trade.executed.v1` contents
- `market-data-consumer` health/logs
- query the candle endpoint directly on `8083`

## Cancel issued, but later BUY still matches

Likely causes:
- Matching engine had not yet consumed the `order.cancelled.v1` record when the crossing order arrived.

Check:
- `order.cancelled.v1` record exists
- `matching-engine` group lag on `order.cancelled.v1`
- use `manual_cancel_test.sh` or the cancel scenario in `e2e_scenarios.sh`

## DLT starts growing

Likely causes:
- JSON payload shape drift
- invalid enum values such as bad `side`
- runtime exceptions in consumer logic

Check:
- consume `<topic>.DLT`
- inspect the original payload
- compare producer DTO vs consumer DTO expectations

---

## 6) Quick commands worth keeping handy

Check compose state:
```bash
docker compose ps
```

Check gateway health:
```bash
curl -s http://localhost:8080/actuator/health
```

Check a downstream health endpoint:
```bash
curl -s http://localhost:8085/actuator/health
```

Inspect a consumer group:
```bash
docker compose exec -T redpanda rpk group describe matching-engine
```

Consume a DLT:
```bash
docker compose exec -T redpanda rpk topic consume trade.executed.v1.DLT --offset beginning
```

Watch the next trade:
```bash
docker compose exec -T redpanda rpk topic consume trade.executed.v1 --offset end -n 1
```

---

## 7) Service-specific operational notes

## Gateway

Operationally important behaviors:
- Removes `Cookie` header.
- Injects `X-Internal-Caller` for refresh and registration routes.
- Applies circuit breakers for orders, transactions, portfolio, and market-data routes.
- Applies Redis IP rate limit only on login route.

What to remember:
- If a downstream service is down, the gateway may return fallback JSON instead of a transport error.

## Orders Service

Operationally important behaviors:
- Accepts orders and emits order events.
- Applies fills from `trade.executed.v1`.
- Only allows cancel while status is `NEW`.

What to remember:
- It is both a command service and a fill-projection consumer.

## Matching Engine

Operationally important behaviors:
- Maintains in-memory books plus persisted resting orders.
- Uses manual Kafka ack and DLT wiring.

What to remember:
- Restart and warm-load behavior matters for correctness after downtime.

## Transaction Processor

Operationally important behaviors:
- Resolves order owner user IDs over HTTP from `orders-service`.
- Produces two `transaction.recorded.v1` events per trade.

What to remember:
- If `orders-service` is unhealthy, transaction journaling can fail even if Kafka is healthy.

## Portfolio Service

Operationally important behaviors:
- Consumes only `transaction.recorded.v1`.
- Projects long-only positions and realized PnL.

What to remember:
- It is not a mark-to-market engine today.

## Market Data Consumer

Operationally important behaviors:
- Consumes `trade.executed.v1`.
- Upserts candles for fixed supported intervals.
- Evicts Redis latest-cache keys on each upsert.

What to remember:
- No new trades means “latest” may remain unchanged by design.

---

## 8) Known operational caveats

- `gateway_smoke.sh` contains a market-data path mismatch and should not be treated as authoritative for that route.
- `e2e_portfolio_service.sh` mixes current behavior with future-looking assumptions.
- Some correctness issues, such as DB/Kafka dual-write gaps, are architectural and will not be fixed by restarts alone.
