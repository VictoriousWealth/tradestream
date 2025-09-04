# 📌 Source of Truth: **Authentication Service**

---

## 1) One-liner — A single, impactful sentence.

Stateless authentication microservice that verifies credentials, issues RSA-signed JWTs (PS256), and exposes a refresh pathway gated for internal callers.

---

## 2) Executive Summary (for recruiters) — Business value + key technical achievements.

* Delivered an **authentication core** for a trading platform: **/login** issues access+refresh tokens; **/refresh** mints short-lived access tokens from long-lived refresh tokens.
* Built for **defense-in-depth**: asymmetric signing (RSA/PS256), **audience/issuer claims**, **token\_type** discrimination, and a **hard gate** on refresh via an interceptor requiring `X-Internal-Caller: api-gateway`.
* **Operationally safe**: Flyway migrations, containerized with Docker, Actuator health, and strict **deny-by-default** security configuration.
* Clean separation of concerns: controller → service (token/JWT) → repository (Postgres via JPA); **stateless** (no sessions) with **idempotent** token issuance behavior on refresh.

---

## 3) What this service does — Responsibilities.

* **Authenticate users** with **BCrypt** password verification against Postgres.
* **Issue JWTs** (access: 15m, refresh: 30d) using **RSA private key**; embed `username`, `sub=userId`, `jti`, `aud`, `iss`, `token_type`, and `scopes`.
* **Refresh access tokens** from valid refresh tokens; **no rotation** of refresh token (access-only rotation).
* **Expose health/info** for runtime checks (`/actuator/health`, `/actuator/info`).
* **Enforce strict ingress policy**: only `GET /`, `GET /actuator/*`, `POST /login`, `POST /refresh` allowed; everything else denied.
* **Gate internal workflows**: `/refresh` requires header `X-Internal-Caller: api-gateway`.

---

## 4) Tech Stack & Key Choices — Table + design rationale highlights.

| Technology                       | Purpose                       | Rationale                                                              |
| -------------------------------- | ----------------------------- | ---------------------------------------------------------------------- |
| **Java 17 + Spring Boot 3**      | Service framework             | Mature ecosystem, records/validation, Actuator, Security               |
| **Spring Security**              | HTTP security & filters       | **Deny-by-default**, stateless, CSRF disabled for API, easy CORS hooks |
| **PostgreSQL + Flyway**          | User store & schema evolution | ACID, UUIDs, versioned migrations (`V1…`, `V2…`)                       |
| **JPA/Hibernate**                | ORM for `users`               | Simple repository (`findByUsername`) with unique index                 |
| **JJWT (io.jsonwebtoken)**       | JOSE/JWT signing & parsing    | **PS256** (RSA-PSS) signatures, compact APIs                           |
| **RSA keypair (Docker secrets)** | Asymmetric crypto             | Private key inside container; public key used for parse/verify         |
| **Docker**                       | Containerization              | Reproducible builds, healthchecks, secret mounts                       |
| **Actuator**                     | Ops visibility                | Liveness/readiness via `/actuator/health`                              |

**Design rationale highlights**

* **Asymmetric signing (PS256)** prevents shared-secret leakage and supports external verification by the API Gateway.
* **Two-token model** (access+refresh) reduces blast radius; **refresh is access-only rotation** to minimize DB/IO.
* **Header-gated refresh** via `InternalCallerInterceptor` ensures refresh path is **not public** even if network boundaries blur.
* **Stateless sessions** enable horizontal scaling without sticky sessions.
* **Issuer/Audience/Type** claims form an **authorization contract** for downstream verifiers.

---

## 5) Architecture at a glance — Primary flows (API + events).

### Flow A: Login (client → Auth)

1. `POST /login` with `{ username, password }`.
2. Repository: `users.findByUsername(username.trim())`.
3. Verify `BCrypt` password; on mismatch ⇒ **401 Unauthorized**.
4. Generate **Access** (TTL 15m) and **Refresh** (TTL 30d) JWTs with `PS256`.
5. Respond `200 OK` with `{ access_token, refresh_token, token_type=Bearer, user_id }`.

### Flow B: Refresh (Gateway → Auth)

1. API Gateway calls `POST /refresh` with `X-Internal-Caller: api-gateway` and body `{ refreshToken }`.
2. Interceptor validates header ⇒ otherwise **403 Forbidden**.
3. Parse & verify refresh JWT with **public key**; enforce `token_type == "refresh"`.
4. Mint **new Access** token (same `sub`, `username`, `scopes`); return **original Refresh**.
5. Respond `200 OK` with new `{ access_token, refresh_token, token_type, user_id }`.

### Flow C: Health/Info (Ops)

* `GET /actuator/health` (readiness/liveness).
* `GET /actuator/info` (build + key path info).

---

## 6) Rules & Invariants — Core business rules enforced.

* **Refresh tokens are not rotated**; only access is refreshed.
* **Refresh endpoint is internal-only**: requires `X-Internal-Caller: api-gateway`.
* **JWT structure**:

  * `alg=PS256`, `typ=JWT`, `iss="authentication-service"`, `aud="api-gateway"`, unique `jti`.
  * `sub` is **UUID** user id; `username` mirrors DB; `token_type ∈ {access, refresh}`; `scopes` is array/string-list.
* **Access TTL = 15m**, **Refresh TTL = 30d**.
* **Deny-all** for any route not explicitly whitelisted.
* **Trimming** of credentials: incoming `username/password` are `.trim()`ed before verification.
* **Error semantics**: 401 on invalid creds or invalid refresh; 404 when user missing; 403 when internal header absent on refresh.

---

## 7) Data Model — Main tables, keys, purpose.

**users**

* **PK**: `id UUID` (default `gen_random_uuid()` via `pgcrypto`).
* **Columns**: `username (unique, not null)`, `password (BCrypt hash, not null)`, `created_at TIMESTAMP DEFAULT now()` *(present in schema; not mapped in entity)*.
* **Indexes/Constraints**: unique on `username`.
* **Purpose**: authoritative user identity & secret verifier.

---

## 8) Configuration (env) — Critical environment variables.

| Key                             | Default                        | Notes                                          |
| ------------------------------- | ------------------------------ | ---------------------------------------------- |
| `SERVER_PORT`                   | `8082`                         | HTTP port                                      |
| `SPRING_DATASOURCE_URL`         | —                              | e.g., `jdbc:postgresql://postgres:5432/authdb` |
| `SPRING_DATASOURCE_USERNAME`    | —                              | DB user                                        |
| `SPRING_DATASOURCE_PASSWORD`    | —                              | DB password                                    |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `validate`                     | Enforce schema sync                            |
| `SPRING_FLYWAY_ENABLED`         | `true`                         | Run migrations                                 |
| `SPRING_FLYWAY_LOCATIONS`       | `classpath:db/migration`       | Migration path                                 |
| `JWT_PRIVATE_KEY_PATH`          | `/run/secrets/jwt_private.pem` | **PKCS#8** “BEGIN PRIVATE KEY”                 |
| `JWT_PUBLIC_KEY_PATH`           | `/run/secrets/jwt_public.pem`  | “BEGIN PUBLIC KEY”                             |
| `MANAGEMENT_PORT`               | `=SERVER_PORT`                 | Actuator on same port by default               |

---

## 9) Operations & Runbook — Run commands, health, and troubleshooting.

**Build & run locally**

```bash
./gradlew clean build -x test
java -jar build/libs/auth-0.0.1-SNAPSHOT.jar
# or Docker:
docker build -t tradestream-auth-service .
docker run -p 8082:8082 \
  -e SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5432/authdb' \
  -e SPRING_DATASOURCE_USERNAME=authuser \
  -e SPRING_DATASOURCE_PASSWORD=authpass \
  -e JWT_PRIVATE_KEY_PATH=/run/secrets/jwt_private.pem \
  -e JWT_PUBLIC_KEY_PATH=/run/secrets/jwt_public.pem \
  -v $PWD/secrets:/run/secrets \
  tradestream-auth-service
```

**Generate RSA keys (PKCS#8 private)**

```bash
# Private (PKCS#8) + Public
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out secrets/jwt_private.pem
openssl rsa -in secrets/jwt_private.pem -pubout -out secrets/jwt_public.pem
```

**Health checks**

```bash
curl -sf http://localhost:8082/actuator/health
curl -s  http://localhost:8082/actuator/info
```

**Seed a user (for local testing)**

```sql
-- bcrypt hash for password 'changeme' (example; generate your own)
INSERT INTO users (username, password) VALUES ('alice', '$2a$10$K8b2m6v2y8z3wQqE4m7wUuXnWb9C5g3qJ3u6y4zM8gZ0S4yq6cVbG');
```

**Troubleshooting**

| Symptom                              | Likely Cause                                 | Fix                                                           |
| ------------------------------------ | -------------------------------------------- | ------------------------------------------------------------- |
| `401 Unauthorized` on `/login`       | Wrong creds or BCrypt mismatch               | Verify username; re-hash password with BCrypt 10+ rounds      |
| `401 Unauthorized` on `/refresh`     | Token invalid/expired or not a refresh token | Ensure `token_type=refresh`; check expiry; re-login           |
| `403 Forbidden` on `/refresh`        | Missing/incorrect header                     | Set `X-Internal-Caller: api-gateway` at Gateway               |
| `500` during startup (key load)      | Wrong key paths or non-PKCS#8 private key    | Mount secrets; regenerate keys with commands above            |
| Build succeeds but `/users` etc. 404 | Deny-by-default, only specific routes exist  | Use only `/login`, `/refresh`, `/actuator/*`                  |
| Flyway mismatch                      | DB not migrated                              | Ensure `SPRING_FLYWAY_ENABLED=true`; check `V1`, `V2` applied |

---

## 10) Extensibility — Future improvements & scaling strategies.

* **Refresh rotation + revocation**: rotate refresh tokens and maintain a **revocation list** (jti blacklist) for compromise containment.
* **Key rotation/JWKS**: publish **JWKS** endpoint and support rolling keys with `kid` headers.
* **OIDC alignment**: expose discovery (`/.well-known/openid-configuration`) + standardized scopes/claims.
* **mTLS between Gateway⇄Auth** or service mesh policy; replace header-gate with network-auth **and** header.
* **Rate limiting + login cooldowns**; account lockout on suspicious activity.
* **Scope derivation from roles** via RBAC tables; admin/user/readonly separation.
* **Audience checks in parser**: enforce `aud`/`iss` on parse for defense-in-depth.
* **Audit trail**: structured logs with jti, sub; optional login history table.

---

## 11) Where this fits in the bigger system — Context.

The Authentication Service is the **identity and token issuer** for the platform: clients authenticate via the **API Gateway**, which forwards login to Auth; downstream microservices rely on the Gateway to validate JWTs and propagate principal data. **Refresh** is strictly **Gateway-initiated** to reduce exposure, while user data remains authoritative in Postgres.

---

## 12) Resume/CV Content (Copy-Paste Ready)

**Impact Bullets**

* Built a **stateless authentication service** (Java 17, Spring Boot, Postgres) issuing **RSA-signed (PS256)** JWTs with access/refresh semantics and strict deny-by-default ingress.
* Implemented an **internal-only refresh flow** using an interceptor that enforces `X-Internal-Caller` to constrain token minting to the API Gateway.
* Operationalized with **Flyway, Actuator, Docker**, healthchecks, and secret-mounted keys for predictable CI/CD and secure runtime.
* Hardened claims model with **issuer/audience/jti/scopes** and **token\_type** discrimination to prevent refresh misuse.

**Scope/Scale (placeholders)**

* Logins/day: **\~N**; peak RPS: **\~X**.
* P95 login latency: **≤ Y ms**; token refresh latency: **≤ Z ms**.
* Uptime: **≥ 99.9%** over rolling 30 days.
* Users in DB: **\~M**.

**Tech Stack Summary**
Java 17, Spring Boot 3, Spring Security, JJWT, PostgreSQL, Flyway, JPA/Hibernate, Docker, Actuator, Lombok

**Cover-letter paragraph**

> I designed and delivered a **stateless Authentication Service** that verifies credentials and issues **RSA-signed JWTs** with robust claims and an internal-only refresh path. The system emphasizes **operational safety** (Flyway, Actuator, Docker) and **defense-in-depth** (deny-by-default security, asymmetric crypto, audience/issuer contracts). I’d bring the same rigor and results-orientation to \<Company/Team>.

**Interview Talking Points**

* **PS256 vs RS256**: why PSS padding; key management, `kid`, rotation plan.
* **Refresh strategy**: access-only rotation vs full rotation; revocation and jti blacklists.
* **Stateless auth trade-offs**: scaling, logout semantics, token invalidation strategies.
* **Defense-in-depth**: internal header gate vs mTLS; where to enforce `aud/iss`.
* **Schema evolution**: Flyway migrations (`V1` create, `V2` rename to `username`, default UUIDs).
* **Error modeling**: mapping auth errors to 401/403/404 and global exception handler.
* **Hardening roadmap**: rate limits, lockouts, OIDC/JWKS, audit trails.

---

## 13) Cheat Sheet (for yourself) — Quick-reference commands, queries, payloads.

**Health**

```bash
curl -sSf http://localhost:8082/actuator/health | jq .
curl -s    http://localhost:8082/actuator/info  | jq .
```

**Login**

```bash
curl -sS -X POST http://localhost:8082/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"changeme"}'
# → {"access_token":"...","refresh_token":"...","token_type":"Bearer","user_id":"<uuid>"}
```

**Refresh (must be called by Gateway / internal tooling)**

```bash
curl -sS -X POST http://localhost:8082/refresh \
  -H "Content-Type: application/json" \
  -H "X-Internal-Caller: api-gateway" \
  -d '{"refreshToken":"<refresh_jwt>"}'
```

**Decode JWT locally (header & claims only)**

```bash
TOKEN=<jwt>
IFS='.' read -r H P S <<< "$TOKEN"; \
  echo "$H" | base64 -d 2>/dev/null | jq .; \
  echo "$P" | base64 -d 2>/dev/null | jq .
```

**SQL: check users**

```sql
SELECT id, username, length(password) AS hash_len, created_at FROM users ORDER BY created_at DESC LIMIT 5;
```

**Generate RSA keys**

```bash
mkdir -p secrets
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out secrets/jwt_private.pem
openssl rsa -in secrets/jwt_private.pem -pubout -out secrets/jwt_public.pem
```

**Docker run (with secrets mounted)**

```bash
docker run -p 8082:8082 --env-file .env -v $PWD/secrets:/run/secrets tradestream-auth-service
```

**Security quick facts**

* Allowed routes: `GET /`, `GET /actuator/health`, `GET /actuator/health/**`, `GET /actuator/info`, `POST /login`, `POST /refresh`.
* Everything else: **denied**. Session policy: **STATELESS**. CSRF: **disabled** (API).
* Refresh requires: `X-Internal-Caller: api-gateway`.

---

## Appendix: API contracts (sample)

**/login → 200 OK**

```json
{
  "access_token": "<jwt>",
  "refresh_token": "<jwt>",
  "token_type": "Bearer",
  "user_id": "8a1c1c7a-6d8e-4a8a-9fd2-2b2f2a5a5e90"
}
```

**/refresh → 200 OK**

```json
{
  "access_token": "<new_access_jwt>",
  "refresh_token": "<same_refresh_jwt>",
  "token_type": "Bearer",
  "user_id": "8a1c1c7a-6d8e-4a8a-9fd2-2b2f2a5a5e90"
}
```

---

