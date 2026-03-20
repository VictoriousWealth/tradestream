# Devlog

## 2025-09-05 05:43:55 +0100 — commit `4ae8ee0`

Commit `4ae8ee00c44d2676358234a8215493bacd04f845` is a mixed runtime-and-documentation pass centered on the API gateway. The changed-file set in `context.txt` and `changed-files.txt` shows that the work touched the gateway’s README, JWT decoder configuration, route YAML, Compose wiring, a new smoke-test script, one cache-behavior fix in market data, and two binary Gradle cache files under `services/matching-engine/` and `services/orders-service/`. Those `.gradle/nb-cache/subprojects.ser` changes are binary in `diff.patch`, so no reliable engineering meaning can be extracted from them beyond the fact that local project metadata changed.

The clearest runtime hardening in this commit is the addition of `api-gateway/src/main/java/com/tradestream/gateway/security/JwtDecoderConfig.java`. Before this file existed, the gateway already had resource-server configuration in `application.yml`, but the evidence does not show an explicit Java bean forcing a particular signature algorithm during key-based verification. After `4ae8ee0`, the gateway creates a `ReactiveJwtDecoder` from the mounted RSA public key and explicitly sets `.signatureAlgorithm(SignatureAlgorithm.PS256)`. This is reinforced in `api-gateway/src/main/resources/application.yml`, which adds `jws-algorithm: PS256` under the resource-server JWT configuration. In before → after terms, JWT validation moved from “use the configured public key” to “use the configured public key and require PS256 specifically,” which closes off ambiguity around accepted signing algorithms.

The same YAML edit also corrects and sharpens route behavior. The `orders` route previously relied on the comment “orders-service already exposes /orders/**” and only applied a circuit breaker. After this commit, the route explicitly adds `StripPrefix=1` and `RewritePath=/api/orders/(?<p>.*),/orders/${p}`. That matters because it converts the orders route from an implied path compatibility assumption into an explicit rewrite contract owned by the gateway. The market-data route changes are even more concrete. Before the commit, the gateway matched `/api/market-data/**` and rewrote it to `/candles/${p}`. After the commit, the predicate narrows to `/api/market-data/candles/**`, and the rewrite changes to `/${p}`. Given the route input shape, this means the gateway now forwards `/api/market-data/candles/...` directly to `/candles/...` instead of prepending an extra `/candles` segment. This is a corrective change rather than a new feature: the public path and backing service path are brought into alignment so the route no longer appears to double-prefix candle requests.

The tail end of `application.yml` also adds verbose security logging:

* `org.springframework.security: DEBUG`
* `org.springframework.security.oauth2: DEBUG`
* `org.springframework.security.oauth2.server.resource: DEBUG`

This does not change business behavior directly, but it is a practical debugging step. Before this edit, the gateway configuration exposed no special security log levels in the observed YAML. After it, diagnosing JWT parsing, scope extraction, and authentication failures becomes easier, which fits with the same commit’s addition of a dedicated smoke-test script.

`docker-compose.yml` is adjusted to match the more explicit JWT and Redis configuration model. Before the commit, the gateway container environment included `REDIS_HOST` and `JWT_PUBLIC_KEY_LOCATION`. After the commit, those become Spring-native property bindings: `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`, `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_PUBLIC_KEY_LOCATION`, and `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWS_ALGORITHM`. This is a subtle but real cleanup. The runtime stops depending on ad hoc environment names and starts wiring the gateway directly through the exact Spring property namespace the application consumes.

`gateway_smoke.sh` is entirely new in this commit and is one of the strongest pieces of evidence for how the gateway was expected to behave on 2025-09-05. The script is not a generic curl scratchpad. It defines a specific validation path through the system:

1. Check gateway health at `/actuator/health`.
2. Register a user via `POST /api/users/register`, accepting `200` on first run or `409` on repeats.
3. Login through `POST /api/auth/login` and parse `access_token`, `refresh_token`, and `user_id`.
4. Call a protected endpoint without a bearer token and expect `401`.
5. Call a protected transactions endpoint with a bearer token.
6. Refresh the token through `POST /api/auth/refresh`.
7. Check market-data access without a token, then with a token.
8. Check portfolio positions with a token.

This script is useful evidence because it makes the gateway contract concrete. It also exposes some first-cut mismatch and uncertainty. The transactions call uses `/api/transactions/$USER_ID?page=0&size=1`, while the gateway README added in the same commit later documents transactions more generally as `GET /api/transactions` with paging parameters. The market-data probe in the script calls `/api/market-data/$TICKER/latest?interval=1m`, but the gateway route YAML in this same commit only matches `/api/market-data/candles/**`. That means the smoke script and route config are not perfectly synchronized inside the same chunk. The safest interpretation is that the script was introduced as a practical integration harness while parts of the public route shape were still settling.

`services/market-data-consumer/src/main/java/com/tradestream/market_data_consumer/service/CandleQueryService.java` receives a targeted cache correction. Before `4ae8ee0`, the `latest(...)` method was annotated with `@Cacheable(cacheNames = "latest", key = "...")`, and the method returned `null` when no candle existed. After the commit, the annotation adds `unless = "#result == null"`. The inline comment says null values are not cached because `cache-null-values=false`, but the annotation still makes that behavior explicit at the method boundary. In before → after terms, the code moves from implicitly relying on cache configuration not to retain misses to explicitly preventing `null` results from being cached at the call site.

The biggest diff by line count in this morning commit is `api-gateway/README.md`, which expands from a short service description into a very large “Ultimate Source of Truth” document. The rewrite is specific in several ways that line up with the runtime changes in the same commit. Before the rewrite, the README described the gateway as a secure request router and token validator, referred to access-token verification, refresh-token decryption, AES-256-GCM, JWE wrapping, secure cookies, and HTTPS-only handling. After `4ae8ee0`, the README reframes the gateway around PS256 JWT validation, Spring Cloud Gateway routing, Redis-backed IP rate limiting, Resilience4j circuit breakers, `X-Request-Id` injection, and actuator observability. It also adds route-by-route examples for auth, registration, orders, transactions, portfolio, and market data.

This rewrite does more than add detail; it replaces one security model with another in the documentation. The earlier version still described the gateway in terms of decrypting JWE refresh tokens and handling HTTP-only cookie flows. The new version documents signed JWT handling at the edge, a refresh-token body payload, and Redis token-bucket rate limiting. That is materially closer to the gateway code observable in `application.yml`, `JwtDecoderConfig.java`, and `gateway_smoke.sh`. There is still some uncertainty in the README because it includes a large amount of recruiter-facing, operational, and example content that cannot be fully verified from this single chunk alone. But the core shift from older JWE/cookie framing toward PS256 resource-server enforcement is clearly grounded.

`copy_to_clipboard_all_readmes.sh` also appears for the first time in this commit. The script changes directory to `~/tradestream`, concatenates `./services/**/*.md` into `pbcopy`, and prints a message claiming it copied all READMEs except the API gateway and root README. This is not product behavior, but it is evidence that documentation consolidation became an active maintenance activity alongside the large README rewrites. The path and message are specific, and the exclusion of root/API-gateway docs is consistent with the files that were being reworked separately.

Chronologically, `4ae8ee0` looks like the point where the gateway stopped being merely present and started being tightened around real usage: PS256 became explicit rather than implicit, route rewrites became concrete instead of assumption-based, market-data routing was corrected toward the actual candle API shape, Compose environment variables were aligned to Spring property names, and a smoke script was added to exercise the expected ingress flow end to end. The large gateway README rewrite in the same commit documents that newer reality, even if some example calls and route descriptions still carry minor inconsistencies.

## 2025-09-05 18:55:16 +0100 — commit `a81176b`

Commit `a81176b3253cfabcc61fbf9d6aeb77e945135c52` is entirely documentation-facing in the allowed evidence, but it is still a substantive repository change because it rewrites the project’s public narrative at the root. The only files changed are `CVREADME.md` and `README.md`.

`CVREADME.md` is added from scratch in this commit. Its content is not a duplicate of the older root README; it is a recruiter-oriented system overview that describes the project as a “High-Performance Trading Microservices Platform,” lays out the gateway-first architecture, enumerates services and ports, includes a data-flow diagram, and gives a concise quickstart. The descriptions in this file are notably more aligned with the gateway and service implementations present by early September 2025 than the older root README had been. It calls out:

* API Gateway as the only public entry point.
* PS256 JWT validation at the edge.
* Orders → Matching Engine → Transaction Processor → Portfolio flow.
* Redis-backed market-data caching.
* Kafka/Redpanda as the message bus.

This matters because before `a81176b`, there was no dedicated recruiter-facing repository summary in the evidence set. After the commit, the repo has a top-level narrative optimized for readers who need an accurate high-level picture without diving into every service README.

The larger correction in this evening commit is the root `README.md` rewrite. Before `a81176b`, the root README still described the repository as “TradeStream — Real-Time Financial Data Processor,” used a broad portfolio framing, and contained multiple statements that no longer matched the codebase reflected elsewhere in this chunk and the previous day’s gateway work. The removed sections explicitly referenced:

* “Kafka or RabbitMQ (configurable)”
* “JWS & JWE (JWT Signed & JWT Encrypted)”
* AWS Lightsail deployment
* Kubernetes and Terraform badges/stack references as if they were part of the technology picture
* old architecture descriptions centered on fewer services and earlier documentation structure

After `a81176b`, the root README is rewritten to say “TradeStream — Distributed Trading Platform” and to distinguish clearly between what exists now and what is future work. The new “What’s the current status?” table is especially important because it corrects several stale claims directly:

* `RabbitMQ option` is marked `❌` and explicitly says Kafka/Redpanda only.
* `JWE (encrypted JWTs)` is marked `❌` and explicitly says signed JWT (PS256) only.
* `Kubernetes/Terraform` are marked as planned, not present.
* The actual service inventory now includes API Gateway, User Registration, Authentication, Orders, Matching Engine, Transaction Processor, Portfolio Service, and Market Data Consumer.

That before → after shift is substantial. The old README mixed current implementation, aspirational infrastructure, and outdated security/message-bus assumptions. The new README tries to separate implemented scope from roadmap scope and ties its claims back to the repository’s actual service layout and route map.

The rest of the root README follows the same correction pattern. The architecture section now presents the gateway as the single perimeter entry, shows Kafka/Redpanda as the sole event bus, and maps concrete routes to concrete services. The “Tech stack (actual)” section removes older ambiguous or misleading wording and replaces it with the technologies visible elsewhere in the repo at this stage: Java 17/21, Spring Boot 3, Spring Cloud Gateway, Project Reactor, PostgreSQL with Flyway, Redis, Resilience4j, Docker Compose, and PS256 JWTs. The quickstart section centers on generating RSA keys, bringing the stack up with Docker Compose, and exercising registration/login/order/portfolio/market-data flows via the gateway. The troubleshooting and security sections also align more directly with the gateway and service behavior described in the earlier 2025-09-05 morning commit.

There are still some small uncertainties and rough edges in the evening documentation pass. `CVREADME.md` is created with uppercase letters, while the rewritten root `README.md` later refers to `cvreadme.md` in lowercase in the repo-layout section. The diff evidence does not show whether this mismatch is intentional, an OS-dependent non-issue, or a documentation typo. Similarly, the new docs are more accurate than the old ones, but they remain documentation: they describe system behavior broadly, whereas only the code changes from `4ae8ee0` directly prove runtime semantics in this chunk.

Chronologically, `a81176b` reads as the documentation cleanup pass that follows the morning’s gateway tightening. Once the gateway had explicit PS256 enforcement, corrected route rewrites, a smoke script, and more aligned compose wiring, the root-level docs were rewritten to stop advertising RabbitMQ, JWE, and Lightsail-era assumptions as if they were part of the present system. The repository’s public story became much closer to the code that now existed: gateway-first, Kafka/Redpanda-only, signed-JWT-based, and composed of a larger set of collaborating microservices than the earlier README admitted.
