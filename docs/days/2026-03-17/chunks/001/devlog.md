# Devlog

## 2026-03-17 04:09:48 +0000 — commit `c97870a`

Commit `c97870a974d36be0ce099db5d5676bbb6c620e1d` is the entry point for this chunk and updates `README.md` before the referenced documents themselves are added in later commits. The patch inserts six new bullets into the existing `Documentation map` section:

* `docs/known-risks-and-non-goals.md`
* `docs/event-contracts.md`
* `docs/security-model.md`
* `docs/test-strategy.md`
* `docs/operational-runbook.md`
* `docs/architecture-decisions.md`

Before this commit, the root README already pointed readers to the PRD, API design, future enhancements, observability guide, and execution diagrams, but it did not surface documents for current limitations, event-contract detail, security boundaries, test coverage reality, local operations, or architecture-decision capture. After `c97870a`, the README becomes a forward reference map for a broader “current state” documentation layer. That matters because the repo’s documentation structure changes from “overview + roadmap + observability plan” into “overview + roadmap + observability plan + explicit implementation reality docs.”

There is an important chronological nuance here: at the instant this commit lands, the README points to files that are not yet present in the chunk history. The very next commits create them. So the before → after evolution is not that the docs already existed and were merely linked; it is that the README was updated first to announce and reserve places for a more complete documentation system.

## 2026-03-17 04:09:51 +0000 — commit `f2ba5fc`

Commit `f2ba5fc553da36005ab85d8ad7d57ad9feb23eb5` creates `docs/architecture-decisions.md`. This file is substantial and explicitly says it is not a generic ADR collection but a capture of “the concrete architectural choices visible in the current repo and the trade-offs they impose.”

Before this commit, the chunk evidence shows no dedicated architecture-decisions document in the file list. After it, the repository has a twelve-entry architecture-decision catalog, each with `Status`, `What the code does`, `Why this was chosen`, and `Consequences`. The file does not speculate about aspirational architecture; it documents the existing one:

* gateway as the only public HTTP entry point
* PS256-signed JWTs at the edge
* Kafka/Redpanda-backed write-path separation
* per-service PostgreSQL storage
* at-least-once delivery with application-level idempotency
* in-memory matching with persisted resting orders
* ticker-keyed trading events
* narrow Redis usage
* Docker Compose as the runtime target
* synchronous order-owner lookup from transaction processor to orders service
* interceptor-gated internal sensitive routes
* narrow, deterministic, long-only portfolio projection

The before → after shift here is one of explicit reasoning capture. Those choices were already implied by code and docs elsewhere in the repo, but they were not assembled into a document that named each design, described the rationale, and stated the downside. After `f2ba5fc`, the repo gains a place where architectural trade-offs are described in current-state language rather than only inferred from code, README prose, or interview-review notes.

The document is also deliberately honest about limits. For example, AD-001 says the gateway is a policy bottleneck and that downstream auth context is not strongly propagated. AD-002 says refresh handling still depends on header-gated internal routing and lacks rotation/revocation. AD-006 says the matching engine is not a distributed matching system. AD-009 says production deployment concerns are intentionally deferred by staying on Compose. This is not generic architecture celebration; it is current-state architecture with visible consequences.

## 2026-03-17 04:09:54 +0000 — commit `4e84be3`

Commit `4e84be3272eeb9fcb54654bb6fa4e442366e4e34` adds `docs/event-contracts.md`, a 306-line document focused on Kafka/Redpanda topic contracts “as currently implemented.” Before this commit, the repo had no chunk-visible single source enumerating all topic producers, consumers, key strategies, idempotency behaviors, and contract inconsistencies. After it, the event surface is documented topic by topic.

The opening sections establish the topic inventory:

* `order.placed.v1`
* `order.cancelled.v1`
* `trade.executed.v1`
* `transaction.recorded.v1`

They also record DLT conventions and shared transport assumptions such as JSON payloads, Kafka-compatible Redpanda transport, at-least-once delivery, and application-level idempotency. The file is especially valuable because it does not pretend the contracts are cleaner than they are. It explicitly calls out that:

* `matching-engine` emits `trade.executed.v1.timestamp` as `OffsetDateTime`
* `transaction-processor` consumes that timestamp as `Instant`
* `order.placed.v1` compatibility relies on `@JsonAlias` for `orderType` vs `type`
* `eventId` is present on `transaction.recorded.v1` but not uniformly enforced on the other topics
* several consumers fall back to synthetic IDs derived from `topic|partition|offset`

That is a significant before → after improvement in technical clarity. Before `4e84be3`, a reviewer had to read multiple service producers and consumers to reconstruct what the message contracts were and where they diverged. After it, the repo has a contract document that is explicit about both the current shapes and the gaps.

The file also captures operational semantics per topic. For `order.cancelled.v1`, it documents that events are only emitted on `NEW -> CANCELED` transitions and that re-cancel does not emit again. For `trade.executed.v1`, it records the downstream consumer effects in orders service, market-data consumer, and transaction processor. For `transaction.recorded.v1`, it documents that one trade produces two records, that the payload already includes `eventId`, and that the portfolio consumer is long-only by implication. This changes the repo from “events exist” to “events are described as code contracts with documented downstream meaning.”

## 2026-03-17 04:09:57 +0000 — commit `6e96c05`

Commit `6e96c0510eb4c64347149111a8cc5b6bc0a07d37` adds `docs/known-risks-and-non-goals.md`. The opening sentence is important: it says the document is intentionally narrow and lists only risks and non-goals directly visible in the current implementation, omitting anything not confirmed in code. Before this commit, those risks existed only as scattered observations in review documents and code reading. Afterward, the repo has a formal statement of what is risky and what is deliberately out of scope.

The document is organized into security/identity risks, eventing/consistency risks, domain/product non-goals, testing/observability limitations, visible script/docs drift, and deliberate non-goals for the current maturity stage.

Examples of current risks it makes explicit:

* gateway auth does not bind request `userId` to JWT subject
* internal service trust is header-based, not identity-based
* refresh tokens are long-lived and not revocable
* CORS is fully open at the gateway
* scopes exist in tokens but are not enforced by route policy
* there is no transactional outbox around DB writes and Kafka sends
* event identity is not uniform across topics
* transaction processor depends synchronously on orders service
* matching engine correctness depends on concurrency assumptions

Examples of non-goals it freezes into documentation:

* long-only portfolio projection
* no market value or unrealized PnL in summary
* fixed market-data intervals only
* Docker Compose as the runtime target
* no real money, brokerage, settlement, or compliance flows
* no exactly-once Kafka claim
* no production-grade internal service identity
* no cloud/IaC in repo
* no refresh-token rotation/revocation
* no full observability stack in the default runtime

The file also captures known drift in scripts and docs: `gateway_smoke.sh` using the wrong market-data path, `e2e_portfolio_service.sh` assuming behaviors the current portfolio service does not implement, and `manual_cancel_test.sh` being more environment-sensitive than other scripts. The before → after evolution here is candidness. The repo stops requiring a careful reader to infer its current limits and starts stating them directly in one place, using only code-confirmed observations.

## 2026-03-17 04:10:00 +0000 — commit `f6f2ac1`

Commit `f6f2ac15b6050d896dc07bfb7acd1385e0cb3a38` adds `docs/operational-runbook.md`, which documents the current local runtime as an operable system rather than just a build artifact. Before this commit, the repository contained health checks, E2E scripts, and operational hints spread across README text and shell scripts, but no dedicated local runtime runbook. After the commit, there is a document that assumes the operating model is local Docker Compose and then describes how to debug it.

The runbook begins by freezing the runtime topology:

* `api-gateway` on `localhost:8080`
* the seven private application services on `8081` through `8087`
* Redpanda on `9092`
* Redis on `6379`
* Postgres instances per service group

It then gives first checks, health endpoints, important topics and consumer groups, a symptom → likely cause → where to look section, handy commands, service-specific operational notes, and known operational caveats.

The symptom mapping is especially concrete. It covers:

* `401` from gateway on protected routes
* `403` on registration or refresh
* login returning `429`
* orders accepted but no trades occurring
* fills happening without portfolio updates
* stale market data
* cancel issued but later BUY still matching
* DLT growth

For each, it names likely causes and where to look next, such as JWT key mismatches, missing internal-caller headers, consumer lag on particular topics, `orders-service /orders/{id}` lookups, or DTO drift between producer and consumer. That changes the repo from having operational knowledge embedded in scripts and code to having an explicit local troubleshooting document.

The runbook also carries forward the same honesty seen in `known-risks-and-non-goals.md`. It explicitly warns that `gateway_smoke.sh` has a market-data path mismatch, `e2e_portfolio_service.sh` mixes current behavior with future assumptions, and some correctness issues such as DB/Kafka dual-write gaps are architectural, not things a restart will solve. So the before → after shift is not just “more docs”; it is “operationally actionable docs that also explain where operations will not save you from design limitations.”

## 2026-03-17 04:10:04 +0000 — commit `dcb85d5`

Commit `dcb85d53d1cc5b33d5bddafa3f79d779c025f918` creates `docs/security-model.md`. Before this commit, the project had security information scattered across the gateway README, service READMEs, and review documents, but no single current-state security model document. After it, the repo has a 236-line security document that explicitly describes the current implementation rather than an idealized future one.

The document defines the public boundary as the gateway on port `8080`, then states what the gateway actually does:

* validates access-token signatures
* decides which routes require authentication
* rate limits login by IP via Redis
* adds `X-Internal-Caller: api-gateway` on selected internal hops

It contrasts that with the private boundary, where `/refresh` and `/register` are not protected by downstream JWT validation but by header-gated interceptors. This is a meaningful before → after change in clarity: the repo now explains that it has a real but weak internal trust model, rather than leaving readers to infer the implications from code and previous docs.

The token model section is equally specific. It documents:

* PS256 signing
* PEM-based key material
* access and refresh token claims
* 15-minute access expiry
* 30-day refresh expiry
* refresh behavior that reuses the refresh token and mints a new access token
* the absence of rotation, revocation, and server-side session storage

The gateway-enforcement and internal-route-protection sections then explain the difference between public gateway permission and downstream header gating, plus the fact that scopes are extracted into authorities but not enforced at route level. The “Current trust gaps” section names the biggest weaknesses directly: JWT-authenticated callers can still submit arbitrary `userId` values in order bodies, internal caller identity is just a shared header convention, authorization is coarse-grained, and token lifecycle is incomplete.

That makes the security model explicit in a way that was not previously concentrated in one place. Before `dcb85d5`, security had to be reconstructed from gateway config and service behavior. After it, the repo names its security strengths and weaknesses in one document and ends with a concise one-sentence characterization: strong edge signature model, weak internal trust model, weak command-identity binding beyond the gateway.

## 2026-03-17 04:10:08 +0000 — commit `9abbc5e`

Commit `9abbc5e8c1d3e7155a7aaace60508e7d2c6b5f6a` adds `docs/test-strategy.md`, a current-state description of what the repo really tests, how the scripts are intended to be used, and where the gaps are. Before this commit, the testing story had to be inferred from CI, E2E scripts, and service tests. After it, the repo gets a dedicated testing reality document.

The file begins by distinguishing unit/application-start tests from end-to-end shell scripts. It names the visible application tests for orders, market-data, user-registration, and portfolio services, and immediately bounds them as likely proving basic Spring context startup rather than end-to-end correctness. That before → after shift is important: the repo moves from merely containing test files to explaining what confidence those tests do and do not provide.

The strongest value is in the script-by-script analysis. The document records what each root script currently covers:

* `gateway_smoke.sh` for quick ingress checks, while explicitly noting the market-data route mismatch
* `e2e_trade_pipeline_test.sh` for order-to-trade-to-candle flow, while noting that it does not validate transaction processor or portfolio service despite the broader-sounding filename
* `manual_cancel_test.sh` for cancel-path debugging, with a note about environment specificity
* `e2e_scenarios.sh` as the strongest regression script, including partial fills, IOC/FOK, cancel, idempotency replay, restart recovery, and DLT behavior
* `e2e_portfolio_service.sh` as useful manual exploration but out of sync with current portfolio-service behavior

The later sections make gaps explicit:

* no event-contract test layer
* no explicit JWT subject vs order `userId` mismatch test
* no explicit failure-window test around DB commit vs Kafka publish
* asymmetric market-data and portfolio verification
* little visible evidence of load or contention testing

The file then gives a recommended way to use the current suite and concrete next additions that would materially improve confidence. Before `9abbc5e`, those judgments existed only informally in a reviewer’s head or in scattered comments. After it, the repo has a written test strategy that distinguishes strong coverage from known blind spots.

## 2026-03-17 04:10:10 +0000 — commit `2ecd200`

Commit `2ecd2008ee343bcdc32fb18892383ec291b54c86` is the only non-documentation change in the chunk. It adds `generate_devlogs.sh`, an executable repository utility for generating per-day development-log evidence bundles and optionally invoking an AI command to write logs.

Before this commit, the chunk evidence shows no such generator script. After it, the repo includes a 231-line Bash tool that:

* accepts arguments such as `--repo`, `--out-dir`, `--skip-existing`, `--force`, `--since`, `--until`, `--no-patch`, `--max-patch-bytes`, and `--ai-command`
* builds `docs/days` and `docs/_planning`
* generates an activity map in both Markdown and CSV
* enumerates unique commit days from `git log`
* writes `commits.txt`, `commit-hashes.txt`, `changed-files.txt`, `diff.patch`, `context.txt`, and `prompt.txt` for each day
* filters changed files through a default noise-exclusion regex
* optionally invokes an external AI command with exported environment variables pointing at the generated evidence files

This is a meaningful before → after evolution in repository tooling. The project moves from manually maintained or ad hoc devlog generation toward a reproducible pipeline that packages daily git evidence in a standard structure. The prompt it writes also encodes the same documentation constraints visible elsewhere in this work: do not invent anything, be detailed and chronological, reference files and commits, and explain before → after evolution.

There are also clear boundaries on what the script does not prove. The commit message says it “automates the creation of daily development logs,” but within the patch it actually automates the collection of git evidence and the scaffolding for log generation. Full devlog authoring still depends on the optional `AI_COMMAND` hook. So the most accurate before → after description is that the repo gained a generator for development-log inputs and prompts, plus optional AI-assisted output generation, not a fully self-contained day-log authoring system.

Taken chronologically, this chunk is a rapid documentation and tooling expansion. The root README is updated first to advertise a broader documentation surface. Six new “current implementation” documents land immediately after, each aimed at a different missing dimension of repo clarity: architecture decisions, event contracts, known risks, operations, security, and testing. The sequence ends with a script that can produce standardized evidence bundles and prompts for development-log generation. The repo’s visible evolution in this slice is therefore not about changing the trading system itself; it is about making the current system easier to reason about, review, operate, and narrate.
