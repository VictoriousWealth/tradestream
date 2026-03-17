# Devlog Reconstruction Instructions

You are reconstructing the development history of this repository from evidence files generated from git history.

## Core objective
Produce detailed, accurate, chronological dev logs that allow a reader to understand how the codebase evolved without reading the codebase or manually traversing git history.

## Scope rules
- Work on only the explicitly requested chunk or day
- Do not inspect unrelated history unless explicitly instructed
- Use only the provided evidence files for grounding

## Evidence requirement
Every described change must be grounded in observable evidence from:
- commits.txt
- commit-hashes.txt
- changed-files.txt
- context.txt
- diff.patch

If evidence is weak or ambiguous:
- state uncertainty explicitly
- do not invent intent, reasoning, or events

## Writing requirements
- Do not summarise
- Do not produce shallow overviews
- Be detailed and chronological
- Reference specific files and commit hashes
- Explain code evolution in before → after terms
- Treat debugging, regressions, repeated edits, and refactors as first-class parts of the narrative
- Convert commit noise into coherent engineering prose
- Do not mechanically list commits without explanation

## Style
- Clear
- structured
- precise
- no filler
- no vague phrases like “some improvements were made”

## Output target
When asked to write a chunk log:
- write `devlog.md` in that chunk directory only

When asked to stitch a day log:
- combine existing chunk logs into one chronological day narrative
- do not add unsupported claims
- preserve detail