# REST Integration Design

**Date:** 2026-06-17  
**Scope:** Wire the Vue frontend to the Spring Boot REST API; move all business logic/calculations to the backend; remove frontend seed data (convert to SQL).

---

## Decisions

| Topic | Decision |
|---|---|
| HTTP client | axios |
| Update strategy | Pessimistic — await API response before updating state |
| Loading strategy | Lazy per screen/tab; parallel within a screen |
| Store structure | Split domain stores (Approach B) |
| Seed data | Remove from frontend; convert to `server/src/main/resources/sample-data.sql` |
| Holdings table | Buefy DataTable with backend pagination + Spring `PagedModel` |
| Business logic | All calculations (cost basis, gain, summaries, series) live in the backend |

### Coding convention
Never abbreviate variable names. Use `wallet` not `w`, `holding` not `h`, `quantity` not `qty`, `contribution` not `contribution`, etc. Both `client/CLAUDE.md` and `server/CLAUDE.md` must document this. Fix any abbreviated names encountered while working.

---

## Backend Changes

### 1. Convert `holdings_overview` from materialized to regular view

New Liquibase changeset: drop the materialized view (and its indexes), recreate as a regular `VIEW`. A regular view always reflects current data — no refresh calls needed after writes.

The view already computes `cost_basis`, `quantity`, `current_value`, and `type_label` via UNION of stock/crypto/fund holdings joined to their lots/contributions. This computation stays. The view retains the same column set; the wallet join is done at query time in the repository.

### 2. New module: `holdingsoverview`

**`GET /private/v1/holdings`**  
Unified paginated list across all holding types, backed by `holdings_overview JOIN wallets`.

Query params: `kind` (optional — `stocks` | `crypto` | `funds`), standard Spring `Pageable` (`page`, `size`, `sort`).

Response: `PagedModel<HoldingRowResponse>`

```
HoldingRowResponse:
  id: UUID
  kind: String                  -- "stocks" | "crypto" | "funds"
  name: String
  ticker: String?               -- null for funds
  typeLabel: String?            -- null for crypto
  walletId: UUID
  walletName: String
  walletCurrency: String
  quantity: BigDecimal?         -- null for funds
  costBasis: BigDecimal         -- in walletCurrency
  currentValue: BigDecimal?     -- null if not set
  gain: BigDecimal?             -- currentValue - costBasis; null if currentValue null
  gainPct: BigDecimal?          -- gain / costBasis * 100; null if gain null
```

Module layout follows existing pattern: `rest/{controllers,payloads}` + `domain/{services,repositories}`.

### 3. New module: `overview`

**`GET /private/v1/overview`** → `PortfolioSummaryResponse`

```
PortfolioSummaryResponse:
  grandInvested: BigDecimal          -- sum of all costBasis converted to base currency
  currentValue: BigDecimal?          -- sum of currentValue (base) for holdings that have it; null if none
  gain: BigDecimal?                  -- currentValue - investedWithValue; null if no current values
  gainPct: BigDecimal?               -- gain / investedWithValue * 100
  holdingsWithValue: Int             -- count of holdings where currentValue is not null
  totalHoldings: Int
  byKind: List<KindSummaryResponse>

KindSummaryResponse:
  kind: String
  invested: BigDecimal               -- in base currency
  wallets: Int
  holdings: Int
```

Currency conversion: `cost_basis * currency_rates.rate` where `currency_rates.currency_code = wallets.currency AND currency_rates.user_id = :userId`. The base currency row (`is_base = true`) has `rate = 1` by convention.

**`GET /private/v1/overview/series`** → `SeriesResponse`

```
SeriesResponse:
  labels: List<String>    -- ["jan/25", "fev/25", ...]
  data: List<BigDecimal>  -- cumulative invested in base currency per month
```

SQL: UNION of `stock_lots`, `crypto_lots`, `fund_contributions` each joined to their holding → wallet → currency_rate, grouped by `date_trunc('month', event_date)`. Cumulative sum computed in the service layer (Kotlin). Always returns ≥ 2 points (prepends zero-entry if only one month exists).

---

## Frontend Changes

### 4. Vite dev proxy

`vite.config.ts`: proxy `/private` → `http://localhost:8080`. No CORS config needed in development.

### 5. API layer (`src/api/`)

```
src/api/
  client.ts           -- axios instance: baseURL /private/v1, JSON headers,
                         response error interceptor → Buefy toast (pt-BR message)
  wallets.ts          -- findAll(), create(), update(), remove()
  holdings.ts         -- findAll(kind?, page, size) → PagedResult<HoldingRow>
  holdingDetails.ts   -- per-type CRUD + lots/contributions
  types.ts            -- stockTypes and fundTypes: findAll(), create(), remove()
  rates.ts            -- findAll(), upsert(code, rate, isBase)
  overview.ts         -- getSummary(), getSeries()
```

All functions typed end-to-end. `PagedResult<T>` is a local TypeScript interface matching Spring's `PagedModel` shape (`content[]`, `page.totalElements`, `page.size`, `page.number`).

### 6. Split stores (`src/stores/`)

`portfolio.ts` removed. `appearance.ts` untouched.

| Store | File | Loads when | Key state |
|---|---|---|---|
| Wallets | `wallets.ts` | WalletsView / OverviewView mount | `wallets: Wallet[]`, `loaded: boolean` |
| Holdings list | `holdingsList.ts` | InvestmentsView tab activated | `rows: HoldingRow[]`, `page`, `total`, `perPage`, `kind`, `loading` |
| Overview | `overview.ts` | OverviewView mount | `summary: PortfolioSummaryResponse`, `series: SeriesResponse` |
| Types list | `typesList.ts` | SettingsView mount or AddInvestment modal open | `stockTypes: StockType[]`, `fundTypes: FundType[]` |
| Rates | `rates.ts` | SettingsView mount | `rates: CurrencyRate[]`, `baseCurrency: string` |

Each store has a single async `load()` action (idempotent — skips if already loaded) and mutation actions that call the API, then update local state from the response.

### 7. `src/types.ts` updates

- `HoldingRow` — mirrors `HoldingRowResponse` for the investments table
- `StockType` / `FundType` — `{ id: string; name: string }` (replaces plain `string[]`)
- Detail holding types (`StockHolding`, `CryptoHolding`, `FundHolding`) updated: `stockType`/`fundType` becomes `{ id: string; name: string }` to carry both UUID (for API calls) and display name
- `CurrencyRate` — mirrors backend `CurrencyRateResponse`
- `PortfolioSummary`, `KindSummary`, `Series` — new types for the overview module

### 8. Views

**OverviewView**: on mount → parallel `overviewStore.load()` + `walletsStore.load()`.

**WalletsView**: on mount → `walletsStore.load()`. Wallet cards show a `holdingCount` field added to `WalletResponse` (a sub-query count from `holdings_overview`). The per-wallet holdings preview list (name + cost for up to 4 holdings) is removed in the new design — it required the global store's full holdings list and is too expensive to reconstruct with split stores.

**InvestmentsView**: tabs drive `holdingsListStore`. On tab change → `holdingsListStore.loadKind(kind)` (resets to page 1). Uses Buefy `<b-table>` with `backend-pagination`:

```html
<b-table
  :data="holdingsListStore.rows"
  :loading="holdingsListStore.loading"
  backend-pagination
  :total="holdingsListStore.total"
  :per-page="holdingsListStore.perPage"
  :current-page="holdingsListStore.page"
  @page-change="holdingsListStore.setPage"
/>
```

**SettingsView**: on mount → parallel `typesListStore.load()` + `ratesStore.load()`.

**AddInvestmentModal**: ensures `typesListStore.load()` before opening (needed for type dropdowns). On submit → calls correct `holdingDetails` API, then triggers `holdingsListStore.invalidate()` so next tab visit re-fetches.

**CreateWalletModal**: calls `walletsStore.create()`.

### 9. Seed data → SQL

Remove `src/data/seed.ts` and `src/data/` directory.

Create `server/src/main/resources/sample-data.sql`: a self-contained script that inserts the current seed data (4 wallets, 11 holdings with all lots/contributions, 4 stock types, 4 fund types, 3 currency rates) scoped to the dev user. Uses a `DO $$ BEGIN … END $$` guard to skip if data already exists for that user. No Liquibase dependency — intended for manual execution in any DB client.

### 10. CLAUDE.md updates (both client and server)

Add to both:
- No-abbreviation variable naming convention
- Client: document the `src/api/` layer, split store structure, lazy loading pattern
- Server: document the new `holdingsoverview` and `overview` modules

---

## File Inventory (new / modified)

### Server (new)
- `db/changelog/changes/2026/06/17-1000-convert-holdings-overview-to-view.xml`
- `holdingsoverview/rest/controllers/HoldingsOverviewController.kt`
- `holdingsoverview/rest/payloads/HoldingRowResponse.kt`
- `holdingsoverview/domain/services/HoldingsOverviewService.kt`
- `holdingsoverview/domain/repositories/HoldingsOverviewRepository.kt`
- `overview/rest/controllers/OverviewController.kt`
- `overview/rest/payloads/PortfolioSummaryResponse.kt`
- `overview/rest/payloads/KindSummaryResponse.kt`
- `overview/rest/payloads/SeriesResponse.kt`
- `overview/domain/services/OverviewService.kt`
- `overview/domain/repositories/OverviewRepository.kt`
- `src/main/resources/sample-data.sql`

### Server (modified)
- `wallets/rest/payloads/WalletResponse.kt` — add `holdingCount: Int`
- `wallets/domain/services/WalletService.kt` — include holding count in response
- `server/CLAUDE.md`

### Client (new)
- `src/api/client.ts`
- `src/api/wallets.ts`
- `src/api/holdings.ts`
- `src/api/holdingDetails.ts`
- `src/api/types.ts`
- `src/api/rates.ts`
- `src/api/overview.ts`
- `src/stores/wallets.ts`
- `src/stores/holdingsList.ts`
- `src/stores/overview.ts`
- `src/stores/typesList.ts`
- `src/stores/rates.ts`

### Client (modified)
- `vite.config.ts` — proxy
- `src/types.ts` — new/updated types
- `src/stores/portfolio.ts` — removed
- `src/data/seed.ts` — removed
- `src/views/OverviewView.vue`
- `src/views/WalletsView.vue`
- `src/views/InvestmentsView.vue`
- `src/views/SettingsView.vue`
- `src/composables/useAddInvestmentForm.ts`
- `src/components/forms/AddInvestmentForm.vue`
- `src/components/forms/AddInvestmentModal.vue`
- `src/components/forms/CreateWalletModal.vue`
- `client/CLAUDE.md`
