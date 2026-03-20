# Devlog

## 2025-07-06 05:28:25 +0100 — commit `728119b6640bbc621d84b4ad4647438bb76cc6c5`

The chunk opens with a full rewrite of [`README.md`](README.md) under the commit message `updated readme to match new idea of overall system`. Before this commit, the README was already a conventional project overview for “TradeStream — Real-Time Financial Data Processor”, but it was shorter, flatter, and oriented around a straightforward engineering-project description. After this commit, the file was reshaped into a much more presentation-heavy portfolio document.

The most visible change was structural. The new version introduced a centered HTML hero block, a linked table of contents, expanded sectioning, and a much more explicitly recruiter-facing tone. The opening paragraphs stopped describing TradeStream primarily as a “scalable, resilient microservice-based system” and instead framed it as a “backend portfolio project” that demonstrates secure APIs, event-driven architecture, decoupled data stores, Dockerized deployment, JWT authentication, real-time processing simulation, and AWS Lightsail deployment. That shift matters because it changed the README from a project-description artifact into a personal portfolio artifact.

The rewrite also expanded the content breadth. New sections were added for:

- usage,
- roadmap,
- security considerations,
- documentation,
- learning objectives,
- license,
- contact,
- acknowledgements.

Within those sections, the file moved from brief descriptive bullets to more explicit audience guidance. For example, “Getting Started” became a more polished installation section with a `git clone` example and `docker-compose up --build`, while “Usage” started explicitly naming backend portfolio demonstrations, microservice learning, container experimentation, and secure API practice as intended outcomes. The roadmap became checklist-like, separating “MVP Core Features” from “Future Enhancements”. The security section was also softened into future-oriented bullets such as rate limiting, RBAC, secure headers, and vulnerability scanning, rather than only describing current-state security.

There is also strong evidence that the inserted content was generated or copied from a template rather than written directly into the Markdown in one clean pass. The top of the file began with prose outside the actual document body: “Here’s a clean, recruiter-friendly…” and a “Finalized README.md” heading, followed by an opening fenced code block marker ````markdown. Those strings were not project content; they were wrapper instructions or scaffolding accidentally committed into the repository. That mistake drove the next several cleanup commits.

## 2025-07-06 05:31:22 +0100 — commit `fb82c206e5a3cc4f7e1eda767c60cda473f287cc`

The next commit was a surgical cleanup of the wrapper text accidentally committed in `728119b6640bbc621d84b4ad4647438bb76cc6c5`. In [`README.md`](README.md), seven lines were removed from the top of the file:

- the “Here’s a clean, recruiter-friendly…” prefatory sentence,
- the separator line under it,
- the “Finalized README.md for TradeStream” heading,
- the opening ````markdown fence.

Before this commit, the file looked like a prompt response pasted verbatim into the repository. After it, the README began directly with the centered project markup. This did not change the substantive content of the document, but it removed evidence of the generation workflow and made the file render as an actual README rather than as a README embedded inside explanatory text.

## 2025-07-06 05:34:05 +0100 — commit `6420fed300923d3608eafb21fea16a1e97730474`

The next edit corrected invalid or awkward HTML nesting at the top of [`README.md`](README.md). In the previous version, the `<h1>` was nested inside a `<p align="center">` block, and the descriptive lines were wrapped in an inner centered `<p>` block. This commit moved the `<h1 align="center">TradeStream — Real-Time Financial Data Processor</h1>` out to stand alone and simplified the remaining centered text into a single paragraph beneath it.

Before this change, the header region was visually intended to be centered, but the markup mixed block-level elements in a way that is fragile in GitHub rendering. After the change, the same visual intent remained, but the top section became cleaner and more likely to render consistently:

- standalone `<h1 align="center">`,
- one centered paragraph for subtitle and link,
- no nested heading inside a paragraph.

This was not a content rewrite. It was a rendering and markup hygiene pass on the already-expanded README structure.

## 2025-07-06 05:34:49 +0100 — commit `cfd35bea327bd5fc381d53a23be17ec47dfd3ae1`

Once the hero block was cleaned up, the next issue addressed was navigation. This commit inserted an explicit HTML anchor, `<a name="project-overview"></a>`, immediately above the “🚀 Project Overview” section in [`README.md`](README.md).

That anchor corresponds to the top-level “Explore the Project Overview »” link introduced earlier in the centered intro block. Before this commit, the link targeted `#project-overview`, but the section heading alone may not have resolved predictably, especially given the mix of emoji and Markdown heading syntax. After this change, the README had a guaranteed stable anchor target for that link.

This is a small patch, but it clarifies the development pattern in this chunk: the author first created a polished, navigable README, then immediately corrected the rough edges revealed by actual rendering and anchor behavior.

## 2025-07-06 05:58:08 +0100 — commit `9d4e28845cd2f322286f35ff2ccc4e029c0d128b`

This commit extended the anchor-fix idea across the rest of [`README.md`](README.md). Explicit named anchors were added before most major sections:

- `technology-stack`
- `system-architecture`
- `getting-started`
- `usage`
- `roadmap`
- `security-considerations`
- `documentation`
- `learning-objectives`
- `license`
- `contact`
- `acknowledgements`

At the same time, a few minor formatting irregularities were cleaned up:

- an extra blank line after the hero block was removed,
- the blank line between the existing `project-overview` anchor and its heading was removed,
- the trailing checklist-style notes at the bottom of the file were deleted.

The before-to-after evolution here is important. Before `9d4e28845cd2f322286f35ff2ccc4e029c0d128b`, the README had a linked table of contents but only one explicit section anchor, so most links depended on GitHub’s automatic heading-ID generation. After this commit, the table of contents had stable manual anchor targets across the document, which reduced the risk of broken links caused by emoji headings or heading-text edits.

The deletion of the closing notes also tightened the document’s tone. The removed lines — such as “You may add a LICENSE file later” and the note about preferring practical links over flashy badges — read like authoring reminders rather than reader-facing documentation. Their removal made the README more self-contained and less obviously derived from a drafting process.

## 2025-07-06 06:01:54 +0100 — commit `67dfd63d0405eed61375b0a53a9630ed70eb658c`

The chunk then expanded beyond the README for the first time by creating [`LICENSE`](LICENSE). The file was added from scratch and selected **Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)** as the governing license.

Before this commit, the repository referenced a license in README text, but no actual license file existed in this chunk’s evidence. After this commit, the repository had a concrete legal artifact defining:

- sharing and adaptation rights,
- attribution obligations,
- the non-commercial restriction,
- a link to the full CC BY-NC 4.0 license text.

This file addition resolved a documentation inconsistency introduced earlier. The README rewrite had included a license section, but that section still contained placeholder wording about choosing a license later. `67dfd63d0405eed61375b0a53a9630ed70eb658c` made the licensing story concrete at the repository level.

## 2025-07-06 06:02:46 +0100 — commit `dc2aeceed101f0a1c38d69e5f849438686b64c51`

Immediately after creating [`LICENSE`](LICENSE), the README’s license section was updated to stop speaking hypothetically. In [`README.md`](README.md), the previous generic sentence:

“Distributed under a license of your choice (e.g., MIT, Apache 2.0). See `LICENSE` for details.”

was replaced with a specific explanation of the chosen CC BY-NC 4.0 license. The new wording made three concrete changes:

- it named the exact license,
- it described allowed use in practical terms,
- it stated explicitly that commercial use is not permitted.

Before this commit, the repository had a real license file but a README still written as though the license decision had not been finalized. After it, the documentation and the legal file were aligned.

## 2025-07-06 06:04:33 +0100 — commit `623c4ce8212c560d9e4c8061b81ec57101316df5`

The last commit in the chunk made the README more externally polished and repository-specific. Two main changes landed in [`README.md`](README.md).

First, a block of shields.io badges was inserted directly beneath the `<h1>`:

- repository size,
- open issues,
- pull requests,
- license badge,
- last commit.

That change moved the README closer to a conventional open-source landing page presentation. Earlier in the chunk, the document had deliberately removed draft notes that mentioned avoiding flashy badges; by the end of the chunk, badges were added anyway, suggesting that the documentation direction shifted toward a more public GitHub-facing style rather than a minimal text-only portfolio page.

Second, the installation instructions were corrected to point at the actual repository URL. The clone command changed from:

`https://github.com/yourusername/tradestream.git`

to:

`https://github.com/VictoriousWealth/tradestream.git`

That edit resolved another artifact of templating. Before this commit, the installation section still contained a placeholder username. After it, the README was tied to the real repository namespace and was executable as written.

## File-level evolution across the chunk

Across commits `728119b6640bbc621d84b4ad4647438bb76cc6c5` through `623c4ce8212c560d9e4c8061b81ec57101316df5`, the work stayed narrowly focused on two files: [`README.md`](README.md) and later [`LICENSE`](LICENSE). The chronology shows a clear progression:

1. The README was expanded dramatically into a recruiter-facing portfolio document.
2. Template spillover and Markdown/HTML wrapper mistakes were removed.
3. Header markup was normalized for better rendering.
4. Navigation anchors were first added for the main section, then rolled out across the entire document.
5. Placeholder notes were removed once the document became more mature.
6. A real license file was added.
7. The README’s license text was updated to match that file.
8. Visual GitHub badges and the correct repository clone URL were added to make the page feel complete and public-facing.

The evidence does not show changes to code, architecture, or runtime behavior in this chunk. Everything observable is documentation-layer work, but it is not static polishing alone. The repeated commits show the author iterating on presentation, navigation, legal clarity, and the transition from a drafted README template into a repository-specific landing page.
