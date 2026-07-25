# InvestLog — RC Pre-release Docker Images Design

**Date:** 2026-07-24
**Status:** Approved (pending spec review)

## Goal

Publishing a GitHub **pre-release** (RC, e.g. `v0.2.0-rc1`) should build and push
Docker Hub images for server and client under that exact tag, using the same
mechanism as a stable release — but must **never** move the `latest` tag. `latest`
stays reserved for stable releases only.

## Context (as-built)

- `.github/workflows/release.yml` (added by
  `docs/superpowers/specs/2026-07-12-cicd-pipeline-design.md`, PR #43) triggers on
  `release: types: [published]` and, for both `publish-server` and `publish-client`
  jobs, unconditionally tags/pushes the release's `tag_name` **and** `latest`.
- GitHub's `published` release activity fires for pre-releases too — only the
  `released` activity type excludes them (confirmed against GitHub Actions docs).
  So this workflow, unchanged, already builds images for pre-releases; the gap is
  that it also wrongly moves `latest` for them.
- `release.yml` has never actually executed (zero runs in Actions history). Both
  existing pre-releases (`v0.1.0-rc1`, 2026-06-27; `v0.1.0-rc2`, 2026-07-13T02:25Z)
  were published before the workflow existed (merged 2026-07-13T12:33Z) — the
  original design's own verification step assumed the opposite (that RC publishes
  would update `latest`), which is the specific behavior this design reverses.
- Pre-release detection: use GitHub's native `prerelease` boolean
  (`github.event.release.prerelease`), the same flag already used to mark
  `v0.1.0-rc1`/`rc2` as pre-releases in the GitHub UI — no new tag-naming
  convention needed.
- Root `compose.yaml` pulls `arthurgregorio/investlog-server:latest` /
  `-client:latest` unconditionally (quick start). RC images are only reachable by
  pinning their exact tag — never surfaced through the quick-start path.

## Decision

| Decision | Choice |
| --- | --- |
| Pre-release detection | `github.event.release.prerelease` (native GitHub flag) |
| Server: version tag | Always built and pushed (`bootBuildImage` + `docker push`), unchanged |
| Server: `latest` tag/push | Gated behind `if: ${{ !github.event.release.prerelease }}` |
| Client: version tag | Always pushed via `docker/build-push-action`, unchanged |
| Client: `latest` tag | Computed tag list step: version tag only when prerelease, version tag + `latest` when stable; fed into `build-push-action`'s `tags:` input |
| Trigger | Unchanged — `release: types: [published]` (already covers both) |
| README | Add a short "Testing a pre-release (RC)" note near Quick start, showing `docker pull ...:vX.Y.Z-rcN` for both images |
| PR/issue split | Single PR — root-level only (`.github/workflows/`, `README.md`), no `server/`/`client/` code touched |

## Architecture — `release.yml`

```
publish-server:
  - checkout
  - setup-java
  - docker login
  - bootBuildImage --imageName=...:<tag_name>              # always
  - docker push ...:<tag_name>                              # always
  - if: !prerelease
      docker tag ...:<tag_name> ...:latest
      docker push ...:latest

publish-client:
  - checkout
  - setup-buildx
  - docker login
  - determine tags (id: tags)                               # always
      prerelease  -> tags=<repo>:<tag_name>
      not prerelease -> tags=<repo>:<tag_name>\n<repo>:latest
  - build-push-action
      tags: ${{ steps.tags.outputs.tags }}
```

`publish-server`'s "push" step splits into an always-run version-tag push and a
conditional latest tag+push, mirroring the existing two-step shape
(tag-then-push) already in the job. `publish-client` needs a tag-list computed
ahead of `build-push-action` since that action takes its tags as a single
multi-line input rather than separate steps.

## Files to create / change

| Path | Action |
| --- | --- |
| `.github/workflows/release.yml` | **update** — gate `latest` tag/push behind `!prerelease` in both jobs |
| `README.md` | **update** — add "Testing a pre-release (RC)" note |

Out of scope: trigger type, auth, build mechanism, job structure, Compose files —
all unchanged from the 2026-07-12 design.

## Verification

- Publishing a pre-release (`vX.Y.Z-rcN`, prerelease checkbox on) results in
  `arthurgregorio/investlog-server:vX.Y.Z-rcN` and `-client:vX.Y.Z-rcN` on Docker
  Hub, with `latest` unchanged on both repos.
- Publishing a stable release results in both the version tag and an updated
  `latest` on both repos, same as before this change.
- `docker pull arthurgregorio/investlog-server:vX.Y.Z-rcN` succeeds and matches
  the README instructions.
- Since Docker Hub pushes can't run in CI without real credentials, verify by
  reading the workflow YAML for correct conditionals/tag computation (workflow
  logic review), not a live run — same limitation the 2026-07-12 design's
  release.yml verification had.
