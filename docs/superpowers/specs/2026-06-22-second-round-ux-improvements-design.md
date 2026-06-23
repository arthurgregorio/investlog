# Second-Round UX Improvements — Post-Usage Feedback

**Date:** 2026-06-22
**Status:** Visual mockup approved (idea-validation level; minor spacing/alignment left as-is)

## Scope

Thirteen targeted improvements identified after further real usage of InvestLog. Numbering
preserves the original request list; item 7 (auto-opening a newly added holding inside a
paginated/sorted list) was dropped as infeasible and is not covered here. Item 12 (search) turned
out to be the same search box as item 6, so the two are specified together as one section.

A visual mockup for the items with appearance impact (6, 8, 9, 10, 11, 12, 13, 14) was built and
pushed to the project's Claude Design prototype
(`https://claude.ai/design/p/1cc15382-85e5-451a-8d11-029bb040c362?file=InvestLog+Dashboard.html`)
and approved by the user. Items 1–5 are pure-behavior changes with no visual impact and were not
mocked up.

1. Ticker uppercase while typing
2. Toast feedback in Settings
3. Editable date on existing lots/contributions
4. URL ↔ tab-filter two-way sync on the investments table
5. "Add investment" modal defaults to the active tab's kind
6. Investments table: sub-type filter + search box
8. Investments table: sortable columns
9. Wallet cards: show current value and gain
10. Compact money formatting actually abbreviates (`239k`, `1,2M`)
11. Navbar logo returns to the overview
12. *(merged into 6 — same search box)*
13. Remove the "Logbook" eyebrow label
14. Overview type-distribution cards: show current value and gain instead of a progress bar

---

## 1 — Ticker uppercase while typing

**Problem:** `AddInvestmentForm.vue` binds the ticker field with a plain `v-model="form.ticker"`
(`client/src/components/forms/AddInvestmentForm.vue:80`), so the user sees whatever case they
type. Uppercasing only happens at submit time, in `useAddInvestmentForm.ts:88,95`
(`form.ticker.trim().toUpperCase()`).

**Fix:** Replace the `v-model` with a computed `get`/`set` (or an `@input` handler) on the ticker
field that uppercases on every keystroke, so what's displayed always matches what will be stored.
Keep the existing trim/uppercase in `submit()` as a safety net for any path that bypasses the
input (e.g. paste events firing before Vue re-renders).

**Files touched:** `client/src/components/forms/AddInvestmentForm.vue`

---

## 2 — Toast feedback in Settings

**Problem:** `SettingsView.vue` has no `useToast()` calls at all — adding/removing stock types,
adding/removing fund types, and updating a currency rate all complete silently. Every other
mutating action in the app already confirms via a Buefy toast (investments, wallet
create/rename/delete, lot/contribution add/delete, price updates), so Settings is the one
remaining silent spot.

**Fix:** Add `const toast = useToast()` to `SettingsView.vue` and fire a success toast after each
of `addStockType`, `addFundType`, `removeStockType` (call), `removeFundType` (call), and
`setRate`, following the existing message style (`{ message: '...', type: 'is-success' }`), e.g.
`"Tipo de ação adicionado."`, `"Tipo de fundo removido."`, `"Taxa atualizada."`.

**Files touched:** `client/src/views/SettingsView.vue`

---

## 3 — Editable date on existing lots/contributions

**Problem:** The date *is* editable when first registering a buy/contribution (`DateInput` in
`AddInvestmentForm.vue:85-87,110-111`). But once a lot or contribution exists, there is no way to
fix its date — `StockHoldingController.kt`, `CryptoHoldingController.kt`, and
`FundHoldingController.kt` only expose `POST` (create) and `DELETE /{id}` on the
lots/contributions sub-resource; there is no `PATCH`. The only current workaround is
delete-and-recreate, which loses the original entry's identity and is easy to get wrong (e.g. for
a fund contribution that's also a cash-flow record).

**Backend:** Add `PATCH /wallets/{walletId}/stock-holdings/{holdingId}/lots/{lotId}`,
the crypto equivalent, and `PATCH /wallets/{walletId}/fund-holdings/{holdingId}/contributions/{contributionId}`,
each accepting `{ date: LocalDate }` (`lotDate` / `contributionDate`) and updating just that
column. Mirrors the existing parent-holding `PATCH` pattern already present in all three
controllers.

**Frontend:** In `HoldingDetailPanel.vue`'s sub-table (`client/src/components/investments/HoldingDetailPanel.vue:143-187`),
make the date cell (`:156`, `:171`) clickable — clicking reveals an inline `DateInput`
replacing the text, with the row's existing delete button staying put. On change, call the new
PATCH endpoint (new `holdingsApi` methods: `updateStockLotDate`, `updateCryptoLotDate`,
`updateFundContributionDate`), then `reloadDetail()` and emit `positionAdded` (same pattern as
`confirmDeleteLot`/`confirmDeleteContribution`), with a success toast.

**Files touched:**
- `server/src/main/kotlin/br/com/investlog/server/stockholdings/rest/controllers/StockHoldingController.kt`
- `server/src/main/kotlin/br/com/investlog/server/cryptoholdings/rest/controllers/CryptoHoldingController.kt`
- `server/src/main/kotlin/br/com/investlog/server/fundholdings/rest/controllers/FundHoldingController.kt`
- (each controller's matching `domain/services` + `domain/repositories` update method)
- `client/src/api/holdings.ts`
- `client/src/components/investments/HoldingDetailPanel.vue`

---

## 4 — URL ↔ tab-filter two-way sync

**Problem:** `InvestmentsView.vue` reads the tab filter from the URL on load and reacts to
external navigation (`filterFromRoute()` at `:30-35`, the `route.query.filter` watcher at
`:40-44`) — that direction already works, and `WalletsView.vue`/`OverviewView.vue` rely on it via
`router.push({ name: 'investments', query: { filter: kind } })`. But `selectTab()` (`:48-53`),
which runs when the user clicks a tab directly inside `InvestmentsView`, only updates the local
`activeFilter` ref and calls `holdingsListStore.loadKind` — it never pushes the new filter into
`route.query`. So the address bar and browser back/forward don't reflect tab clicks made from
inside the page itself.

**Fix:** In `selectTab()`, also call `router.replace({ query: { ...route.query, filter: kind } })`
(use `replace`, not `push`, so clicking through tabs doesn't spam browser history) before/alongside
the existing `loadKind` call.

**Files touched:** `client/src/views/InvestmentsView.vue`

---

## 5 — "Add investment" modal defaults to the active tab's kind

**Problem:** `openAddInvestment(kind: WalletKind = 'STOCKS')` (`client/src/App.vue:25-27`) always
defaults to `STOCKS` unless a caller passes a kind explicitly. The only call sites,
`InvestmentsView.vue:93,117`, call it with no argument — so opening the modal while on the
Crypto or Funds tab still defaults the form to Stocks.

**Fix:** Both call sites in `InvestmentsView.vue` should pass the view's current
`activeFilter` (when it's not `"all"`) as the kind: `modals.openAddInvestment(activeFilter.value !== 'all' ? activeFilter.value : undefined)`.

**Files touched:** `client/src/views/InvestmentsView.vue`

---

## 6 / 12 — Investments table: sub-type filter + search box

**Problem:** `InvestmentsView.vue`'s tabs are only `all/STOCKS/CRYPTO/FUNDS` — there's no way to
narrow by asset sub-type (the stock-types/fund-types managed in Settings) or to search by
ticker/name. With enough holdings this makes finding one investment slow.

**Backend:** `GET /private/v1/holdings` (`HoldingsOverviewController.kt`) gains two optional
query params: `typeLabel: String?` (exact match against `overview.TYPE_LABEL`, since the view has
no `type_id` to join against — labels are unique per kind already) and `search: String?` (case-
insensitive `ILIKE` against `overview.NAME` OR `overview.TICKER` in `HoldingsOverviewRepository.kt`).
Both are added as extra jOOQ conditions ANDed alongside the existing `kindCondition`.

**Frontend:** Replace the bare `.seg-tabs` row in `InvestmentsView.vue` with a new toolbar:
the existing tab buttons, plus (right-aligned) a `SelectInput` for sub-type — populated from
`typesListStore.stockTypes`/`fundTypes` depending on the active tab, hidden when the tab is
`"all"` or has no types — and a search input (icon + text field, debounced ~250ms). Both feed
into `holdingsListStore.loadKind(kind, page, { typeLabel, search })`, which forwards them as query
params. Switching tabs resets the sub-type selection (mirrors the mockup's `selectFilter`
behavior).

**Files touched:**
- `server/src/main/kotlin/br/com/investlog/server/holdingsoverview/rest/controllers/HoldingsOverviewController.kt`
- `server/src/main/kotlin/br/com/investlog/server/holdingsoverview/domain/repositories/HoldingsOverviewRepository.kt`
- `client/src/api/holdings.ts`
- `client/src/stores/holdingsList.ts`
- `client/src/views/InvestmentsView.vue`

---

## 8 — Investments table: sortable columns

**Problem:** The investments table is a plain `<table>` (`InvestmentsView.vue:127-138`) with
static `<th>` headers — nothing is clickable. `HoldingsOverviewRepository.kt:43` hardcodes
`.orderBy(overview.COST_BASIS.desc())` and ignores `pageable.sort` entirely, even though
`HoldingsOverviewController.kt`'s `Pageable` parameter would auto-bind a `?sort=` query param if
the repository read it.

**Backend:** Read `pageable.sort` in `HoldingsOverviewRepository.kt` and map a whitelisted set of
sort keys to jOOQ fields/expressions:
- `wallet` → `wallets.NAME`
- `price` → `overview.CURRENT_PRICE`
- `invested` → `overview.COST_BASIS`
- `current` → `overview.CURRENT_VALUE`
- `gain` → the computed expression `overview.CURRENT_VALUE.sub(overview.COST_BASIS)` (gain isn't
  a stored column — it's computed in Kotlin today — but as a SQL arithmetic expression over two
  real columns it can be sorted directly in the `ORDER BY`)

Default to today's `COST_BASIS.desc()` when no sort is given. Nulls (e.g. `current`/`gain` for
holdings without a current price) should sort last regardless of direction (`NULLS LAST`).

**Frontend:** Add a small `SortTh` component used for the Carteira/Preço atual/Investido/Valor
atual/Resultado headers — clickable, shows a sort icon (neutral when inactive, up/down chevron
when active), toggles asc/desc on repeat click and defaults to desc on a new column (matches the
mockup). Clicking updates a `sortKey`/`sortDir` state that feeds `?sort=<key>,<dir>` into
`holdingsListStore.loadKind`. Also add the "Preço atual" column to the header/rows if not already
rendered for all kinds (funds show "—").

**Files touched:**
- `server/src/main/kotlin/br/com/investlog/server/holdingsoverview/domain/repositories/HoldingsOverviewRepository.kt`
- `client/src/stores/holdingsList.ts`
- `client/src/views/InvestmentsView.vue`

---

## 9 — Wallet cards: show current value and gain

**Problem:** `WalletsView.vue:125-127` only shows `fmt.money(wallet.totalInvested, ...)` on each
wallet card. `WalletResponse` has no `currentValue`/`gain` fields, and `WalletRepository.kt`'s
`findAll`/`create`/`findByExternalId`/`update` queries only select `holdingCountField()` and
`totalInvestedField()` — both correlated scalar subqueries over `HOLDINGS_OVERVIEW`.

**Backend:** Add a `currentValueField()` helper alongside `totalInvestedField()`:
```kotlin
private fun currentValueField() =
    DSL.field(
        DSL.select(DSL.sum(HOLDINGS_OVERVIEW.CURRENT_VALUE))
            .from(HOLDINGS_OVERVIEW)
            .where(HOLDINGS_OVERVIEW.WALLET_ID.eq(WALLETS.ID))
    ).`as`("current_value")
```
Deliberately **not** `COALESCE`d — `SUM` over all-NULL rows returns `NULL`, which is exactly
"this wallet has no holdings with a current value set" and should render as "—", matching the
per-row semantics already used in `HoldingsOverviewRepository.kt`. Add `currentValue:
BigDecimal?`, `gain: BigDecimal?`, `gainPct: BigDecimal?` to `WalletResponse`; compute `gain`/
`gainPct` in `toResponse()` the same way `HoldingsOverviewRepository.kt:48-52` does (`gain =
currentValue?.let { it - totalInvested }`, `gainPct` guarded against `totalInvested.signum() ==
0`).

**Frontend:** In `WalletsView.vue`, widen `.wallet-grid` columns from `minmax(280px,1fr)` to
`minmax(320px,1fr)` and insert a new `.wallet-result` row (between invested amount and the
holdings list) showing "Valor atual" and "Resultado" via a `GainChip`-equivalent (reuse whatever
gain-chip component already renders gain elsewhere, e.g. in the investments table), `"—"` when
`currentValue` is null.

**Files touched:**
- `server/src/main/kotlin/br/com/investlog/server/wallets/domain/repositories/WalletRepository.kt`
- `server/src/main/kotlin/br/com/investlog/server/wallets/rest/payloads/WalletResponse.kt`
- `client/src/types.ts`
- `client/src/views/WalletsView.vue`
- `client/src/assets/styles.css`

---

## 10 — Compact money formatting actually abbreviates

**Problem:** `useFormat.ts:12-16`'s `money()` `compact` option only switches from 2-decimal to
0-decimal formatting once `|v| >= 100000` (e.g. `R$ 150000` instead of `R$ 150000,00`) — it never
abbreviates. The topnav total, overview donut center value/legend, and wallet `wi-base` all pass
`compact: true` expecting something like `R$ 239k`, but currently see the full number.

**Fix:** Replace the threshold logic with real abbreviation: `>= 1_000_000` → one decimal +
`"M"` (e.g. `R$ 1,2M`), `>= 100_000` → rounded thousands + `"k"` (e.g. `R$ 239k`), otherwise fall
back to the normal 2-decimal format. Matches what was already implemented in the Claude Design
mockup's `ui.jsx`.

**Files touched:** `client/src/composables/useFormat.ts`

---

## 11 — Navbar logo returns to the overview

**Problem:** The brand mark in `TheNavbar.vue:35-38` is a static, non-interactive `<div
class="brand">` — clicking it does nothing.

**Fix:** Wrap it in a `<router-link to="/">` (or a `<button>` with `router.push({ name:
'overview' })`), keeping the existing markup/classes inside. Matches the mockup's
`<button className="brand" onClick={...}>`.

**Files touched:** `client/src/components/layout/TheNavbar.vue`

---

## 13 — Remove the "Logbook" eyebrow label

**Problem:** `<div class="page-eyebrow">Logbook</div>` repeats above the page title on four
pages, adding visual noise without conveying anything (the page title already says what the page
is).

**Fix:** Delete the `<div class="page-eyebrow">Logbook</div>` line from each of:
- `client/src/views/WalletsView.vue:76`
- `client/src/views/InvestmentsView.vue:89`
- `client/src/views/OverviewView.vue:102`
- `client/src/views/SettingsView.vue:54`

The `.page-eyebrow` CSS rule itself can stay (harmless, possibly reused later) or be removed if
nothing else uses it.

**Files touched:** the four view files above.

---

## 14 — Overview type-distribution cards: show current value and gain

**Problem:** `OverviewView.vue:211-243`'s per-type cards render a progress bar
(`:226-233`) and only show the **invested** amount (`typeRow.invested`) plus wallet/holdings
counts — no current value or gain per kind. No backend change is needed: `KindSummary`
(`client/src/types.ts:44-51`) already exposes `totalCurrentValue`, `totalGain`, `totalGainPct`
per kind — the top-level portfolio KPI cards already consume the equivalent
`summary.totalCurrentValue`/`totalGain` fields, confirming the data is already there.

**Fix:** In the `typeRows` computed (`OverviewView.vue:42-57`), also map
`kindSummary?.totalCurrentValue`/`totalGain`/`totalGainPct` per row. Replace the `.type-bar`
progress bar with a `.type-result-row` showing "Valor atual" (compact-formatted) and "Resultado"
(gain chip), keeping the existing wallet/holdings-count meta line below it — matches the approved
mockup.

**Files touched:**
- `client/src/views/OverviewView.vue`
- `client/src/assets/styles.css`

---

## Implementation order

1. **CSS-only / pure frontend, no backend dependency** — items 10 (compact formatting), 11 (logo
   link), 13 (remove eyebrow), 14 (type-distribution cards — data already in `KindSummary`)
2. **Frontend-only behavior fixes** — items 1 (uppercase ticker), 4 (URL sync), 5 (modal default
   kind), 2 (Settings toasts)
3. **Backend + frontend, additive fields only** — item 9 (wallet current value/gain)
4. **Backend + frontend, new query params** — item 6/12 (sub-type filter + search), item 8
   (sortable columns) — naturally grouped since both touch
   `HoldingsOverviewController`/`HoldingsOverviewRepository` and the investments toolbar/table at
   once
5. **New PATCH endpoints** — item 3 (editable lot/contribution date) — touches three separate
   controllers/services/repositories, kept last since it's the most backend-heavy and the least
   visually coupled to the rest
