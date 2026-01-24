# Future Enhancements (Planned)

These are not implemented in the current codebase; they are candidate roadmap items.

## Bot Market Simulation (Full Set)

Common guardrails (applied to all bots by default):
* **Cash reserve**: keep 10-20% unspent to avoid deadlock.
* **Auto-deleveraging**: sell a small slice when budget is maxed.
* **Rotation sell**: if a new buy signal appears, sell lowest-confidence holding first.

| Bot | Category | Functionality | ML Stack | ML Type | Mode | Scheduler | Guardrails |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Random Retail | Retail | Small random buys/sells | None | n/a | n/a | None | Reserve + Deleverage + Rotation |
| Buy-and-Hold | Retail | Buy once, rare trades | None | n/a | n/a | None | Reserve + Deleverage + Rotation |
| Trend Chaser | Retail | Follows short trends | sklearn | simple classifier | infer_only | Prefect | Reserve + Deleverage + Rotation |
| News-Reactive (sim) | Retail | Trades on simulated spikes | XGBoost | gradient boosting | infer_only | Prefect | Reserve + Deleverage + Rotation |
| Passive Market Maker | Market Maker | Bid/ask around mid | None | n/a | n/a | None | Reserve + Deleverage + Rotation |
| Adaptive Market Maker | Market Maker | Spread widens on vol | sklearn | random forest | infer_only | Prefect | Reserve + Deleverage + Rotation |
| Inventory-Aware Maker | Market Maker | Reduce quotes if heavy | None | n/a | n/a | None | Reserve + Deleverage + Rotation |
| TWAP Executor | Institutional | Sliced execution | None | n/a | n/a | None | Reserve + Deleverage + Rotation |
| VWAP Executor | Institutional | Volume-weighted execution | None | n/a | n/a | None | Reserve + Deleverage + Rotation |
| Mean Reversion | Hedge | Z-score mean reversion | sklearn | logistic regression | train_infer | Airflow | Reserve + Deleverage + Rotation |
| Momentum | Hedge | MA cross + trend | XGBoost | gradient boosting | train_infer | Airflow | Reserve + Deleverage + Rotation |
| Stat Arb (Pairs) | Hedge | Divergence trading | XGBoost | gradient boosting | train_infer | Airflow | Reserve + Deleverage + Rotation |
| Breakout | Hedge | Volume breakout entries | sklearn | random forest | infer_only | Prefect | Reserve + Deleverage + Rotation |
| Liquidity Taker | HFT | Aggressive on signal | XGBoost | gradient boosting | infer_only | Prefect | Reserve + Deleverage + Rotation |
| Spoof/Cancel Sim | HFT | Rapid place/cancel noise | None | n/a | n/a | None | Reserve + Deleverage + Rotation |
| Latency-Sensitive | HFT | Fast polling, micro orders | None | n/a | n/a | None | Reserve + Deleverage + Rotation |
| Risk-Parity | Portfolio | Volatility-weighted allocation | None | n/a | n/a | None | Reserve + Deleverage + Rotation |
| Stop-Loss Bot | Portfolio | Exits on drawdown | None | n/a | n/a | None | Reserve + Deleverage + Rotation |
| Rebalancer | Portfolio | Periodic target weights | None | n/a | n/a | None | Reserve + Deleverage + Rotation |

## Confluent Schema Registry + Contract Testing

* **Schema Registry** stores event schemas for Kafka topics (`order.placed.v1`, `trade.executed.v1`, `transaction.recorded.v1`, `signal.generated.v1`).
* **Compatibility checks** prevent breaking changes (e.g., BACKWARD compatibility).
* **Contract tests** in CI validate producer and consumer schemas match before deploy.
* **Benefit**: safe evolution of event payloads across services.

## Streamlit Analytics Console

* **UI inspiration:** https://trade-tapestry-view.lovable.app (screenshot reference for layout/feel).
* Multi-tab console that mirrors a trading ops desk:
  * **Overview:** KPI tiles (PnL, active bots, orders/min), health badges, recent alerts.
  * **Market Data:** OHLC candles with trade markers + ML signal overlays.
  * **Bot Performance:** leaderboard, win rate, drawdown, activity rate.
  * **Portfolio & Risk:** positions table, exposure by ticker, VaR/drawdown.
  * **Order Flow:** order timeline, fill rates, slippage heatmap.
  * **ML Monitoring:** model registry view, drift indicators, recent accuracy/F1.
* Data sources:
  * `/api/market-data/candles/*`
  * `/api/portfolio/*`
  * `/api/transactions/*`
  * `signal.generated.v1` (optional) for ML signals
* Tech: Streamlit + Plotly + Pandas.

## Observability Stack

### Final product (what you see)
* **Grafana dashboards**: latency, error rates, Kafka lag, orders/sec, PnL pipeline health.
* **Jaeger tracing UI**: end-to-end traces from Gateway through downstream services.
* **Loki log explorer**: logs searchable by `X-Request-Id` or trace ID.
* **Alerts**: Slack/email on error spikes, lag, or service down.

### How it works (data flow)
1. Java services expose metrics via `/actuator/prometheus`.
2. Prometheus scrapes metrics on an interval.
3. Grafana visualizes metrics and logs.
4. OpenTelemetry collects traces (Java + Python).
5. Jaeger stores and visualizes traces.
6. Logs stream to Loki (via promtail or Docker log driver).

### Path from idea to product (phased)
1. **Metrics first**: add Prometheus + Grafana; ship core dashboards.
2. **Logs next**: add Loki + promtail; correlate by `X-Request-Id`.
3. **Tracing**: add OpenTelemetry + Jaeger; validate trace propagation.
4. **Alerting**: define Grafana alert rules and notifications.

## Platform/Infra

* Kubernetes + Terraform for cloud deployment.
* Redpanda on EKS (StatefulSet with PVs).
* MLflow for model registry + experiment tracking.
* Airflow (scheduled training) and Prefect (ad-hoc retraining).
* JWKS + key rotation; optional JWE for encrypted JWTs.
* Refresh token rotation and revocation list.
* Per-user rate limiting at the gateway (beyond login IP limits).
* WebSocket/SSE for order/portfolio streaming.
* Market data fan-out (quote/BarUpdated events).
