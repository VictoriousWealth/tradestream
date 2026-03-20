# Devlog

## 2026-01-24 17:07:33 +0000 — commit `a026f036bc7441698f71a5d2eaaa05e541faf1bb`

The first commit in this chunk adjusted [`CVREADME.md`](CVREADME.md) to clarify what role that document plays relative to the root repository docs. Before the change, the opening description said “This **root README** is the entry point for recruiters and engineers,” which blurred the distinction between the actual root [`README.md`](README.md) and the separate recruiter-oriented file. After the change, the wording became “This **recruiter-facing README** is the entry point for architecture, how to run, critical routes, and links into each service’s source of truth.”

That is a small textual edit, but it matters because it resolves a documentation identity problem. The file name `CVREADME.md` already implied a specialized audience; the previous introduction incorrectly described it as the root README. The edit brought the prose into line with the file’s actual purpose.

The same commit also extended the “Build & Ops” row in the technology table from:

- `Maven, Gradle, Spring Actuator`

to:

- `Maven, Gradle, Spring Actuator, GitHub Actions`

This addition indicates that CI had become important enough to include in recruiter-facing project positioning. Before the change, the build/ops line covered only local build tooling and health endpoints. After it, the line acknowledged automation in the toolchain.

## 2026-01-24 17:07:33 +0000 — commit `35ff5052693ba39e61c39ad2089709236b307713`

The next commit moved similar corrections into the main [`README.md`](README.md). Two distinct fixes happened here.

### Root README corrected the recruiter-doc reference

In the repo inventory table, the entry for the recruiter summary previously pointed to `cvreadme.md`. This commit changed that path to `CVREADME.md`. Before the change, the path casing did not match the actual file in the repository. After it, the reference became consistent and usable on case-sensitive filesystems.

### Root README began explicitly listing CI as implemented scope

The repo inventory gained a new row:

- `CI (GitHub Actions)` at `.github/workflows/ci.yml`
- status `✅`
- description `Docker Compose E2E checks for core flows`

The “CI/CD/Cloud” bullet in the technology/stack summary was also rewritten. Before the edit, it said the project was “local-first; cloud/IaC (Kubernetes/Terraform) are planned, not in-repo.” After the edit, it became:

- `GitHub Actions (E2E via Docker Compose); cloud/IaC (Kubernetes/Terraform) are planned, not in-repo`

This is a precise scope correction. Before the commit, CI was effectively omitted from the implemented-state narrative. After it, the README started distinguishing between:

- CI that already exists in-repo, and
- deployment/IaC work that remains future work.

That distinction reduced one of the recurring documentation problems in this repository: previously, tooling that already existed could still be described as purely planned or not emphasized at all.

## 2026-01-24 17:07:33 +0000 — commit `79574e4ea9bc8f1c03de316b24a46c91d33e1927`

This commit added a brand-new file, [`docs/api-design.md`](docs/api-design.md), and the contents are tightly implementation-oriented rather than aspirational.

The document introduced three main sections:

1. external gateway routes,
2. internal service endpoints,
3. Kafka/Redpanda event topics.

### External routes documented against the gateway as implemented

The gateway route table captured the externally visible API surface, including:

- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/users/register`
- order placement, lookup, and cancellation
- transactions reads
- portfolio reads
- market-data candle reads

Each route is annotated with:

- auth requirement,
- rewrite behavior,
- destination service and port.

This matters because it converts routing knowledge that had previously been scattered across service READMEs and gateway config into a single current-state reference. The notes section is also deliberately specific:

- login is IP rate-limited via Redis at `10 r/s` with burst `20`,
- `/api/auth/refresh` and `/api/users/register` are downstream-internal-only via `X-Internal-Caller: api-gateway`.

Those notes narrow the document to actual behavior rather than generic API conventions.

### Internal endpoints split by service

The internal route list then enumerated specific endpoints per service:

- Authentication Service: `/login`, `/refresh`
- User Registration Service: `/register`
- Orders Service: `/orders`, `/orders/{id}`, `/orders/{id}/cancel`
- Transaction Processor: user/ticker/since query endpoints
- Portfolio Service: positions and summary
- Market Data Consumer: candle query endpoints

Before this commit, there was no dedicated file in the evidence set that acted as the authoritative API map across services. After it, there was a document explicitly framing itself as “Current Implementation.”

### Event surface documented as Redpanda/Kafka topics

The final section listed:

- `order.placed.v1`
- `trade.executed.v1`
- `transaction.recorded.v1`

This made the event bus visible in the same document as the HTTP routes. It also reinforced the stack convergence seen elsewhere in the chunk: the docs were moving away from older “Kafka or RabbitMQ” language toward the actual Redpanda/Kafka setup.

## 2026-01-24 17:07:33 +0000 — commit `0df0b10bfe31d42467515d2e624998d943317fc0`

This commit added [`docs/future-enhancements.md`](docs/future-enhancements.md) as a dedicated roadmap document. The opening line is explicit that these items are not implemented in the current codebase, which is an important framing choice given earlier ambiguity in other docs between current and planned scope.

### Bot simulation roadmap defined in detail

The first major section laid out a “Bot Market Simulation (Full Set)” table. It grouped bot ideas across categories such as:

- retail,
- market maker,
- institutional,
- hedge,
- HFT,
- portfolio.

Each row specified:

- bot name,
- category,
- functionality,
- ML stack,
- ML type,
- mode,
- scheduler,
- guardrails.

This is much more specific than a typical “future AI trading bots” placeholder. It does not claim implementation, but it defines a proposed design space with operational controls such as cash reserve, auto-deleveraging, and rotation sell logic.

### Event-contract and platform roadmap became explicit

The file then added forward-looking sections for:

- Confluent Schema Registry and contract testing,
- Streamlit analytics console,
- observability stack,
- platform and infrastructure.

The platform section named several concrete candidates:

- Kubernetes + Terraform,
- Redpanda on EKS,
- MLflow,
- Airflow and Prefect,
- JWKS and key rotation,
- refresh-token rotation and revocation,
- per-user gateway rate limiting,
- WebSocket/SSE,
- market-data fan-out.

Before this commit, future work references in the repo were distributed and often brief. After it, roadmap scope had its own file with concrete categories and implementation direction.

## 2026-01-24 17:07:33 +0000 — commit `4731eac2461e344c181743e58a735d5b6efb0264`

The next change corrected inaccuracies in [`docs/order-placement-matching-trade-execution-diagram.md`](docs/order-placement-matching-trade-execution-diagram.md). These are important because they are not cosmetic; they narrow the sequence diagram toward actual implementation details.

### Gateway behavior in the sequence diagram was de-generalized

Two gateway-related lines changed:

- `participant G as API Gateway (JWT verify + rate limit)` became `participant G as API Gateway (JWT verify)`
- `G-->>C: 401 if invalid JWT / 429 if rate-limited` became `G-->>C: 401 if invalid JWT`

This removed the implication that the illustrated order-placement path is generally rate-limited. That aligns with the newer docs elsewhere in the chunk, which constrain rate limiting to login rather than all gateway traffic.

### Route path and event-bus naming were corrected

The client-to-gateway request changed from:

- `POST /orders (JWT)`

to:

- `POST /api/orders (JWT)`

The event bus participant changed from:

- `Event Bus (Kafka/RabbitMQ)`

to:

- `Event Bus (Redpanda/Kafka)`

These edits replace generic or stale abstractions with the actual external path and actual message-bus choice. Before the commit, the diagram mixed pre-gateway-inventory naming and optional broker wording. After it, the diagram matched the gateway path shape and the Redpanda/Kafka stack used elsewhere in the docs.

## 2026-01-24 17:07:33 +0000 — commit `7d73907041c728edd62ee8bf9bbc759b30acb7d0`

This commit made a larger correction pass on [`docs/trade-execution-market-data-&-portfolio-updates-+-order-status-diagram.md`](docs/trade-execution-market-data-&-portfolio-updates-+-order-status-diagram.md). The changes here are best read as a structural fix to the downstream event flow after trade execution.

### Market data component renamed to the implemented service

The participant:

- `Market Data Service`

was changed to:

- `Market Data Consumer`

and the Redis cache label changed from:

- `Redis Cache (quotes/candles)`

to:

- `Redis Cache (market latest)`

The cache action also changed from a broader, more speculative behavior:

- `Cache update (quotes/top-of-book/last trade) [TTL 1–5s]`
- optional `Publish QuoteUpdated / BarUpdated`

to a narrower and more concrete one:

- `Cache latest candle [TTL 10m]`

This is a strong example of before-to-after documentation hardening. Before the commit, the diagram described a wider real-time market-data subsystem with quote/top-of-book style behavior and optional outbound updates. After it, the document described a simpler and more grounded current implementation centered on latest-candle caching.

### Transaction Processor inserted into the post-trade path

The biggest architectural correction was the insertion of a `Transaction Processor` participant and a `Transactions DB (journal)`. Before the patch, the diagram showed Portfolio Service consuming `TradeExecuted` directly and appending transaction history itself. After the patch:

1. `TradeExecuted` is delivered to `Transaction Processor`.
2. `Transaction Processor` appends buyer and seller transaction history.
3. `Transaction Processor` publishes `TransactionRecorded`.
4. `Portfolio Service` consumes `TransactionRecorded` rather than `TradeExecuted`.

This is not just a naming improvement. It changes the documented ownership boundary of journaling and portfolio projection. Before, the diagram implied portfolio owned both positions and transaction history updates. After, transaction journaling moved into its own service, and portfolio became a consumer of `transaction.recorded.v1`.

### TradeExecuted payload note was corrected

The event note over the bus changed from a payload shape with:

- `orderId`, `userId`, `side`

to one with:

- `buyOrderId`, `sellOrderId`

and no per-event user-side semantics.

That matches the direction of the rest of the docs in this chunk, which now describe trade execution as a bilateral event later split into buyer/seller transaction records by the transaction processor.

### Client polling path corrected to gateway namespace

The note for client polling changed from:

- `GET /orders/{id} via Gateway`

to:

- `GET /api/orders/{id} via Gateway`

Again, the fix is small, but it is part of a consistent repo-wide effort in this chunk to normalize public paths around the actual gateway namespace.

## 2026-01-24 17:07:33 +0000 — commit `d3a2bb5ee8c421c10bddc158a0dec721f2305c39`

The largest single textual update in the chunk was the rewrite of [`docs/tradestream-prd.md`](docs/tradestream-prd.md). The overall theme was to replace stale architectural options and speculative deployment assumptions with the currently implemented stack.

### Core stack language was narrowed from generic options to actual choices

In the “Key upgrades in v2.0” section:

- `Kafka/RabbitMQ` became `Redpanda (Kafka API)`
- `Redis-backed API Gateway for caching and per-user rate-limiting` became `Redis-backed API Gateway for login rate limiting`
- `JWS + JWE JWT authentication` became `JWS (PS256) JWT authentication (no JWE)`

This is a direct before-to-after correction of technical scope. The previous text blended implemented features with broader possibilities. After the edit, the PRD explicitly dropped:

- RabbitMQ as an active message-bus option,
- general per-user gateway rate limiting,
- JWE as part of the JWT story.

### Technology table updated to reflect current runtime model

The technology overview table shifted in several places:

- `Stream Processing`: from `Kafka or RabbitMQ` to `Redpanda (Kafka API)`
- `Cache Layer`: from generic caching plus rate-limiting to `Rate limiting + market latest`
- `Authentication`: from `JWS & JWE` to `JWS (JWT signed, PS256)`
- `CI/CD`: purpose softened from build/test/deploy automation to `Build/test automation`
- `Deployment`: from `AWS Lightsail` to `Local Docker Compose (dev)`

These changes are collectively significant. Before the commit, the PRD still described a cloud-hosted MVP on Lightsail and a broader crypto/auth stack. After it, the document presented the system as local-first and implementation-grounded.

### MVP goals and deliverables were synchronized with actual services

The goals section now says:

- login rate limiting rather than generic gateway rate limiting,
- local Docker Compose runtime rather than Lightsail deployment.

The MVP deliverables list also changed:

- `Market Data Service` became `Market Data Consumer`
- `Transaction Processor` was added explicitly
- `Kafka/RabbitMQ integration` became `Redpanda (Kafka API) integration`

This is one of the strongest evidence points that the docs were catching up to an already expanded codebase. Before the commit, the PRD under-described the deployed service set. After it, Transaction Processor had a first-class place in the MVP list.

### Main sequence and architecture diagrams inside the PRD were corrected

The embedded Mermaid sequence and flowchart were updated to show:

- `POST /api/orders` instead of `POST /orders`
- `Market Data Consumer` instead of `Market Data Service`
- a `Transaction Processor` between `TradeExecuted` and portfolio projection
- `Event Bus (Redpanda/Kafka)` instead of a generic bus
- `TransactionRecorded` feeding Portfolio Service

The flowchart also changed the environment framing from:

- `AWS Lightsail VM (Docker Compose)`

to:

- `Local Docker Compose`

and updated Redis, Auth, Redpanda, and Portfolio labels accordingly.

### Event schema examples were made closer to current payloads

The `OrderPlaced` schema was corrected so that:

- `ticker` is no longer constrained in the table to `max 5 chars`,
- `price` is optional / `null` for market orders,
- `quantity` is `Decimal` instead of `Integer`,
- timestamp notes point to `createdAt`.

The `TradeExecuted` schema was also rewritten from a one-sided payload with `orderId`, `userId`, and `side` into a bilateral payload with:

- `buyOrderId`
- `sellOrderId`
- decimal quantity

This aligns the PRD with the diagram fixes made earlier in the chunk.

### Security mapping and constraints were narrowed

The assumptions/constraints section removed the single-Lightsail-instance assumption and replaced it with:

- local Docker Compose runtime,
- Redpanda as the Kafka-compatible broker,
- event communication via Redpanda only.

The API gateway rate-limit policy table was reduced from multiple user classes to a single implemented policy:

- `POST /api/auth/login` at `10 requests/sec`, burst `20`, IP-based via Redis.

Finally, the security mapping changed from:

- generic JWT signature validation plus HttpOnly refresh cookie wording

to:

- PS256 signature validation,
- internal-only refresh via `X-Internal-Caller`.

That is another example of the docs moving away from previously broader or outdated auth assumptions and toward the actual gateway/service contract.

## 2026-01-24 19:20:27 +0000 — commit `73d2844eea6458d624577dca2b1ed8601a5cd0ab`

The last commit returned to [`docs/future-enhancements.md`](docs/future-enhancements.md) and substantially deepened two previously short sections: the Streamlit console and the observability stack.

### Streamlit console moved from simple idea to product-shaped plan

Before this commit, the Streamlit section was only a short note:

- live dashboard for PnL, positions, signals, and order flow,
- reads from existing REST APIs and optional Kafka topics,
- built with Streamlit, Plotly, Pandas.

After the commit, that section expanded into a much more product-like specification.

It added:

- a concrete UI inspiration reference (`trade-tapestry-view.lovable.app`),
- a multi-tab structure with named areas:
  - Overview,
  - Market Data,
  - Bot Performance,
  - Portfolio & Risk,
  - Order Flow,
  - ML Monitoring,
- explicit data sources such as:
  - `/api/market-data/candles/*`
  - `/api/portfolio/*`
  - `/api/transactions/*`
  - optional `signal.generated.v1`

Before this commit, the console was a generic dashboard idea. After it, it was described as a trading-ops desk style interface with concrete inputs and panels.

### Observability section gained “final product”, “data flow”, and phased rollout

The previous observability section was only three bullets:

- Prometheus + Grafana for metrics,
- Loki + Grafana for logs,
- OpenTelemetry + Jaeger for tracing.

After `73d2844eea6458d624577dca2b1ed8601a5cd0ab`, the section was reorganized into three layers.

First, “Final product (what you see)” described user-facing operational outputs:

- Grafana dashboards for latency, error rates, Kafka lag, orders/sec, and PnL pipeline health,
- Jaeger trace UI,
- Loki log explorer searchable by `X-Request-Id` or trace ID,
- Slack/email alerts.

Second, “How it works (data flow)” described a pipeline:

1. Java services expose `/actuator/prometheus`
2. Prometheus scrapes metrics
3. Grafana visualizes
4. OpenTelemetry collects traces
5. Jaeger stores/visualizes traces
6. Logs stream to Loki

Third, “Path from idea to product (phased)” defined an implementation order:

1. metrics first,
2. logs next,
3. tracing,
4. alerting.

This changed the section from a technology wish list into a staged delivery plan with both operator-facing outcomes and implementation sequencing.

## Documentation evolution across the chunk

The chronology across these eight commits is consistent and evidence-backed:

1. clarify the audience and role of [`CVREADME.md`](CVREADME.md),
2. correct root README references and acknowledge implemented CI,
3. add [`docs/api-design.md`](docs/api-design.md) as a current-state route/event reference,
4. add [`docs/future-enhancements.md`](docs/future-enhancements.md) as a dedicated roadmap file,
5. fix one sequence diagram so it no longer implies RabbitMQ, generic rate limiting, or non-gateway paths,
6. fix a second sequence diagram so transaction processing and portfolio projection reflect the actual event chain,
7. realign the PRD with the implemented system by removing stale references to RabbitMQ, JWE, Lightsail-first deployment, and generalized gateway rate limiting,
8. deepen the future-enhancements doc from roadmap bullets into more product-shaped plans.

The evidence does not show any runtime code changes in this chunk. The work is entirely documentary, but it is not superficial: the main effect is to reduce drift between the written system description and the implemented platform by correcting stack choice, auth model, route namespaces, event ownership, and the boundary between current functionality and future work.
