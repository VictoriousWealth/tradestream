# **TradeStream — Event-Driven Trading Simulation Platform**

**Product Requirements & Technical Design Document**
**Version:** 2.0 (Upgraded Architecture)
**Author:** Nick Efe Oni
**Date:** 08 August 2025

---

## **Table of Contents**

* [1. Title & Document Control](#1-title--document-control)
* [2. Executive Summary](#2-executive-summary)
* [2A. Technology Overview](#2a-technology-overview)
* [3. Goals & Objectives](#3-goals--objectives)
* [4. Scope & Deliverables](#4-scope--deliverables)
* [5. High-Level Architecture](#5-high-level-architecture)
* [6. Technical Design](#6-technical-design)
* [7. Assumptions & Constraints](#7-assumptions--constraints)
* [8. Risks & Mitigations](#8-risks--mitigations)
* [9. Timeline & Milestones](#9-timeline--milestones)
* [10. Security Mapping](#10-security-mapping)
* [11. References & Resources](#11-references--resources)
* [12. Appendix](#12-appendix)

---

## **1. Title & Document Control**

| Field            | Content                                                |
| ---------------- | ------------------------------------------------------ |
| **Project Name** | TradeStream — Event-Driven Trading Simulation Platform |
| **Version**      | 2.0 (Upgraded Architecture)                            |
| **Author**       | Nick Efe Oni                                           |
| **Date**         | Fri, 08 August 2025                                    |
| **Reviewers**    | N/A                                                    |

---

## **2. Executive Summary**

**TradeStream** simulates a full securities trading lifecycle using an event-driven, microservice-based backend.

Key upgrades in v2.0:

* **Matching Engine Service** with per-ticker in-memory order books.
* **Asynchronous trade propagation** using **Redpanda (Kafka API)**.
* **OHLC and volume updates** driven by actual trades.
* **Redis-backed API Gateway** for **login** rate limiting.
* **JWS (PS256) JWT authentication** for API security (no JWE).

---

## **2A. Technology Overview**

| Category          | Technology                      | Purpose                            |
| ----------------- | ------------------------------- | ---------------------------------- |
| Backend Framework | Java Spring Boot                | Microservice APIs                  |
| Stream Processing | Redpanda (Kafka API)            | Event-driven trade & order updates |
| Database          | PostgreSQL                      | Service-specific storage           |
| Cache Layer       | Redis                           | Rate limiting + market latest      |
| Authentication    | JWS (JWT signed, PS256)         | Secure API auth                    |
| Containerization  | Docker & Docker Compose         | Deployment packaging               |
| CI/CD             | GitHub Actions                  | Build/test automation              |
| Deployment        | Local Docker Compose (dev)      | Local-first runtime                |

---

## **3. Goals & Objectives**

### Technical Goals

* Implement market/limit order placement.
* Realistic matching with price-time priority and partial fills.
* OHLC/volume updates from trade events.
* JWT + Redis **login** rate-limiting in API Gateway.
* Run MVP locally via Docker Compose (cloud/IaC planned).

### Learning Goals

* Deepen knowledge of event-driven architecture.
* Implement and optimize an order-matching algorithm.
* Apply production-grade JWT handling.

---

## **4. Scope & Deliverables**

### MVP Deliverables

* API Gateway with JWT validation & Redis login rate-limiting.
* Auth & User Registration services.
* Orders Service.
* Matching Engine Service.
* Market Data Consumer.
* Transaction Processor.
* Portfolio Service.
* Redpanda (Kafka API) integration.
* PostgreSQL per service.

---

## **5. High-Level Architecture**

### Overview Flow

```mermaid
sequenceDiagram
    actor U as User
    participant C as Client App
    participant G as API Gateway
    participant O as Orders Service
    participant ME as Matching Engine
    participant MD as Market Data Consumer
    participant TP as Transaction Processor
    participant PF as Portfolio Service
    participant B as Event Bus (Redpanda/Kafka)

    U->>C: Place Order
    C->>G: POST /api/orders
    G->>O: Forward w/ userId
    O->>B: Publish OrderPlaced
    B->>ME: Deliver event
    ME->>B: Publish TradeExecuted
    B->>MD: Deliver trade
    B->>TP: Deliver trade
    TP->>B: Publish TransactionRecorded
    B->>PF: Deliver transaction
```

**Component Roles**

| Component              | Role                                                     |
| ---------------------- | -------------------------------------------------------- |
| API Gateway            | JWT verification, Redis login rate limiting              |
| Orders Service         | Intake & store orders, emit events                       |
| Matching Engine        | Match orders, emit trades                                |
| Market Data Consumer   | Update OHLCV candles, cache latest in Redis              |
| Transaction Processor  | Journal trades, emit `transaction.recorded.v1`           |
| Portfolio Service      | Update positions from `transaction.recorded.v1`          |

---

## **6. Technical Design**

### 6.1 Event Models

#### `OrderPlaced` Schema

| Field       | Type     | Required | Notes                         |
| ----------- | -------- | -------- | ----------------------------- |
| orderId     | UUID     | Yes      | Unique per order              |
| userId      | UUID     | Yes      | Authenticated user            |
| ticker      | String   | Yes      | Uppercase symbol              |
| side        | Enum     | Yes      | BUY / SELL                    |
| type        | Enum     | Yes      | MARKET / LIMIT                |
| timeInForce | Enum     | Yes      | DAY / GTC                     |
| price       | Decimal  | No       | `null` for MARKET             |
| quantity    | Decimal  | Yes      | > 0 (BigDecimal)              |
| timestamp   | ISO-8601 | Yes      | UTC instant (createdAt)       |

#### `TradeExecuted` Schema

| Field       | Type     | Required | Notes                          |
| ----------- | -------- | -------- | ------------------------------ |
| tradeId     | UUID     | Yes      | Unique per trade               |
| buyOrderId  | UUID     | Yes      | Buy-side order id              |
| sellOrderId | UUID     | Yes      | Sell-side order id             |
| ticker      | String   | Yes      | Stock symbol                   |
| price       | Decimal  | Yes      | Execution price                |
| quantity    | Decimal  | Yes      | Filled amount (BigDecimal)     |
| timestamp   | ISO-8601 | Yes      | UTC                            |

---

### 6.2 Matching Engine Threading Model

* **Single-threaded per ticker** for MVP → avoids race conditions.
* Horizontal scaling possible by partitioning symbols across engine instances.

---

### 6.3 API Gateway Rate Limit Policy

| Endpoint              | Requests/sec | Burst | Notes                           |
| --------------------- | ------------ | ----- | ------------------------------- |
| `POST /api/auth/login`| 10           | 20    | IP-based token bucket via Redis |

---

### 6.4 Deployment Topology

```mermaid
flowchart LR
  %% External
  User[Client / Browser\n(HTTP)] -->|8080| GW[(API Gateway\nJWT verify + Redis login rate limit)]

  %% Local Docker Compose
  subgraph DC[Local Docker Compose]
    direction LR

    %% Edge/Gateway and cache
    GW --> RDS[(Redis\nLogin rate limit + market latest)]

    %% Auth & user onboarding
    GW --> URS[User Registration Service]
    GW --> AUTH[Auth Service\n(PS256 issuance)]
    AUTH ---|secrets| KPRIV[(jwt_private.pem)]
    AUTH ---|secrets| KPUB[(jwt_public.pem)]
    URS --> P_AUTH[(Postgres: authdb)]
    AUTH --> P_AUTH

    %% Orders & matching
    GW --> ORD[Orders Service]
    ORD --> P_ORD[(Postgres: ordersdb)]
    ORD --> BUS[(Redpanda/Kafka)]

    BUS --> ME[Matching Engine Service]
    ME -->|TradeExecuted| BUS

    %% Consumers
    BUS --> MDS[Market Data Consumer]
    BUS --> TP[Transaction Processor]
    MDS --> P_MKT[(Postgres: marketdb)]
    TP --> P_TX[(Postgres: transactiondb)]
    TP -->|TransactionRecorded| BUS
    BUS --> PFS[Portfolio Service]
    PFS --> P_PF[(Postgres: portfoliodb)]
  end

  %% Notes
  classDef pub fill:#eef,stroke:#88a,color:#000,stroke-width:1px;
  class GW pub
```

---

## **7. Assumptions & Constraints**

* Local Docker Compose for MVP runtime.
* Each service has its own DB schema.
* Kafka broker is Redpanda (Kafka API compatible).
* Event communication via Redpanda (Kafka API) only.

---

## **8. Risks & Mitigations**

| Risk                 | Mitigation           |
| -------------------- | -------------------- |
| Event loss           | Persistent queues    |
| Redis downtime       | Graceful degradation |
| Single-instance SPOF | K8s in Phase 4       |

---

## **9. Timeline & Milestones**

| Phase | Target   | Deliverables      | GitHub Epic |
| ----- | -------- | ----------------- | ----------- |
| 1     | Aug 2025 | Event bus MVP     | `#12`       |
| 2     | Sep 2025 | Orders + Matching | `#21`       |

---

## **10. Security Mapping**

* ASVS 2.1.1: JWT signature validation (PS256).
* ASVS 3.4.1: Internal-only refresh via `X-Internal-Caller` gate.

---
