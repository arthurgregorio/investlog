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

**Every PR must, at creation time:**
- Get the `feature` label (or whatever label matches the work — `bug`, `documentation`, etc. —
  `feature` is the default for anything adding new functionality).
- Be assigned to `arthurgregorio`.
- Reference its tracking issue in the body, using the issue number from the phase/feature this
  work belongs to.
  - **This PR is the entire fix for the issue** (e.g. a self-contained docs/spec issue, or the
    last remaining layer of a split feature): use `Closes #N` — GitHub auto-closes the issue when
    this PR merges. This is about whether the *work* is fully done by this PR, not about which
    category (server/client/docs) it falls into — a docs-only issue with one PR still gets
    `Closes #N`.
  - **More PRs are still coming for the same issue** (the normal case for a multi-layer feature,
    since server/client/docs are always split — see above): every one of those PRs uses `Refs #N`,
    never `Closes #N`. Using `Closes #N` on more than one PR auto-closes the issue the moment the
    *first* of them merges, even though the others (and the rest of the phase) are still open.
    Close the issue manually once every PR for that phase has actually merged.
- Carry the same milestone as its tracking issue (`gh issue view <N> --json milestone` to check,
  `gh pr create --milestone "<name>"` or `gh pr edit <N> --milestone "<name>"` to set it). Easy to
  drop since `gh pr create` doesn't set it from the issue automatically.

Use `gh pr edit <N> --add-label feature --add-assignee arthurgregorio --milestone "<name>"` right
after `gh pr create` if any of label/assignee/milestone weren't set at creation time — don't leave
a PR missing one of these.

## Branch naming

Every branch Claude creates must be `feature/<slug>` or `fix/<slug>` and reference an issue —
no exceptions. Only the user may bypass this rule themselves.

## Comments

**No comments that just restate the spec or business logic**, in server or client code — only
comment a non-obvious WHY (a framework quirk, a workaround, a hidden constraint). Applies
everywhere in the repo, not just the folder currently being edited.

<!-- rtk-instructions v2 -->
# RTK (Rust Token Killer) - Token-Optimized Commands

## Golden Rule

**Always prefix commands with `rtk`**. If RTK has a dedicated filter, it uses it. If not, it passes through unchanged. This means RTK is always safe to use.

**Important**: Even in command chains with `&&`, use `rtk`:
```bash
# ❌ Wrong
git add . && git commit -m "msg" && git push

# ✅ Correct
rtk git add . && rtk git commit -m "msg" && rtk git push
```

## RTK Commands by Workflow

### Build & Compile (80-90% savings)
```bash
rtk cargo build         # Cargo build output
rtk cargo check         # Cargo check output
rtk cargo clippy        # Clippy warnings grouped by file (80%)
rtk tsc                 # TypeScript errors grouped by file/code (83%)
rtk lint                # ESLint/Biome violations grouped (84%)
rtk prettier --check    # Files needing format only (70%)
rtk next build          # Next.js build with route metrics (87%)
```

### Test (60-99% savings)
```bash
rtk cargo test          # Cargo test failures only (90%)
rtk go test             # Go test failures only (90%)
rtk jest                # Jest failures only (99.5%)
rtk vitest              # Vitest failures only (99.5%)
rtk playwright test     # Playwright failures only (94%)
rtk pytest              # Python test failures only (90%)
rtk rake test           # Ruby test failures only (90%)
rtk rspec               # RSpec test failures only (60%)
rtk test <cmd>          # Generic test wrapper - failures only
```

### Git (59-80% savings)
```bash
rtk git status          # Compact status
rtk git log             # Compact log (works with all git flags)
rtk git diff            # Compact diff (80%)
rtk git show            # Compact show (80%)
rtk git add             # Ultra-compact confirmations (59%)
rtk git commit          # Ultra-compact confirmations (59%)
rtk git push            # Ultra-compact confirmations
rtk git pull            # Ultra-compact confirmations
rtk git branch          # Compact branch list
rtk git fetch           # Compact fetch
rtk git stash           # Compact stash
rtk git worktree        # Compact worktree
```

Note: Git passthrough works for ALL subcommands, even those not explicitly listed.

### GitHub (26-87% savings)
```bash
rtk gh pr view <num>    # Compact PR view (87%)
rtk gh pr checks        # Compact PR checks (79%)
rtk gh run list         # Compact workflow runs (82%)
rtk gh issue list       # Compact issue list (80%)
rtk gh api              # Compact API responses (26%)
```

### JavaScript/TypeScript Tooling (70-90% savings)
```bash
rtk pnpm list           # Compact dependency tree (70%)
rtk pnpm outdated       # Compact outdated packages (80%)
rtk pnpm install        # Compact install output (90%)
rtk npm run <script>    # Compact npm script output
rtk npx <cmd>           # Compact npx command output
rtk prisma              # Prisma without ASCII art (88%)
rtk uv run <cmd>        # Compact uv project command output
```

### Files & Search (60-75% savings)
```bash
rtk ls <path>           # Tree format, compact (65%)
rtk read <file>         # Code reading with filtering (60%)
rtk grep <pattern>      # Search grouped by file (75%). Format flags (-c, -l, -L, -o, -Z) run raw.
rtk find <pattern>      # Find grouped by directory (70%)
```

### Analysis & Debug (70-90% savings)
```bash
rtk err <cmd>           # Filter errors only from any command
rtk log <file>          # Deduplicated logs with counts
rtk json <file>         # JSON structure without values
rtk deps                # Dependency overview
rtk env                 # Environment variables compact
rtk summary <cmd>       # Smart summary of command output
rtk diff                # Ultra-compact diffs
```

### Infrastructure (85% savings)
```bash
rtk docker ps           # Compact container list
rtk docker images       # Compact image list
rtk docker logs <c>     # Deduplicated logs
rtk kubectl get         # Compact resource list
rtk kubectl logs        # Deduplicated pod logs
```

### Network (65-70% savings)
```bash
rtk curl <url>          # Compact HTTP responses (70%)
rtk wget <url>          # Compact download output (65%)
```

### Meta Commands
```bash
rtk gain                # View token savings statistics
rtk gain --history      # View command history with savings
rtk discover            # Analyze Claude Code sessions for missed RTK usage
rtk proxy <cmd>         # Run command without filtering (for debugging)
rtk init                # Add RTK instructions to CLAUDE.md
rtk init --global       # Add RTK to ~/.claude/CLAUDE.md
```

## Token Savings Overview

| Category | Commands | Typical Savings |
|----------|----------|-----------------|
| Tests | vitest, playwright, cargo test | 90-99% |
| Build | next, tsc, lint, prettier | 70-87% |
| Git | status, log, diff, add, commit | 59-80% |
| GitHub | gh pr, gh run, gh issue | 26-87% |
| Package Managers | pnpm, npm, npx | 70-90% |
| Files | ls, read, grep, find | 60-75% |
| Infrastructure | docker, kubectl | 85% |
| Network | curl, wget | 65-70% |

Overall average: **60-90% token reduction** on common development operations.
<!-- /rtk-instructions -->