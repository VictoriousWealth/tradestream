# Devlog

## 2025-08-21 04:30:57 +0100 — commit `538f9a694ec75dd98640778c4a152777c5bfe518`

This chunk is a single follow-up hardening commit centered on identity services and on making the newly introduced `portfolio-service` legible and testable.

The changed-file set falls into four clear groups:

- `authentication-service` runtime/security/schema cleanup
- `user-registration-service` security, validation, schema, and documentation upgrades
- `portfolio-service` documentation and a small Kafka-listener config cleanup
- a new end-to-end script focused specifically on portfolio behavior

There are also two `.gradle/nb-cache/subprojects.ser` changes under `matching-engine` and `orders-service`, but they are binary IDE/build cache updates and carry no source-level behavior in this chunk.

## The first visible move in the chunk was to add a dedicated end-to-end script for the portfolio path

[`e2e_portfolio_service.sh`](e2e_portfolio_service.sh) is new in this commit. That is important because it shows the portfolio side of the platform had become concrete enough to deserve its own focused scenario runner rather than being treated as a vague future service.

### The script defines a portfolio-specific acceptance path that did not exist in this chunk before

The script is built around:

- `orders-service` at `http://orders-service:8085`
- `portfolio-service` at `http://portfolio-service:8087`
- Redpanda topic interaction through `rpk`

and walks through these stages:

1. health check for `portfolio-service`
2. seed a crossing trade via `orders-service`
3. watch for two `transaction.recorded.v1` ledger rows
4. query portfolio positions for buyer and seller
5. create a realization scenario by selling part of a long position
6. query post-realization positions again
7. inject a synthetic `trade.executed.v1` message as a mark-price event
8. inspect positions again for potential unrealized PnL effects
9. query an unknown user and expect an empty portfolio

This is detailed behavioral evidence, not just smoke-test scaffolding. Before this commit, the chunk shows no such dedicated portfolio scenario script. After it, the project had a concrete operational recipe for:

- opening positions
- realizing PnL
- probing mark/unrealized behavior
- checking empty-user behavior

### The script also exposes where evidence is stronger in docs than in implementation

The script tries endpoints like:

- `GET /portfolio/{userId}/positions`
- `GET /portfolio/{userId}/positions/{ticker}`
- `GET /portfolio/{userId}/summary`

and it explicitly labels one endpoint as optional:

- if the single-ticker endpoint is not implemented, it prints a skip message

It also treats synthetic `trade.executed.v1` production as a possible way to set a last price “if portfolio-service consumes trade.executed.v1 for marks”.

That wording matters. It signals uncertainty from the developer’s own test harness:

- the README may describe a richer capability set
- the script still probes defensively because some parts may not yet be implemented in code

This becomes important when reading the new `portfolio-service` README added in the same commit.

## Authentication-service was hardened for health checks, stricter security matching, and username-based schema alignment

The `authentication-service` changes are small in file count but meaningful in intent.

Changed files:

- [`services/authentication-service/build.gradle`](services/authentication-service/build.gradle)
- [`services/authentication-service/src/main/java/com/tradestream/auth/config/SecurityConfig.java`](services/authentication-service/src/main/java/com/tradestream/auth/config/SecurityConfig.java)
- [`services/authentication-service/src/main/java/com/tradestream/auth/controller/UserController.java`](services/authentication-service/src/main/java/com/tradestream/auth/controller/UserController.java)
- [`services/authentication-service/src/main/java/com/tradestream/auth/service/TokenService.java`](services/authentication-service/src/main/java/com/tradestream/auth/service/TokenService.java)
- [`services/authentication-service/src/main/resources/application.yml`](services/authentication-service/src/main/resources/application.yml)
- [`services/authentication-service/src/main/resources/db/migration/V2__rename_email_to_username.sql`](services/authentication-service/src/main/resources/db/migration/V2__rename_email_to_username.sql)

### Actuator was added, and the service became explicitly health-checkable on its real port

[`build.gradle`](services/authentication-service/build.gradle) gained:

- `implementation 'org.springframework.boot:spring-boot-starter-actuator'`

[`application.yml`](services/authentication-service/src/main/resources/application.yml) changed:

- `server.port` from `${SERVER_PORT:8080}` to `${SERVER_PORT:8082}`

and changed `info.app` from a flat string into a nested structure:

- `name: auth-service`
- `description: Authentication microservice`
- `version: 0.0.1`

This is a concrete before -> after runtime cleanup:

- before: the auth service still defaulted to the generic Spring `8080` port in this file
- after: it aligns with the actual service port used elsewhere in the stack and includes actuator metadata intended for health/info inspection

### Security rules were narrowed and made explicit for actuator and POST-only login/refresh

[`SecurityConfig.java`](services/authentication-service/src/main/java/com/tradestream/auth/config/SecurityConfig.java) changed the allowlist from:

- `GET /`, `/error`
- `/login`, `/refresh`

to:

- `GET /`, `/actuator/health`, `/actuator/health/**`, `/actuator/info`
- `/error`
- `POST /login`, `POST /refresh`

while still denying all other routes by default.

This is a meaningful tightening:

- before: login/refresh were permitted without HTTP-method restriction
- after: only POST requests are allowed for those auth endpoints

It is also an operations change:

- before: health endpoints were not explicitly opened here
- after: actuator health/info are intentionally public for service monitoring

### Login input sanitization shifted from destructive space removal to normal trimming

[`UserController.java`](services/authentication-service/src/main/java/com/tradestream/auth/controller/UserController.java) changed:

- `replace(" ", "")` -> `trim()`

for both `username` and `password`.

That is a subtle but important behavioral correction.

Before this commit:

- internal spaces anywhere in the credential were removed completely

After this commit:

- only leading/trailing whitespace is removed

That means credentials like `"my user"` or passwords containing spaces are no longer silently mutated by deleting internal characters. This is a safer and more predictable treatment of user input.

### TokenService corrected its date type import

[`TokenService.java`](services/authentication-service/src/main/java/com/tradestream/auth/service/TokenService.java) replaced:

- `java.sql.Date`

with:

- `java.util.Date`

The diff does not show the exact usage site in the truncated patch, so the safest statement is narrow:

- this commit corrected the imported `Date` type in the token service away from the SQL date class and toward the general-purpose Java util date class

Given the file’s role and the surrounding imports (`Instant`, crypto/key classes, JWT-related types), this strongly suggests token-time handling cleanup, but the exact call path is not fully visible in the supplied patch excerpt.

### Auth schema migrated from `email` to `username`

New migration:

- [`V2__rename_email_to_username.sql`](services/authentication-service/src/main/resources/db/migration/V2__rename_email_to_username.sql)

This migration does three things:

1. renames `users.email` to `users.username`
2. ensures a unique constraint named `users_username_key` exists
3. enables `pgcrypto` and sets `id` default to `gen_random_uuid()`

This is one of the clearest structural changes in the chunk.

Before the migration:

- the auth DB schema still used `email` as the column name, even though current runtime code paths in the repo had already shifted toward usernames

After the migration:

- the table schema matches the application’s `username` vocabulary
- inserts can rely on server-side UUID generation as well

This schema rename is mirrored in `user-registration-service` in the same commit, which shows the identity stack was being normalized end-to-end rather than piecemeal.

## User-registration-service was upgraded from a simple write endpoint into a more explicitly validated and operationally hardened internal service

The `user-registration-service` changes are broader than the auth service changes and are tightly coherent.

Changed files:

- [`services/user-registration-service/build.gradle`](services/user-registration-service/build.gradle)
- [`services/user-registration-service/README.md`](services/user-registration-service/README.md)
- [`services/user-registration-service/src/main/java/com/tradestream/user_registration_service/config/SecurityConfig.java`](services/user-registration-service/src/main/java/com/tradestream/user_registration_service/config/SecurityConfig.java)
- [`services/user-registration-service/src/main/java/com/tradestream/user_registration_service/controller/UserController.java`](services/user-registration-service/src/main/java/com/tradestream/user_registration_service/controller/UserController.java)
- [`services/user-registration-service/src/main/java/com/tradestream/user_registration_service/dto/UserDTO.java`](services/user-registration-service/src/main/java/com/tradestream/user_registration_service/dto/UserDTO.java)
- [`services/user-registration-service/src/main/java/com/tradestream/user_registration_service/model/User.java`](services/user-registration-service/src/main/java/com/tradestream/user_registration_service/model/User.java)
- [`services/user-registration-service/src/main/resources/db/migration/V2__rename_email_to_username.sql`](services/user-registration-service/src/main/resources/db/migration/V2__rename_email_to_username.sql)

### The build file was expanded from a minimal starter set to a more complete service baseline

[`build.gradle`](services/user-registration-service/build.gradle) gained:

- `spring-boot-starter-validation`
- `spring-boot-starter-actuator`
- `flyway-core`
- `flyway-database-postgresql`

and, notably, `spring-boot-starter-actuator` appears twice in the dependencies list.

That duplication is worth recording because it means the build file was clearly being patched quickly rather than carefully deduplicated in the same pass.

The before -> after direction is still clear:

- before: registration service had web/security/JPA but not explicit validation, actuator, or Flyway support in this file
- after: it is configured more like a production microservice with validation, health endpoints, and versioned schema migrations

### Security shifted from a very small allowlist to a clearer deny-by-default API posture

[`SecurityConfig.java`](services/user-registration-service/src/main/java/com/tradestream/user_registration_service/config/SecurityConfig.java) changed substantially.

Before this commit, the visible policy was:

- disable CSRF
- enable CORS defaults
- permit `GET /`, `/error`
- permit `/register`
- deny everything else

After this commit, the policy becomes more explicit:

- permit `/error`
- permit `GET /`, `/actuator/health`, `/actuator/info`
- permit all `OPTIONS /**` for CORS preflight
- permit `POST /register`
- deny everything else

It also adds a bean:

- `PasswordEncoder passwordEncoder() -> new BCryptPasswordEncoder()`

This is important for two reasons:

1. registration now has an explicit security posture aligned with internal-service health checks and CORS preflight behavior
2. password hashing is now provided as an injectable bean rather than instantiated ad hoc inside the controller

### Registration moved from “always save” to validated, conflict-aware writes

[`UserController.java`](services/user-registration-service/src/main/java/com/tradestream/user_registration_service/controller/UserController.java) changed in several coordinated ways.

Before this commit:

- controller instantiated `new BCryptPasswordEncoder()` inline
- request body was not `@Valid`
- there was no explicit duplicate-username check in the method shown

After the patch:

- `PasswordEncoder` is injected
- request body is `@Valid @RequestBody`
- controller checks `userRepository.findByUsername(...)`
- duplicate username returns `409 CONFLICT` with `"Username already exists."`
- otherwise the password is encoded and user persisted

This is a concrete correctness change.

Before:

- duplicate handling depended entirely on database behavior or unshown exception flow

After:

- the service explicitly detects duplicates and maps them to a stable HTTP-level conflict response before attempting the save

### DTO validation became explicit

[`UserDTO.java`](services/user-registration-service/src/main/java/com/tradestream/user_registration_service/dto/UserDTO.java) gained:

- `@NotBlank @Size(min = 3, max = 255)` on `username`
- `@NotBlank @Size(min = 6, max = 255)` on `password`

That makes input constraints visible in code rather than only in documentation or database rules.

Before this commit:

- there is no evidence in this chunk of request-level Bean Validation on the registration DTO

After:

- invalid username/password lengths can be rejected at controller binding time

### User entity UUID generation strategy was made explicit

[`model/User.java`](services/user-registration-service/src/main/java/com/tradestream/user_registration_service/model/User.java) changed:

- `@GeneratedValue` -> `@GeneratedValue(strategy = GenerationType.UUID)`

This aligns the entity with modern Hibernate/Spring Boot UUID generation semantics and with the new migration that also sets a DB-level UUID default.

That duality is worth noting:

- app side: JPA generates UUIDs explicitly
- DB side: migration also enables `gen_random_uuid()` default

The evidence does not show whether both paths were needed simultaneously or simply added for safety, but both are present after this commit.

### Registration schema also migrated from `email` to `username`

The registration service got the same new Flyway migration name and content as auth:

- [`V2__rename_email_to_username.sql`](services/user-registration-service/src/main/resources/db/migration/V2__rename_email_to_username.sql)

It:

1. renames `email` to `username`
2. ensures uniqueness on `username`
3. enables `pgcrypto`
4. sets `id` default to `gen_random_uuid()`

This parallel migration strongly indicates the developer was standardizing the two identity-related services together.

Before this commit:

- naming drift still existed between schema history and current username-based code/API expectations

After this commit:

- both services have an explicit migration path aligning stored schema with username-based logic

### The README was rewritten into a much more ambitious “source of truth” document

[`services/user-registration-service/README.md`](services/user-registration-service/README.md) was transformed from a short project README into a long, recruiter-oriented and ops-oriented document.

The new README now covers:

- one-liner and executive summary
- rules and invariants
- data model
- configuration/env table
- runbook and troubleshooting
- extensibility ideas
- system role relative to gateway and auth service
- CV bullets, cover-letter paragraph, interview talking points, and cheat sheet

This mirrors the documentation style already seen in `market-data-consumer` and now in `portfolio-service`.

Chronologically, the repo is clearly moving away from brief service notes toward canonical “source of truth” documents that double as portfolio artifacts.

## Portfolio-service received its first full narrative documentation in the same commit

[`services/portfolio-service/README.md`](services/portfolio-service/README.md) is new and large.

That matters because, in earlier chunks, `portfolio-service` was introduced primarily as a scaffold:

- build
- Docker
- compose wiring
- runtime config

but not much visible business code.

This README, by contrast, describes a fairly complete intended service:

- consumes `transaction.recorded.v1`
- maintains positions, weighted average cost, realized PnL
- uses `(topic, message_id)` idempotency
- uses pessimistic locking
- exposes:
  - `GET /portfolio/{userId}/positions`
  - `GET /portfolio/{userId}/positions/{ticker}`
  - `GET /portfolio/{userId}/summary`
- supports DLT/backoff operational patterns

### The documentation is richer than the code evidence in this chunk

This is exactly where evidence needs to stay careful.

The changed source file under `portfolio-service` in this chunk is only:

- [`ListenerFactoryConfig.java`](services/portfolio-service/src/main/java/com/tradestream/portfolio_service/config/ListenerFactoryConfig.java)

and its change is minimal:

- removal of an unused `KafkaProperties` import

So the README is clearly describing the intended or existing architecture of the service, but this chunk alone does not show the implementation changes that would justify every documented behavior.

The safest evidence-based reading is:

- by 2025-08-21 the developer considered `portfolio-service` mature enough, or at least defined enough, to warrant a comprehensive source-of-truth document and a dedicated end-to-end scenario script
- this specific chunk does not itself implement those capabilities; it mainly documents and exercises them

That distinction matters because otherwise the README could be mistaken for proof of fresh implementation work in this commit.

## A small `portfolio-service` config cleanup suggests listener wiring was being tidied while docs/tests expanded

[`ListenerFactoryConfig.java`](services/portfolio-service/src/main/java/com/tradestream/portfolio_service/config/ListenerFactoryConfig.java) only removes:

- `import org.springframework.boot.autoconfigure.kafka.KafkaProperties;`

That is a tiny cleanup, but it does signal that the service’s listener configuration was being actively maintained even though the visible code diff here is mostly docs and scripts.

The evidence is too weak to claim a broader listener refactor in this chunk. The most accurate statement is simply that an unused Kafka-properties import was removed as part of the same portfolio-focused pass.

## The identity services were being aligned around a shared mental model: username-based accounts, internal-only writes, and health-checkable runtime behavior

Taken together, the auth and registration changes form a coherent cross-service evolution.

### Before this commit

The evidence in this chunk implies several mismatches or weak spots still existed:

- auth defaulted to generic port `8080` in config
- login credential normalization removed internal spaces entirely
- schema naming still referred to `email`
- registration lacked explicit DTO validation in the visible code
- registration built BCrypt inline instead of through a bean
- health/actuator exposure was not explicitly wired in the same way

### After this commit

The services are more aligned:

- both auth and registration adopt `V2__rename_email_to_username.sql`
- both expose actuator-related runtime affordances
- auth and registration security rules explicitly permit health/info and deny all else
- registration is validated and conflict-aware
- auth login sanitization becomes less destructive and more conventional
- username becomes the explicit canonical field across schema and runtime

This is not a full identity rearchitecture, but it is a clear normalization step across the two services that own identity creation and authentication.

## The chronological shape of `538f9a694ec75dd98640778c4a152777c5bfe518` is therefore tight and consistent

Before this commit, `portfolio-service` had been introduced structurally but was still thinly evidenced in docs/tests, and the identity services still showed drift between current API semantics and older schema/runtime defaults.

After the commit:

- `portfolio-service` gained a comprehensive source-of-truth README
- a dedicated `e2e_portfolio_service.sh` script defined concrete behavioral expectations for positions, realized PnL, and mark-price behavior
- `user-registration-service` became more production-like with validation, actuator, Flyway, explicit duplicate handling, and injected password encoding
- `authentication-service` aligned around username naming, health endpoints, stricter method matching, and cleaner input handling
- both identity services introduced the same migration to rename `email` to `username` and to establish UUID defaults

The strongest engineering signal in the chunk is not new algorithmic code. It is service hardening and alignment:

- hardening auth/registration around current identity semantics
- making portfolio behavior explicit enough to document and test end to end
- reducing drift between runtime code, schema history, and operational expectations
