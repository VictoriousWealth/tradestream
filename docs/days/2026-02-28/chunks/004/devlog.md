# Devlog

## Chronology note from the evidence

As with the other `2026-02-28` chunks, `diff.patch` shows July 2025 `AuthorDate` values paired with February 28, 2026 `CommitDate` values. The evidence set does not explain that divergence. The narrative below therefore focuses on the concrete file evolution visible in the chunk and uses the commit order provided, without inferring why the history was rewritten.

## 2025-07-08 01:13:22 +0100 / 2026-02-28 01:47:29 +0000 — commit `41d1664`

Commit `41d1664a6c71f09f79eb8caa83cc321d1152610b` is the first major diagram-path reshuffle in this chunk. It touches three items:

* `README.md`
* `api-gateway/README.md`
* a binary rename from `docs/architecture-diagram.drawio.png` to `api-gateway/docs/token-authentication-flow.drawio.png`

Before this commit, the repo was still treating `docs/architecture-diagram.drawio.png` as the detailed architecture diagram referenced from the root README, and the API gateway README embedded that same file via:

* `![Token Authentication Flow](../docs/architecture-diagram.drawio.png)`

After `41d1664`, two things change at once. First, the binary diagram file is physically moved out of the shared `docs/` directory into `api-gateway/docs/` and renamed to `token-authentication-flow.drawio.png`. Second, the API gateway README is updated to point at its new local copy:

* `![Token Authentication Flow](docs/token-authentication-flow.drawio.png)`

This is an important before → after shift in ownership. The detailed token/authentication flow diagram stops living as a generic architecture asset at repo level and becomes a service-local API gateway documentation artifact.

The root `README.md` is also updated in the same commit, but only partially correctly. The descriptive label changes from the old generic detailed architecture-diagram wording to:

* `See api-gateway/docs/architecture-diagram.drawio.png for a more detailed version focused on API Gateway and the Authentication Service.`

However, the actual Markdown link target visible in the patch still points to `docs/architecture-diagram.png`, not to the new gateway-local file. So before → after, the root README starts trying to acknowledge the diagram’s new location and purpose, but the hyperlink target is not yet corrected. This sets up the next few commits.

## 2025-07-08 01:14:45 +0100 / 2026-02-28 01:47:30 +0000 — commit `b599b85`

Commit `b599b850a808aa31a75326ef0756c07672d684ba` is the first root-README correction pass after the diagram move. The visible change is only one line, but it matters:

* the label text is updated from `api-gateway/docs/architecture-diagram.drawio.png`
* to `api-gateway/docs/token-authentication-flow.drawio.png`

This means the root README’s descriptive text now matches the new filename introduced in `41d1664`. But the visible Markdown link target in the diff still remains `docs/architecture-diagram.png`. So the before → after shift is only partial progress: the human-facing text becomes more accurate, while the underlying clickable target shown in the patch is still stale.

## 2025-07-08 01:15:47 +0100 / 2026-02-28 01:47:31 +0000 — commit `c382cb1`

Commit `c382cb12e26f0d709ecb9cdc3b675946ea872820` continues the same one-line repair cycle in `README.md`. In this pass, the Markdown link target is changed away from the stale shared `docs/architecture-diagram.png` path and toward an API gateway path:

* from `docs/architecture-diagram.png`
* to `api-gateway/docs/token-authentication-flow.png`

This is progress, but it is still not fully correct. The descriptive label already says `token-authentication-flow.drawio.png`, but now the target path omits the `.drawio` portion and points at `token-authentication-flow.png`. So before → after, the root README becomes closer to the real file location, but the filename extension/path still does not align with the actual renamed binary shown in `41d1664`.

This is a good example of documentation drift being fixed incrementally rather than in one perfect edit. The evidence shows the path being corrected in stages: first the label, then the target, but with an intermediate wrong filename.

## 2025-07-08 01:16:22 +0100 / 2026-02-28 01:47:32 +0000 — commit `a855c68`

Commit `a855c68015cd21aa82a5b9b8c30ddf3b3cc56795` completes the immediate path fix in the root README by updating the link target from:

* `api-gateway/docs/token-authentication-flow.png`

to:

* `api-gateway/docs/token-authentication-flow.drawio.png`

At this point, the human-facing label and the Markdown target finally match the binary file introduced in `41d1664`. The before → after effect is that the root README’s reference to the detailed gateway/authentication diagram becomes internally consistent, at least for the current filename.

These three quick successive commits (`b599b85`, `c382cb1`, `a855c68`) are best understood as a narrow repair sequence. They do not change the documentation model again; they correct the fallout from the first move-and-rename by progressively aligning the README text and link target with the actual gateway-local diagram asset.

## 2025-07-08 01:18:07 +0100 / 2026-02-28 01:47:33 +0000 — commit `ad2b2f1`

Commit `ad2b2f19f5534014be65efa98a69b2344756d842` is unrelated to diagrams and makes a single contact-details correction in the root README. The LinkedIn profile link in the `Contact` section changes from:

* `https://www.linkedin.com/in/nick-oni`

to:

* `https://www.linkedin.com/in/nick-efe-oni`

This is a straightforward before → after accuracy fix. The repo’s README contact section stops pointing at a shorter or older LinkedIn path and starts using the more complete profile URL. It is low in technical significance, but it matters because the README by this point is clearly being treated as a polished public-facing project document, and correctness of owner/contact links is part of that polish.

## 2025-07-09 03:46:19 +0100 / 2026-02-28 01:47:33 +0000 — commit `e89f2af`

Commit `e89f2afd6de00d21fd2045878d5d29482359323a` is the other major diagram event in this chunk. It performs two binary operations:

* renames `api-gateway/docs/token-authentication-flow.drawio.png` to `api-gateway/docs/api-gateway-flow.drawio.png`
* adds `authentication-service/docs/authentication-flow.drawio.png` as a new binary asset

This is the point where the repo’s diagram model becomes more semantically separated. Before this commit, the gateway-local detailed diagram had a filename centered on “token authentication flow,” and it was described in the README as a diagram focused on API Gateway and Authentication Service together. After `e89f2af`, the gateway-local diagram is renamed to `api-gateway-flow.drawio.png`, and a separate `authentication-service/docs/authentication-flow.drawio.png` appears.

In before → after terms, one shared detailed authentication-flow artifact is split into two service-specific documentation assets:

* one for the API gateway
* one for the authentication service

Because both files are binary, the patch does not expose their visual contents. The safest interpretation is based on filenames and rename/add operations alone: the repository stopped using a single gateway-local “token authentication flow” diagram to represent the combined story and began maintaining distinct diagram assets for gateway and authentication-service concerns.

The commit message is simply `Deploy`, so the file operations themselves are the only reliable guide here.

## 2025-07-09 03:48:14 +0100 / 2026-02-28 01:47:35 +0000 — commit `aad0964`

Commit `aad09642ea0f67898206fe342990363acf85f97f` updates `api-gateway/README.md` to match the diagram rename from the previous commit. Before this patch, the gateway README still embedded:

* `![Token Authentication Flow](docs/token-authentication-flow.drawio.png)`

After `aad0964`, it instead references:

* `![Token Authentication Flow](docs/api-gateway-flow.drawio.png)`

This is the cleanup that aligns service-local documentation with the renamed binary asset introduced in `e89f2af`. Before → after, the API gateway README stops pointing at a file path that no longer exists and starts pointing at the new gateway-specific flow diagram filename.

One subtle thing remains worth stating from the evidence. The alt text stays `Token Authentication Flow` even though the file itself has been renamed to `api-gateway-flow.drawio.png`. That suggests the diagram’s conceptual scope may still include token/authentication lifecycle content, even after the asset naming was made more explicitly gateway-centric. The patch alone cannot confirm whether the image contents changed semantically, only that the reference and filename did.

Taken as a whole, this chunk is a diagram-ownership and reference-correction pass. First, a detailed architecture/authentication diagram is pulled out of the shared `docs/` area and made local to `api-gateway/docs`, while the API gateway README is updated to embed it there. Then the root README undergoes three quick successive fixes to get the new path right. A contact-link correction lands in the middle. Finally, the detailed flow diagram is split conceptually into gateway and authentication-service assets, and the gateway README is brought into sync with the renamed gateway diagram. The core evolution here is not code or architecture behavior; it is documentation structure becoming more service-scoped, even though the patch shows that path correctness had to be repaired incrementally rather than in one clean pass.
