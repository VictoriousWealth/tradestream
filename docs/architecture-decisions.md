# Architecture Decisions (Current State)

Last reviewed against code/config on 2026-05-10.

This is not a generic ADR collection. These entries capture the concrete architectural choices visible in the current repo and the trade-offs they impose.

---

## AD-001: Use a gateway as the only public HTTP entry point

Status:
- Implemented

What the code does:
- `api-gateway` is the only service exposed on host port `8080`.
- Downstream services are on the private Compose network.
- Gateway handles JWT validation, route rewriting, login rate limiting, and circuit-breaker fallbacks.

Why this was chosen:
- Centralizes auth and ingress behavior.
- Keeps downstream services simpler.
- Makes local demos and recruiter review easier because there is one entry point.

Consequences:
- Good: simpler downstream services, clear trust boundary, unified routing.
- Bad: gateway becomes a policy bottleneck; downstream auth context is not strongly propagated beyond “request had a valid token”.

---

## AD-002: Use PS256-signed JWTs at the edge

Status:
- Implemented

What the code does:
- Auth service reads RSA private/public keys from mounted PEM files.
- Auth service issues access and refresh tokens with PS256.
- Gateway validates signatures with the public key.

Why this was chosen:
- Asymmetric signing keeps verification separate from signing.
- Stronger and more realistic than unsigned or symmetric demo tokens.

Consequences:
- Good: realistic token issuance and edge validation.
- Bad: refresh handling still depends on header-gated internal routing, and there is no rotation/revocation model yet.

---

## AD-003: Keep write path services separate and connect them with Kafka/Redpanda

Status:
- Implemented

What the code does:
- Orders emit `order.placed.v1` and `order.cancelled.v1`.
- Matching emits `trade.executed.v1`.
- Transaction processor emits `transaction.recorded.v1`.

Why this was chosen:
- The trading pipeline is naturally asynchronous and state transitions are meaningful events.
- It also creates a better engineering story than a monolithic CRUD flow.

Consequences:
- Good: decoupled services, replay-friendly event flow, clear domain stages.
- Bad: idempotency, DLT handling, schema evolution, and dual-write concerns move into application logic.

---

## AD-004: Use per-service PostgreSQL storage

Status:
- Implemented

What the code does:
- Auth/user-registration share `authdb`.
- Orders, matching, market data, transaction processor, and portfolio each use separate Postgres databases.
- Flyway migrations are enabled per service.

Why this was chosen:
- Keeps bounded data ownership clear.
- Avoids one shared database becoming the de facto integration layer.

Consequences:
- Good: clear service ownership and local schema evolution.
- Bad: joining state across services requires APIs or event projections instead of direct SQL.

---

## AD-005: Accept at-least-once delivery and enforce idempotency in consumers

Status:
- Implemented

What the code does:
- Orders service dedupes fills using a fill-ingest ledger.
- Market data dedupes trades in `ingested_trades`.
- Transaction processor dedupes by `(topic, tradeId)`.
- Portfolio dedupes by `(topic, messageId)`.
- Matching dedupes via `processed_messages`.

Why this was chosen:
- Considerably simpler than building around Kafka transactions/exactly-once claims.
- More transparent for interview discussion because correctness logic is explicit in application code.

Consequences:
- Good: pragmatic, observable, portable across consumers.
- Bad: message identity must be carefully designed, and the current event contracts are not yet fully uniform.

---

## AD-006: Match in memory, persist resting orders in Postgres

Status:
- Implemented

What the code does:
- Matching engine maintains one in-memory `OrderBook` per ticker.
- Resting orders are also persisted in the matching database.
- On restart, active orders can be reloaded into memory.

Why this was chosen:
- Fast matching behavior without requiring a specialized external engine.
- Persistence gives some recovery capability.

Consequences:
- Good: simple, fast, and explainable.
- Bad: correctness depends on partitioning and concurrency assumptions; the in-memory book is not a distributed matching system.

---

## AD-007: Key trading events by ticker

Status:
- Implemented for `order.placed.v1`, `order.cancelled.v1`, and `trade.executed.v1`

What the code does:
- Orders and trades are produced with `ticker` as the Kafka key.

Why this was chosen:
- Preserves per-symbol ordering within a partition.
- Supports the intended single-threaded-per-ticker matching story.

Consequences:
- Good: intuitive and aligned with market microstructure reasoning.
- Bad: “by ticker” is a convention, not yet a fully hardened concurrency contract.

---

## AD-008: Use Redis only where latency/control-plane value is obvious

Status:
- Implemented

Current Redis uses:
- Gateway login rate limiting.
- Market-data “latest candle” cache.

Why this was chosen:
- Avoids adding Redis everywhere just because it exists in the stack.
- Keeps cache/control-plane usage concrete and explainable.

Consequences:
- Good: focused use of infrastructure.
- Bad: portfolio caching is not meaningfully implemented yet even though Redis settings exist there.

---

## AD-009: Keep the runtime target as Docker Compose

Status:
- Implemented

What the repo does:
- Compose defines the default runtime, health checks, secrets mount pattern, and local networking model.
- CI runs end-to-end validation through Compose.

Why this was chosen:
- Faster iteration and easier verification for a portfolio project than Kubernetes/Terraform at this stage.

Consequences:
- Good: reliable local bring-up and concrete E2E validation.
- Bad: production deployment concerns are intentionally deferred.

---

## AD-010: Use HTTP lookup from transaction processor to orders service for order ownership

Status:
- Implemented

What the code does:
- When a trade is consumed, transaction processor calls `orders-service /orders/{id}` twice to resolve buyer and seller `userId`.

Why this was chosen:
- Avoids duplicating user ownership in the trade event.
- Keeps `trade.executed.v1` relatively compact.

Consequences:
- Good: fewer fields in trade events; source of truth stays in orders service.
- Bad: transaction journaling now depends on orders-service availability and data correctness.

---

## AD-011: Keep internal sensitive routes “open” in Spring Security but gate them with interceptors

Status:
- Implemented

Routes:
- `authentication-service /refresh`
- `user-registration-service /register`

Why this was chosen:
- Straightforward to implement in local/private-network conditions.
- Makes the gateway responsible for the trusted hop.

Consequences:
- Good: simple and effective for a Compose demo.
- Bad: not a strong long-term internal auth model.

---

## AD-012: Keep portfolio projection narrow and deterministic

Status:
- Implemented

What the code does:
- Projects quantity, weighted average cost, and realized PnL from `transaction.recorded.v1`.
- Does not compute live marks, market value, or short positions.

Why this was chosen:
- Allows a deterministic read model with clean event inputs.
- Avoids pretending the system already has a full pricing/risk engine.

Consequences:
- Good: stable and explainable projection logic.
- Bad: summary data is intentionally incomplete for real portfolio analytics.
