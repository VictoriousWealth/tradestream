# Devlog

## 2026-01-04 16:24:09 +0000 — commits `1f072a8`, `12c8ee4`, `d16ac60`, `1476152`

The first four commits in this chunk all share the same timestamp in `context.txt` and `commits.txt`, and the evidence shows them touching four different concerns at once: CI bootstrap, a test-script bug fix, and new service-local `.gitignore` files for `matching-engine` and `orders-service`. Because these commits are timestamp-identical, the exact sub-second ordering is not recoverable from the allowed evidence; the safest way to read them is as one initial push of related repository hygiene and automation changes.

Commit `1f072a89b3cd44b1acc4e53483935ab5902b593c` creates `.github/workflows/ci.yml` from an empty file. Before this commit, the repository had no observable GitHub Actions workflow in the chunk evidence. After it, the repo gains a `CI` workflow triggered on both `push` and `pull_request`, with `COMPOSE_DOCKER_CLI_BUILD=1` and `DOCKER_BUILDKIT=1` exported globally. The only job is `e2e`, which runs on `ubuntu-latest` with a 90-minute timeout. The initial step sequence is simple but consequential:

1. `actions/checkout@v4`
2. `docker version` and `docker compose version`
3. `./e2e_portfolio_service.sh`
4. `./e2e_trade_pipeline_test.sh`
5. `./manual_cancel_test.sh`
6. `./e2e_scenarios.sh`
7. `docker compose down -v || true` under `if: always()`

This is a major before → after transition in repository operations. Before `1f072a8`, end-to-end validation existed only as local shell scripts. After it, those scripts become part of a declared CI contract, meaning the project starts moving from “manual integration runs” toward “hosted repeatable integration checks.” At this first stage, though, the workflow still assumes the environment is already suitable for running those scripts. There are not yet any explicit steps to build images, start the stack, install a specific JDK, or generate JWT keys.

Commit `12c8ee41d5304133a0d35d46bbd840b0e9e0a69c` fixes a concrete defect in `manual_cancel_test.sh`. Before this change, the script created its ticker with `TICK="E2CANTEST$(date +%s)"`. The new inline comment explains why this was a problem: `orders-service` enforces ticker length `<=16`, and the older value was both long and time-based. After the fix, the script introduces `TS="$(date +%s)"` and derives `TICK="E2CAN${TS: -6}"`, preserving time-based uniqueness while constraining the string length. This is not speculative; the comment in the patch explicitly ties the change to the orders-service validation rule. In practical terms, the cancel-flow test stops risking self-inflicted failures caused by generating invalid tickers rather than exercising cancel behavior itself.

Commits `d16ac60776fa52e0bf8283c0bcb94091b7f3e912` and `14761521e442da33bc94b422e9f19b1ccd129a67` add identical `.gitignore` files to `services/matching-engine/` and `services/orders-service/`. Before these commits, the chunk evidence shows no service-local ignore rules in those directories. Afterward, each service ignores the expected Gradle outputs (`.gradle`, `build/`), common IDE and editor state for STS, IntelliJ, NetBeans, and VS Code, and two local key files: `jwt_private.pem` and `jwt_public.pem`. The before → after effect here is repository hygiene rather than runtime behavior. Build products, IDE metadata, and local JWT test keys are now intended to stay out of version control at the service level. That is especially relevant given later CI changes in this same chunk that generate JWT keys under `secrets/`; the `.gitignore` additions show a parallel concern for keeping local cryptographic materials and developer tooling noise from polluting commits.

Two binary files also change in the initial timestamp group: `services/matching-engine/.gradle/nb-cache/subprojects.ser` and `services/orders-service/.gradle/nb-cache/subprojects.ser`. Because `diff.patch` only records these as binary differences, their exact significance is not knowable from the allowed evidence. The only grounded statement is that IDE or Gradle metadata changed around the same time as the new service-local ignore rules.

## 2026-01-04 16:27:59 +0000 — commit `943df9e`

Commit `943df9e5bdb5073f2f509eba1aa16281564a68dc` is the first sign that the newly introduced CI workflow was incomplete in practical terms. The patch adds two steps to `.github/workflows/ci.yml` immediately after Docker version reporting:

* `Build images` → `docker compose build`
* `Start stack` → `docker compose up -d`

This is an important workflow correction. Before `943df9e`, the CI job ran end-to-end scripts but did not explicitly build or launch the Docker Compose environment those scripts likely depended on. The workflow therefore described tests without provisioning the services they were meant to test. After this commit, the CI job becomes self-hosting at the container layer: it builds images first, starts the stack second, and only then proceeds into the portfolio, trade-pipeline, cancel-flow, and scenarios scripts. This is a clear before → after maturation from “call the scripts” to “prepare the system those scripts need.”

The evidence does not show whether the missing build/start steps were discovered through a failed CI run or inferred ahead of time. What is observable is that the workflow was amended only minutes after its introduction, which strongly suggests the first version was recognized as operationally incomplete.

## 2026-01-04 16:30:00 +0000 — commit `f2c2770`

Commit `f2c2770cbbf65ed86cbd028aa62396fdf577092f` refines the CI workflow again by inserting a dedicated Maven build step for the API gateway:

* `Build API Gateway jar` → `mvn -B -DskipTests package -f api-gateway/pom.xml`

Before this commit, the CI job built Docker images directly after the initial environment setup. After it, the gateway JAR is packaged explicitly before image construction. The patch itself does not explain why this was necessary, so intent has to stay bounded. The grounded part is the workflow evolution: the gateway artifact became a first-class CI prerequisite rather than something left entirely to `docker compose build`. That suggests the gateway image build path either expected a prebuilt JAR or was considered safer/more deterministic when the Maven package step happened explicitly in the workflow.

This commit narrows the CI pipeline around one particular service rather than the whole stack. That is notable because the chunk touches only one Maven build step, not a matrix or generalized service build strategy. The API gateway had enough packaging sensitivity to be called out separately.

## 2026-01-04 16:31:26 +0000 — commit `53cbba0`

Commit `53cbba0d3cf8cefa9a185964d4195203975b14bc` addresses the toolchain dependency implied by the previous step. It adds a `Setup JDK 21` action to `.github/workflows/ci.yml` using `actions/setup-java@v4` with `distribution: temurin` and `java-version: 21`.

The before → after transition here is direct. Before this commit, the workflow asked GitHub Actions to run a Maven package command for `api-gateway/pom.xml` without explicitly provisioning a Java runtime. After the commit, the job guarantees a JDK 21 environment before Maven packaging. The chunk evidence does not expose the gateway’s `pom.xml`, so it would be unsafe to claim more than that the workflow now deliberately aligns itself with a Java 21 requirement. What is firmly grounded is that the CI pipeline went from assuming the runner environment was sufficient to declaring the necessary JDK version explicitly.

This change also reinforces the interpretation that the earlier CI workflow was being hardened iteratively in response to concrete setup gaps. The addition of Docker build/start, then Maven package, then JDK setup forms a stepwise provisioning chain: infrastructure first, application artifact next, language runtime beneath it.

## 2026-01-04 16:39:40 +0000 — commit `c2a6eb0`

Commit `c2a6eb09237bcc2c79bf9ca3044ad6e92175be5a` adds another missing prerequisite to the same workflow: JWT key generation. The new step runs before Docker diagnostics and creates `secrets/jwt_private.pem` and `secrets/jwt_public.pem` using OpenSSL, with a 4096-bit RSA keypair.

Before this commit, the CI job now had checkout, Docker inspection, JDK 21 setup, gateway JAR packaging, image build, stack startup, and multiple E2E scripts, but it still did not create the cryptographic material needed by the stack. After `c2a6eb0`, the workflow provisions those secrets itself:

```bash
mkdir -p secrets
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -out secrets/jwt_private.pem
openssl rsa -in secrets/jwt_private.pem -pubout -out secrets/jwt_public.pem
```

This is another clear before → after correction. The repository’s integration environment now stops depending on pre-existing local secrets and instead generates ephemeral JWT test keys on every CI run. That change is especially coherent when read against the earlier `.gitignore` additions in `services/matching-engine/.gitignore` and `services/orders-service/.gitignore`, which explicitly ignore local JWT PEM files. Together, those changes indicate a repository norm emerging in this chunk: JWT keys are necessary for execution, but they should be generated for the environment that needs them rather than committed.

By the end of `c2a6eb0`, the CI workflow has evolved substantially from the empty-file baseline visible at the start of the chunk. It now performs checkout, test-key generation, Docker diagnostics, JDK 21 setup, gateway packaging, container-image build, Compose stack startup, four end-to-end script runs, and unconditional teardown. The key engineering story in this chunk is not feature delivery inside the services themselves; it is the rapid conversion of existing local integration scripts into a CI pipeline with enough environment provisioning to plausibly execute them on a clean GitHub-hosted runner.
