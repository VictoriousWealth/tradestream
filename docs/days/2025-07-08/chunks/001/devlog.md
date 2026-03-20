# Devlog

## 2025-07-08 00:00:07 +0100 — commit `b36d5cacff92aef417ab37aede5277d815eec949`

The first commit in this chunk touched only one file:

- [`docs/architecture-diagram.drawio.png`](docs/architecture-diagram.drawio.png)

The binary asset size jumped from `107504` bytes to `297057` bytes. Because the evidence file is binary-only, the exact visual edits are not observable, but the size change is large enough to show this was not a trivial metadata rewrite. Before this commit, the diagram asset existed as a smaller Draw.io-exported PNG. After it, the same file had been substantially reworked.

What can be said safely is that the repository’s detailed architecture diagram was still under active revision immediately after the new documentation structure introduced on the preceding days. No README text changed yet in this commit; the work remained confined to the diagram asset itself.

## 2025-07-08 00:04:37 +0100 — commit `1180894559bffbed09ba21ebb5771389a2b97256`

The same file, [`docs/architecture-diagram.drawio.png`](docs/architecture-diagram.drawio.png), was adjusted again four minutes later, moving from `297057` bytes to `296740` bytes.

This second binary-only update indicates the diagram introduced in `b36d5cacff92aef417ab37aede5277d815eec949` was not yet considered final. Before this commit, the revised diagram existed in one form; after it, a slightly smaller follow-up revision replaced it. The evidence does not expose whether the edits were layout cleanup, label correction, or component changes, so that remains uncertain. What is supported is the existence of a two-step refinement cycle on the root architecture diagram before any surrounding prose was updated to reference it.

## 2025-07-08 00:16:39 +0100 — commit `4f6c07c1ccab75b4363037b2caec0be67372a3b3`

This commit shifted from asset editing to documentation wiring in [`README.md`](README.md). The “System Architecture” section previously contained a single sentence pointing readers to `docs/architecture-diagram.png` as the full system diagram. After this commit, that single reference was split into two separate lines:

- one pointing to `/docs/high-level-architecture-diagram.png` as a “full high level system diagram”
- one pointing to `/docs/architecture-diagram.drawio.png` as a more detailed version focused on API Gateway and the Authentication Service

This is an important before-to-after change in how the root README described the repository’s diagrams. Before this commit, the README treated system architecture as one canonical image. After it, the README explicitly distinguished between:

- a high-level system view, and
- a more detailed diagram focused on gateway/auth behavior.

The wording also shows that the detailed Draw.io image was being reinterpreted as a narrower, subsystem-focused artifact rather than as the one universal diagram. The actual markdown links still point to `docs/architecture-diagram.png` in the href position, even though the visible labels name different targets. That mismatch is part of the evidence: the narrative was updated first, but the linked path values were not yet fully corrected.

## 2025-07-08 00:30:13 +0100 — commit `014859f5210a8c57caa38eefaa3efde8c42b5910`

This commit expanded both the root README and added the first API Gateway README.

### Root README gained service-level documentation links

In [`README.md`](README.md), a new subsection titled `### 🔍 Service-Specific Docs` was inserted under the system architecture section. It told readers that each microservice had its own documentation and listed links for:

- API Gateway
- Authentication Service
- Transaction Processor
- Market Data Consumer

The before-to-after effect is straightforward:

- before: the root README described the system at project level, but did not explicitly act as a hub into per-service docs in this section
- after: the README began to function as a documentation index, inviting readers to branch into individual service readmes

There is also an evidence limitation here: the listed links use paths like `authentication-service/README.md`, `transaction-processor/README.md`, and `market-data-consumer/README.md`, but this chunk’s changed-file set does not include those files. The chunk only proves the index entries were added to the root README, not whether all linked files already existed at that moment.

### API Gateway README was created

The same commit created [`api-gateway/README.md`](api-gateway/README.md) from an empty file. The new document framed the gateway as the central access point for client requests and described it as responsible for:

- secure HTTPS communication,
- forwarding token issuance requests,
- access-token validation and routing,
- refresh-token processing,
- rejecting malformed, expired, or tampered tokens.

The README also embedded a diagram:

- `![Token Authentication Flow](../docs/architecture-diagram.drawio.png)`

and stated that the diagram illustrated the “full authentication lifecycle.” It further asserted several security practices:

- all security-sensitive communication is encrypted using HTTPS,
- refresh tokens are never exposed in URLs,
- access tokens are sent as Bearer tokens,
- refresh tokens are recommended in secure cookies or request bodies.

This first gateway README is notable for two reasons. First, it gave the service its own explanatory surface instead of relying only on the root README and PRD. Second, it tied the API Gateway documentation directly to the root-level `docs/architecture-diagram.drawio.png`, so at this point the detailed gateway/auth diagram was still living in the shared top-level `docs/` area rather than under the gateway’s own directory.

## 2025-07-08 01:13:22 +0100 — commit `0deda2f6adbd183f28b37e31e46a07b051c02fea`

This commit corrected the placement and references for the gateway-specific diagram. Three coordinated changes happened.

### Root README reference was repointed toward the gateway docs area

In [`README.md`](README.md), the second architecture note changed from referring to:

- `docs/architecture-diagram.drawio.png`

to:

- `api-gateway/docs/architecture-diagram.drawio.png`

The visible text still described it as “a more detailed version focused on API Gateway and the Authentication Service.” This moved the conceptual ownership of the detailed diagram away from the root docs area and toward the API Gateway service itself.

### API Gateway README image source changed

In [`api-gateway/README.md`](api-gateway/README.md), the image embed changed from:

- `../docs/architecture-diagram.drawio.png`

to:

- `docs/token-authentication-flow.drawio.png`

This is the clearest file-local correction in the chunk. Before the commit, the gateway README depended on a shared repository-level diagram file. After it, the README referenced a service-local diagram asset.

### The binary asset was physically moved and renamed

The diff shows a pure rename with 100% similarity:

- from `docs/architecture-diagram.drawio.png`
- to [`api-gateway/docs/token-authentication-flow.drawio.png`](api-gateway/docs/token-authentication-flow.drawio.png)

Taken together, the three changes show a full before-to-after evolution:

1. the detailed diagram started as a root-level architecture file,
2. the root README described it as the detailed gateway/auth diagram,
3. the gateway got its own README that still referenced the root-level asset,
4. this commit relocated the asset into the gateway service and renamed it to match its actual role.

That is one of the main structural moves in the chunk: a shared diagram was reclassified as a service-specific token-authentication artifact.

## 2025-07-08 01:14:45 +0100 — commit `6d3006f8b43c29b8d5dad460be99ac926bfdea26`

This commit corrected the root README’s visible path label in [`README.md`](README.md). The prior line mentioned:

- `api-gateway/docs/architecture-diagram.drawio.png`

but the actual file had just been renamed in the previous commit. This edit changed the visible label to:

- `api-gateway/docs/token-authentication-flow.drawio.png`

However, the href target in the markdown link was still not corrected at this point; it continued to point to `docs/architecture-diagram.png`.

So this commit resolved only half of the inconsistency:

- before: visible label and actual filename disagreed
- after: visible label matched the new filename, but the clickable target still did not

This is a small but useful example of iterative doc repair rather than one clean atomic rewrite.

## 2025-07-08 01:15:47 +0100 — commit `fc2d12fdf480043095b9e487b63ec1e77007f4a7`

The next commit advanced that same line in [`README.md`](README.md) one step further. The markdown link target changed from the stale root-doc path to:

- `api-gateway/docs/token-authentication-flow.png`

This improved the link direction in one sense because it now pointed into the gateway docs directory, but it introduced a new mismatch: the asset created in `0deda2f6adbd183f28b37e31e46a07b051c02fea` was named `token-authentication-flow.drawio.png`, not `token-authentication-flow.png`.

So the before-to-after change here was:

- before: correct visible label, wrong target directory/path
- after: correct service directory, but still wrong target filename

This shows the author converging on the right link through a sequence of small corrective edits.

## 2025-07-08 01:16:22 +0100 — commit `c88d776315b5b785f2ae9ec3eca2d4c3cb67d8a6`

The last commit in the chunk completed that correction. The root README link target in [`README.md`](README.md) was changed from:

- `api-gateway/docs/token-authentication-flow.png`

to:

- `api-gateway/docs/token-authentication-flow.drawio.png`

At this point, the visible path text and the link target finally matched the actual renamed asset created earlier in the chunk. The root README’s detailed-diagram reference was now internally consistent.

## Documentation evolution across the chunk

All observable work in this chunk stays within four files:

- [`docs/architecture-diagram.drawio.png`](docs/architecture-diagram.drawio.png), which is later renamed away
- [`api-gateway/docs/token-authentication-flow.drawio.png`](api-gateway/docs/token-authentication-flow.drawio.png)
- [`api-gateway/README.md`](api-gateway/README.md)
- [`README.md`](README.md)

The chronology is coherent:

1. the root-level detailed architecture diagram was revised twice as a binary asset,
2. the root README then began distinguishing between a high-level system diagram and a more detailed gateway/authentication diagram,
3. the root README also gained a service-docs hub section,
4. the API Gateway received its own README that initially depended on the shared root-level diagram,
5. the detailed diagram was then moved into the gateway service and renamed to `token-authentication-flow.drawio.png`,
6. the gateway README was updated to use that service-local asset,
7. the root README went through three quick successive edits to make its visible path text and its actual link target agree with the moved file.

The evidence does not show any code or runtime changes. It shows a documentation-boundary change: a diagram that had been treated as part of the root architecture docs was reclassified as a gateway-specific token-authentication flow asset, and the README network around it was gradually brought into alignment.
