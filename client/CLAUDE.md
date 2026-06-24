# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
npm install
npm run dev        # dev server at http://localhost:5173
npm run build      # vue-tsc --noEmit -p tsconfig.app.json, then vite build (fails on type errors)
npm run preview    # preview the production build
npm run type-check # vue-tsc --noEmit -p tsconfig.app.json only
npm run test       # vitest (unit tests)
```

`tsconfig.app.json` has `strict`, `noUnusedLocals` and `noUnusedParameters` enabled,
so unused imports/variables fail `npm run build` / `npm run type-check`.

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

InvestLog is a manual (PT-BR) investment logbook: stocks, crypto and funds, entered by hand —
there is no live market feed. Stack: Vue 3 `<script setup>` + TypeScript, Pinia, Vue Router 4,
Vite 6. Path alias `@` → `src/`. Backend: Spring Boot 4 / Kotlin at `http://localhost:8080`,
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

### Domain types (`src/types.ts`)

- `WalletKind` = `'stocks' | 'crypto' | 'funds'`.
- `WalletResponse` — wallet from API with `holdingCount` and `totalInvested`.
- `HoldingRow` — paginated row from `GET /holdings` (summary only, no lots).
- `StockHoldingDetail` / `CryptoHoldingDetail` / `FundHoldingDetail` — full holding fetched
  lazily when a row is expanded in the investments table.
- `PortfolioSummary`, `KindSummary`, `SeriesPoint` — overview endpoint shapes.
- `AssetType`, `CurrencyRate` — settings endpoint shapes.
- `PagedResponse<T>` — Spring `PagedModel` envelope: `{ content, page: { size, number, totalElements, totalPages } }`.

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

**Pessimistic updates**: every mutation awaits the API response before updating store state.
**Lazy loading**: each store is loaded by the view/component that needs it, in `onMounted`.
Parallel loads within a screen use `Promise.all([store1.load(), store2.load()])`.

### Investments table (`InvestmentsView.vue`)

Uses Buefy `b-table` with `backend-pagination` (Spring `PagedModel`) and `detailed` row
expansion. Tab changes call `holdingsListStore.loadKind(kind, 0)`. Row expansion renders
`HoldingDetailPanel` which lazy-fetches the full holding detail from the individual endpoint.

### App-shell modals (`src/composables/useModals.ts`)

`AddInvestmentModal` and `CreateWalletModal` are rendered once in `App.vue`, outside the
router views, and controlled via `provide`/`inject`. Any view calls `useModals()` to get
`{ openAddInvestment, openCreateWallet }`.

### Static presentation maps (`src/utils/walletTypes.ts`)

`WALLET_TYPES: Record<WalletKind, WalletTypeMeta>` — accent colors, labels, icons per kind.
`badgeColor(ticker, kind)` — consistent hash-derived badge color per ticker.

### Theming

`App.vue` sets `data-theme` (`light`/`dark`), `data-accent` (`blue`/`indigo`/`teal`/`green`)
and a fixed `data-density="comfortable"` on `.app-root`. Accent color is the only
user-configurable appearance setting.

### Composables

- `useFormat` — pure pt-BR formatting helpers (money, signed money, percent, quantity, date).
- `useAddInvestmentForm` — reactive form state/validation/submit for the add-investment modal;
  uses `walletsStore` + `typesListStore`; submits directly to the API.
- `useModals` — app-shell modal injection.
