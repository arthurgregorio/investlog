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

## Every piece of work starts with a GitHub issue

**No work happens in this repository without an issue.** Not a bugfix, not a one-line typo, not a
docs tweak. The issue is where the work is specified, reviewed and remembered; the branch, the
commits and the PR are just the mechanics that carry it out.

When the user asks for work that has no issue yet, the first step is to **create the issue** (see
the template below), confirm it with the user, and only then branch and write code. Do not start
editing files and open the issue afterwards to paper over it.

Before starting on an existing issue, **check whether a PR already targets it** — `gh issue view
<N>` (linked PRs show in the timeline) or `gh pr list --search "<N>"`. `gh issue list` never
surfaces PRs, so issue-only triage silently duplicates in-flight work.

### The issue is the spec — never a file in the repo

Design specs and implementation plans are **not** committed as `.md` files anywhere in the working
tree. The spec is the issue body; additional detail is issue comments. This overrides the default
behaviour of the `superpowers:brainstorming` and `superpowers:writing-plans` skills, which want to
write to `docs/superpowers/specs/` and `docs/superpowers/plans/` — do not let anything new land
there. A scratchpad copy outside the repo is fine when a local file is genuinely needed (e.g. as a
`--body-file` source or a `PLAN_FILE` for tooling).

`docs/superpowers/specs/` and `docs/superpowers/plans/` already contain files from before this
rule. They are **frozen history**: don't add to them, don't update them, and don't treat them as
current truth — the issue always wins.

## Writing an issue

**Title:** short and precise. A reader scanning the issue list should know what it is without
opening it. Prefix with the layer when the issue is one layer of a larger piece of work
(`server: wallet detail endpoint`, `client: wallet detail view`).

**Body:** the repo has a settled section skeleton — #204, #205, #206, #211 and #213 are good
worked examples. Follow it; don't invent a new arrangement per issue. The single most important
property is that an agent picking the issue up cold can **start implementing from the body alone**,
without asking a follow-up question.

### Feature and sub-issue skeleton

```markdown
## Motivation
## Dependencies        <- optional; blocking or ordering-related #N issues
## Design
### Schema             <- only when a migration is involved
### Server             <- only for the layers this issue actually touches
### Client
## Out of scope
## Acceptance criteria
```

- **Motivation** — what this achieves and why, in **5 lines or fewer**, as prose. What's broken or
  missing today, then what this issue changes. No design detail.
- **Design** — the whole specification: the change points and how they behave. This is where an
  implementer looks to know what to build, so it must **name its reference points explicitly** —
  file paths (`client/src/views/LoginView.vue`), repositories, packages, endpoint signatures, table
  and column names, component names. A `### Server` section that says what to build but not where
  is not finished. Subsections mirror the layers being touched, so the reader sees the layer split
  the sub-issues will follow.
- **Out of scope** — what someone might reasonably assume is included and isn't, and which issue
  owns it instead. This is what keeps a PR from sprawling.
- **Acceptance criteria** — a checkable list of the observable outcomes that mean the issue is
  done. Written so it can be verified from the running app or the tests, not from reading the diff.

### Umbrella skeleton

An umbrella issue opens with a blockquote callout naming the feature branch and where sub-PRs go,
then:

```markdown
> **Umbrella issue.** Work happens on the long-lived `feature/<N>-<slug>` branch. Each subtask below
> gets its own branch and its own PR **into that feature branch**, not into `main`. Once every
> subtask has landed, the feature branch gets a single PR into `main` and merging it closes this issue.

## Motivation
## Scope
## Subtasks                    <- checkbox list of the sub-issues, plus suggested order
## Related, not part of this   <- optional; adjacent issues this is often confused with
## Out of scope
## Acceptance criteria
```

`Scope` replaces `Design` here: the umbrella states the shape of the whole feature, and each
sub-issue carries the `Design` detail for its own layer.

### Bug and maintenance skeleton

Small defect and housekeeping issues use a lighter shape — don't force them through the feature
template:

```markdown
## Problem      (or ## Summary)
## Fix
## Why          <- optional; only when the fix isn't self-explanatory
```

`## Failing tests` is a useful addition when a test is what surfaced the bug (see #196).

### Keeping the body stable

The body is the stable statement of the work. Extra context found while implementing, revised
approaches, and review findings go in as **issue comments** (`gh issue comment <N> --body-file
<scratchpad>`), not by rewriting the body — the body is the spec, the comments are its history.
Rewrite the body only when the spec itself genuinely changed.

**Every issue gets, at creation time:**

- A **milestone** — always, unless the user explicitly says not to link one when the issue is
  created. Check what's open with `gh api repos/:owner/:repo/milestones --jq '.[].title'` and set
  it with `gh issue create --milestone "<name>"`.
- The **right label**: `feature` for new functionality, `bug` for defects, `documentation` for
  docs, `maintenance` for refactors and housekeeping, `github_actions` for workflow changes.
- **`arthurgregorio` as assignee.**

## Umbrella issues and sub-issues

An issue maps to exactly one PR (see below). When a piece of work touches several parts of the
software — which is almost always, since server and client changes never share a PR — it does
**not** become one issue with several PRs. It becomes an **umbrella issue** with **sub-issues**.

- The **umbrella issue** holds the objective, the full survey of change points, and the overall
  plan. It also **names the feature branch** where all the sub-work is integrated and tested
  together (e.g. `feature/211-wallet-relocation`).
- Each **sub-issue** is one self-contained chunk with its own PR. The natural cut is by layer:
  server, client, and docs/infra. Sub-issue bodies use the ordinary feature skeleton, scoped to that
  chunk, and open by stating which branch they target: *"Subtask of #211. Targets the
  `feature/211-wallet-relocation` branch, not `main`."* (#212 and #213 are the worked examples.)
- Sub-issue PRs target the **feature branch**, not `main`.
- Once every sub-issue has landed on the feature branch, **one PR merges the feature branch into
  `main`, and that PR closes the umbrella issue.**

Link sub-issues to their parent with **GitHub's sub-issues feature**, not just a "Subtask of #N"
line in the body — prose links don't show up in `gh api .../sub_issues`, don't populate the parent's
progress bar, and don't survive anyone reorganising the text:

```bash
for n in <child> <child>; do id=$(rtk proxy gh api repos/:owner/:repo/issues/$n --jq .id); rtk proxy gh api -X POST repos/:owner/:repo/issues/<parent>/sub_issues -F sub_issue_id=$id --jq '"linked #\(.number)"'; done
```

Two traps this command works around. The endpoint wants the child's **REST database id** (`gh api
repos/:owner/:repo/issues/<n> --jq .id`) — not its issue number, and not the GraphQL node id that
`gh issue view --json id` returns. And that id must arrive as a JSON integer, so `-F` is required;
`-f sub_issue_id=$id` sends a string and fails with `422 Invalid property /sub_issue_id: … is not
of type integer`. `rtk proxy` keeps RTK's filter off the raw ids. Verify afterwards with `gh api
repos/:owner/:repo/issues/<parent>/sub_issues --jq '.[].number'`.

**Why the layer split exists:** mixing server and client changes in one PR was tried once and made
review painful — a reviewer looking at Spring Security config doesn't want to scroll past Vue
components, and vice versa. That's a rule about how work is *sliced into sub-issues*, and the
one-PR-per-issue rule follows from it rather than fighting it.

**Maintenance is the exception.** The layer split applies to work that *builds something* — features
and fixes. Pure maintenance of the project's existing structure — dependency and toolchain bumps,
build config, CI workflows, repo conventions — stays a **single issue and a single PR** even when it
spans `server/`, `client/` and the repo root at once. The review-pain argument doesn't apply: a
reviewer reading a version bump wants to see every version that moved in one place, not three PRs
that only make sense together. Label these `maintenance` (plus `documentation`, `github_actions`,
etc. as applicable) and don't build an umbrella for them.

### Issues opened before these rules

Some open issues predate this model and say so out loud — #211, for instance, states *"this ships as
a server PR and a client PR, both referencing this issue"*, which the one-PR-per-issue rule now
forbids. **The issue body does not override this file.** When you pick up an issue whose shape
conflicts with these rules, say so and ask the user whether to restructure it before starting —
don't silently follow the outdated instruction in the body, and don't restructure someone's issue
unasked.

#176 is the reference example of the shape to aim for: an umbrella issue with #174, #212 and #213
attached as real sub-issues, each targeting the `feature/176-wallet-detail-view` branch.

## Branch naming

Every branch Claude creates must be `feature/<slug>` or `fix/<slug>` and reference its issue —
no exceptions. Only the user may bypass this rule themselves.

## Pull request conventions

**One issue, one PR.** A PR closes exactly one issue, and an issue is closed by exactly one PR. If
the work doesn't fit in a single PR, that's the signal to restructure it as an umbrella issue with
sub-issues — not to open a second PR against the same issue.

**Every PR must, at creation time:**

- **`Closes #N`** in the body, naming its own issue. Because of the 1:1 rule there is never a
  reason to use `Refs #N` for the issue a PR implements; a sub-issue PR may additionally mention
  its umbrella issue as context, but the only `Closes` is its own sub-issue.
- The **same milestone as its issue** — `gh issue view <N> --json milestone` to check. `gh pr
  create` does not inherit it, so it's the easiest of these to drop.
- The **label** matching the work (`feature`, `bug`, `documentation`, `maintenance`, …).
- **`arthurgregorio` as assignee.**

If any of these weren't set at creation time, fix it immediately:

```bash
gh pr edit <N> --add-label feature --add-assignee arthurgregorio --milestone "<name>"
```

## Writing issue and PR bodies

**Never hard-wrap prose.** Write each paragraph and each list item as a single physical line, no
matter how long. GitHub renders a single `\n` inside a paragraph as a hard line break, so every
"tidy" wrap at 90 characters becomes a visible break and turns the issue into a narrow, choppy
column. Structural markdown (headings, blank lines between blocks, `- ` / `1. ` markers) still
goes on its own line as normal — only the prose *within* a block must stay unwrapped.

**Pass bodies with `--body-file`**, pointing at a file in the session scratchpad, rather than an
inline heredoc. Large bodies containing backticks and code fences have broken inline heredoc
quoting before.

## Comments

**Code carries no comments.** If something needs a comment to be understood, the design is wrong —
refactor it until the names and the structure say it instead. This applies to server and client
code alike, everywhere in the repo, not just the folder currently being edited.

The only surviving exception is a **non-obvious WHY that the code genuinely cannot express and the
issue does not already record**: a framework quirk, an upstream bug being worked around, a hidden
constraint that makes the obvious implementation wrong. Never a comment that restates the spec, the
business rule, or what the next line does.

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