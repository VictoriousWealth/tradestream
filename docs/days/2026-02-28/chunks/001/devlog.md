# Devlog

## Chronology note from the evidence

This chunk is unusual in one specific way that should be stated upfront because it affects how the timeline is read. In `commits.txt` and `diff.patch`, the commits carry July 2025 `AuthorDate` values, but their `CommitDate` values are February 28, 2026. The allowed evidence does not explain why that divergence exists. The safest interpretation is that this chunk captures rewritten or replayed history for early repository work. The narrative below therefore focuses on the observable before → after file evolution and uses the commit order shown in the chunk, without inventing any reason for the date mismatch.

## 2025-07-05 04:22:46 +0100 / 2026-02-28 01:47:01 +0000 — commit `56a60e8`

Commit `56a60e8afd564834f5c980054b294bcb196d06bc` touches `.gitignore` and deletes `.env.example`, which is shown in the patch as an empty file being removed. Before this commit, `.gitignore` was narrower and more root-oriented:

* `/target/`
* `/*.iml`
* `*.class`
* `*.env`
* `*.log`
* `.DS_Store`
* `.idea/`
* `.vscode/`

After the commit, the ignore file becomes more recursive and explicit about common Java/build/editor noise:

* `**/target/`
* `**/*.class`
* `**/*.iml`
* `**/.idea/`
* `*.env`
* `.env`
* `.env.*`
* `*.log`
* `.DS_Store`
* `.vscode/`
* `.idea/`
* `Thumbs.db`

This is a real before → after cleanup. The repository moves from a mix of root-specific and file-pattern ignores to a more practical multi-module ignore scheme that better matches a nested Java workspace. The addition of both `.env` and `.env.*` also broadens local-environment-file coverage. The deletion of `.env.example` is weaker evidence because the file was empty in the patch, so no configuration intent can be inferred from its contents. What is grounded is only that the repository stopped carrying an empty example env file and tightened its ignore rules around local build/editor artifacts.

## 2025-07-05 04:27:55 +0100 / 2026-02-28 01:47:02 +0000 — commit `1a1eca3`

Commit `1a1eca3e262d3c660241f2e7475ebb2b4118400a` makes a very small but revealing `README.md` correction. The patch removes two lines near the end of the file:

```text
```
```

Before this commit, the README ended with a dangling code fence after the acknowledgements/disclaimer area. After the commit, that accidental formatting artifact is gone. This is a small edit, but it matters because the README was in the middle of a larger restructuring sequence in the following commits. The document moved from malformed Markdown toward a renderable state before more substantial layout work continued.

## 2025-07-06 05:28:25 +0100 / 2026-02-28 01:47:03 +0000 — commit `31a2635`

Commit `31a2635e485ae34bfea4c36792d2435c699b498b` is the first major README rewrite in the chunk. The patch replaces a comparatively plain top section with a much larger, more presentation-oriented structure. Before this commit, the README opened with a simple Markdown title and short description:

* `# TradeStream — Real-Time Financial Data Processor`
* a brief sentence about resilience and financial-system-style engineering practices

After `31a2635`, the top of the file becomes a much more elaborate template-like structure. The diff shows several things happening at once:

* a centered HTML/Markdown presentation block is introduced
* a `Table of Contents` section is added
* sections are rewritten and expanded under clearer headings
* wording becomes more portfolio-oriented and polished

The rewrite also changes some of the repo’s self-description. For example:

* “TradeStream demonstrates” becomes “TradeStream is a backend portfolio project that demonstrates”
* microservices are described as “decoupled” with independent Postgres and Redis stores
* deployment is described as AWS Lightsail rather than more generic cloud phrasing
* the CI row becomes `CI/CD (MVP)` rather than simply `CI`
* the note is changed to explicitly say the project is educational and does not process real financial transactions

The architecture and usage sections are also reformatted to read more like a polished portfolio README than an engineer’s scratch overview. This commit is not just copyediting. The README moves from a simpler technical summary toward a recruiter-friendly/project-showcase document with navigation, stronger sectioning, and more explicit framing of the system as a portfolio artifact.

There is also a notable evidence-based caution here: the very first inserted lines say “Here’s a clean, recruiter-friendly, professional hybrid `README.md`…” and include “Finalized README.md for TradeStream” plus an opening ````markdown fence. That indicates the new content initially included template/instructional wrapper text rather than only final repository prose. The next commit addresses that.

## 2025-07-06 05:31:22 +0100 / 2026-02-28 01:47:04 +0000 — commit `9447769`

Commit `94477699fcd9315b39166f59247d9a3d99c5d4c1` removes the template residue introduced in `31a2635`. Specifically, it deletes:

* the explanatory line beginning “Here’s a clean, recruiter-friendly…”
* the “Finalized README.md for TradeStream” line
* the opening ````markdown fence

Before this commit, the README was no longer just plain project documentation; it still contained scaffolding text that made it read like generated output or a proposed template. After `9447769`, the document is normalized back into direct repository-facing prose beginning with the centered HTML block. This is a clear before → after cleanup: the polished README introduced in the previous commit stops looking like a pasted instruction/template artifact and starts looking like an actual project README.

## 2025-07-06 05:34:05 +0100 / 2026-02-28 01:47:05 +0000 — commit `4d51109`

Commit `4d51109e4f04f7ce3dc5cee885d9ec1f2a784b9f` adjusts the README header formatting. Before this commit, the centered block contained an `<h1 align="center">` nested inside `<p align="center">`, followed by a nested `<p align="center">` for the subtitle content. After the patch:

* the `<h1 align="center">TradeStream — Real-Time Financial Data Processor</h1>` is moved out as a standalone top-level HTML heading
* the surrounding `<p align="center">` then contains only the descriptive text and the anchor link
* the nesting becomes flatter and more conventional for GitHub README rendering

This is a formatting/alignment cleanup, but it matters because the immediately preceding commits were still settling the README’s presentation model. The before → after evolution is from a more awkward nested HTML structure to a cleaner header arrangement that is less likely to render strangely on GitHub.

## 2025-07-06 05:34:49 +0100 / 2026-02-28 01:47:06 +0000 — commit `b15b5c7`

Commit `b15b5c763fc5acde8edd2093479df6c3653f6173` adds one small but specific navigation improvement to `README.md`: an explicit anchor line before the project overview section:

* `<a name="project-overview"></a>`

Before this commit, the header block linked users to `#project-overview`, but the README relied on GitHub’s automatic heading ID behavior alone. After the patch, that target is made explicit. This is a minor before → after change, but it is part of the broader navigation work completed in the next commit. It shows the documentation moving from “linked in spirit” to “explicitly anchor-addressable.”

## 2025-07-06 05:58:08 +0100 / 2026-02-28 01:47:08 +0000 — commit `06c6bf9`

Commit `06c6bf906975daa91c2225a78b65594329c7508f` expands the anchor/navigation strategy across the entire README. Before this commit, only `project-overview` had an explicit anchor, and some spacing was inconsistent around headings and separators. After the patch, named anchors are added ahead of nearly every major section:

* `technology-stack`
* `system-architecture`
* `getting-started`
* `usage`
* `roadmap`
* `security-considerations`
* `documentation`
* `learning-objectives`
* `license`
* `contact`
* `acknowledgements`

The patch also removes some stray blank lines and deletes the trailing checklist-style note:

* “You may add a `LICENSE` file later”
* “You prefer clean, practical documentation links over flashy GitHub badges”

This is a more meaningful edit than it first appears. Before `06c6bf9`, the README had already become more polished, but it still mixed final documentation with residue from how it had been assembled, and its in-page navigation was only partially explicit. After the commit, the file becomes a more internally coherent navigation document with stable named anchors and fewer out-of-band editorial notes. The before → after effect is that the README now reads much more like a deliberately authored long-form project document rather than a recently adapted template.

## 2025-07-06 06:01:54 +0100 / 2026-02-28 01:47:09 +0000 — commit `63d6f37`

Commit `63d6f37c835e3285d9c6f625ad3a2a5292150d2a` adds a `LICENSE` file containing a short-form Creative Commons Attribution-NonCommercial 4.0 notice. Before this commit, the README’s license section said the project was “Distributed under a license of your choice (e.g., MIT, Apache 2.0). See `LICENSE` for details,” but there was no `LICENSE` file in the chunk evidence. After the commit, that placeholder situation is resolved by adding a specific non-commercial license text:

* `Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)`
* copyright attribution to Nick Efe Oni
* short bullet-point permissions and restrictions
* a link to the full CC BY-NC 4.0 license page

This is an important before → after clarification. The repo stops implying that a license file might exist or be chosen later and instead commits to one concrete license. It also retroactively makes the earlier README language about licensing more meaningful, though the README itself in this chunk still contains the generic “license of your choice” phrasing rather than being updated to match the exact CC BY-NC wording in the new file.

That mismatch is worth stating as uncertainty/imperfection rather than glossing over it. The chunk clearly proves that the `LICENSE` file was added with a CC BY-NC 4.0 license, but the README text visible in the same patch sequence still reads like a placeholder. So the evolution here is only partially complete within this slice: legal intent becomes concrete in the repository root, but the user-facing README language has not yet fully caught up.

Taken together, this chunk shows the repo’s earliest visible documentation and hygiene pass being reconstructed under cleaned-up commit messages. The repository first tightens ignore rules and drops an empty `.env.example`, then fixes malformed Markdown, then performs a multi-step README transformation from a basic technical note into a centered, recruiter-friendly, sectioned document with table of contents and anchors, and finally lands a real license file. The engineering substance of the product does not change in this slice; the repo’s shape as a presentable, navigable, and legally labeled project does.
