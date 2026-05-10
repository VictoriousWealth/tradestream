# Test Strategy (Current Repo)

Last reviewed against scripts/config on 2026-05-10.

This document describes what the repo actually tests today, how those tests are intended to be run, and where the current gaps or script drifts are.

---

## 1) Test layers that exist today

### Unit / application-start tests

Visible in the repo:
- `AuthApplicationTests`
- `OrdersServiceApplicationTests`
- `MatchingEngineApplicationTests`
- `TransactionProcessorApplicationTests`
- `MarketDataConsumerApplicationTests`
- `UserRegistrationServiceApplicationTests`
- `PortfolioServiceApplicationTests`
- `OrdersControllerTest`
- `OrderServiceTest`

What these currently prove:
- Basic Spring context startup for each service.
- Targeted controller and service behavior coverage in `orders-service`.

What they do not prove:
- End-to-end event correctness.
- Cross-service contracts.
- Failure recovery behavior.
- Equivalent focused unit coverage across most of the other services.

### End-to-end shell scripts

Current root scripts:
- `gateway_smoke.sh`
- `e2e_trade_pipeline_test.sh`
- `e2e_portfolio_service.sh`
- `manual_cancel_test.sh`
- `e2e_scenarios.sh`

These are the highest-value tests in the repo because they exercise the integrated stack, Kafka topics, and service interactions.

---

## 2) What each script currently covers

## `gateway_smoke.sh`

Purpose:
- Quick ingress check through the API Gateway.

Confirmed covered behaviors:
- Gateway health endpoint.
- User registration through gateway.
- Login through gateway.
- Protected route rejected without token.
- Protected transactions route accepted with token.
- Refresh path through gateway.
- Portfolio read through gateway.

Known issue:
- The script currently hits market data at `/api/market-data/$TICKER/latest`.
- The configured gateway route expects `/api/market-data/candles/**`.
- That step is therefore out of sync with the current gateway route map.

Use it for:
- Fast auth/routing smoke checks.

Do not treat it as:
- A full gateway regression suite.

## `e2e_trade_pipeline_test.sh`

Purpose:
- Basic trade-flow validation from order placement to `trade.executed.v1` and market-data candle generation.

Confirmed covered behaviors:
- Compose startup for orders, matching, market-data, Redpanda, Redis, and backing Postgres services.
- Health waiting for orders, matching, and market-data services.
- Placement of a resting SELL and crossing BUY.
- Observation of a `trade.executed.v1` record directly from Kafka.
- Query of latest candle from market-data-consumer.

Important limitation:
- It does not bring up or validate `transaction-processor` or `portfolio-service`.
- Despite the filename, this is not the full downstream pipeline.

## `manual_cancel_test.sh`

Purpose:
- Focused cancel-path debugging script.

Confirmed covered behaviors:
- Place a resting SELL order.
- Observe a single `order.cancelled.v1` record.
- Check matching-engine group lag on `order.cancelled.v1`.
- Post a crossing BUY after cancel.
- Observe whether a trade still fires.

Good for:
- Narrow reproduction of cancel race/ordering issues.

Weakness:
- More environment-specific than the other scripts because it defaults to hard-coded network/container names.

## `e2e_scenarios.sh`

Purpose:
- Most comprehensive integration script in the repo.

Confirmed scenarios covered:
1. Partial fill.
2. IOC partial fill.
3. IOC with no liquidity.
4. FOK with insufficient liquidity.
5. MARKET order against resting liquidity.
6. Cancel flow with Kafka confirmation and matching-engine lag catch-up.
7. Idempotency by re-publishing a previously observed trade.
8. Matching-engine recovery after restart.
9. DLT behavior by publishing a poison `order.placed.v1` payload.

Additional validation:
- Checks transaction-processor ledger effects for trade-producing scenarios.
- Verifies no ledger rows appear for no-trade scenarios.

This is currently the strongest regression script in the repo.

## `e2e_portfolio_service.sh`

Purpose:
- Targeted portfolio read-model exploration.

What it currently does:
- Seeds trades through `orders-service`.
- Watches `transaction.recorded.v1`.
- Queries portfolio endpoints for resulting positions.

Important drift / limitations:
- The script comments mention potential short-position expectations, but the current projector is long-only.
- It tries to create a synthetic `trade.executed.v1` as a price mark, but portfolio-service does not consume `trade.executed.v1`; it consumes `transaction.recorded.v1`.
- That synthetic trade can create noise/failures in `transaction-processor` because the random order IDs do not exist in `orders-service`.

Conclusion:
- Useful as a manual exploration script, but it should not currently be treated as a precise specification of portfolio behavior.

---

## 3) What CI currently validates

From the current GitHub Actions workflow, CI does all of the following:
- Generates temporary JWT key material under `secrets/`.
- Builds the API Gateway jar and then `docker compose build`s the stack.
- Starts the Compose runtime and waits explicitly on `orders-service` and `portfolio-service`.
- Runs `e2e_portfolio_service.sh`.
- Runs `e2e_trade_pipeline_test.sh`.
- Runs `manual_cancel_test.sh`.
- Runs `e2e_scenarios.sh`.

The key practical benefit of the CI setup is that it validates integration timing and container orchestration issues, not just code compilation.

---

## 4) Current strengths of the test strategy

- Real Kafka topics are exercised instead of only mocked producers/consumers.
- Matching behavior is tested through realistic order sequences.
- DLT behavior is exercised explicitly.
- Restart/recovery is exercised at least once.
- Transaction journaling is checked in integrated scenarios rather than assumed.

---

## 5) Current gaps

### No event-contract test layer

What is missing:
- A producer/consumer contract suite that fails fast when JSON shape changes.

Why it matters:
- The system already has multiple independently evolving event DTOs.
- Some consumers use compatibility shims such as `type` vs `orderType`.

### No explicit test for JWT subject vs order `userId` mismatch

What is missing:
- A negative test proving whether a token holder can or cannot submit another user’s UUID in `POST /api/orders`.

Why it matters:
- That is one of the most important current security gaps.

### No explicit failure-window test around DB commit vs Kafka publish

What is missing:
- A reproducible test for the dual-write hazard in orders or transaction-processor.

Why it matters:
- This is a real correctness risk in the current architecture.

### Market-data and portfolio verification are asymmetric

Current state:
- Market-data validation exists in the trade pipeline script.
- Portfolio validation exists, but the dedicated portfolio script contains documented drift and should be tightened.

### Little evidence of load or contention testing

What is missing:
- Repeated same-ticker bursts.
- High-cardinality ticker fan-out.
- Partition-skew tests.
- Consumer lag growth tests.

---

## 6) Recommended way to use the current test suite

For a quick confidence pass:
1. `gateway_smoke.sh`
2. `e2e_trade_pipeline_test.sh`
3. `e2e_scenarios.sh`

For targeted debugging:
- matching/cancel ordering issue -> `manual_cancel_test.sh`
- portfolio projection issue -> `e2e_portfolio_service.sh`, but read its comments critically

For CI:
- Prefer `e2e_scenarios.sh` as the primary behavior gate because it covers the richest cross-service surface.

---

## 7) Concrete next additions that would materially improve confidence

1. Add a gateway test that submits a valid JWT for user A and an order body for user B.
2. Add schema/contract tests for all four event topics.
3. Split `e2e_portfolio_service.sh` into:
   - one script for current supported portfolio behavior
   - one backlog/spec script for future mark-to-market features
4. Add a scripted check for `transaction.recorded.v1.DLT` remaining empty during happy-path scenarios.
5. Add one replay test for `transaction.recorded.v1` into portfolio-service, not just `trade.executed.v1` into orders/txproc.
