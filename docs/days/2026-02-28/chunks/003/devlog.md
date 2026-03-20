# Devlog

## Chronology note from the evidence

This chunk has the same date pattern seen elsewhere on 2026-02-28: `AuthorDate` values are in July 2025, while the `CommitDate` values in `diff.patch` are February 28, 2026. The evidence provided here does not explain why. The safest way to handle that is to narrate the file evolution shown by the commits in order, without speculating about the reason for the rewritten commit dates.

## 2025-07-06 07:47:20 +0100 / 2026-02-28 01:47:19 +0000 — commit `2c24772`

Commit `2c2477245b3051749f6f375656626eb70bbe3086` makes a small but important addition to `docs/tradestream-prd.md`: under section `5.2 High-Level System Diagram`, it inserts an HTML image tag pointing at the repository-hosted architecture diagram:

* `<img src="https://github.com/VictoriousWealth/tradestream/blob/main/docs/architecture-diagram.png" alt="High-Level System Diagram">`

Before this commit, the PRD had a heading for the high-level system diagram but no actual embedded image beneath it. After the patch, the section stops being a placeholder and starts visually anchoring the architecture narrative with a diagram reference. This is a real before → after documentation improvement. The PRD no longer just promises a diagram section; it renders one inline through a GitHub-hosted asset reference.

There is also a subtle limitation visible from the evidence: the image points to `docs/architecture-diagram.png`, which later commits in this same chunk replace or reinterpret. So this first embedding is an initial binding between the PRD and one diagram asset, not yet the final settled diagram structure.

## 2025-07-07 10:15:47 +0100 / 2026-02-28 01:47:23 +0000 — commit `da91dbe`

Commit `da91dbe2e6b6cf8feb238a0a9ae39d0513fd2627` is the dominant change in this chunk. It simultaneously:

* adds `docs/architecture-diagram.drawio.png`
* deletes `docs/architecture-diagram.png`
* adds `docs/high-level-architecture-diagram.png`
* rewrites large sections of `docs/tradestream-prd.md`

The binary file changes show a diagram split emerging. Before this commit, the repo had only one architecture image path in the reviewed slice, `docs/architecture-diagram.png`, which the prior commit had embedded as the PRD’s high-level diagram. After `da91dbe`, that single path is removed, and two new diagram assets appear:

* `docs/architecture-diagram.drawio.png` at 107,504 bytes
* `docs/high-level-architecture-diagram.png` at 92,527 bytes

Even though the binary content is not inspectable from the patch, the filenames themselves and the accompanying PRD edits show the documentation model changing from one architecture image to two different levels of diagram.

The PRD rewrite confirms this structural shift. At the top of the document, a full `Table of Contents` is inserted, linking eleven numbered sections:

* title/document control
* executive summary
* technology overview
* goals and objectives
* scope and deliverables
* high-level architecture
* technical design
* assumptions and constraints
* risks and mitigations
* references and resources
* appendix

Before this commit, the PRD was a long linear document with bold section headers but without a top-level navigation system. Afterward, it becomes a navigable document with explicit internal links and section-level “back to top” links throughout. This is a major before → after shift in document usability. The PRD stops being just a static design memo and becomes a browsable long-form project document.

The content also changes in specific ways beyond navigation:

* the document version is updated from `0.1 (Draft)` to `1.0 (Finalised)`
* many sections gain `[↑ Top]` or `[↑ Section Top]` navigation links
* section `5.2` swaps the earlier image reference from `architecture-diagram.png` to `high-level-architecture-diagram.png`
* a large new `Section 11: Appendix` is appended

The new appendix is not trivial filler. It adds:

* acronyms and abbreviations
* definitions of key concepts such as microservice architecture, EDA, containerization, IaC, observability, authentication service, API gateway, message broker, JWT, and rate limiting
* a tools and technologies summary table
* example MVP credentials
* example transaction creation request/response payloads
* a future-considerations checklist
* quick links to major technical resources
* contact and ownership information

This changes the PRD from a requirements-and-design document into a more self-contained project reference. Before `da91dbe`, the PRD was formal but comparatively straightforward. After the commit, it behaves more like a handbook: navigable, referential, and suitable for someone reading it nonlinearly.

There are also a few content details worth treating carefully. The PRD now labels itself `1.0 (Finalised)` and includes fixed milestone language such as `PRD Finalized — July 2025` and `Authentication Service Functional — August 2025`. Those are text-level claims inside the PRD. This chunk proves that the document was revised to make those statements, but it does not independently verify those project milestones beyond their presence in the documentation.

## 2025-07-07 23:55:33 +0100 / 2026-02-28 01:47:24 +0000 — commit `27ab533`

Commit `27ab5339563de415af7df30ec39a01678bf79432` brings similar navigation thinking into `README.md`. Before this commit, the README already had named anchors for major sections, but the section headings themselves did not all include explicit “go back to table of contents” links. After the patch, the following major headings are rewritten to append `[↑ Top](#table-of-contents)`:

* Project Overview
* Technology Stack
* System Architecture
* Getting Started
* Usage
* Roadmap
* Security Considerations
* Documentation
* Learning Objectives
* License
* Contact
* Acknowledgements

The patch also contains small whitespace-normalization edits, such as a trailing space after `<a name="technology-stack"></a>` and after “Planned Future Enhancements:” and “Core Components:”. Those are low-signal. The main before → after change is navigational: the README becomes easier to use as a long page because every major section now offers a direct jump back to the top-level contents list.

This commit is also a sign that the documentation work in the PRD and README was being brought into stylistic alignment. The PRD had just gained `[↑ Top]` and `[↑ Section Top]` links in the previous commit, and now the README receives a lighter version of the same pattern.

## 2025-07-07 23:56:09 +0100 / 2026-02-28 01:47:25 +0000 — commit `5908b78`

Commit `5908b78d4d1332e99ff7aade7f9c8b6791b31dd7` completes the README navigation loop by adding an explicit anchor before the table of contents section:

* `<a name="table-of-contents"></a>`

Before this patch, the headings in commit `27ab533` were linking upward to `#table-of-contents`, but the README did not yet have a named anchor at that location in the reviewed diff. After `5908b78`, that target becomes explicit. This is a small but necessary before → after correction. The “back to top” links introduced one commit earlier now have a stable anchor to land on, instead of relying only on heading-generated IDs or a missing explicit anchor.

This is a good example of the repo’s documentation work being iterative rather than perfectly complete in one pass: navigation links were added first, then the corresponding destination anchor was inserted seconds later.

## 2025-07-08 00:00:07 +0100 / 2026-02-28 01:47:26 +0000 — commit `25629b1`

Commit `25629b1fa497fa7c5ec32a9787032747e7e8464d` updates `docs/architecture-diagram.drawio.png`, increasing it from 107,504 bytes to 297,057 bytes. Because the file is binary, the patch does not expose what changed visually. The strongest evidence-based statement is that the detailed architecture diagram was heavily revised very shortly after its introduction.

The timing and file growth suggest the newly added detailed diagram was still being substantially worked on. In before → after terms, the repo’s detailed architecture asset moves from an initial checked-in version to a significantly larger revised version, but the exact semantic content of that change is not visible from the evidence alone.

## 2025-07-08 00:04:37 +0100 / 2026-02-28 01:47:26 +0000 — commit `975935a`

Commit `975935adb58e90e3d8d1e51e57ef948ad025f6d2` immediately revises `docs/architecture-diagram.drawio.png` again, this time reducing it slightly from 297,057 bytes to 296,740 bytes. As with the previous commit, the file is binary and the patch gives no inspectable visual diff.

What can be said reliably is that the detailed diagram underwent at least two closely spaced iterations after being introduced. The first revision was large; the second appears to be a small refinement. This suggests the diagram was being actively tuned for correctness or presentation. Any stronger interpretation about exact content changes would be unsupported because the binary patch reveals only size differences, not diagram labels or layout.

## 2025-07-08 00:16:39 +0100 / 2026-02-28 01:47:28 +0000 — commit `3bcaf99`

Commit `3bcaf99f5251078afe8d48210449549fbe9012eb` updates `README.md` so that the system-architecture section reflects the two-diagram model introduced earlier in the chunk. Before this commit, the README pointed to a single diagram path:

* `See /docs/architecture-diagram.png for the full system diagram.`

After the patch, that becomes two separate references:

* `See /docs/high-level-architecture-diagram.png for a full high level system diagram.`
* `See /docs/architecture-diagram.drawio.png for a more detailed version focused on API Gateway and the Authentication Service.`

This is the cleanup that aligns README references with the file split introduced in `da91dbe`. Before `3bcaf99`, the README was still pointing at the old single-diagram model even though the repo had already moved to separate high-level and detailed binary assets. After the patch, the architecture section explicitly distinguishes between:

* a high-level overall system view
* a more detailed view focused on API Gateway and Authentication Service

This is a meaningful before → after clarification. The architecture documentation stops treating “the diagram” as a single artifact and starts presenting multiple diagram layers for different abstraction levels.

There is, however, one important evidence-based caveat in the patch itself. The Markdown links are textually inconsistent:

* the label text names `high-level-architecture-diagram.png`, but the actual Markdown link target shown in the diff is still `docs/architecture-diagram.png`
* the second line labels `architecture-diagram.drawio.png`, but its link target also appears as `docs/architecture-diagram.png`

That means the wording was updated for clarity, but the underlying hyperlink targets in the visible diff may not have been corrected at the same time. The safest interpretation is that the README was being adapted to the new diagram structure, but at least in the reviewed patch, the path references still show signs of mismatch between label and actual link target. This is exactly the kind of documentation drift that should be noted rather than normalized away.

Taken together, this chunk shows the repo’s architecture documentation becoming layered and more navigable. The PRD first gets a single inline diagram, then is substantially restructured with a full table of contents, section-top links, a split between high-level and detailed architecture images, and an appendix. The README then gains “back to table of contents” links and the missing table-of-contents anchor. Finally, the architecture references in the README are rewritten to distinguish between high-level and detailed diagrams, even though the visible Markdown link targets remain imperfect in the patch. The engineering system itself does not change here, but the way its architecture is explained becomes much more elaborate, hierarchical, and audience-aware.
