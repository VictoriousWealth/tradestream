# Devlog

## 2025-07-16 07:23:16 +0100 — authentication service port mapping corrected in Compose (`79389babbd5bf770ca5c552d0c0345bcd8ad4023`)

The chunk opens with commit `79389ba` / `79389babbd5bf770ca5c552d0c0345bcd8ad4023`, labeled `Deploy` in `commits.txt`. The patch touches only `docker-compose.yml`, and the substantive change is concentrated in the `authentication-service` port mapping.

Before this commit, the service was exposed as:

```yaml
ports:
  - "8080:8080"
```

After this commit, it becomes:

```yaml
ports:
  - "8082:8082"
```

This is the central before -> after change in the commit. The service image is still `tradestream-auth-service:dev`, and the rest of the service block is largely unchanged in the excerpt, so the evidence indicates this was a corrective alignment of the container’s public binding with the authentication service’s intended port rather than a broader Compose redesign.

The operational implication is straightforward even though the patch is small. Before the change, the authentication service was trying to occupy host port `8080`, which is conventionally the gateway-facing entry point elsewhere in the repository’s documentation. After the change, it is moved to `8082`, which fits the auth-service port convention that appears in other documentation from nearby periods. Since this chunk’s evidence is limited to the patch itself, the safest wording is that the commit corrected the Compose-level host/container port pairing for auth service; it is strongly suggestive of a conflict or mismatch being fixed, but the evidence does not explicitly state what runtime problem triggered it.

The remaining hunk lines in the patch only remove and add blank lines around the `postgres`, `volumes`, and `secrets` sections:

- two blank lines are removed before `postgres`
- blank lines are inserted before `volumes:` and `secrets:`

Those edits do not alter behavior. They are formatting side effects around the real change.

Because `changed-files.txt` for the entire chunk lists only two files total:

- `docker-compose.yml`
- `README.md`

this first commit establishes the day’s pattern clearly. There is no service-code evolution here. The repository work visible in this chunk is split between one environment-level correction and a series of documentation link repairs later that night.

## 2025-07-16 23:32:59 +0100 — README architecture-diagram link text is edited, but target mismatch remains (`2c737896ea58367a85dc5e349279061a26a72137`)

More than sixteen hours later, activity resumes with commit `2c73789` / `2c737896ea58367a85dc5e349279061a26a72137`, titled `Update README.md`.

The patch changes one line in the `System Architecture` section of `README.md`.

Before:

```markdown
> _See [`/docs/high-level-architecture-diagram.png`](docs/architecture-diagram.png) for a full high level system diagram._
```

After:

```markdown
> _See [./docs/high-level-architecture-diagram.png](docs/architecture-diagram.png) for a full high level system diagram._
```

This is a documentation-link correction attempt, but it is only partial. The before -> after change removes the code-formatted display text and replaces it with a simpler Markdown label `./docs/high-level-architecture-diagram.png`, yet the actual target remains `docs/architecture-diagram.png`.

That means the visible filename in the prose and the actual link destination still do not match after this commit:

- displayed label suggests `high-level-architecture-diagram.png`
- target still points to `architecture-diagram.png`

The patch therefore shows not a final fix, but the first step in a multi-commit cleanup sequence. This is a good example of why the commit sequence matters. If this commit were read alone, it might look like the architecture diagram reference had been standardized. The immediate follow-up commits show that it had not.

## 2025-07-16 23:33:52 +0100 — README link targets are normalized to explicit relative paths (`c350682175d5a4a22a4b79bc62b81ca180bc4471`)

Less than a minute later, commit `c350682` / `c350682175d5a4a22a4b79bc62b81ca180bc4471` continues work on the same `System Architecture` section.

Two lines are changed.

Before this commit, after `2c737896ea58367a85dc5e349279061a26a72137`, the section read:

```markdown
> _See [./docs/high-level-architecture-diagram.png](docs/architecture-diagram.png) for a full high level system diagram._

> _See [`api-gateway/docs/token-authentication-flow.drawio.png`](api-gateway/docs/token-authentication-flow.drawio.png) for a more detailed version focused on API Gateway and the Authentication Service._
```

After `c350682175d5a4a22a4b79bc62b81ca180bc4471`, it becomes:

```markdown
> _See [./docs/high-level-architecture-diagram.png](./docs/high-level-architecture-diagram.png) for a full high level system diagram._

> _See [`api-gateway/docs/token-authentication-flow.drawio.png`](./api-gateway/docs/token-authentication-flow.drawio.png) for a more detailed version focused on API Gateway and the Authentication Service._
```

This commit performs a more mechanical path normalization:

- both links are rewritten to use `./...` relative targets
- the architecture-diagram target is changed away from `docs/architecture-diagram.png` to `./docs/high-level-architecture-diagram.png`
- the API-gateway flow link is also given an explicit `./` prefix

The before -> after evolution here is not about new concepts; it is about path consistency. The author appears to be moving from mixed root-relative and relative-link styles toward a consistent local-relative style.

There is, however, an uncertainty the evidence forces us to keep visible: the chunk contains only `README.md` and `docker-compose.yml`, so there is no way from this evidence set alone to verify whether `./docs/high-level-architecture-diagram.png` actually exists on this date. The patch shows the README reference being changed to that path, but the existence of the underlying asset is outside this chunk’s visible files. The same applies to `./api-gateway/docs/token-authentication-flow.drawio.png`. The safe claim is that the README was rewritten to point there, not that the target was definitely valid.

## 2025-07-16 23:36:14 +0100 — architecture references split into separate gateway and authentication flow diagrams, but one target is miswired (`e571e6aed2850d4cefeefa5ef8d733251f570abd`)

Commit `e571e6a` / `e571e6aed2850d4cefeefa5ef8d733251f570abd`, also titled `Update README.md`, introduces the largest conceptual change in the README portion of this chunk.

Before this commit, the detailed-flow note was a single line:

```markdown
> _See [`api-gateway/docs/token-authentication-flow.drawio.png`](./api-gateway/docs/token-authentication-flow.drawio.png) for a more detailed version focused on API Gateway and the Authentication Service._
```

After this commit, that single combined reference is replaced with two separate references:

```markdown
> _See [`./api-gateway/docs/api-gateway-flow.drawio.png`](./api-gateway/docs/api-gateway-flow.drawio.png) for a more detailed version focused on API Gateway._

> _See [`./authentication-service/docs/authentication-flow.drawio.png`](./api-gateway/docs/api-gateway-flow.drawio.png) for a more detailed version focused on the Authentication Service._
```

This is an important before -> after step because it shows the documentation being refined from one shared “token authentication flow” diagram into two service-specific diagram references:

- one explicitly for API Gateway
- one explicitly for Authentication Service

That tells us the architecture narrative was becoming more granular. Instead of presenting a single combined flow visual, the README is trying to direct readers to distinct diagrams per component.

But the second line also introduces a concrete mistake visible directly in the patch. Its label says:

- `./authentication-service/docs/authentication-flow.drawio.png`

while its link target still points to:

- `./api-gateway/docs/api-gateway-flow.drawio.png`

So after this commit, the Authentication Service diagram reference is textually differentiated but functionally miswired. The visible prose says “focused on the Authentication Service,” but the destination path is still the gateway diagram.

This is exactly the kind of detail the chunk evidence supports well: the documentation is not merely being updated; it is being updated through iterative trial, and one of those iterations leaves behind a mismatch between label and destination.

## 2025-07-16 23:37:29 +0100 — final README cleanup closes the authentication diagram link mismatch and standardizes code-style formatting (`f919960b171c87cee7ffbb780fe5f3694efadf17`)

The final commit in the chunk, `f919960` / `f919960b171c87cee7ffbb780fe5f3694efadf17`, resolves the mistake introduced one minute earlier and also slightly standardizes formatting.

The architecture-diagram line changes from:

```markdown
> _See [./docs/high-level-architecture-diagram.png](./docs/high-level-architecture-diagram.png) for a full high level system diagram._
```

to:

```markdown
> _See [`./docs/high-level-architecture-diagram.png`](./docs/high-level-architecture-diagram.png) for a full high level system diagram._
```

This is a small presentational cleanup: the displayed path is switched back into backtick-style formatting, matching the style already used for the component-specific diagram references below it.

More importantly, the Authentication Service line changes from the incorrect state introduced in `e571e6aed2850d4cefeefa5ef8d733251f570abd`:

```markdown
> _See [`./authentication-service/docs/authentication-flow.drawio.png`](./api-gateway/docs/api-gateway-flow.drawio.png) for a more detailed version focused on the Authentication Service._
```

to the corrected version:

```markdown
> _See [`./authentication-service/docs/authentication-flow.drawio.png`](./authentication-service/docs/authentication-flow.drawio.png) for a more detailed version focused on the Authentication Service._
```

The before -> after evolution is precise and complete here:

- before: authentication-service diagram label existed, but its destination still pointed to the gateway asset
- after: both the label and the target point to the authentication-service asset path

That makes `f919960b171c87cee7ffbb780fe5f3694efadf17` the actual completion point of the README cleanup sequence that started with `2c737896ea58367a85dc5e349279061a26a72137`.

## file-level evolution across the chunk

### `docker-compose.yml`

Only one commit touches Compose in this chunk: `79389babbd5bf770ca5c552d0c0345bcd8ad4023`.

Before:
- `authentication-service` exposed `8080:8080`

After:
- `authentication-service` exposes `8082:8082`

No other Compose behavior is shown changing in the evidence. This reads as a targeted environment correction to align the auth service with its intended port.

### `README.md`

All four later commits in the chunk touch the same `System Architecture` subsection, and together they form a tight repair sequence rather than four unrelated edits.

The progression is:

1. `2c737896ea58367a85dc5e349279061a26a72137`
   - starts adjusting the architecture-diagram link label
   - leaves label/target mismatch in place

2. `c350682175d5a4a22a4b79bc62b81ca180bc4471`
   - rewrites the targets into explicit `./...` relative paths
   - also changes the architecture-diagram destination from `architecture-diagram.png` to `high-level-architecture-diagram.png`

3. `e571e6aed2850d4cefeefa5ef8d733251f570abd`
   - splits one combined detailed-flow reference into two service-specific references
   - introduces an incorrect destination on the authentication-service line

4. `f919960b171c87cee7ffbb780fe5f3694efadf17`
   - fixes the authentication-service link target
   - restores consistent backtick formatting on the high-level diagram line

The overall before -> after evolution of the README in this chunk is therefore:

- before the sequence: one high-level architecture diagram link and one combined gateway/auth flow diagram link, using inconsistent relative-path conventions
- after the sequence: one high-level architecture diagram link plus two separate component-specific flow-diagram links, all written with more explicit path formatting, and with the authentication-service destination corrected in the final step

The evidence is strongest on the mechanics of the link edits and weaker on whether every target file truly existed at this point, because the chunk exposes only `README.md` and `docker-compose.yml`. The README changes are therefore best understood as a documentation-path cleanup sequence, not as proof that all referenced image assets were already present and correct in the repository at that exact moment.
