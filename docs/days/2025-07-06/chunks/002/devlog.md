# Devlog

## evidence boundary and visible inconsistency inside the chunk metadata

This reconstruction is grounded only in:

- `context.txt`
- `commits.txt`
- `commit-hashes.txt`
- `changed-files.txt`
- `diff.patch`

One inconsistency is visible immediately in the chunk metadata and needs to be preserved as uncertainty rather than normalized away. `context.txt` says `Chunk commit count: 8`, but `commits.txt`, `commit-hashes.txt`, and `diff.patch` expose only 7 commits:

- `30f9fa11080c9f572a216ad7394d9a92d9b5bc22`
- `4359dfb48f6a489792f0bbe8ac645284f8c36532`
- `34d571451d6584286c0fc4e70658d0318772e1df`
- `89a09ed4ba1c7008b7abf6d639543527886cd49a`
- `e8f97ab1b132338aa32269e0b38d072f8a86fe01`
- `dc8f8af70529e48869083516ece28ee65817bdc1`
- `7ce1d25c6b85d07037ea953ee2191941a5af35e1`

The devlog below follows the observable seven-commit sequence, because there is no eighth commit available in the evidence files.

## 2025-07-06 06:09:19 +0100 — README presentation layer tightened around technology branding (`30f9fa11080c9f572a216ad7394d9a92d9b5bc22`)

The first visible change in chunk `002` is commit `30f9fa1` / `30f9fa11080c9f572a216ad7394d9a92d9b5bc22`, labeled only `Deploy` in `commits.txt`. Despite the generic message, `diff.patch` makes the scope specific: only `README.md` changes, and the modification is purely in the technology-logo strip.

Before this commit, the README’s centered logo section rendered a set of technology icons at `width="90px"`:

- Java
- Spring Boot
- Docker
- Kubernetes
- PostgreSQL
- Redis
- RabbitMQ
- AWS
- Terraform

After this commit, each of those widths is reduced from `90px` to `80px`. No technologies are added or removed. No headings, prose, roadmap items, or links change in this patch.

The before -> after evolution here is therefore about visual density, not project meaning. The README had already been reworked in the previous chunk into a more polished, badge-heavy, recruiter-oriented document. This commit compresses the icon row so that the stack presentation consumes less vertical space and likely fits more comfortably in the centered layout. That interpretation is evidence-based only to the extent that the widths shrink consistently across every listed icon. The patch itself does not state the reason, so anything stronger than “the icon presentation was uniformly reduced” would overstate the evidence.

The scope is also notable because `changed-files.txt` for the entire chunk lists only four files total:

- `README.md`
- `docs/tradestream-prd.md`
- `docs/tradestream-prd.pdf`
- `docs/architecture-diagram.png`

That means this chunk is not about code or service scaffolding at all. It is a documentation-asset chunk focused on refining the public narrative (`README.md`) and building out a formal product/design artifact (`docs/tradestream-prd.md`), with the architecture diagram and old PRD PDF moving around those changes.

## 2025-07-06 06:13:23 +0100 — a full textual PRD replaces the earlier PDF-only placeholder pattern (`4359dfb48f6a489792f0bbe8ac645284f8c36532`)

The second commit, `4359dfb` / `4359dfb48f6a489792f0bbe8ac645284f8c36532`, is the major substantive event in this chunk. `diff.patch` shows `docs/tradestream-prd.md` being created from nothing to a 521-line Markdown document.

This is the clearest before -> after transition in the chunk:

- before: the repository already had `docs/tradestream-prd.pdf` from the earlier scaffold, but in the evidence from 2025-07-05 that file had been introduced as an empty placeholder blob; there was no visible text-based PRD in this chunk prior to `4359dfb48f6a489792f0bbe8ac645284f8c36532`
- after: the project now has a large, editable Markdown PRD that defines architecture, scope, objectives, assumptions, risks, milestones, and references in explicit prose

The PRD content is broad, but the development history inside this single addition is still traceable because the document reveals the system model being formalized at this point.

### document-control and maturity framing

At the top of `docs/tradestream-prd.md`, the system is still called `TradeStream — Real-Time Financial Data Processor`, and the document is marked:

- `Version: 0.1 (Draft)`
- `Date: Fri, 04 July 2025`
- `Author: Nick Efe Oni`

The explicit `0.1 (Draft)` version matters. The README in prior work already presented TradeStream as polished and portfolio-ready, but this PRD addition makes the design maturity much more tentative and process-oriented. Before this commit, the repository had architecture claims largely carried by the README. After this commit, the project gains a structured planning artifact that openly describes itself as draft-stage.

### MVP versus future enhancement separation becomes much more formal

A major before -> after shift introduced by `4359dfb48f6a489792f0bbe8ac645284f8c36532` is the formal separation of “MVP technology stack” from “planned / future enhancements.”

The PRD explicitly states that for the MVP:

- microservices will be containerized
- deployment will happen on a single AWS Lightsail instance
- Java Spring Boot, Kafka or RabbitMQ, PostgreSQL, Redis, Docker, JWT, GitHub Actions, and Lightsail form the main stack

Then it separates future work into:

- Kubernetes
- Terraform
- Prometheus / Grafana
- stronger security controls
- expanded CI/CD

This is more disciplined than the earlier README roadmap language because it puts those items into a phased planning structure rather than listing them as aspirations. The document also begins attaching caveats and assumptions to these choices, for example explicitly saying the final broker choice between Kafka and RabbitMQ is still open and will be decided during technical implementation.

That uncertainty is critical historical evidence. The repository later converges away from “Kafka or RabbitMQ” ambiguity, but in this chunk the formal design still treats broker selection as unresolved.

### architectural boundaries are rewritten around a dedicated authentication service

The PRD’s “High-Level Architecture” section is especially important because it formalizes a system split that was only more loosely described before. It states that TradeStream has:

- a public `API Gateway`
- a dedicated `Authentication Service`
- a `Transaction Processor`
- a `Market Data Consumer`
- future services such as fraud detection and notifications
- a `Message Broker (Kafka/RabbitMQ)`
- PostgreSQL and Redis

The most concrete architectural boundary emphasized in this PRD is that:

- the API Gateway is the only public entry point
- the Authentication Service issues JWTs
- internal services and data stores live on a private containerized network

This is the first detailed textual evidence in this chunk that authentication issuance and request enforcement are being treated as separate concerns. The earlier README already named gateway and auth service, but this PRD turns that into a principle-driven architecture section with component descriptions, system boundaries, and key architectural principles.

### the technical design section converts the architecture into concrete API and data-flow expectations

Another meaningful before -> after shift is that the repository moves from mostly high-level descriptive documentation to explicit endpoint and flow design.

Inside the newly added `docs/tradestream-prd.md`, the technical design specifies:

- `POST /auth/login` on the Authentication Service
- gateway handling `/*` plus forwarding of `/auth/login`
- Transaction Processor endpoints:
  - `POST /transactions`
  - `GET /transactions/{id}`
  - `GET /health`
- Market Data Consumer:
  - `GET /health`

It also spells out a transaction creation flow:

1. client sends `POST /transactions` with a valid JWT to the API Gateway
2. gateway validates the token and routes to Transaction Processor
3. Transaction Processor validates input, writes to PostgreSQL, publishes an event to Kafka or RabbitMQ, and may cache in Redis
4. other services consume the event
5. response returns back through the gateway

Before this PRD commit, the repo’s public documentation described a system in broad strokes. After it, the project has a design document that is detailed enough to infer intended runtime responsibilities, endpoint surfaces, and data flow sequencing, even though this chunk contains no implementation code for those flows.

### storage and trust assumptions are made explicit, including one assumption that later history may challenge

The new PRD states that each microservice is responsible for its own PostgreSQL and Redis instances and that internal microservices trust requests forwarded by the gateway without re-validating tokens.

This matters historically because it captures not just planned components but planned trust relationships. Before the PRD commit, the README talked about secure service-to-service communication in a generic way. After `4359dfb48f6a489792f0bbe8ac645284f8c36532`, that trust model is written down explicitly:

- Authentication Service issues JWTs
- API Gateway validates them
- internal services trust gateway-forwarded traffic

The evidence does not show code implementing this. It only shows this as design intent in the PRD. That distinction has to be maintained.

### scope control also becomes more explicit

The PRD addition does not merely describe what the project will have. It also names what is out of scope for the MVP:

- full distributed production infrastructure
- Terraform
- advanced observability
- high availability and automated scaling
- extended hardening beyond baseline auth and input validation

This is an important documentary evolution because the project moves from promotional README language toward boundary-setting. The design starts distinguishing between deliverables that are expected in the near term and capabilities intentionally deferred.

### risk management and timeline sections reveal the project as a learning program as much as a build plan

The newly added `docs/tradestream-prd.md` includes extensive risk, timeline, and resource sections. Risks include:

- lack of Kubernetes expertise
- unfamiliarity with Kafka/RabbitMQ
- security weaknesses due to limited hardening
- complexity from multi-service integration

Project constraints include:

- self-initiated learning project scope
- limited personal time and resources
- MVP deployment constrained to AWS Lightsail
- simplified user management
- exclusion of a market data generator for now

The timeline then maps out July–September 2025 phases for PRD finalization, MVP development, deployment, and testing.

These sections show a before -> after shift from technical aspiration to self-conscious planning. The repository is no longer just saying “this project uses enterprise patterns”; it is documenting why some enterprise-grade features are intentionally missing from the first version and how that trade-off fits the author’s learning pace and portfolio goals.

### references section broadens the project’s declared influences

The PRD addition ends with a long references/resources section spanning:

- Spring Boot
- Spring Security
- Kafka
- RabbitMQ
- Docker
- PostgreSQL
- Redis
- JWT/Auth0
- AWS Lightsail
- OWASP
- microservices.io
- HackTheBox CBBH
- Cybrary IT and Cybersecurity Foundations

This is noteworthy because it broadens the declared source material from software-framework docs into explicit security-learning and architecture-learning influences. Before this commit, those influences were only loosely gestured at in the README acknowledgements. After `4359dfb48f6a489792f0bbe8ac645284f8c36532`, they are formalized as reference infrastructure for the project.

## 2025-07-06 06:13:48 +0100 — stray leading delimiter removed from the PRD (`34d571451d6584286c0fc4e70658d0318772e1df`)

The third commit, `34d5714` / `34d571451d6584286c0fc4e70658d0318772e1df`, is a tiny corrective follow-up to the PRD creation.

`diff.patch` shows two deletions at the start of `docs/tradestream-prd.md`:

```diff
----

```

Before this commit, the freshly added PRD began with a standalone horizontal rule before the main heading `# **Section 1: Title & Document Control**`.

After this commit, the document begins directly with the title section heading.

The change is purely editorial, but it shows that the PRD was being cleaned immediately after the initial dump. This mirrors the previous day’s pattern where major scaffold or documentation additions were followed by quick hygiene edits. The evidence does not support any content change beyond formatting cleanup.

## 2025-07-06 06:14:44 +0100 — empty PDF placeholder removed once the Markdown PRD exists (`89a09ed4ba1c7008b7abf6d639543527886cd49a`)

The fourth commit, `89a09ed` / `89a09ed4ba1c7008b7abf6d639543527886cd49a`, deletes `docs/tradestream-prd.pdf`.

This deletion matters more than its zero-line patch might suggest because it changes the repository’s documentation medium.

Before this commit:
- the repository had a `docs/tradestream-prd.pdf` path from earlier scaffold work
- within the evidence available from earlier chunks, that PDF had been an empty placeholder rather than a populated binary artifact
- the new real design content now lived in `docs/tradestream-prd.md`

After this commit:
- the empty PDF placeholder is gone
- the Markdown PRD becomes the repository’s sole visible substantive requirements document in this evidence set

The before -> after change is therefore a cleanup of duplication and ambiguity. Instead of carrying both an empty PDF and a populated Markdown PRD, the repo removes the non-substantive placeholder. The evidence does not show whether the author intended eventually to regenerate a PDF from Markdown; only the deletion is observable.

## 2025-07-06 07:39:34 +0100 — the architecture diagram becomes a real binary asset instead of an empty placeholder (`e8f97ab1b132338aa32269e0b38d072f8a86fe01`)

After more than an hour’s gap, commit `e8f97ab` / `e8f97ab1b132338aa32269e0b38d072f8a86fe01` updates `docs/architecture-diagram.png`.

The patch is binary-only, but the size transition is visible:

- before: `0` bytes from the placeholder state
- after: `90106` bytes

That means the architecture diagram path created earlier as a stub now receives actual image content. This is a meaningful before -> after transformation even without image internals:

- before, the README and PRD could reference an architecture diagram path, but the asset itself was not populated in the evidence available from prior scaffolding
- after, the repository has a non-empty diagram file that can plausibly support those documentation references

Because the patch is binary and there is no extracted preview in the evidence files, the exact content of the diagram cannot be described safely. What can be said is that the path became materially populated at this point, turning diagram references elsewhere in the docs from placeholders into references to an actual artifact.

## 2025-07-06 07:43:23 +0100 — the architecture diagram is revised shortly after first being added (`dc8f8af70529e48869083516ece28ee65817bdc1`)

Only a few minutes later, commit `dc8f8af` / `dc8f8af70529e48869083516ece28ee65817bdc1` modifies the same binary image again:

- before: `90106` bytes
- after: `86994` bytes

The file remains `docs/architecture-diagram.png`, but its size decreases, indicating a revised version of the diagram replaced the first one.

This immediate back-to-back change suggests the first binary upload was not considered final. Since the evidence contains only binary-size changes and not the image content, the exact nature of the revision is uncertain. Plausible interpretations include:

- layout cleanup
- content correction
- export-size optimization
- removal/addition of architectural elements

Only the existence of a second revision is certain. The important historical point is that the architecture diagram did not simply appear once; it was populated and then quickly adjusted.

## 2025-07-06 07:47:20 +0100 — the PRD is linked directly to the hosted architecture diagram (`7ce1d25c6b85d07037ea953ee2191941a5af35e1`)

The final visible commit in this chunk is `7ce1d25` / `7ce1d25c6b85d07037ea953ee2191941a5af35e1`, again touching `docs/tradestream-prd.md`.

The change is small but structurally meaningful. Under section `5.2 High-Level System Diagram`, the commit inserts:

```html
<img src="https://github.com/VictoriousWealth/tradestream/blob/main/docs/architecture-diagram.png" alt="High-Level System Diagram">
```

Before this commit:
- the PRD had a heading for `5.2 High-Level System Diagram`
- there was no embedded image under that heading in the Markdown evidence

After this commit:
- the PRD now explicitly points to the repository-hosted PNG asset

This links together three documentation threads that had previously been only loosely associated:

1. the README already referenced `docs/architecture-diagram.png`
2. the chunk had just converted `docs/architecture-diagram.png` from placeholder to real binary asset and then revised it
3. now the PRD itself embeds the hosted diagram under the section where the system topology is discussed

The before -> after effect is that the PRD stops being purely textual in its architecture section and becomes an integrated design artifact combining prose and diagram. That also means the diagram revisions in `e8f97ab1b132338aa32269e0b38d072f8a86fe01` and `dc8f8af70529e48869083516ece28ee65817bdc1` immediately gain more importance, because the image is no longer merely present in the docs folder; it is now actively referenced from within the main design document.

## file-by-file evolution across chunk `002`

### `README.md`

Visible changes in this chunk:
- only one commit touches it: `30f9fa11080c9f572a216ad7394d9a92d9b5bc22`
- that change shrinks all centered technology-icon widths from `90px` to `80px`

Before this chunk’s README edit:
- the document already had a polished, technology-badge-heavy presentation

After the edit:
- the same stack is presented with slightly tighter spacing

No evidence in this chunk shows README changes to project scope, architecture language, or roadmap content.

### `docs/tradestream-prd.md`

This file undergoes the largest and most meaningful evolution in the chunk.

Before `4359dfb48f6a489792f0bbe8ac645284f8c36532`:
- absent

After `4359dfb48f6a489792f0bbe8ac645284f8c36532`:
- a 521-line Markdown PRD exists
- it defines title/versioning, executive summary, MVP stack, future enhancements, goals, scope, architecture, technical design, assumptions, risks, timeline, and references

After `34d571451d6584286c0fc4e70658d0318772e1df`:
- stray leading delimiter removed

After `7ce1d25c6b85d07037ea953ee2191941a5af35e1`:
- the architecture diagram section embeds the hosted PNG

This shows a progression from absent -> fully drafted -> lightly cleaned -> visually integrated with a live diagram.

### `docs/tradestream-prd.pdf`

Before this chunk:
- present as a path from earlier scaffold evidence, but earlier evidence showed it as an empty placeholder

After `89a09ed4ba1c7008b7abf6d639543527886cd49a`:
- deleted

The repository therefore moves away from a placeholder PDF representation and toward a maintained Markdown PRD.

### `docs/architecture-diagram.png`

Before this chunk:
- present as a path from earlier scaffold evidence, but earlier evidence showed it as an empty placeholder

After `e8f97ab1b132338aa32269e0b38d072f8a86fe01`:
- populated to `90106` bytes

After `dc8f8af70529e48869083516ece28ee65817bdc1`:
- replaced with a revised version at `86994` bytes

After `7ce1d25c6b85d07037ea953ee2191941a5af35e1`:
- actively referenced from inside `docs/tradestream-prd.md`

This is a clear asset lifecycle: placeholder -> first real diagram -> revised diagram -> embedded design artifact.
