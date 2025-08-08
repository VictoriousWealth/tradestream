# **Table of Contents**

* [1. Title & Document Control](#1-title--document-control)
* [2. Executive Summary](#2-executive-summary)
* [2A. Technology Overview](#2a-technology-overview)
* [3. Goals & Objectives](#3-goals--objectives)
* [4. Scope & Deliverables](#4-scope--deliverables)
* [5. High-Level Architecture (Updated with Matching Engine)](#5-high-level-architecture-updated-with-matching-engine)
* [6. Technical Design](#6-technical-design)
* [7. Assumptions & Constraints](#7-assumptions--constraints)
* [8. Risks & Mitigations](#8-risks--mitigations)
* [9. Timeline & Milestones](#9-timeline--milestones)
* [10. References & Resources](#10-references--resources)
* [11. Appendix](#11-appendix)

---

# **1. Title & Document Control**

| Field            | Content                                                |
| ---------------- | ------------------------------------------------------ |
| **Project Name** | TradeStream — Event-Driven Trading Simulation Platform |
| **Version**      | 2.0 (Upgraded Architecture)                            |
| **Author**       | Nick Efe Oni                                           |
| **Date**         | Fri, 08 August 2025                                    |
| **Reviewers**    | N/A                                                    |

---

# **2. Executive Summary**

**TradeStream — Event-Driven Trading Simulation Platform** is a microservice-based, event-driven system that simulates the full lifecycle of securities trading, including order placement, order matching, trade execution, portfolio updates, and market data aggregation.

This release evolves the previous design from a passive transaction and market data updater into a **realistic market simulation engine** with:

* A dedicated **Matching Engine Service** to process orders and maintain per-ticker order books.
* **Event-driven trade propagation** using Kafka or RabbitMQ for asynchronous updates to portfolios and market data.
* Real-time OHLC and volume updates based on actual trades.
* Redis-backed API Gateway rate-limiting and caching.
* JWT with **JWS + JWE** for secure API authentication.

---

# **2A. Technology Overview**

## Minimum Viable Product Stack

| Category          | Technology                         | Purpose                             |
| ----------------- | ---------------------------------- | ----------------------------------- |
| Backend Framework | Java Spring Boot                   | Microservice APIs                   |
| Stream Processing | Kafka or RabbitMQ                  | Event-driven trade & order updates  |
| Database          | PostgreSQL                         | Service-specific persistent storage |
| Cache Layer       | Redis                              | Caching quotes & rate-limiting      |
| Authentication    | JWS & JWE (JWT signed & encrypted) | Secure API auth                     |
| Containerization  | Docker & Docker Compose            | Packaging & deployment              |
| CI/CD             | GitHub Actions                     | Automated build/test/deploy         |
| Deployment        | AWS Lightsail                      | Initial cloud hosting               |

## Planned Enhancements

| Category                | Technology                           | Purpose                   |
| ----------------------- | ------------------------------------ | ------------------------- |
| Container Orchestration | Kubernetes                           | Scale & resilience        |
| IaC                     | Terraform                            | Infrastructure as code    |
| Observability           | Prometheus + Grafana                 | Metrics, monitoring       |
| Security Enhancements   | RBAC, token hardening, vuln scanning | Production-grade security |

---

# **3. Goals & Objectives**

## 3.1 Purpose

Deliver a realistic, secure, and modular trading simulation platform that demonstrates enterprise-level backend architecture.

## 3.2 Technical Objectives

* Implement **order placement** with market/limit orders.
* Introduce a **Matching Engine** for realistic price movement and partial fills.
* Update **Market Data Service** reactively from trade events.
* Manage user portfolios dynamically from executed trades.
* Secure all APIs behind an API Gateway with JWT validation and Redis-based rate-limiting.
* Deploy the entire stack on AWS Lightsail with Docker Compose.

## 3.3 Learning Objectives

* Master event-driven microservice patterns.
* Build a functional order book and matching algorithm.
* Apply secure authentication (JWS + JWE).
* Integrate Redis caching and rate-limiting.
* Deploy multi-service systems in a real cloud environment.

---

# **4. Scope & Deliverables**

## 4.1 MVP Scope

* API Gateway with authentication enforcement & rate-limiting.
* User Registration & Authentication services.
* Orders Service for order intake and persistence.
* Matching Engine Service with in-memory order book per ticker.
* Market Data Service for OHLC & volume aggregation.
* Portfolio Service for holdings & transaction history.
* Event Bus (Kafka/RabbitMQ) for `OrderPlaced` and `TradeExecuted`.
* Postgres DB per service, Redis for cache/rate limit.

## 4.2 Out of Scope (MVP)

* Advanced order types (stop loss, iceberg).
* Multi-day order persistence.
* Real-time WebSocket feeds.
* Kubernetes deployment.
* Advanced observability.

---

# **5. High-Level Architecture (Updated with Matching Engine)**

## 5.1 Overview

The system now processes trades end-to-end:

1. User submits an order → API Gateway → Orders Service.
2. Orders Service publishes `OrderPlaced` → Matching Engine.
3. Matching Engine updates order book, matches orders, publishes `TradeExecuted`.
4. Portfolio Service & Market Data Service consume `TradeExecuted`.
5. Market Data recalculates OHLC and volume.
6. Portfolio updates holdings and transactions.

## 5.2 Components

| Component           | Role                                               |
| ------------------- | -------------------------------------------------- |
| API Gateway         | Routing, JWT decrypt/verify, Redis rate limit      |
| User Registration   | User onboarding                                    |
| Auth Service        | Issues JWE(JWS) tokens                             |
| Orders Service      | Receives and stores orders, publishes events       |
| Matching Engine     | Maintains order book, matches orders, emits trades |
| Market Data Service | Consumes trades, updates OHLC/volume               |
| Portfolio Service   | Consumes trades, updates positions & transactions  |
| Kafka/RabbitMQ      | Event delivery                                     |
| Redis               | Rate-limiting & caching                            |
| PostgreSQL          | Service persistence                                |

---

# **6. Technical Design**

## 6.1 Event Model

### `OrderPlaced`

```json
{
  "orderId": "uuid",
  "userId": "uuid",
  "ticker": "AAPL",
  "side": "BUY",
  "type": "LIMIT",
  "price": 196.20,
  "quantity": 100,
  "timeInForce": "DAY",
  "timestamp": "2025-08-08T10:30:00Z"
}
```

### `TradeExecuted`

```json
{
  "tradeId": "uuid",
  "orderId": "uuid",
  "userId": "uuid",
  "ticker": "AAPL",
  "price": 196.20,
  "quantity": 40,
  "side": "BUY",
  "timestamp": "2025-08-08T10:30:01Z"
}
```

## 6.2 Matching Engine Logic

* Maintain **two priority queues** (bids: max-heap by price; asks: min-heap by price).
* Match orders on price overlap.
* Partial fills generate multiple `TradeExecuted` events.
* Remaining quantities stay in the book until filled or expired.

## 6.3 API Gateway Security

* Decrypt JWE, verify JWS signature.
* Inject `X-User-Id` header for downstream services.
* Apply Redis-based request throttling per user.

## 6.4 Data Storage

* **Orders DB:** pending & completed orders.
* **Trades DB:** historical trades (Market Data).
* **Positions DB:** holdings per user (Portfolio).

---

# **7. Assumptions & Constraints**

* MVP runs on single AWS Lightsail instance.
* Services communicate only via API Gateway (public) or broker (internal).
* Each service owns its DB schema.
* Matching Engine runs single-threaded per ticker for MVP.

---

# **8. Risks & Mitigations**

| Risk                        | Mitigation                             |
| --------------------------- | -------------------------------------- |
| Event loss on broker        | Enable persistent topics/queues        |
| Order book concurrency bugs | Lock per-ticker book updates           |
| Matching latency            | Keep in-memory for MVP, optimize later |
| JWT handling complexity     | Use tested JOSE/JWT libraries          |

---

# **9. Timeline & Milestones**

| Phase   | Target   | Deliverables                            |
| ------- | -------- | --------------------------------------- |
| Phase 1 | Aug 2025 | Event bus, `TradeExecuted` flow MVP     |
| Phase 2 | Sep 2025 | Orders Service + basic matching         |
| Phase 3 | Oct 2025 | Real-time OHLC updates, Redis caching   |
| Phase 4 | Post-MVP | WebSockets, advanced orders, K8s deploy |

---

# **10. References & Resources**

(Same as old PRD, plus order matching resources)

| Resource                           | Description               |
| ---------------------------------- | ------------------------- |
| \[MIT Market Microstructure Notes] | Understanding order books |
| \[Spring Cloud Gateway Docs]       | API Gateway config        |
| \[Kafka/RabbitMQ Docs]             | Event streaming           |
| \[Redis Rate Limiter Examples]     | API Gateway rate control  |

---

# **11. Appendix**

## 11.1 Acronyms

(Same as old PRD)

## 11.2 Example Order Lifecycle

1. User places buy order for 100 AAPL @ 196.20.
2. Order enters bid book, matches existing sell @ 196.20 for 40 shares.
3. `TradeExecuted`(40\@196.20) emitted.
4. Market Data updates OHLC/volume.
5. Portfolio updates user’s holdings (+40 AAPL).
6. Remaining 60 shares stay on bid side until filled or cancelled.

---
