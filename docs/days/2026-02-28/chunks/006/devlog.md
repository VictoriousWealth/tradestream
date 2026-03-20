# Devlog

## Chronology note from the evidence

This chunk follows the same pattern as the other `2026-02-28` chunks: the `AuthorDate` values shown in `diff.patch` are in July 2025, while the `CommitDate` values are February 28, 2026. The evidence provided here does not explain why, so the narrative below is limited to the observable file evolution and the commit order in the chunk.

## 2025-07-09 05:02:33 +0100 / 2026-02-28 01:47:44 +0000 — commit `c8031b6`

Commit `c8031b66dfa99a3e9f7ebd3342a0464903ec2a64` updates `authentication-service/README.md` so the architecture diagram is rendered inline rather than merely referenced as a file path. Before this commit, the `Architecture Diagram` section consisted of a sentence:

* `See docs/authentication-flow.drawio.png for a visual overview of the login and token generation process.`

After the patch, that section becomes a collapsible details block:

* `<details>`
* `<summary>Click to view authentication flow diagram</summary>`
* embedded Markdown image `![Authentication Flow Diagram](./docs/authentication-flow.drawio.png)`
* closing `</details>`

This is a real before → after shift in how the service documentation is presented. The README stops treating the authentication-flow diagram as something the reader must open separately and instead embeds it directly in the page, albeit behind a collapsible disclosure. The underlying diagram asset is unchanged in this commit; what changes is the documentation’s willingness to surface it inline.

## 2025-07-09 05:27:08 +0100 / 2026-02-28 01:47:45 +0000 — commit `9b4bc14`

Commit `9b4bc140a9821db0d3c02a61b8188641309b2c4a` immediately revises the same `Architecture Diagram` section in `authentication-service/README.md`. The collapsible `<details>` wrapper introduced in `c8031b6` is removed, and the image is made directly visible:

* `![Authentication Flow Diagram](./docs/authentication-flow.drawio.png)`

An explanatory italic caption is also added below it:

* `This diagram shows the full login and token issuance process used by the Authentication Service.`

Before this commit, the diagram was embedded but hidden behind a click-to-expand control. After the patch, it is always visible and carries explanatory text. The before → after evolution is therefore about reducing friction and making the visual explanation a first-class part of the README, not an optional hidden element.

This quick reversal also shows the documentation style was still being tuned. The author first tried an expandable presentation and then decided on always-on visibility minutes later.

## 2025-07-09 05:41:55 +0100 / 2026-02-28 01:47:46 +0000 — commit `154530b`

Commit `154530b1d1bf027b17df2d1fdcbaeb8a09500eb2` is the first large content pass on `authentication-service/README.md`. The document gains an extensive shield-badge header and several section-heading refinements.

Before this commit, the README began with a simple heading:

* `## 🛡️ Authentication Service`

Afterward, it starts with a long strip of badges advertising:

* `SECURITY: HIGH`
* `JWT ENABLED`
* `SIGNING: RSA-PSS (PS256)`
* `HTTPS ENFORCED`
* `ENCRYPTION: AES-256-GCM`
* `ACCESS TOKEN: JWS`
* `REFRESH TOKEN: JWE`
* `CONFIG: ENV-BASED`
* `MAINTENANCE: ACTIVE`
* `TESTS: MISSING`

The main heading is also reformatted to:

* `## Authentication Service ![SECURITY: HIGH](...)`

The rest of the patch keeps the same basic section structure but renames headings to be more badge-annotated and polished:

* `Responsibilities` now includes JWT and signing badges
* `Security Highlights` includes HTTPS/encryption badges
* `How It Works` gets JWS/JWE badges
* `Configuration` gets a configurability badge
* `TODO / Future Improvements` gets maintenance and tests-missing badges

This is a substantial before → after presentation shift. The authentication-service README moves from a straightforward engineering note into a much more visual, badge-heavy artifact optimized for quick signaling of security posture and feature themes.

It is also worth stating the boundary of the evidence here. The badges claim things such as AES-256-GCM encryption and JWE refresh tokens. Those are textual assertions in the README. This chunk proves the README was rewritten to emphasize them; it does not by itself validate those claims against code in this slice.

## 2025-07-09 05:44:37 +0100 / 2026-02-28 01:47:47 +0000 — commit `41c524c`

Commit `41c524cc908ed14a7719d7ad298d4e766dd5bfcb` updates the binary asset `api-gateway/docs/api-gateway-flow.drawio.png`, increasing it from 296,740 bytes to 301,122 bytes.

Because the file is binary, the exact content change is not visible in `diff.patch`. The most that can be said from the evidence is that the API gateway flow diagram continued to be refined after the earlier path and ownership cleanups in previous chunks. In before → after terms, the gateway-local diagram changes in place, but without any textual reference updates in the same commit.

## 2025-07-09 07:04:01 +0100 / 2026-02-28 01:47:48 +0000 — commit `f69f231`

Commit `f69f23133130ae3682bbad924dc5c299155ffac6` is the other major edit in this chunk. It changes three files:

* `api-gateway/docs/api-gateway-flow.drawio.png`
* `authentication-service/docs/authentication-flow.drawio.png`
* `authentication-service/README.md`

Both binary diagram assets are revised again:

* gateway flow grows from 301,122 bytes to 310,520 bytes
* authentication flow grows from 360,249 bytes to 396,086 bytes

The simultaneous binary changes strongly suggest the visual documentation for both services was still being refined together. The patch does not reveal how the diagrams changed semantically, so those specifics remain uncertain.

The textual README edits are more informative. Several lines are rewritten to make the service explanation more narrative and explicit:

* `Verifies login credentials from users.` becomes `Verifies login credentials from users via secure database lookup.`
* The architecture-diagram section goes back to a collapsible `<details>` block, reversing the always-visible image style from `9b4bc14`.
* `How It Works` loses the JWS/JWE badge-heavy title and becomes a simpler `### 🧠 How It Works`.
* The login-flow prose becomes more stepwise and operationally specific:
  * credentials are validated using a secure DB lookup
  * if valid, the access token is signed with PS256
  * the refresh token is generated as a signed JWS
  * the refresh token is sent to the API Gateway, which encrypts it as JWE
  * both tokens are returned via the gateway
* `Configuration` and `TODO / Future Improvements` headings are simplified again, dropping the badge suffixes introduced in `154530b`

This means the authentication-service README is no longer moving in just one direction toward more visual ornamentation. The before → after effect of `f69f231` is a partial rollback from the heavily badged style toward more explanatory prose, while still keeping the badge strip at the top. The document becomes more explicit about the step-by-step login and refresh path, even as some of the heading-level decorative badges are removed.

The reintroduction of the collapsible `<details>` wrapper around the diagram is also notable. The chunk evidence now shows a back-and-forth pattern:

1. embed diagram behind details
2. make it always visible
3. return it to details

That sequence indicates the README layout was being actively tuned for readability, not simply expanded once and left untouched.

## 2025-07-09 07:32:03 +0100 / 2026-02-28 01:47:48 +0000 — commit `52e25e1`

Commit `52e25e100b481b518891161bcc91a2146339ea38` rewrites `api-gateway/README.md` from a short authentication-flow note into a much fuller gateway overview.

Before this commit, the gateway README was headed:

* `## 🔐 Authentication & Token Validation Flow`

and mostly described the gateway in a compact way:

* secure HTTPS communication
* forwarding token issuance requests to authentication service
* access token validation and routing
* refresh token processing
* rejecting malformed, expired, or tampered tokens

After the patch, the README becomes:

* `## API Gateway – Secure Request Routing & Token Validation`

and expands into multiple sections:

* overview paragraph explaining the gateway as the central gatekeeper
* `Core Responsibilities`
* `Token Handling Flow`
* `Access Tokens (JWS)`
* `Refresh Tokens (JWE)`
* `Security Practices`
* `Architecture Overview`
* `Key Concepts Reflected in Diagram`
* a closing pointer to `docs/tradestream-prd.md`

The new text makes several architectural claims explicit that were previously only summarized:

* the gateway terminates and enforces HTTPS
* login and refresh are forwarded to the authentication service
* access tokens are verified with the RSA public key
* refresh tokens are decrypted using AES-256-GCM with a CEK encrypted by the API gateway’s RSA public key
* secure cookies and query-string avoidance are part of the security model

The architecture overview section also embeds the gateway flow diagram directly:

* `![API Gateway Flow Diagram](docs/api-gateway-flow.drawio.png)`

This is a major before → after documentation evolution. The API gateway README stops being a narrow note about token validation and becomes a service-level overview document with responsibilities, flow explanation, security model description, and a diagram.

Again, there is an evidence boundary. The README text asserts details such as JWE wrapping and AES-256-GCM usage. This chunk proves those claims were added to documentation; it does not independently validate them against code within this slice.

Taken together, this chunk is the point where the service-local READMEs become much more intentional. The authentication-service README experiments with how prominently to surface its diagram and how badge-heavy its presentation should be, while simultaneously refining the flow explanation. The gateway diagram asset keeps evolving in binary form, and by the end of the slice the API gateway README is transformed into a more complete service overview centered on secure routing and token handling rather than just a diagram caption. The technical system itself does not visibly change in these patches, but the repo’s service-level documentation becomes richer, more visual, and more audience-facing.
