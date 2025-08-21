# 📌 The Ultimate Source of Truth: **User Registration Service** (best-of-both combined)

---

## One-liner — A single, impactful sentence.

Internal-only Spring Boot microservice that validates and persists new users with BCrypt, enforcing a deny-by-default + zero-trust ingress model via an internal-caller contract.

---

## Executive Summary (for recruiters)

* Built a **security-hardened, single-purpose registration service** that acts as the transactional core for identity onboarding; **API Gateway is the sole caller** (internal header contract) for a **zero-trust, defense-in-depth** posture.&#x20;
* Guarantees **credential safety** (BCrypt) and **idempotent creates** using DB-enforced unique usernames; strict DTO validation on length/format.&#x20;
* **Operationally sound**: versioned **Flyway** migrations (email→username rename, UUID defaults), **Actuator** health probes, **Docker/Compose** with private network segmentation.
* Clean boundaries: write-only registration here; authentication/JWT issuance handled by a separate Auth service for least privilege and independent scaling.&#x20;

---

## What this service does

* ✅ **Registers users** via `POST /register` (internal only; gateway adds `X-Internal-Caller`).&#x20;
* 🧾 Validates input (`username` 3–255, `password` 6–255).
* 🔐 **Hashes** passwords with **BCrypt**; plaintext never stored.&#x20;
* 🗄️ Persists to **PostgreSQL**; **unique(username)** prevents duplicates; returns **409** on conflict.&#x20;
* 🩺 Exposes `GET /actuator/health` for liveness/readiness.
* 🚫 **Deny-by-default** security policy; only `/`, `/register`, `/actuator/health|info`, and `OPTIONS` (CORS preflight) are allowed.

---

## Tech Stack & Key Choices

| Technology                          | Purpose                       | Rationale                                                                         |
| ----------------------------------- | ----------------------------- | --------------------------------------------------------------------------------- |
| **Java 17 + Spring Boot 3**         | Service framework             | Mature ecosystem, rapid delivery, production hardening                            |
| **Spring Security 6**               | HTTP authz & filters          | Deny-all baseline, precise allowlist, CSRF disabled for APIs                      |
| **Spring MVC `HandlerInterceptor`** | Internal-caller gate          | Enforces `X-Internal-Caller: api-gateway` at `/register` for zero-trust ingress.  |
| **Spring Data JPA (Hibernate)**     | ORM / repositories            | Transactional persistence with concise repository pattern                         |
| **BCryptPasswordEncoder**           | Credential hashing            | Industry standard, adaptive cost, salt handling.                                  |
| **PostgreSQL 15 + Flyway**          | ACID store & schema evolution | Versioned migrations; email→username rename; server-side UUID default             |
| **Actuator**                        | Health endpoints              | Simple container health checks                                                    |
| **Docker + docker-compose**         | Packaging & runtime           | Reproducible deploy; **private network** isolation via compose.                   |

**Design rationale highlights**

* **Defense-in-depth / zero-trust**: network segmentation + Spring Security deny-all + app-level interceptor.&#x20;
* **Idempotency via DB**: `unique(username)` is the idempotency key for create.
* **Thin transactional core**: single atomic write per registration; minimal business logic on hot path.
* **Forward-compatible schema**: migrations cover rename + UUID generation; DB maintains `created_at`.

---

## Architecture at a glance

**Flow 1: Successful Registration (Gateway → Service → DB)**

1. End-user submits username/password to UI → **API Gateway** authenticates/validates and forwards on **private network**, adding `X-Internal-Caller: api-gateway`.
2. `InternalCallerInterceptor` verifies header, then controller validates DTO.
3. Repo checks for username; if taken → **409**.
4. Hash with BCrypt; persist user; return **200 OK**.&#x20;

**Flow 2: Rejected Registration (Direct External Caller)**

1. Request hits service **without** the internal header.
2. Interceptor blocks immediately → **403 FORBIDDEN**; controller not reached.&#x20;

---

## Rules & Invariants

* `username` **required**, **unique**, length **\[3..255]**; `password` **required**, length **\[6..255]**.
* **Plaintext passwords are never stored**; only BCrypt hashes.&#x20;
* `/register` requires `X-Internal-Caller: api-gateway`; otherwise **403**.&#x20;
* Non-whitelisted routes are denied by default.

---

## Data Model

**Table: `users`**

* **PK**: `id UUID` (server-side default `gen_random_uuid()` from migration)
* **Columns**:

  * `username VARCHAR(255) UNIQUE NOT NULL` (renamed from `email` in V2 migration)
  * `password VARCHAR(255) NOT NULL` (BCrypt hash)
  * `created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`
* **Purpose**: system-wide **source of truth** for registered identities.&#x20;

---

## Configuration (env)

| Key                             | Default / Example                        | Notes                                      |
| ------------------------------- | ---------------------------------------- | ------------------------------------------ |
| `SERVER_PORT`                   | `8081`                                   | Dockerfile exposes 8081; compose sets this |
| `SPRING_DATASOURCE_URL`         | `jdbc:postgresql://postgres:5432/authdb` | JDBC URL                                   |
| `SPRING_DATASOURCE_USERNAME`    | `authuser`                               | DB user                                    |
| `SPRING_DATASOURCE_PASSWORD`    | `authpass`                               | DB pass                                    |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `validate`                               | Enforce schema correctness                 |
| `SPRING_FLYWAY_ENABLED`         | `true`                                   | Apply migrations                           |
| `SPRING_FLYWAY_LOCATIONS`       | `classpath:db/migration`                 | Migration path                             |
| `MANAGEMENT_PORT`               | *(unset)*                                | Health served on same port                 |

---

## Operations & Runbook

**Build & run (local):**

```bash
./gradlew clean build -x test
java -jar build/libs/user-registration-service-0.0.1-SNAPSHOT.jar \
  --server.port=8081 \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/authdb \
  --spring.datasource.username=authuser \
  --spring.datasource.password=authpass
```

**Docker:**

```bash
docker build -t user-registration-service:dev .
docker run -p 8081:8081 --env-file .env user-registration-service:dev
```

**Compose (project root):**

```bash
docker compose up -d user-registration-service postgres
```

**Health check:**

```bash
curl -f http://localhost:8081/actuator/health
```

**Troubleshooting**

| Symptom                                    | Likely Cause                                                                | Fix                                                               |
| ------------------------------------------ | --------------------------------------------------------------------------- | ----------------------------------------------------------------- |
| All registrations fail with **403**        | Missing/wrong `X-Internal-Caller` header at gateway                         | Ensure header name/value exactly match interceptor expectations.  |
| **409 Conflict** when username “seems new” | Username already exists; possible case-sensitivity mismatch in expectations | Pick a different username; confirm DB collation & comparisons.    |
| Service starts but health is **DOWN**      | DB unavailable/creds wrong; migration failed                                | Validate DB envs; inspect `flyway_schema_history` for errors.     |

---

## Extensibility

* **Email/phone verification** (event: `user.registered.v1` or gateway callback).&#x20;
* **Rate limiting / anti-bot** at the gateway (e.g., IP throttling, CAPTCHA).&#x20;
* **Richer profile fields** (email/full name) with validation.&#x20;
* **SSO/OIDC handoff** via gateway → internal provisioning endpoint.&#x20;

---

## Where this fits in the bigger system

The service is the **write-only, transactional core** for user creation. **API Gateway** is the exclusive ingress. **Auth Service** reads the `users` table to verify credentials and issue JWTs; it **never writes**. This separation minimizes attack surface and decouples scaling/ownership boundaries.&#x20;

---

## Resume/CV Content (Copy-Paste Ready)

### Impact Bullets

* Built a **secure, internal-only registration microservice** (Java 17, Spring Boot, Postgres) with **deny-by-default** policy and interceptor-gated zero-trust ingress.&#x20;
* Implemented **BCrypt credential storage** and **idempotent creates** via DB uniqueness; enforced DTO validation and clear **409/403** semantics.&#x20;
* Operationalized with **Flyway**, **Actuator**, and **Docker/Compose**; executed schema rename (email→username) and UUID default without downtime.
* Established **clear service boundaries**: write-only registration vs. read-only authentication/JWT issuance.&#x20;

### Scope/Scale (placeholders)

* Throughput: \~**N** registrations/sec (peak)
* p95 latency (`POST /register`): **X** ms
* Availability: **99.9%** over **Y** months
* Users onboarded: **Z**+

### Tech Stack Summary (single line)

Java 17, Spring Boot 3, Spring Security 6, Spring Data JPA (Hibernate), PostgreSQL 15, Flyway, Actuator, Docker.&#x20;

### Cover-letter paragraph

> I engineered a **security-hardened registration service** that acts as the transactional core for identity onboarding. Using **Spring Security + an internal-caller contract**, it implements a zero-trust ingress model, validates inputs, and persists credentials with **BCrypt** and database-enforced idempotency. With **PostgreSQL + Flyway**, I emphasized reliability, schema evolution, and clean boundaries—rigor I’m excited to bring to \<Company/Team>.&#x20;

---

## Interview Talking Points

* **Defense-in-depth** vs. relying solely on network rules; why app-level checks matter.&#x20;
* **Interceptor vs. Security Filter** trade-offs for this ingress contract.&#x20;
* **BCrypt** (adaptive cost, salts) vs. fast hashes; operational cost-factor migration.&#x20;
* **Schema evolution** with Flyway in shared DBs; idempotent scripts and baselining.&#x20;
* **Failure modes**: DB outage, duplicate usernames, 403/409 mapping, logging redaction.

---

## Cheat Sheet (for yourself)

**Register user (simulate gateway):**

```bash
curl -X POST http://localhost:8081/register \
  -H "Content-Type: application/json" \
  -H "X-Internal-Caller: api-gateway" \
  -d '{"username":"alice","password":"S3cureP@ss!"}'
# -> 200 OK  ("All good.")
```

**Common failure cases:**

```bash
# Missing internal header -> 403
curl -X POST http://localhost:8081/register \
  -H "Content-Type: application/json" \
  -d '{"username":"bob","password":"short"}'

# Duplicate username -> 409
curl -X POST http://localhost:8081/register \
  -H "Content-Type: application/json" \
  -H "X-Internal-Caller: api-gateway" \
  -d '{"username":"alice","password":"whatever"}'
```

**Health:**

```bash
curl -f http://localhost:8081/actuator/health
```

**DB quick checks (psql):**

```sql
-- Was the user created?
SELECT id, username, created_at FROM users
WHERE username = 'alice';

-- Count users:
SELECT COUNT(*) FROM users;

-- Verify uniqueness path:
EXPLAIN SELECT 1 FROM users WHERE username = 'alice';
```

**`.env` template:**

```dotenv
SERVER_PORT=8081
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/authdb
SPRING_DATASOURCE_USERNAME=authuser
SPRING_DATASOURCE_PASSWORD=authpass
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
SPRING_FLYWAY_ENABLED=true
SPRING_FLYWAY_LOCATIONS=classpath:db/migration
```

**Useful cURL header reminder:** `-H "X-Internal-Caller: api-gateway"` (required)&#x20;

---

