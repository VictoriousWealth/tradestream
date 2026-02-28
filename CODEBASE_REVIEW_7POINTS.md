# TradeStream Codebase Review (7-Point Interview Lens)

Last updated: 2026-02-27

## Scope and method

This review combines:
- current code and config state
- git history signals (high-signal commits and changed files)
- interview framing for Palantir SWE/FDSE style discussions

Note: many commits are labeled `Deploy`, so historical intent is inferred mainly from the few descriptive commits and the files they touched.

## Git history signals used to refine this review

- `5e2cf43` (2025-07-05): Initial scaffold with microservice structure, docs, docker compose, CI file.
- `728119b` (2025-07-06): README reworked to align architecture direction.
- `1f072a8` (2026-01-04): CI workflow added for E2E.
- `a1e17f8` (2026-01-04): CI wait-for-readiness added before E2E.
- `c2a6eb0` (2026-01-04): CI JWT test key generation added.
- `12c8ee4` (2026-01-04): ticker uniqueness fix in cancel flow test.
- `0df0b10` (2026-01-24): future enhancements doc added (explicit roadmap mindset).
- `7d73907` (2026-01-24): event-bus execution diagram clarified.
- `b8e193c` (2026-02-11): observability stack guide added.

Interpretation: the project matured from scaffold -> E2E hardening -> architecture/doc clarity -> observability planning.

## 1) Architecture decisions (and why they are defensible)

- Microservices split is explicit in topology and Compose service layout.
  - Gateway, auth, registration, orders, matching, tx processor, portfolio, market-data are independently deployable.
- Kafka/Redpanda is used for cross-service async event flow in trade pipeline (`order.placed.v1`, `trade.executed.v1`, `transaction.recorded.v1`).
- Redis is used where latency or control-plane behavior benefits from it:
  - login rate limiting at edge
  - market-data "latest" read acceleration and eviction
- Docker Compose is used for full local integration and CI reproducibility (sensible for portfolio MVP velocity).
- JWT (PS256) at gateway centralizes auth verification and keeps downstream services simpler.

Code anchors:
- `docker-compose.yml`
- `api-gateway/src/main/resources/application.yml`
- `api-gateway/src/main/java/com/tradestream/gateway/security/SecurityConfig.java`
- `api-gateway/src/main/java/com/tradestream/gateway/security/JwtDecoderConfig.java`

## 2) Trade-offs made (the useful interview content)

- Chose Compose over Kubernetes/Terraform for faster iteration and demonstrable E2E reliability.
  - You can show intentional scope control, not avoidance.
- Chose at-least-once Kafka + idempotency ledgers over exactly-once stack complexity.
  - Simpler operations, more application-level rigor.
- Chose pessimistic locking in hot paths instead of optimistic retries.
  - Better under contention, less retry storm risk.
- Chose edge auth enforcement and internal caller headers to keep downstream simpler.
  - Fast and practical, but header-based trust is weaker than mTLS/service identity.

Docs/history anchors:
- `README.md` (Kubernetes/Terraform marked planned)
- `docs/future-enhancements.md` (roadmap posture)
- `services/orders-service/README.md`
- `services/portfolio-service/README.md`

## 3) Bugs/problems that forced deeper thinking

Strong concrete examples:

- Idempotency fallback mismatch in matching consumers:
  - code comment says fallback should be business identity
  - implementation currently falls back to topic+partition+offset
  - this can fail dedupe across replay/re-publish scenarios
  - Files:
    - `services/matching-engine/src/main/java/com/tradestream/matching_engine/stream/OrderPlacedConsumer.java`
    - `services/matching-engine/src/main/java/com/tradestream/matching_engine/stream/OrderCancelledConsumer.java`

- Potential dual-write inconsistency:
  - DB state and Kafka publish are not protected by outbox/transactional eventing
  - if publish fails after commit, downstream misses a state transition
  - Files:
    - `services/orders-service/src/main/java/com/tradestream/orders_service/service/OrderService.java`
    - `services/transaction-processor/src/main/java/com/tradestream/transaction_processor/service/TransactionService.java`

- Concurrency hazard surface in matching:
  - shared `books` map + non-thread-safe `PriorityQueue` in `OrderBook`
  - assumes effective serialization by partitioning/consumer behavior
  - if listener concurrency rises or ordering assumptions break, race risk appears
  - Files:
    - `services/matching-engine/src/main/java/com/tradestream/matching_engine/matching/MatchingService.java`
    - `services/matching-engine/src/main/java/com/tradestream/matching_engine/matching/OrderBook.java`

## 4) Boundaries between services (integration thinking)

- Gateway owns ingress concerns:
  - JWT verification
  - route rewrites
  - rate limiting
  - fallback behavior
- Domain services own writes in their own DBs (clear bounded storage).
- Async propagation:
  - orders -> matching -> transaction -> portfolio/market projections
- Failure propagation behavior:
  - downstream outages produce degraded fallback responses at gateway
  - consumer-side poison events are pushed to DLT with backoff

Key files:
- `api-gateway/src/main/resources/application.yml`
- `api-gateway/src/main/java/com/tradestream/gateway/web/FallbackController.java`
- `services/*/src/main/java/**/KafkaDlqConfig.java`

## 5) What to redesign with 2 more weeks

Priority redesigns:

1. Add transactional outbox for producer paths (orders + tx processor).
2. Standardize event identity contract:
   - always include `eventId`
   - use business-stable fallback keys
   - remove offset-derived synthetic IDs except as emergency telemetry.
3. Harden service-to-service trust:
   - move from static internal header to mTLS or signed internal tokens.
4. Formalize matching concurrency model:
   - enforce per-ticker single-threading by design and test it.
5. Add production-grade observability implementation, not just guide docs:
   - trace propagation, lag dashboards, DLT alerting.

## 6) Documentation quality and evidence trail

Strong point in this repo:
- docs are unusually rich and interview-usable
- architecture and future enhancements are explicit
- observability plan exists with concrete goals and checks

History evidence:
- `7d73907`: event bus diagram clarity
- `0df0b10`: future enhancements added
- `b8e193c`: observability guide added

## 7) Moment you simplified (and why this is good)

Best narrative:
- You intentionally deferred Terraform/Kubernetes and focused on a reliable Compose-based end-to-end system with CI E2E checks first.
- This shows prioritization and delivery discipline:
  - establish correctness and integration confidence
  - then scale deployment complexity later

Support:
- `README.md` (Kubernetes/Terraform marked planned)
- CI workflow hardening commits on 2026-01-04

## Interview-ready 5 bullets

1. Architecture decision:
   - "I used Kafka between core services to decouple availability and support asynchronous processing under bursty workloads."
2. Trade-off:
   - "I chose Docker Compose over Kubernetes/Terraform initially to prioritize a stable, testable MVP and E2E confidence."
3. Mistake:
   - "I underestimated idempotency-key design in consumers; offset-based fallback is insufficient for replay scenarios."
4. Improvement:
   - "I would add an outbox pattern to eliminate DB/Kafka dual-write inconsistency risk."
5. Proud point:
   - "I built strong idempotency and locking patterns across services to keep behavior deterministic in at-least-once event delivery."

## 45-minute review plan (tonight)

15 min:
- Re-read root `README.md`
- Re-read `docs/tradestream-prd.md`

15 min:
- Open and reason through:
  - `docker-compose.yml`
  - gateway security/routing config
  - one Kafka consumer and one producer path

15 min:
- Memorize your five bullets above and one concrete file reference per bullet.

