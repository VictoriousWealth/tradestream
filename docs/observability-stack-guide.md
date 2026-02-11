# Observability Stack Guide

This document captures the full observability plan for TradeStream: what it involves, the assumed knowledge, implementation steps, and what the final product should look like.

## Scope

For this repo, a full observability stack means four pillars working together:

1. Metrics: Prometheus scrapes service metrics; Grafana visualizes SLO-style dashboards.
2. Logs: Loki stores logs; Promtail ships container logs with labels.
3. Tracing: OpenTelemetry instrumentation exports traces to Jaeger.
4. Alerting: Grafana alert rules notify Slack/email on failure patterns.

## Pre-assumed Knowledge

You should be comfortable with:

1. Docker Compose
2. Spring Boot Actuator
3. Prometheus basics: metrics, labels, scrape targets
4. Distributed tracing basics: spans, trace IDs, parent/child relationships
5. Log structure: JSON logs, correlation IDs (`X-Request-Id`)
6. HTTP and Kafka flow in this system
7. Grafana basics: dashboards, panels, alert rules

## Skills This Demonstrates

Implementing this end-to-end shows:

1. Ability to operate distributed systems under failure
2. Ability to correlate incidents across logs, metrics, and traces
3. Alert design that is actionable, not noisy
4. Observability-driven performance and reliability tuning

## Step-by-step Implementation

### 1) Define Observability Goals

Pick key questions:

- Is the platform up?
- Are orders flowing end-to-end?
- Where is latency coming from?
- Are consumers lagging?

Define signals to track:

- Error rate
- P95 latency
- Kafka lag
- DLQ volume
- Order throughput

### 2) Metrics Foundation (Prometheus + Grafana)

- Add/verify `spring-boot-starter-actuator` and Micrometer Prometheus registry in services.
- Expose `/actuator/prometheus`.
- Add Prometheus to `docker-compose.yml`.
- Configure `prometheus.yml` scrape jobs for gateway and all services.
- Add Grafana and connect Prometheus datasource.
- Build first dashboards:
  - Service health/up
  - HTTP request rate/latency/error rate
  - JVM memory/GC/thread pools
  - Kafka consumer lag and processing rate (where exposed)

### 3) Structured Logs + Centralized Logs (Loki + Promtail)

- Standardize JSON logging in services.
- Ensure log lines include `service`, `level`, `timestamp`, `X-Request-Id`, and later `traceId/spanId`.
- Add Loki and Promtail to Compose.
- Configure Promtail to tail container logs and attach labels (`service`, `container`, `env`).
- Add Loki datasource in Grafana.
- Create log queries by service, level, and request ID.

### 4) Distributed Tracing (OpenTelemetry + Jaeger)

- Add OpenTelemetry Java instrumentation for Spring services.
- Propagate trace context through gateway and downstream HTTP calls.
- Add Kafka producer/consumer tracing instrumentation.
- Run Jaeger in Compose and export OTEL traces to it.
- Validate one full trace:
  - `POST /api/orders` at gateway
  - Orders service
  - Matching engine
  - Transaction processor
  - Portfolio projector

### 5) Correlation Wiring

- Ensure IDs connect all three signals:
  - Logs include `traceId` and `X-Request-Id`
  - Traces include service and span names
  - Dashboards link to logs and traces
- In Grafana, enable jump-to-logs and jump-to-traces where possible.

### 6) Alerting

Create Grafana alert rules for:

- Service down
- 5xx spike at gateway
- P95 latency threshold breach
- Kafka lag above threshold
- DLQ events > 0 for N minutes

Wire Slack/email contact points and include runbook links.

### 7) Validation and Game-day Tests

Simulate failures:

- Stop one service
- Inject malformed event into Kafka
- Add artificial latency

Confirm each case produces:

- Alert firing
- Dashboard anomaly visibility
- Logs and traces sufficient for root-cause analysis

## End Product: What It Looks Like

### Grafana Dashboard Suite

1. Platform Overview
   - Request volume, error %, P95 latency, service uptime
2. Trading Pipeline
   - Orders placed/sec, trades executed/sec, transactions recorded/sec, portfolio updates/sec
3. Kafka Reliability
   - Consumer lag, retry rate, DLQ counts
4. Infra/JVM
   - CPU, memory, GC, threads, DB pool pressure

### Jaeger Trace UI

- Search by service/operation
- Full order lifecycle trace with per-hop timings
- Quick bottleneck identification

### Loki Log Explorer

- Filter by service, level, trace ID, request ID
- Fast request-level debugging using correlation IDs

### Alert Delivery

- Slack/email alerts including:
  - Condition
  - Affected service
  - Links to dashboards/logs/traces
  - Short runbook action

## Key Features of the Final Stack

1. Real-time health and reliability visibility
2. Fast root-cause analysis across services
3. Production-style operational readiness
4. Evidence-based performance tuning
5. Strong "build and operate systems" signal for recruiters

## Recommended Build Order

1. Metrics first
2. Logs second
3. Traces third
4. Alerts fourth
5. Failure drills last

