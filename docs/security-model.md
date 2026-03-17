# Security Model (Current Implementation)

Last reviewed against code/config on 2026-03-17.

This document describes the current security model as implemented, not the ideal future model.

---

## 1) Security boundary overview

### Public boundary

The only public HTTP entry point in the default runtime is:
- `api-gateway` on port `8080`

The gateway is responsible for:
- validating access-token signatures
- deciding which routes require authentication
- rate limiting login by IP via Redis
- adding `X-Internal-Caller: api-gateway` on selected internal hops

### Private boundary

Downstream services live on the Compose private network.

Sensitive internal routes:
- `authentication-service /refresh`
- `user-registration-service /register`

Those routes are not protected by downstream JWT validation. They are protected by a Spring MVC interceptor requiring a specific header value.

That is a real security model, but it is a weak one compared with mTLS or service-signed credentials.

---

## 2) Token model

## Signing

Algorithm:
- PS256

Key material:
- Auth service reads private and public PEM files from mounted secrets.
- Gateway validates using the public key.

Current token types:
- access token
- refresh token

## Access token contents

Auth service sets:
- `jti`
- `sub` = user ID string
- `typ` header = `JWT`
- `alg` header = `PS256`
- `iat`
- `exp` = now + 15 minutes
- `iss` = `authentication-service`
- `aud` = `api-gateway`
- `username`
- `token_type` = `access`
- `scopes`

## Refresh token contents

Auth service sets the same core claims, except:
- `exp` = now + 30 days
- `token_type` = `refresh`

## Current refresh behavior

- The refresh endpoint parses and validates the refresh token using the public key.
- It confirms `token_type == refresh`.
- It reuses the same refresh token and mints a new access token.

What does not exist:
- refresh rotation
- revocation
- server-side session store

---

## 3) Gateway enforcement

## Public routes at the gateway

Currently permitted without JWT:
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/users/register`
- `GET /actuator/health`
- `GET /actuator/info`

Everything else:
- requires authentication at the gateway

Important nuance:
- `refresh` and `register` are public only at the gateway layer.
- They are still header-gated at the downstream service.

## JWT validation

The gateway is configured as a resource server:
- public key location from `JWT_PUBLIC_KEY_LOCATION`
- JWS algorithm `PS256`

## Authorities/scopes

The gateway maps `scopes` or `scope` claims into `SCOPE_*` authorities.

Current limitation:
- There are no route rules that require specific scopes.
- Scope extraction exists, but scope-based authorization does not.

---

## 4) Internal route protection

## `authentication-service /refresh`

Spring Security behavior:
- `POST /refresh` is permitted at the service level.

Actual gate:
- `InternalCallerInterceptor`
- required header: `X-Internal-Caller: api-gateway`

Where the header is added:
- gateway route `auth-refresh`

## `user-registration-service /register`

Spring Security behavior:
- `POST /register` is permitted at the service level.

Actual gate:
- `InternalCallerInterceptor`
- required header: `X-Internal-Caller: api-gateway`

Where the header is added:
- gateway route `user-registration`

Security consequence:
- Anyone with private-network reachability to those services can bypass the gateway if they can forge that header.

---

## 5) Password handling

Confirmed behavior:
- User registration hashes passwords with BCrypt.
- Login loads the stored hash and verifies with BCrypt.

This is the right basic storage model for a service of this scope.

What is not reviewed here:
- password policy complexity rules beyond DTO validation
- account lockout
- password reset flows

---

## 6) Rate limiting and abuse control

Current implementation:
- Gateway rate limits only `POST /api/auth/login`.
- Uses `RequestRateLimiter` with Redis-backed token bucket.
- Replenish rate: `10`
- Burst capacity: `20`
- Key resolver: IP-based

What does not exist:
- Per-user rate limiting on authenticated routes
- Separate write-path quotas for order placement/cancel
- Refresh endpoint rate limiting

---

## 7) Current trust gaps

### User identity is not propagated as an enforced command context

What happens today:
- Gateway validates the JWT.
- Orders requests still carry `userId` in the JSON body.
- Orders service trusts that field.

What is missing:
- Rewriting `userId` from the JWT subject.
- Rejecting body/JWT mismatch.

Impact:
- Authenticated users can potentially act on behalf of another UUID.

### Internal caller identity is only a shared header convention

Impact:
- Trust is network-local and convention-based, not cryptographically bound.

### Authorization is coarse-grained

What exists:
- Authenticated vs unauthenticated separation.

What does not exist:
- role-based access control
- scope enforcement
- per-route permission policies

### Token lifecycle is incomplete

What exists:
- signed access and refresh tokens with expiry

What does not exist:
- refresh rotation
- logout/revocation model
- key rotation workflow in runtime

---

## 8) Security-relevant operational notes

- Gateway CORS is permissive: `allowedOrigins: "*"` and all methods/headers.
- Gateway removes `Cookie` from proxied requests.
- Security logging in the gateway is currently set to DEBUG for several Spring security categories.

That logging level is useful while building, but it is too verbose for a production posture.

---

## 9) Security model in one sentence

TradeStream currently uses a strong edge signature model for authentication, but only a weak internal trust model and a weak command-identity binding model beyond the gateway.
