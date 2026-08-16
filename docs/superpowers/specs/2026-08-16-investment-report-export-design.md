# InvestLog — Investment Report Export Design

**Date:** 2026-08-16
**Status:** Draft — pending review

## Goal

Add a printable investment report: a new "Exportar relatório" button on the Investments
screen opens a dedicated report page in a new browser tab, which the user saves as a PDF via
the browser's native print dialog (Ctrl+P). The report groups every investment by **Kind →
Type/Ticker → Wallet**, with a subtotal at every level and a grand total, and shows the same
current-state figures as the on-screen table — explicitly excluding buy/contribution history.

## Context (as-built)

- **No PDF/print/export functionality exists anywhere in the repo today** — confirmed by
  grepping both `client/` and `server/` for `pdf|print|export|jsPDF|csv|window.print`. This is
  a greenfield feature: no PDF library, no print stylesheet, no `/export` route or endpoint.
- **Holding data model**: there is no single "Investment" entity. Three parallel tables
  (`finances.stock_holdings`+`stock_lots`, `finances.crypto_holdings`+`crypto_lots`,
  `finances.fund_holdings`+`fund_contributions`) are unified only through a read-only Postgres
  `VIEW`, `finances.holdings_overview`
  (`server/src/main/resources/db/changelog/changes/2026/06/12-1050-create-holdings-overview-view.xml`),
  a `UNION ALL` of the three tables aggregating lots/contributions per holding into
  `quantity`/`cost_basis`/`current_value`/`current_price`. `type_label` is the stock/fund type
  name (`NULL` for crypto, which has no categorization at all).
- **No uniqueness constraint** on `(wallet_id, ticker)` for `crypto_holdings` (and, by the same
  schema pattern, `stock_holdings`) — the same ticker can appear as multiple separate holding
  rows within one wallet, let alone across wallets. Nothing in the codebase currently merges
  same-ticker holdings; each is a distinct row all the way through `holdings_overview`.
- **Existing read endpoint**: `GET /private/v1/holdings`
  (`server/src/main/kotlin/br/com/investlog/server/holdingsoverview/rest/HoldingsOverviewController.kt`)
  — `@RequestParam kind: WalletKind?`, `typeLabel: String?`, `walletId: UUID?`, `search: String?`,
  plus Spring `Pageable`. `HoldingsOverviewRepository.findAll` (same package,
  `repositories/HoldingsOverviewRepository.kt`) joins `HOLDINGS_OVERVIEW` with `WALLETS`,
  applies the four optional filter conditions, and returns a paginated
  `HoldingRowResponse` (`id, kind, name, ticker, typeLabel, walletId, walletName,
  walletCurrency, quantity, costBasis, currentPrice, currentValue, gain, gainPct`) — `gain`/
  `gainPct` computed in Kotlin from `currentValue - costBasis`, not in SQL. Client mirror:
  `HoldingRow` (`client/src/types.ts:30`), fetched via `holdingsApi.findAll(...)`
  (`client/src/api/holdings.ts`).
- **`InvestmentsView.vue`** (`client/src/views/InvestmentsView.vue`) holds the current filter
  state as plain refs: `activeFilter` (`'all' | WalletKind`), `typeLabelFilter`,
  `walletIdFilter`, `searchQuery` (lines 47–53). The toolbar (`.inv-toolbar`, lines 187–223)
  has wallet/type-label selects, a search input, and the primary "Adicionar investimento"
  `b-button` — the report button's insertion point, right next to it.
- **`finances.wallets`** carries one `currency` per wallet; wallets can differ in currency.
  `useCurrencyStore` (`client/src/stores/currency.ts`) holds `displayCurrency` (the user's
  preferred currency, from their profile) and `convert(amount, fromCurrency): number`, using
  `useRatesStore` for exchange rates — this is the same conversion already used by the Overview
  screen and is reused here so every value in the report is comparable/summable.
- **Buy/contribution history** already has its own UI (`HoldingDetailPanel.vue`, lazy-fetched
  per-holding detail) and is explicitly excluded from this report per the request — the report
  only needs current-state rows, never `lots`/`contributions`.
- **App shell**: `App.vue` always wraps every authenticated route in `TheNavbar` (`.navbar`) +
  `TheTopNav` (`.topnav`) — there is no per-route layout switch besides the unapproved-session
  case. The report page reuses the same route-based structure and hides the chrome via
  `@media print` rather than introducing a new layout.
- **Existing branding**: `LogoMark.vue` (`client/src/components/icons/LogoMark.vue`) + the
  "Invest**Log**" wordmark, already used in `TheNavbar.vue` — reused for the report header.
- **Migration convention** (`server/CLAUDE.md`): new changelog file only, under
  `db/changelog/changes/<year>/<month>/<DD-HHMM>-<description>.xml`, `<include>`d from
  `db.changelog-master.xml`. Never edit an existing file.
- **Service-layer convention** (`server/CLAUDE.md`): skip `@Service` when a controller method
  would do nothing but call a repository and return its result unchanged — `holdingsoverview`
  already follows this (no service class). The new report endpoint follows the same pattern.

## Decision

| Decision | Choice |
| --- | --- |
| Export mechanism | New route in a new browser tab, print-styled (`@media print`), saved to PDF via the browser's native print dialog — no PDF library, no server-side generation |
| Report scope | Respects whatever filters (kind/wallet/type/search) are active on the Investments screen at the moment "Exportar relatório" is clicked |
| Grouping hierarchy | **Kind → Type-or-Ticker → Wallet → holding rows**, uniform across Stocks, Funds, and Crypto: Stocks/Funds sub-group by `typeLabel` ("Outros" if null), Crypto sub-groups by `ticker` instead |
| Crypto ticker merge | The same ticker in the same wallet is merged into one row (summed quantity/cost/current value); the same ticker across different wallets is **not** merged into one number — each wallet gets its own row nested under that ticker, per the drawing |
| Row columns | Ticker/name (row label) — quantity (when applicable), current price, invested value, current value, result (gain + gain %) |
| Buy/contribution history | Excluded entirely — current-state rows only |
| Totals | Subtotal at every grouping level (wallet-within-type/ticker, type/ticker, kind) plus one grand total, shown in the report header per the drawing |
| Currency | Every monetary value (price, invested, current, gain) converted to the user's display currency — no mixed-currency rows or totals |
| Backend aggregation | New Postgres **view** (`finances.holdings_report_rows`) on top of `holdings_overview`, doing the same-wallet-same-ticker merge in SQL — keeps the report repository query short instead of embedding a long aggregation query inline |
| Backend endpoint | New unpaginated `GET /private/v1/holdings/report`, same filter params as `GET /holdings`, added to the existing `HoldingsOverviewController`/`HoldingsOverviewRepository` (no new service, per convention) |
| Client grouping | Purely presentational — the client only nests the already-merged flat rows into sections and sums subtotals; no dedup logic in JS |

## Architecture

### Database

New view, built on `finances.holdings_overview`, merging rows that share `(wallet_id, kind,
ticker, type_label, name)` — this collapses duplicate same-wallet/same-ticker crypto (or stock)
holdings into one row; wallets/kinds/types/tickers that already have exactly one row per group
(the common case) pass through unchanged:

```sql
CREATE VIEW finances.holdings_report_rows AS
SELECT
    MIN(external_id) AS external_id,
    wallet_id,
    kind,
    name,
    ticker,
    type_label,
    CASE
        WHEN SUM(quantity) IS NOT NULL AND SUM(quantity) <> 0
            THEN SUM(current_value) / SUM(quantity)
        ELSE MAX(current_price)
    END AS current_price,
    SUM(quantity) AS quantity,
    SUM(cost_basis) AS cost_basis,
    SUM(current_value) AS current_value
FROM finances.holdings_overview
GROUP BY wallet_id, kind, name, ticker, type_label;
```

New changelog file under `server/src/main/resources/db/changelog/changes/2026/08/`, included
from `db.changelog-master.xml`. Symmetric rollback (`DROP VIEW finances.holdings_report_rows`).
`jooqCodegen` picks up the new view automatically on the next build.

### Server

```
holdingsoverview/
  rest/
    HoldingsOverviewController.kt        # + GET /holdings/report (no Pageable)
  repositories/
    HoldingsOverviewRepository.kt        # + findAllForReport(...): List<HoldingRowResponse>
```

`HoldingsOverviewRepository.findAllForReport` mirrors the existing `findAll` — same four
optional filter conditions (`kind`, `typeLabel`, `walletId`, `search`), same join against
`WALLETS`, same `HoldingRowResponse` mapping (`gain`/`gainPct` computed the same way) — but
selects from `HOLDINGS_REPORT_ROWS` instead of `HOLDINGS_OVERVIEW`, and drops `limit`/`offset`/
count query entirely, returning a plain `List<HoldingRowResponse>`:

```kotlin
fun findAllForReport(
    userId: Long,
    kind: JooqWalletKind?,
    typeLabel: String?,
    walletId: UUID?,
    search: String?,
): List<HoldingRowResponse>
```

`HoldingsOverviewController` gains one endpoint, no new service (same
call-repository-return-unchanged pattern already used by `findAll`):

```kotlin
@GetMapping("/report")
fun findAllForReport(
    @RequestParam(required = false) kind: WalletKind?,
    @RequestParam(required = false) typeLabel: String?,
    @RequestParam(required = false) walletId: UUID?,
    @RequestParam(required = false) search: String?,
): ResponseEntity<List<HoldingRowResponse>> {
    val userId = currentUserProvider.getCurrentUser().id
    val holdings = holdingsOverviewRepository.findAllForReport(
        userId, kind?.jooqWalletKind, typeLabel, walletId, search
    )
    return ResponseEntity.ok(holdings)
}
```

No `SecurityConfig` change — this falls under the same `STATUS_APPROVED` catch-all as the
existing `GET /holdings`, scoped to the caller's own data via `userId` exactly like `findAll`.
No new response type — `HoldingRowResponse` is reused as-is.

### Client

```
src/api/holdings.ts              # + findAllForReport(filters): Promise<HoldingRow[]>
src/utils/reportGrouping.ts      # new — pure grouping/subtotal function, no API calls
src/views/InvestmentReportView.vue   # new — the printable report page
src/views/InvestmentsView.vue    # + "Exportar relatório" button in .inv-toolbar
src/router/index.ts              # + /investments/report route
src/assets/styles.css            # + @media print rules
```

`reportGrouping.ts` exports a pure function taking the flat `HoldingRow[]` (already merged by
the backend view) plus a `convert(amount, fromCurrency) => number` callback, and returns a
nested structure: one entry per `WalletKind` present in the data → sub-grouped by `typeLabel`
(stocks/funds) or `ticker` (crypto) → sub-grouped by `walletId` → the (currency-converted) rows
in that wallet, with a subtotal computed at the wallet, type/ticker, and kind level, plus one
grand total across everything. Empty groups are simply never created (no filtering step needed
downstream). No dedup/merge logic here — that already happened in the SQL view.

`InvestmentReportView.vue` (new route `/investments/report`, name `investments-report`, added
next to the existing routes in `router/index.ts`, no special guard beyond the existing
authenticated-route check):
- On mount, reads `kind`/`typeLabel`/`walletId`/`search` from the route query, loads
  `currencyStore` + `ratesStore`, calls the new `holdingsApi.findAllForReport(...)`, and runs
  the result through `reportGrouping.ts`.
- Renders the header (logo + "InvestLog" wordmark, report title, generation date, active
  filters if any, the grand total — matching the drawing's layout), then one block per kind
  section, each rendering its type/ticker subsections and wallet groups with their subtotals.
- A small non-printing toolbar (an "Imprimir" button calling `window.print()`, and a "Voltar"
  link back to `/investments`) sits above the report content.
- Empty result set (no holdings match the filters) renders a plain message instead of an empty
  report shell.

`InvestmentsView.vue` gains an "Exportar relatório" `b-button` next to "Adicionar investimento"
in `.inv-toolbar`. On click, it opens a new tab at `/investments/report` carrying the view's
current `activeFilter`/`typeLabelFilter`/`walletIdFilter`/`searchQuery` as query params (via
`router.resolve(...).href` + `window.open(..., '_blank')`), so the report reflects exactly what
was on screen.

`styles.css` gains `@media print` rules hiding `.navbar`, `.topnav`, and the report's own
non-printing toolbar, plus `break-inside: avoid` on section/wallet blocks so a wallet's holdings
don't split awkwardly across a page boundary.

## Files to create / change

| Path | Action |
| --- | --- |
| `server/src/main/resources/db/changelog/changes/2026/08/<DD-HHMM>-create-holdings-report-rows-view.xml` | **create** |
| `server/src/main/resources/db/changelog/db.changelog-master.xml` | **update** — `<include>` the new file |
| `server/src/main/kotlin/br/com/investlog/server/holdingsoverview/repositories/HoldingsOverviewRepository.kt` | **update** — add `findAllForReport(...)` |
| `server/src/main/kotlin/br/com/investlog/server/holdingsoverview/rest/HoldingsOverviewController.kt` | **update** — add `GET /holdings/report` |
| `client/src/api/holdings.ts` | **update** — add `findAllForReport(filters)` |
| `client/src/utils/reportGrouping.ts` | **create** |
| `client/src/views/InvestmentReportView.vue` | **create** |
| `client/src/views/InvestmentsView.vue` | **update** — add the export button |
| `client/src/router/index.ts` | **update** — add the `/investments/report` route |
| `client/src/assets/styles.css` | **update** — add `@media print` rules |

Per root `CLAUDE.md`, this splits into a **server PR** (migration + repository/controller
changes) and a **client PR** (api/util/view/route/styles), each referencing the same tracking
issue for this feature.

## Verification

- **Server integration test**: `HoldingsReportEndpointTest` (or extended
  `HoldingsOverviewControllerTest`) — seed two crypto holdings for the same wallet+ticker with
  different lots, call `GET /holdings/report`, assert exactly one merged row with summed
  quantity/cost/current value; seed holdings across two wallets with the same ticker and assert
  two separate rows (no cross-wallet merge); assert stock/fund rows pass through unaffected;
  assert the endpoint respects `kind`/`typeLabel`/`walletId`/`search` filters identically to
  `GET /holdings`; assert no pagination envelope (plain array response).
- **Server unit test** (if `findAllForReport` warrants its own): verify the SQL view merge
  behavior directly against a seeded database (duplicate wallet+ticker rows collapse; distinct
  wallets don't).
- **Manual UI check**: from Investments, apply a filter (e.g. one wallet, or the "Cripto" tab),
  click "Exportar relatório", confirm the new tab's content matches the filter; confirm the
  Kind → Type/Ticker → Wallet nesting and subtotals/grand total are correct against the
  on-screen figures; confirm a ticker held in two wallets shows one row per wallet under that
  ticker; confirm every value is in the display currency; open the browser's print preview and
  confirm the navbar/topnav/toolbar are hidden and sections don't split awkwardly across pages.
- **Manual UI check**: with an empty filter result (e.g. a wallet with no crypto), confirm the
  report shows the "no holdings" message instead of an empty shell.
