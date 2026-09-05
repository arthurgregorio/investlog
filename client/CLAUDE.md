# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Node 24.15 or newer is required (`engines.node` in `package.json`); `vitest` and `jsdom` refuse
older runtimes.

```bash
npm install
npm run dev        # dev server at http://localhost:8081
npm run build      # vue-tsc --noEmit -p tsconfig.app.json, then vite build (fails on type errors)
npm run preview    # preview the production build
npm run type-check # vue-tsc --noEmit -p tsconfig.app.json only
npm run test       # vitest (unit tests)
npm run lint       # eslint . --fix
npm run format     # prettier --write src/
```

Code style is enforced by `.prettierrc.json` (no semicolons, single quotes, trailing commas) and
`eslint.config.js` (Vue + TypeScript + Prettier, flat config). Point WebStorm's formatter at
Prettier (Settings → Languages & Frameworks → JavaScript → Prettier → set package path, enable
"On code reformat" and "On save") so `Ctrl+Alt+L` matches `npm run format`/`npm run lint`.

`tsconfig.app.json` has `strict`, `noUnusedLocals` and `noUnusedParameters` enabled,
so unused imports/variables fail `npm run build` / `npm run type-check`.

**TypeScript stays on the 6.0 line.** TS 7 is not usable here: it stopped exporting
`typescript/lib/tsc`, which crashes `vue-tsc` 3, and typescript-eslint refuses to load under it
(its peer range is `>=4.8.4 <6.1.0`). TS 6.0 is the line both tools support. Relatedly,
`tsconfig.app.json` carries no `baseUrl` — TS 6 hard-errors on it (TS5101) and the `@/*` path
alias does not need one, so don't re-add it.

## Coding Conventions

**No abbreviated names** — use full descriptive names for variables, props, loop iterators,
and composable locals. Examples:

| Avoid | Use instead |
|-------|------------|
| `w in wallets` | `wallet in wallets` |
| `h in holdings` | `holding in holdings` |
| `qty`, `pct`, `amt` | `quantity`, `percentage`, `amount` |
| `baseCurrency` shorthand `base` | always `baseCurrency` |

## Architecture

InvestLog is a manual (PT-BR) investment logbook: stocks and FIIs, crypto and funds, entered by
hand. Stock/FII `current_price` is auto-updated hourly server-side during B3 trading hours via
brapi.dev; funds remain fully manual with no live market feed. Either way the manual price-edit
flow keeps working as an override. Stack: Vue 3 `<script setup>` + TypeScript 6, Pinia 4,
Vue Router 5, Vite 8, Vitest 5, ESLint 10. Path alias `@` → `src/`.
Backend: Spring Boot 4 / Kotlin at `http://localhost:8080`,
proxied via `/private` by Vite dev server.

The UI was ported pixel-for-pixel from a Claude Design React/Babel prototype handoff. `src/assets/styles.css`
is that ported CSS spec (Tabler visual language) and is the single source of styling truth —
components rely on its classes and CSS custom properties rather than scoped/component styles.

### API layer (`src/api/`)

All HTTP calls go through axios — `src/api/client.ts` creates the instance with
`baseURL: '/private/v1'` and an error interceptor that shows a Buefy toast on failure.

| File | Endpoints |
|------|-----------|
| `wallets.ts` | `GET/POST/PATCH/DELETE /wallets` |
| `holdings.ts` | `GET /holdings`, stock/crypto/fund CRUD + lots/contributions |
| `overview.ts` | `GET /overview`, `GET /overview/series` |
| `assetTypes.ts` | `GET/POST/DELETE /stock-types`, `/fund-types` |
| `rates.ts` | `GET /currency-rates`, `PUT /currency-rates/{code}` |
| `auth.ts` | `POST /auth/login`, `/register`, `/totp/enroll`, `/totp/verify`, `/google/link`, `GET /auth/session`, `/auth/config`, `POST /auth/logout`, `GET`/`DELETE /auth/trusted-devices` |
| `usersAdmin.ts` | `GET /users`, `PATCH /users/{id}/approve`\|`block`\|`unblock`\|`role`\|`totp-reset`, `DELETE /users/{id}` (admin-only) |
| `profile.ts` | `GET`/`PATCH /profile`, `PATCH /profile/password` |
| `configurations.ts` | `GET /configurations`, `PATCH /configurations/{key}` (runtime feature toggles) |
| `stockPriceSync.ts` / `cryptoPriceSync.ts` | `POST /stock-price-sync`, `POST /crypto-price-sync` (admin-only manual triggers) |

### Domain types (`src/types.ts`)

- `WalletKind` = `'stocks' | 'crypto' | 'funds'`.
- `WalletResponse` — wallet from API with `holdingCount` and `totalInvested`.
- `HoldingRow` — paginated row from `GET /holdings` (summary only, no lots).
- `StockHoldingDetail` / `CryptoHoldingDetail` / `FundHoldingDetail` — full holding fetched
  lazily when a row is expanded in the investments table.
- `PortfolioSummary`, `KindSummary`, `SeriesPoint` — overview endpoint shapes.
- `AssetType`, `CurrencyRate` — settings endpoint shapes.
- `PagedResponse<T>` — Spring `PagedModel` envelope: `{ content, page: { size, number, totalElements, totalPages } }`.
- `UserStatus` = `'PENDING' | 'APPROVED' | 'BLOCKED'`; `SessionResponse` (`name`, `email`, `role`,
  `status`) and `UserAdminResponse` (adds `id`, `authProvider`, `totpEnabled`) are the auth-facing
  shapes — see **Authentication & Authorization** below.

### State (`src/stores/`)

Split domain stores — each loads lazily (call `.load()` in `onMounted`, no double-fetch):

| Store | Loads from | Exposes |
|-------|------------|---------|
| `wallets` | `GET /wallets` | `wallets[]`, `walletById(id)`, `refresh()` |
| `holdingsList` | `GET /holdings` | `rows[]`, `page`, `totalElements`, `loadKind(kind, page)` |
| `overview` | `GET /overview` + `/overview/series` | `summary`, `series`, `refresh()` |
| `typesList` | `GET /stock-types` + `/fund-types` | `stockTypes[]`, `fundTypes[]`, CRUD actions |
| `rates` | `GET /currency-rates` | `rates[]`, `baseCurrency`, `upsertRate(...)` |
| `appearance` | `localStorage` | `dark`, `accent` — persisted across sessions |
| `auth` | `GET/POST /auth/*` | `session`, `isAdmin`, `login/register/enrollTotp/verifyTotp/logout/restoreSession` |
| `usersAdmin` | `GET/PATCH/DELETE /users/*` | `users[]`, `approve/block/unblock/changeRole/resetTotp/remove` |
| `configurations` | `GET/PATCH /configurations` | runtime feature toggles (price-sync switches) |
| `currency` | `GET /currency-rates` | the display-currency selector's state |
| `trustedDevices` | `GET/DELETE /auth/trusted-devices` | `devices[]`, `load()`, `revoke(id)` |

**Pessimistic updates**: every mutation awaits the API response before updating store state.
**Lazy loading**: each store is loaded by the view/component that needs it, in `onMounted`.
Parallel loads within a screen use `Promise.all([store1.load(), store2.load()])`.

### Investments table (`InvestmentsView.vue`)

Uses Buefy `b-table` with `backend-pagination` (Spring `PagedModel`) and `detailed` row
expansion. Tab changes call `holdingsListStore.loadKind(kind, 0)`. Row expansion renders
`HoldingDetailPanel` which lazy-fetches the full holding detail from the individual endpoint.

### Routes and their views (`src/router/index.ts`)

`/overview`, `/wallets`, `/investments` and `/investments/report` (`InvestmentReportView.vue`) are
the main app; `/settings` redirects to `/settings/price-currencies` (`PriceCurrenciesView.vue`) and
covers `/settings/types` and `/settings/users` — the whole `/settings/*` subtree is admin-only.
`/login` and `/pending-approval` are the two public routes.

### App-shell modals (`src/composables/useModals.ts`)

`AddInvestmentModal` and `CreateWalletModal` are rendered once in `App.vue`, outside the
router views, and controlled via `provide`/`inject`. Any view calls `useModals()` to get
`{ openAddInvestment, openCreateWallet }`.

### Static presentation maps (`src/utils/walletTypes.ts`)

`WALLET_TYPES: Record<WalletKind, WalletTypeMeta>` — accent colors, labels, icons per kind.
`badgeColor(ticker, kind)` — consistent hash-derived badge color per ticker.

### Theming

`App.vue` sets `data-theme` (`light`/`dark`), `data-accent` (`blue`/`indigo`/`teal`/`yellow`)
and a fixed `data-density="comfortable"` on `.app-root`. Accent color is the only
user-configurable appearance setting.

### Authentication & Authorization

`router/index.ts`'s `beforeEach` guard drives the whole flow off `auth.session`: no session on a
non-public route → `/login`; session with `status !== 'APPROVED'` → `/pending-approval`; non-admin
on `/settings` → `/overview`. `PUBLIC_ROUTE_NAMES` is the allow-list for routes reachable without
(or despite) an approved session — extend it rather than adding one-off exceptions in the guard
body. `LoginView.vue` is a single 5-step state machine (`credentials`/`register`/`enroll`/`totp`/`link`)
driven by one `step` ref, not five separate views. The Google button starts from the `credentials`
step, but `link` **is** a real step: the server redirects there with `?error=email_in_use&linkToken=…`
when the Google account's email already belongs to a local account, and the step collects that
account's password to attach the two.

**`v-if="auth.isAdmin"` and the router's role/status redirects are presentational only — they are
not the security boundary.** The server enforces every gate independently (`ROLE_ADMIN` on
`/users/**`, `STATUS_APPROVED` everywhere else, re-checked live via `CurrentUserProvider` — see
`server/CLAUDE.md`). Hiding a button or redirecting a route improves the UX for a legitimate user;
it does nothing against a client that calls the API directly. Don't reason about client-side gating
as if it were access control.

`TrustedDevicesModal.vue` (backed by the `trustedDevices` store) lists the devices allowed to skip
TOTP and revokes them. The cookie the server sets is `HttpOnly` and scoped to `/private/v1/auth`, so
the client can never read or clear it directly — revoking is always a server call, never a local
cookie delete.

`PendingApprovalView.vue` only branches its message on whether a session exists at all
(unauthenticated vs. pending) — a `BLOCKED` user never reaches this screen, since `AuthService.login`
rejects the login attempt outright with a generic error before any session is established. Known,
accepted gap for a session that goes stale mid-use: `GET /auth/session` echoes the status cached at
login, so a user blocked mid-session won't see any UI change until their next real API call 403s or
they refresh — the server-side revocation (next action, not next login) is the actual security
guarantee; this view is UX, not enforcement.

`UsersView.vue` (route `/settings/users`, admin-only like the rest of `/settings/*`) is the local-user management screen,
laid out with the same `.wallet-grid`/`.wallet-card` pattern as `WalletsView.vue`. The acting
admin's own row hides role-change/block/delete (`isSelf(user.email)`) but leaves approve and
TOTP-reset visible — those two are safe no-ops on your own account, not lockout risks, so hiding
them would remove a valid self-recovery path for no benefit. `Block` is only offered on
currently-`APPROVED` rows and `Unblock` only on currently-`BLOCKED` rows — blocking is for revoking
existing access, not for handling new signups (those stay on approve/delete).

### Buefy/Bulma gotchas

- **Never write a global `.button { }` rule in `styles.css`.** Bulma 1.x button types
  (`is-success`, `is-danger`, etc.) set `--bulma-button-h/s/l` HSL variables that modifiers like
  `is-outlined` consume to compute colors. A global override with static values fights this
  cascade and breaks every typed/modifier button in the app. Scope button styling tightly instead
  (`.navbar .button`, `.modal-card-head .button`, `.button.is-static`).
- **Use `type="is-ghost"`, not `is-text"`,** for transparent/icon-only buttons (subtle inline
  triggers, link-like actions). For per-row destructive icon actions (e.g. delete in a table row),
  use `type="is-danger" outlined`.

### Composables

- `useFormat` — pure pt-BR formatting helpers (money, signed money, percent, quantity, date).
- `useAddInvestmentForm` — reactive form state/validation/submit for the add-investment modal;
  uses `walletsStore` + `typesListStore`; submits directly to the API.
- `useChartTheme` — chart colors/options derived from the active theme and accent.
- `useModals` — app-shell modal injection.
