# Known Risks and Non-Goals (Current Codebase)

Last reviewed against code/config on 2026-05-10.

This document is intentionally narrow: it only lists risks and non-goals that are directly visible in the current implementation. If something is not confirmed in code, it is omitted rather than guessed.

---

## 1) Security and identity risks

### Gateway auth does not bind request `userId` to JWT subject

What exists now:
- The API Gateway validates JWT signatures and requires authentication for non-whitelisted routes.
- The Orders API still accepts `userId` in the request body.
- `orders-service` persists whatever `userId` arrives in `PlaceOrderRequest`.

Why this matters:
- A client with any valid token can submit an order for a different `userId` if the caller knows or guesses another UUID.
- This is a trust-boundary gap, not just a missing convenience feature.

Current scope:
- There is no code in the gateway or orders service that rewrites `userId` from the JWT subject or rejects mismatches.

### Internal service trust is header-based, not identity-based

What exists now:
- `/refresh` in `authentication-service` is protected by a Spring MVC interceptor that requires `X-Internal-Caller: api-gateway`.
- `/register` in `user-registration-service` uses the same pattern.
- The gateway adds that header for the corresponding routes.

Why this matters:
- Any caller that can reach those services on the private network and set that header can impersonate the gateway.
- This is acceptable for a local Compose portfolio environment, but it is not a strong production trust model.

Current scope:
- No mTLS.
- No signed internal tokens.
- No service identity system.

### Refresh tokens are long-lived and not revocable

What exists now:
- Refresh tokens are PS256-signed JWTs with a 30-day expiry.
- They are parsed and re-used to mint new access tokens.

What does not exist:
- Refresh token rotation.
- Revocation list / denylist.
- Server-side refresh-session persistence.

Why this matters:
- A stolen refresh token remains usable until expiry unless keys are rotated.

### CORS is fully open in the gateway

What exists now:
- Gateway `globalcors` allows `allowedOrigins: "*"` with all methods and headers.
- `allowCredentials` is false.

Why this matters:
- This is acceptable for dev convenience, but it is intentionally looser than a production policy.

### Security scopes exist in tokens but are not enforced by route policy

What exists now:
- Auth service issues access and refresh tokens with `scopes`.
- Gateway maps scopes into `GrantedAuthority` values.

What does not exist:
- Route-level authorization rules based on scopes or roles.

Why this matters:
- In practice, the system currently distinguishes between `authenticated` and `unauthenticated`, not between permission levels.

---

## 2) Eventing and consistency risks

### No transactional outbox between DB writes and Kafka publishes

Confirmed cases:
- `orders-service` saves an order, then publishes `order.placed.v1`.
- `orders-service` saves a cancel state change, then publishes `order.cancelled.v1`.
- `transaction-processor` saves buyer/seller ledger rows and processed-message state, then publishes two `transaction.recorded.v1` events.

Why this matters:
- A process crash or broker failure after the DB commit but before the Kafka send can leave local state committed without the downstream event.

What this means operationally:
- Reconciliation would currently require manual investigation and replay logic.

### Event identity is not uniform across topics

What exists now:
- `transaction.recorded.v1` payloads include `eventId`.
- `order.placed.v1`, `order.cancelled.v1`, and `trade.executed.v1` do not have a uniformly enforced `eventId`.
- Matching and portfolio consumers fall back to synthetic IDs derived from `topic|partition|offset` when necessary.

Why this matters:
- Offset-derived IDs are stable for a given record in Kafka, but they are not business-stable across republish or replay-to-new-topic scenarios.
- This weakens true replay-safe idempotency.

### Transaction Processor depends synchronously on Orders Service during trade journaling

What exists now:
- For every `trade.executed.v1`, `transaction-processor` resolves `buyOrderId` and `sellOrderId` via HTTP calls to `orders-service`.

Why this matters:
- If `orders-service` is unavailable or returns incomplete data, transaction journaling cannot complete even though the trade event already exists.

Operational consequence:
- This is a coupling point inside an otherwise event-driven path.

### Matching engine correctness depends on concurrency assumptions

What exists now:
- Matching uses one in-memory `OrderBook` per ticker.
- `OrderBook` uses plain `PriorityQueue`.
- `MatchingService` stores books in a `ConcurrentHashMap`, but the queues themselves are not thread-safe.
- Kafka listener ack mode is manual, and the intended design appears to rely on per-partition ordering keyed by ticker.

Why this matters:
- If listener concurrency changes, partitioning assumptions break, or the same ticker is processed concurrently, the book can race.

Current scope:
- This is acceptable for the current single-consumer-group, keyed-by-ticker design.
- It is not yet a formally enforced concurrency model.

---

## 3) Domain and product non-goals

### Portfolio projection is long-only

What exists now:
- `PortfolioProjector` clamps SELL quantity to the current long quantity.
- If a user sells more than they hold, only the currently held long quantity is realized/projected.
- The resulting position does not go negative.

What this means:
- Short positions are not modeled in the current portfolio projection.

### Portfolio summary does not compute market value or unrealized PnL

What exists now:
- `/portfolio/{userId}/summary` returns `realizedPnl`.
- `unrealizedPnl` and `marketValue` are returned as `null`.
- Position responses also return `lastPrice` and `unrealizedPnl` as `null`.

What this means:
- The service is currently a realized-PnL and quantity projection, not a full mark-to-market portfolio engine.

### Market data intervals are fixed

Confirmed supported intervals:
- `1m`
- `5m`
- `1h`
- `1d`

What this means:
- There is no general interval engine exposed in the current implementation.

### The runtime target is local Docker Compose

What exists now:
- Compose is the primary supported runtime.
- Health checks and scripts assume Compose networking and container names/service DNS.

What does not exist:
- Kubernetes manifests.
- Terraform.
- Cloud secret management.
- Multi-environment deployment automation.

### This repo does not process real money or broker integrations

What exists now:
- A simulated trade processing stack with matching, journaling, portfolio projection, and candles.

What does not exist:
- Exchange connectivity.
- Brokerage APIs.
- Settlement flows.
- Compliance, reporting, or custody integrations.

---

## 4) Testing and observability limitations

### Tests are strong at scenario coverage, weaker at invariant proof

What exists now:
- Helpful end-to-end scripts cover partial fills, IOC/FOK, cancel flow, duplicate trade replay, recovery after restart, and DLT behavior.

What does not exist:
- A formal contract-test layer for event schemas.
- A documented load/stress test suite.
- A clearly automated reconciliation test for DB/Kafka dual-write failure windows.

### Observability is still mostly Actuator-level

What exists now:
- Health endpoints and some metrics exposure.
- Logging.
- Documented observability plan.

What does not exist:
- Checked-in Prometheus/Grafana/Loki/Jaeger stack wiring in the root runtime.
- Alert rules.
- Trace propagation verification across the full pipeline.

---

## 5) Script and documentation drift already visible

These are not architecture risks, but they are practical review notes worth keeping explicit.

### `gateway_smoke.sh` uses a market-data path that does not match the gateway route map

Current gateway route:
- `/api/market-data/candles/{ticker}/latest`

Path used in the script:
- `/api/market-data/$TICKER/latest`

Expected effect:
- That step will not hit the configured gateway market-data route as written.

### `e2e_portfolio_service.sh` assumes behaviors the current portfolio service does not implement

Examples:
- It comments on short-position expectations, but the projector is long-only.
- It tries to create a synthetic `trade.executed.v1` as a price mark, but portfolio-service does not consume `trade.executed.v1`; it consumes `transaction.recorded.v1`.
- That synthetic trade can also create noise for `transaction-processor`, because the random order IDs will not resolve in `orders-service`.

### `manual_cancel_test.sh` is more environment-sensitive than the other scripts

What exists now:
- It defaults to a specific network name and container name pattern.

Why this matters:
- It is more likely to break if the Compose project name changes.

---

## 6) Deliberate non-goals for the current stage

These are reasonable omissions for the repo’s current maturity and should be described as intentional scope limits, not accidents.

- No exactly-once Kafka semantics claim.
- No production-grade internal service identity system.
- No cloud deployment/IaC in the repo.
- No short-selling or derivatives model.
- No live price marking in portfolio summary.
- No refresh-token rotation or revocation.
- No event-schema registry/compatibility gate yet.
- No full observability stack wired into the default local runtime.
