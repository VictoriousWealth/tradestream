# Devlog

## 2025-07-09 04:43:11 +0100 — commit `92c1fe7737b2b8383959e71cf1ab04cbc8c95db6`

This chunk begins by normalizing the location of the authentication-service diagram asset. The file:

- `authentication-service/authentication-flow.drawio.png`

was moved to:

- [`authentication-service/docs/authentication-flow.drawio.png`](authentication-service/docs/authentication-flow.drawio.png)

The diff reports `similarity index 100%`, so this was a pure relocation with no observable binary-content change. Before the commit, the authentication flow image sat at the top level of the service directory. After the commit, it lived under a `docs/` subdirectory, which aligned it structurally with how the API Gateway was already organizing service-local documentation assets.

That move is small but important for understanding the rest of the chunk. The previous chunk ended with the service’s diagram work being deleted from `authentication-service/docs/`. This commit shows that a diagram asset still existed in the repository, but it was in the wrong place. The repository then re-established a service-local documentation convention by moving it under `docs/`.

## 2025-07-09 04:59:08 +0100 — commit `8dcf7abda7c3e23d58ea26edf5122a3e776f9214`

The next commit created [`authentication-service/README.md`](authentication-service/README.md) from scratch. This is the first substantial textual documentation for the authentication service in the evidence.

### Initial positioning and security model

The initial README described the service as the component responsible for:

- verifying user credentials,
- issuing signed access tokens,
- issuing encrypted refresh tokens,
- using `PS256` signing,
- interfacing with the API Gateway for delivery and validation.

Before this commit, there was no service-level README at all. After it, the service had a standalone narrative explaining responsibilities, security highlights, a diagram reference, workflow, configuration, and future-improvement items.

### Architecture and token model in the first draft

The wording in this first version is especially revealing about the documentation model in use at that time. It states that the service:

- generates signed access tokens,
- issues encrypted refresh tokens,
- uses a private RSA key,
- works with an API Gateway that encrypts refresh tokens using `AES-256-GCM`,
- expects HTTPS-only traffic,
- returns tokens via the API Gateway.

The README therefore encoded a fairly elaborate token design in which:

1. the Authentication Service signs tokens,
2. the Gateway participates in refresh-token encryption/decryption,
3. the overall system is explained through a split-responsibility security flow.

Whether that model was fully implemented at this point cannot be established from this chunk alone, but the README absolutely documents that intent.

### Diagram referenced but not embedded

The first version of the architecture section used a textual pointer:

- `See docs/authentication-flow.drawio.png for a visual overview...`

So at creation time, the README acknowledged the diagram asset but did not embed it inline.

## 2025-07-09 05:02:33 +0100 — commit `5c815ca854c8221ac441eea25daea021bd25ee3c`

The first follow-up edit changed how the diagram appeared in [`authentication-service/README.md`](authentication-service/README.md). Instead of a plain sentence pointing to the asset, the README now embedded the image inside a collapsible `<details>` block:

- summary text: “Click to view authentication flow diagram”
- inline image: `![Authentication Flow Diagram](./docs/authentication-flow.drawio.png)`

Before this commit, the diagram was only linked conceptually. After it, readers could expand the README and view the diagram directly without leaving the page.

This is a documentation UX decision rather than a content rewrite. It shows the author experimenting with how much visual material should be visible by default in a service README.

## 2025-07-09 05:27:08 +0100 — commit `38dfd02ab14a72588d3166a326ea79b3b9c22527`

The next commit reversed that presentation choice. The `<details>` wrapper was removed from [`authentication-service/README.md`](authentication-service/README.md), and the diagram was made visible inline by default:

- the image stayed embedded,
- the collapsible wrapper disappeared,
- a new explanatory line was added:
  - “This diagram shows the full login and token issuance process used by the Authentication Service.”

This created a before-to-after change in how the service README was meant to be consumed:

- before: the diagram was optional and hidden behind a click
- after: the diagram became part of the primary reading flow

The added explanatory sentence also made the diagram’s purpose more explicit, which suggests the previous `<details>` version may have felt too visually minimal or too indirect.

## 2025-07-09 05:41:55 +0100 — commit `269d5ef42c1e2dcbfa9e206880da0a3dbcf2e172`

This commit restyled the README heavily without fundamentally changing its subject matter. The top of [`authentication-service/README.md`](authentication-service/README.md) gained a long line of shields/badges describing:

- high security,
- JWT enabled,
- RSA-PSS signing,
- HTTPS enforced,
- AES-256-GCM encryption,
- JWS access tokens,
- JWE refresh tokens,
- env-based configuration,
- active maintenance,
- missing tests.

Section headings were also rewritten to include badges inline, for example:

- `Responsibilities` with JWT/signing badges
- `Security Highlights` with HTTPS and AES badges
- `How It Works` with JWS/JWE badges
- `Configuration` with a configurable badge
- `TODO / Future Improvements` with maintenance/tests badges

### Presentation changed more than substance

The underlying content stayed broadly the same:

- responsibilities remained focused on credential verification and token issuance,
- the embedded diagram remained in place,
- configuration variables stayed the same,
- the future-improvement list stayed the same.

What changed was tone and presentation. Before this commit, the README used emoji headings and relatively plain prose. After it, the document looked more like a security-focused marketing or showcase page, front-loading capability badges and repeating them inside section headers.

This makes the README more visually assertive, but also noisier. That matters because the next commit partially rolls some of that styling back.

## 2025-07-09 05:44:37 +0100 — commit `4db4b89a0ddc9fdabd9cbcc0f79ec24d7047647c`

While the authentication README was being styled, the API Gateway diagram asset kept evolving independently. This commit updated:

- [`api-gateway/docs/api-gateway-flow.drawio.png`](api-gateway/docs/api-gateway-flow.drawio.png)

from `296740` bytes to `301122` bytes.

No textual documentation changed in this commit. Because the diff is binary-only, the actual diagram edits are not visible from the evidence. The grounded conclusion is simply that the gateway flow diagram was still being revised after its earlier rename and README hookup in the previous day’s work.

This is important context for the service README changes: the text docs were not being updated against a fixed visual asset. The diagrams themselves were still moving.

## 2025-07-09 07:04:01 +0100 — commit `be376a0097552f9bd261eb657c4aa7544994436a`

The last commit in the chunk is the most substantive combined revision. It touched:

- [`api-gateway/docs/api-gateway-flow.drawio.png`](api-gateway/docs/api-gateway-flow.drawio.png)
- [`authentication-service/docs/authentication-flow.drawio.png`](authentication-service/docs/authentication-flow.drawio.png)
- [`authentication-service/README.md`](authentication-service/README.md)

### Both diagram assets were revised again

The gateway diagram grew from `301122` bytes to `310520` bytes, and the authentication-service diagram grew from `360249` bytes to `396086` bytes. This indicates both visuals were still under active refinement.

As with the earlier binary-only commits, the exact diagram changes cannot be described from the patch. The evidence only supports stating that both diagrams were revised in the same commit, which strongly suggests an effort to keep the gateway and auth-service documentation visually aligned.

### README content became more explanatory and less badge-heavy in headings

The README edits are more revealing.

#### Responsibilities section became slightly more concrete

The first bullet changed from:

- `Verifies login credentials from users.`

to:

- `Verifies login credentials from users via secure database lookup.`

This is a small but meaningful clarification. The earlier wording described the responsibility generically. The new wording adds an implementation-oriented mechanism, even if it is still high level.

#### Diagram presentation reverted to collapsible view

The “Architecture Diagram” section changed again:

- the plain inline image and explanatory `<em>` caption were removed,
- the section title regained an emoji,
- the image returned to a `<details>` block with a “Click to view” summary.

So the presentation sequence across the chunk becomes:

1. plain text reference only,
2. collapsible embedded image,
3. always-visible embedded image with caption,
4. collapsible embedded image again.

That is a clear example of iteration rather than linear improvement. The author was testing different README reading experiences and did not settle immediately.

#### “How It Works” was restructured for procedural clarity

The heading changed from a badge-heavy:

- `How It Works ![JWS] ![JWE]`

to a simpler:

- `🧠 How It Works`

More importantly, the login-flow bullets were rewritten. Before the change, step 2 was summarized as “If credentials are valid,” with nested bullet points for token generation and return. After the change, the step explicitly inserted database lookup and gateway encryption sequencing:

1. client sends login request over HTTPS,
2. credentials are validated using a secure DB lookup,
3. if valid:
   - access token is generated and signed,
   - refresh token is generated as a signed JWS,
   - refresh token is sent to API Gateway for JWE encryption,
   - both tokens are returned via API Gateway.

This is the clearest content refinement in the README. The earlier text described the model in broad security terms; the new text turns it into a more operational step-by-step flow.

#### Heading style was partially normalized

The section headings for:

- Architecture Diagram,
- How It Works,
- Configuration,
- TODO / Future Improvements

were simplified away from badge-heavy heading text and back toward emoji-labeled headings. The very large badge line at the top of the document remained, but some of the intra-document visual noise was reduced.

This suggests the author kept the “security posture” badges as a page-level framing device while deciding that repeating badges in each heading made the document harder to read.

## Evolution across the chunk

This chunk is more stable than `2025-07-09/chunks/001`. The previous chunk showed experimentation that ended in deletion of the authentication-service diagram artifacts from one path. This chunk shows those assets being put on firmer footing:

1. the authentication-service diagram is moved under `docs/`,
2. a first full service README is created,
3. the diagram embedding style is tried as collapsible,
4. then changed to always-visible with explanatory caption,
5. then the README is heavily badge-styled,
6. then simplified again while also making the token flow prose more explicit,
7. meanwhile both the gateway and authentication diagrams continue to evolve in parallel.

The strongest before-to-after shift is that the authentication-service documentation moved from nonexistent to service-specific and structured, with a named diagram, responsibilities, flow explanation, configuration, and future-work section. The exact visual content of the diagrams remains uncertain because the diffs are binary-only, but the textual evidence makes the intent clear: the repository was trying to give the authentication service the same documentation identity that the API Gateway had started to receive, while still iterating on naming, placement, and presentation details.
