# Devlog

## 2025-07-14 06:20:29 +0100 — commit `f9d52e548a129fc3967ff2d4f6382dc396f05be0`

This chunk is a single, large bootstrap commit that introduced two new backend services in one sweep:

- [`authentication-service/`](authentication-service)
- [`user-registration-service/`](user-registration-service)

The commit also wired them into repository-level operations through:

- [`docker-compose.yml`](docker-compose.yml)
- [`automated_scripts/dev-deploy.sh`](automated_scripts/dev-deploy.sh)
- [`README.md`](README.md)
- [`.gitignore`](.gitignore)

The evidence shows this was not an incremental feature tweak. It was the first full service scaffold and runtime wiring for auth and registration as independent Spring Boot applications.

## Repository-level framing changed to acknowledge the new security model

Before looking at the service internals, the top-level docs and ignore rules show what problem this commit was trying to solve.

### Root `.gitignore` started excluding key material

[` .gitignore`](.gitignore) gained:

- `*key*.pem`
- `secrets`

That change is tightly coupled to the new authentication-service key loading later in the patch. Before this commit, the repo did not explicitly ignore PEM files or a secrets directory. After it, the repo began treating local signing keys and secret mounts as operational artifacts rather than versioned source.

### Root README moved from generic JWT wording to JWS/JWE terminology

The technology table in [`README.md`](README.md) changed the authentication row from:

- `JWT (JSON Web Tokens)`

to:

- `JWS & JWE (JWT Signed & JWT Encrypted)`

The security section was also rewritten. Before the change, it said:

- input validation throughout services,
- future roadmap included rate limiting, RBAC, secure headers, vulnerability scanning.

After the change, it instead highlighted:

- “Special header(s) set by the API gateway that the other services will now check to double confirm origin of the request”

and removed RBAC from the future-security list.

This is important because it documents the core pattern introduced in code later in the same commit: sensitive endpoints in the new services are protected not by general public exposure, but by an internal caller header expected from the gateway.

### Root README also acknowledged the new service split, but with visible drift

The README gained:

- a new service-docs link for [`user-registration-service/README.md`](user-registration-service/README.md),
- `User Registration Service` in the checked MVP feature list,
- “Login, registration, transaction, and health endpoints” in the access text.

It also added an explicit note that the repository documentation was missing user-registration coverage because the service had been added “last minute.”

That note is revealing. Before the commit, the docs described the platform without a dedicated registration service. After it, the root README both advertised the new service and admitted that the documentation set had not fully caught up.

## Authentication Service was introduced as a standalone Spring Boot application

The `authentication-service` directory was populated with a full Gradle-based Spring Boot project:

- Gradle wrapper and wrapper metadata
- [`build.gradle`](authentication-service/build.gradle)
- [`settings.gradle`](authentication-service/settings.gradle)
- [`Dockerfile`](authentication-service/Dockerfile)
- [`.gitignore`](authentication-service/.gitignore)
- [`.gitattributes`](authentication-service/.gitattributes)
- application code under `src/main/java`
- configuration and Flyway migration under `src/main/resources`
- a context-load test

At the same time, an empty [`pom.xml`](authentication-service/pom.xml) was deleted. That indicates a build-tool decision: before this commit, Maven was at least nominally present in the service directory; after it, the service was standardized on Gradle.

### Service bootstrap and dependencies

[`authentication-service/src/main/java/com/tradestream/auth/AuthApplication.java`](authentication-service/src/main/java/com/tradestream/auth/AuthApplication.java) introduced the Spring Boot entrypoint.

[`authentication-service/build.gradle`](authentication-service/build.gradle) shows the initial dependency surface:

- Spring Web
- Spring Security
- Spring Data JPA
- PostgreSQL driver
- Lombok
- JJWT (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`)

This is the first hard evidence in the chunk that token issuance is implemented inside the service rather than delegated elsewhere.

### Security posture in the first auth implementation

[`authentication-service/src/main/java/com/tradestream/auth/config/SecurityConfig.java`](authentication-service/src/main/java/com/tradestream/auth/config/SecurityConfig.java) configured the service as:

- CSRF disabled
- CORS enabled with defaults
- stateless session management
- `GET /` and `/error` permitted
- `/login` and `/refresh` permitted
- all other routes denied

This is a narrow public surface. The service was not trying to be a broad internal API; it exposed only the auth endpoints plus minimal health/error reachability.

### Sensitive endpoint gating via interceptor

The real trust boundary was implemented in:

- [`authentication-service/src/main/java/com/tradestream/auth/security/InternalCallerInterceptor.java`](authentication-service/src/main/java/com/tradestream/auth/security/InternalCallerInterceptor.java)
- [`authentication-service/src/main/java/com/tradestream/auth/config/WebMvcConfig.java`](authentication-service/src/main/java/com/tradestream/auth/config/WebMvcConfig.java)

The interceptor requires:

- header `X-Internal-Caller`
- expected value `api-gateway`

and `WebMvcConfig` applies it specifically to:

- `/refresh`

This is the first concrete implementation of the “special header(s)” described in the root README. Before this commit, there was no evidence of this gateway-origin enforcement. After it, refresh token exchange was allowed by the Spring Security filter chain in general terms, but then narrowed by an MVC interceptor that only allowed calls marked as originating from the gateway.

That distinction matters:

- Spring Security alone would have left `/refresh` openly callable,
- the interceptor layered on a service-to-service trust requirement.

### Login and refresh controller behavior

[`authentication-service/src/main/java/com/tradestream/auth/controller/UserController.java`](authentication-service/src/main/java/com/tradestream/auth/controller/UserController.java) introduced two endpoints:

- `POST /login`
- `POST /refresh`

The `login` flow:

1. strips spaces from username and password using `.replace(" ", "")`
2. loads the user by username from the database
3. verifies the password with `BCryptPasswordEncoder`
4. generates:
   - an access token
   - a refresh token
5. returns both tokens plus `token_type` and `user_id`

The `refresh` flow:

1. accepts a [`RefreshRequest`](authentication-service/src/main/java/com/tradestream/auth/dto/RefreshRequest.java)
2. extracts username, userId, and scopes from the refresh token
3. generates a new access token
4. returns the original refresh token unchanged alongside the new access token

This is a concrete before-to-after introduction of token lifecycle endpoints. Before this commit, the repository had auth diagrams and README prose but no actual authentication-service controller code. After it, the service could validate credentials and issue or refresh signed JWTs.

### Token service defined the initial cryptographic implementation

[`authentication-service/src/main/java/com/tradestream/auth/service/TokenService.java`](authentication-service/src/main/java/com/tradestream/auth/service/TokenService.java) is the core of the auth service.

The implementation:

- reads RSA private/public keys from file paths injected via:
  - `JWT_PRIVATE_KEY_PATH`
  - `JWT_PUBLIC_KEY_PATH`
- parses PEM material manually
- signs both access and refresh tokens with `PS256`
- encodes claims for:
  - subject (`userId`)
  - issuer (`authentication-service`)
  - audience (`api-gateway`)
  - username
  - token type (`access` or `refresh`)
  - scopes

It also defines:

- `15 minutes` access-token expiry
- `30 days` refresh-token expiry

and refresh-token parsing helpers:

- `extractUsernameFromRefreshToken`
- `extractUserIdFromRefreshToken`
- `extractScopesFromRefreshToken`
- `isNotRefreshToken`

There is an important implementation nuance here. Despite the README’s repository-level move toward “JWS & JWE” language, the code in this commit only generates signed JWTs using JJWT and RSA keys. There is no JWE construction logic in the auth service itself. The comments and README imply a larger encrypted-refresh-token model, but the observable code here implements signed tokens plus verification/parsing. That mismatch should be stated plainly because the evidence supports it.

### Persistence and schema design

The auth service includes:

- [`User`](authentication-service/src/main/java/com/tradestream/auth/model/User.java)
- [`UserRepository`](authentication-service/src/main/java/com/tradestream/auth/repository/UserRepository.java)
- Flyway migration [`V1__create_users_table.sql`](authentication-service/src/main/resources/db/migration/V1__create_users_table.sql)

The Java entity uses:

- `username`
- `password`

but the SQL migration creates columns:

- `email`
- `password`
- `created_at`

This is one of the most important evidence-based inconsistencies in the chunk. Before this commit, neither the entity nor the migration existed. After it, both existed, but they did not align:

- the code expects `username`,
- the schema creates `email`.

That means the service was bootstrapped in one commit, but with a likely schema-model mismatch already embedded.

### Error model

The service added custom exceptions for:

- invalid credentials
- invalid refresh token
- unknown scopes claim format
- unable to generate RSA key pair
- user not found

and a [`GlobalErrorHandler`](authentication-service/src/main/java/com/tradestream/auth/handler/GlobalErrorHandler.java) mapped them to:

- `401`
- `400`
- `404`
- `500`

The existence of these handlers shows the author was not just scaffolding endpoints; they were also shaping a public error contract from the start.

## User Registration Service was introduced in parallel, with the same structural pattern

The `user-registration-service` directory was added as a second Gradle/Spring Boot service with the same general scaffold shape:

- wrapper files
- [`build.gradle`](user-registration-service/build.gradle)
- [`settings.gradle`](user-registration-service/settings.gradle)
- [`Dockerfile`](user-registration-service/Dockerfile)
- ignore and attributes files
- application code
- Flyway migration
- test class
- service README and screenshot asset

### Security and request gating

Like the auth service, the registration service introduced:

- [`SecurityConfig`](user-registration-service/src/main/java/com/tradestream/user_registration_service/config/SecurityConfig.java)
- [`WebMvcConfig`](user-registration-service/src/main/java/com/tradestream/user_registration_service/config/WebMvcConfig.java)
- [`InternalCallerInterceptor`](user-registration-service/src/main/java/com/tradestream/user_registration_service/security/InternalCallerInterceptor.java)

The security filter chain permits:

- `GET /`, `/error`
- `/register`

and denies everything else.

Then the interceptor applies to:

- `/register`

and requires the same `X-Internal-Caller: api-gateway` header pattern.

So the service is “public” in the Spring Security rule table, but only effectively usable by callers carrying the internal header. This mirrors the auth-service `/refresh` arrangement and confirms that the gateway-header trust model was designed as a cross-service pattern, not a one-off.

### Registration flow implementation

[`user-registration-service/src/main/java/com/tradestream/user_registration_service/controller/UserController.java`](user-registration-service/src/main/java/com/tradestream/user_registration_service/controller/UserController.java) adds `POST /register`:

1. accept username/password via DTO
2. hash the password with `BCryptPasswordEncoder`
3. create a `User` entity
4. persist it through `UserRepository`
5. return `"All good."`

This is intentionally simpler than the auth service. There is no duplicate-user guard visible in the controller, no custom exception handling layer in this chunk, and no additional validation beyond the database uniqueness constraint implied by the entity. That simplicity is part of the before-to-after evolution: the repository gained a dedicated registration service, but it arrived first as a thin persistence-and-hash wrapper rather than as a more fully defended account-creation pipeline.

### Persistence model and schema mismatch repeated

The user-registration service repeats the same schema pattern as the auth service:

- entity uses `username`
- migration [`V1__create_users_table.sql`](user-registration-service/src/main/resources/db/migration/V1__create_users_table.sql) creates `email`

This indicates the mismatch was not accidental in only one place; it was duplicated across both services in the same bootstrap commit. That makes it a systemic modeling inconsistency in the first version of the auth/registration split.

## Service READMEs were added, but they were not equally mature

### Authentication README existed already and received only a small extension

[`authentication-service/README.md`](authentication-service/README.md) changed by one line in this commit, adding:

- `Add monitoring system or something alogn the lines of that`

to the future-improvements section.

That tells us the auth-service README had been created earlier than this chunk, and this commit focused mainly on code and project scaffolding rather than rewriting its documentation.

### User Registration README was introduced from scratch

[`user-registration-service/README.md`](user-registration-service/README.md) is much more revealing because it was new. It describes the service as:

- a simple registration microservice,
- built to learn microservices,
- using Java Spring Boot, Flyway, Docker, and Gradle,
- protected by an internal caller interceptor checking the API Gateway header.

The README also includes:

- a screenshot asset reference,
- local build instructions using `./gradlew clean build -x test`,
- licensing and contact sections.

This README is more informal than the root README and auth-service README. It mixes project explanation with personal learning motivation. That difference is part of the repo’s maturity state at this point: the new service existed and was documented, but its documentation tone was still closer to a personal devlog than a polished platform doc.

## Compose and deployment wiring introduced the first runnable auth/registration stack

[`docker-compose.yml`](docker-compose.yml) was created in this commit and wired up:

- `user-registration-service`
- `auth-service`
- a shared `postgres`

with:

- database `authdb`
- user `authuser`
- password `authpass`
- Flyway enabled
- mounted external secrets:
  - `jwt_private.pem`
  - `jwt_public.pem`

There is also a noteworthy configuration mistake in the auth service compose block:

- container port mapping is `8080:8080`
- environment says `SERVER_PORT: 8082`

That means the compose file, as written in this commit, maps the wrong exposed port for the auth service relative to its configured server port. Before this commit, there was no compose wiring for these services. After it, there was runnable orchestration, but with at least one port inconsistency already present.

The new [`automated_scripts/dev-deploy.sh`](automated_scripts/dev-deploy.sh) complements compose with a Docker Swarm-oriented update flow:

- iterate over `auth-service` and `user-registration-service`
- build timestamp-tagged images
- run `docker service update --image ... --force`

This shows the operational model was not only local compose. The repo was also preparing for iterative service rollout into a named stack called `tradestream`.

## Build-tool and project-standardization work happened alongside the feature work

Both services received:

- Gradle wrappers
- wrapper property files
- `.gitattributes`
- `.gitignore`
- Dockerfiles that build with `./gradlew clean build -x test`

That means the commit did not merely add source code. It standardized both services as independently buildable and containerizable units from day one.

For the authentication service specifically, the deletion of an empty [`pom.xml`](authentication-service/pom.xml) is significant because it shows a toolchain decision being finalized inside the same commit that introduced the real service implementation.

## Overall before-to-after evolution in this chunk

Before `f9d52e548a129fc3967ff2d4f6382dc396f05be0`, the repository had documentation discussing authentication and registration concerns, but there was no full evidence-backed implementation here for either service as standalone runnable projects.

After this commit:

- `authentication-service` existed as a complete Spring Boot + Gradle + Flyway + Docker project
- `user-registration-service` existed as a parallel Spring Boot + Gradle + Flyway + Docker project
- both enforced an `X-Internal-Caller: api-gateway` trust header on their sensitive endpoints
- compose wiring and deployment scripts existed for both services
- the root README acknowledged the new service split and the changed security model

At the same time, the commit introduced several inconsistencies that are visible directly in the evidence:

- auth and registration entities use `username`, but both SQL migrations create `email`
- auth-service compose maps `8080:8080` while `SERVER_PORT` is `8082`
- repo-level docs now talk in `JWS & JWE` terms, but the observable auth-service code only generates signed JWTs and parses them

Those contradictions matter because they show this was a true first implementation pass: large functional surface area landed at once, but the model, schema, infrastructure, and documentation were not yet fully synchronized.
