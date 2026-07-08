# CLAUDE.md

This file provides repository-wide guidance to Claude Code. See also `client/CLAUDE.md` and
`server/CLAUDE.md` for stack-specific conventions.

## Pull request conventions

**Always split a piece of work into separate PRs by layer — never bundle them:**

- **Server PR** — any change under `server/`.
- **Client PR** — any change under `client/`.
- **Docs/spec PR** — any change outside both (`docs/`, `README.md`, `.env.example`, root-level
  config, etc.), when the work also touches server and/or client.

A single feature almost always spans layers; each layer still gets its own branch and its own PR.
Mixing server and client changes in one PR was tried once and made review painful — a reviewer
looking at Spring Security config doesn't want to scroll past Vue components, and vice versa.

When a feature is developed on a long-lived feature branch (e.g. `feature/authentication`), the
per-layer PRs target that feature branch, not `main`; the feature branch itself gets one PR into
`main` once all its layer PRs have landed.

Every commit and PR description should reference the tracking issue for the phase/feature it
belongs to (`Closes #N` / `Refs #N`) when one exists.

## Migrations

Never edit an existing Liquibase changelog file — always add a new one under
`server/src/main/resources/db/changelog/changes/**`.
