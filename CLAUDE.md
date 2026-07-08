# CLAUDE.md

This file provides repository-wide guidance to Claude Code. InvestLog is a monorepo with two
independently built projects:

| Folder | Stack | Read its CLAUDE.md when you |
|---|---|---|
| `server/` | Kotlin / Spring Boot 4 / jOOQ / Liquibase | touch server code, write a migration, add an endpoint, or run/build the backend |
| `client/` | Vue 3 / TypeScript / Pinia / Buefy | touch client code, add a component/view/store, or run/build the frontend |

Each subfolder's `CLAUDE.md` is the source of truth for that stack's commands, coding
conventions, and architecture — read it before making changes there. This root file only holds
conventions that apply across the whole repo, regardless of which folder you're working in.

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

Never edit an existing Liquibase changelog file — always add a new one. See `server/CLAUDE.md`
("Migrations") for the file-naming convention and why this matters.
