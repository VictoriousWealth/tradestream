# Devlog

## 2026-03-16 23:53:16 +0000 — commit `1a9ff4b`

Commit `1a9ff4bd2986d9d1c25d5fe9ed6e35c52a2ea90c` is a large rewrite of `CODEBASE_REVIEW_7POINTS.md`. Before this commit, the document was titled `TradeStream Codebase Review (7-Point Interview Lens)`, last updated `2026-02-27`, and explicitly framed around “Palantir SWE/FDSE style discussions.” It also carried a note that many commits were labeled `Deploy`, so historical intent had to be inferred from file changes and the few descriptive commit messages. After the patch, the document becomes `TradeStream Codebase Review (General Tech Interview Lens)`, the date is advanced to `2026-03-16`, and the framing is broadened away from one interview archetype toward “general software engineering and systems interviews.”

That title and scope change is not cosmetic. The document’s methodology section is updated to say it now incorporates “recent documentation evolution through the February 28, 2026 merge and the rewritten commit history,” and the old caveat about vague `Deploy` messages is replaced by the statement that commit messages were rewritten into clearer conventional-style summaries. In before → after terms, the review stops presenting itself as an interpretation exercise over noisy history and instead positions itself as a more direct reading of a cleaner, rewritten commit history.

The “Git history signals” section is rewritten almost entirely. Before `1a9ff4b`, the document used a shorter list anchored on older commit IDs such as `5e2cf43`, `728119b`, `1f072a8`, `a1e17f8`, `c2a6eb0`, `12c8ee4`, `0df0b10`, `7d73907`, and `b8e193c`, then summarized repo growth as scaffold → E2E hardening → architecture/doc clarity → observability planning. After the rewrite, the list becomes longer and more structured, with a different commit vocabulary:

* `4b06088` for API Gateway initial setup
* `a787763` for the comprehensive root README
* a run of January 4 CI hardening commits (`8a35645`, `e534fdc`, `85237b2`, `2e200c9`, `39415ea`, `f187470`)
* January 24 documentation/roadmap/API/diagram commits (`fa4554e`, `82cd07c`, `eb87b62`, `22ebad5`, `2442507`, `ada837f`)
* February 11 observability (`200018d`)
* February 28 codebase review and merge commits (`9f8f17d`, `8495c32`)

The interpretation line is changed accordingly. Before, maturity was summarized as scaffold → hardening → clarity → observability. After, the maturity story becomes service setup → root platform documentation → CI hardening → architecture and roadmap clarity → observability planning → explicit self-review and interview readiness. This is a meaningful before → after evolution in how the project narrates itself: the codebase review document now treats documentation and reflection as part of engineering maturity rather than as side material after implementation.

The rewrite continues throughout all seven review sections. In the architecture section, new bullets and “History anchors” are added to tie implementation decisions to documentation cleanup such as corrected event-bus language, separated recruiter-facing versus engineer-facing docs, and the addition of review and observability materials. In the trade-off section, a new bullet explicitly treats the post-build investment in docs and review artifacts as a trade-off in itself: time spent on clarity and maintainability rather than pure feature output.

The bugs-and-risks section grows by adding an explicit CI/readiness example tied to later historical fixes. The service-boundary section now calls out documentation quality as another kind of boundary discipline, not just HTTP and Kafka ownership. The redesign section gains a sixth item about landing one roadmap slice that proves extensibility end to end, with Schema Registry + contract tests or a Streamlit analytics console given as concrete examples. The documentation-quality section adds the presence of an explicit self-review artifact as a strength and explains the separation between `CVREADME.md`, `README.md`, and `CODEBASE_REVIEW_7POINTS.md`.

The largest expansion is the new interview-support material appended near the bottom. Before `1a9ff4b`, the file ended with a short set of “Interview-ready 5 bullets” and a 45-minute review plan. After the commit, it adds several new sections:

* “General interview situations and 3 examples for each”
* “Strongest commit-backed stories to tell”
* “What not to say in interview”
* “How to adapt this document for a general interview”
* an expanded 45-minute review plan that now explicitly includes `docs/observability-stack-guide.md`, `CODEBASE_REVIEW_7POINTS.md`, and memorizing commit-backed stories

This changes the function of the document. Before, it was a focused 7-point review with some interview framing. After, it becomes a much broader interview-preparation artifact that includes behavioral examples, story prompts, anti-patterns in how to talk about technical choices, and explicit adaptation guidance for backend, SRE/platform, product/full-stack, and behavioral rounds.

There is one important uncertainty boundary here. The document refers to many commit hashes and repo-level developments outside the three-file patch surface of this chunk, but those references appear only as text inside `CODEBASE_REVIEW_7POINTS.md`. Within the allowed evidence for this chunk, what is directly proven is that the review document was rewritten to incorporate those references and to present the project as more mature and interview-ready. The broader underlying history is not revalidated by this chunk alone.

## 2026-03-16 23:53:18 +0000 — commit `c61d903`

Commit `c61d9039a2b9606abf2b07886272acbe9d63fcd2` updates `README.md` and repositions the root README as a more explicit documentation map plus operational-readiness overview. The diff in this chunk shows a set of targeted additions rather than a full rewrite, but the cumulative effect is significant.

The first visible change is in the status table near the top of the README. Before this commit, the table already listed the implemented services, a recruiter summary doc, CI, and several not-implemented items such as RabbitMQ and JWE. After the patch, the table gains three new documentation rows:

* `Docs: codebase review` → `CODEBASE_REVIEW_7POINTS.md`
* `Docs: observability` → `docs/observability-stack-guide.md`
* `Docs: future work` → `docs/future-enhancements.md`

At the same time, the old `Kubernetes/Terraform` row is replaced by a more specific `Roadmap delivery` row that says the next work is documented but not implemented, naming observability stack, analytics console, contracts, bot simulation, and infrastructure hardening. Before → after, the README stops describing future work in technology-bucket shorthand and starts pointing readers to concrete roadmap artifacts with named tracks.

The technology-stack section is also adjusted. Before this commit, its last bullet said `GitHub Actions (E2E via Docker Compose); cloud/IaC (Kubernetes/Terraform) are planned, not in-repo`. Afterward, it says deployment/orchestration beyond local Compose is roadmap work, not in-repo. This is a narrower and more accurate phrasing: it reduces emphasis on Kubernetes/Terraform as the only future-shape and instead aligns the root README with the more detailed future-work docs named elsewhere in the patch.

The most structurally important addition is the new `## Documentation map` section. Before `c61d903`, the README contained architecture, tech stack, run instructions, quickstart, routes, troubleshooting, and repo layout, but no explicit “start here depending on what you need” map. After the commit, the root doc enumerates:

* `README.md` as the implementation overview/local runbook
* `CVREADME.md` as the recruiter-facing summary
* `CODEBASE_REVIEW_7POINTS.md` as the architecture/trade-off/bug/redesign review for interview prep
* `docs/tradestream-prd.md`
* `docs/api-design.md`
* `docs/future-enhancements.md`
* `docs/observability-stack-guide.md`
* two execution-flow diagram docs

This is a direct before → after improvement in discoverability. The root README no longer assumes readers will infer where to go next; it tells them explicitly which doc serves which audience and purpose.

Another new section, `## CI and operational readiness`, makes the CI workflow’s behavior explicit in prose. The added bullets state that GitHub Actions:

* generates temporary JWT test keys
* sets up JDK 21
* builds the API Gateway JAR
* builds and starts the Compose stack
* waits for core services to become healthy before testing
* runs portfolio, trade pipeline, cancel flow, and scenario scripts

The section also points to `.github/workflows/ci.yml` plus operationally relevant scripts and docs such as `docs/observability-stack-guide.md`, `gateway_smoke.sh`, and the E2E scripts. Before this addition, those CI and readiness details existed only implicitly in workflow files and scripts. After it, the root README makes the project’s operational posture legible without requiring readers to open CI YAML first.

The new `## Future work (documented, not implemented)` section is similarly important. It consolidates the roadmap into five explicit tracks:

* observability implementation
* schema governance
* analytics console
* bot market simulation
* platform hardening

Before this patch, roadmap references were more scattered and less prominent in the root doc. After it, the README tells reviewers exactly what is next, explicitly distinguishing those items from implemented features. That makes the repo’s honesty about scope stronger: current state and next state are separated cleanly.

The repo-layout section at the bottom is also corrected. Before the commit, it omitted `portfolio-service` from the visible service tree, omitted `docs/`, and still showed `cvreadme.md` in lowercase. Afterward, `portfolio-service/` is included, `docs/` is added, `CVREADME.md` is capitalized correctly, and `CODEBASE_REVIEW_7POINTS.md` is listed as a first-class root artifact. In before → after terms, the repo map is brought into alignment with the actual documentation and service structure described elsewhere in this chunk.

Within the allowed evidence, `c61d903` is the commit that turns the root README from “project overview plus runbook” into “overview + runbook + doc router + operational maturity statement + roadmap gate.” It does not add new runtime code, but it makes the repo’s current state and navigational structure much clearer.

## 2026-03-16 23:54:05 +0000 — commit `354bece`

Commit `354bece0b8ba259a933ee82842b5d50a26aa723d` updates `CVREADME.md`, and unlike the prior commit it is tightly focused on recruiter-facing presentation rather than the engineer-facing root README. The patch adds a new `## What’s Next` section and adjusts the repo layout and deep-dive links so they match the current doc structure more accurately.

Before this change, `CVREADME.md` already served as a recruiter-oriented overview of the project, with architecture, service map, quickstart, and source-of-truth README links. What it did not do was state the next tranche of work in a concise recruiter-safe way. After the commit, the document gains a roadmap summary that explicitly lists:

* observability implementation across Prometheus/Grafana, Loki, Jaeger/OpenTelemetry, lag dashboards, and alerting
* schema governance with Schema Registry and CI contract tests
* an analytics console
* bot market simulation
* platform hardening such as stronger internal auth, refresh-token rotation/revocation, and later cloud/IaC work

The closing sentence of that new section is important because it defines the intent: the summary should keep the recruiter document honest about present scope while still showing a clear next-step direction. In before → after terms, `CVREADME.md` stops being purely a description of what already exists and becomes a controlled statement of future direction that does not collapse into vague “planned enhancements.”

The same commit also repairs the repo-layout and deep-dive sections. `portfolio-service/` is added to the services tree, `docs/` is added to the top-level tree, and the source-of-truth list gains:

* `services/portfolio-service/README.md`
* `docs/future-enhancements.md`
* `docs/observability-stack-guide.md`

Before this patch, those links were missing from the recruiter-oriented document even though they were relevant to understanding the full project. After the patch, the recruiter summary becomes more complete without losing its audience-specific focus.

Read across the three commits, this chunk shows a very fast, documentation-only refinement cycle aimed at audience separation and repo honesty. `1a9ff4b` broadens the codebase review into a general interview-prep artifact. `c61d903` turns the root README into a better map for engineers and operators. `354bece` updates the recruiter-facing summary so it communicates both present scope and next-step direction without overpromising. The evidence does not show any code, config, or workflow changes in this slice, so the engineering evolution here is purely documentary, but it is still specific: the repository’s self-explanation becomes more segmented, more accurate, and more explicit about what is implemented versus merely planned.
