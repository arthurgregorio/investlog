# UX Improvements — First-Usage Feedback

**Date:** 2026-06-19
**Status:** Approved

## Scope

Six targeted improvements identified after first real usage of InvestLog:

1. Overview auto-refresh on navigate
2. Update current price / value for all holding types
3. Rename wallet
4. Real user info in navbar
5. Sub-table vertical alignment fix
6. Currency rate rows uniform height

---

## 1 — Overview auto-refresh on navigate

**Problem:** `OverviewView.onMounted` calls `overviewStore.load()`, which short-circuits when `loaded === true`. Navigating away and back shows stale data after adding investments.

**Fix:** Replace `overviewStore.load()` with `overviewStore.refresh()` in `OverviewView.vue`. The store already exposes `refresh()` (sets `loaded = false`, then calls `load()`).

**Files touched:** `client/src/views/OverviewView.vue`

---

## 2 — Update current price / value (all holding types)

**Problem:** There is no way to set a holding's current price without adding a new buy/lot.

**Backend:** PATCH endpoints already exist and accept these optional fields:
- Stocks: `PATCH /wallets/{walletId}/stock-holdings/{holdingId}` → `{ currentPrice }`
- Crypto: `PATCH /wallets/{walletId}/crypto-holdings/{holdingId}` → `{ currentPrice }`
- Funds:  `PATCH /wallets/{walletId}/fund-holdings/{holdingId}`  → `{ currentValue }`

**Frontend design:**

`holdingsApi` gets three new methods: `updateStockHolding`, `updateCryptoHolding`, `updateFundHolding`, each sending a PATCH with only the relevant field.

`HoldingDetailPanel.vue` gains a price-editor state (`editingPrice: boolean`, `priceInput: number | ''`). In `detail-foot-right`, alongside "Preço médio" and "Remover", a button appears:
- Stocks / Crypto: `"Atualizar preço"` (pencil icon)
- Funds: `"Atualizar valor atual"` (pencil icon)

Clicking it reveals an inline editor — a `NumberInput` prefixed with the wallet's currency symbol, plus "Salvar" and "Cancelar" buttons — replacing the footer row. On save: calls the matching PATCH method, reloads the detail via `reloadDetail()`, and emits `positionAdded` so the parent list and overview are refreshed.

**Files touched:**
- `client/src/api/holdings.ts`
- `client/src/components/investments/HoldingDetailPanel.vue`

---

## 3 — Rename wallet

**Problem:** There is no UI to change a wallet's name after creation.

**Backend:** `PATCH /wallets/{id}` with `{ name }` already exists. `walletsApi.update()` already calls it.

**Frontend design:**

`WalletsView.vue` gets a `renameWallet(walletId, currentName)` function that opens a `dialog.prompt` (Buefy) pre-filled with `currentName`. On confirm: calls `walletsApi.update(walletId, { name: newName })`, shows a success toast, then calls `walletsStore.refresh()`.

A small edit/pencil button (`is-small`, outlined) is placed immediately before the delete button in the wallet card header. Uses `icon-left="pencil"`.

**Files touched:** `client/src/views/WalletsView.vue`

---

## 4 — Real user info in navbar

**Problem:** The navbar shows hardcoded `"Rafael T."` / `"Investidor"` instead of the real user from the database.

**Backend:** `GET /private/v1/profile` returns `{ name, email, avatarUrl, accentColor, preferredCurrency }`.

**Frontend design:**

New file `client/src/api/profile.ts` exposes `profileApi.getProfile()`.

`TheNavbar.vue` fetches the profile on `onMounted`. A local `profile` ref holds `{ name: string; email: string } | null`. While null, display placeholder text (initials `"?"`, name `"..."`) to avoid layout shift.

Initials derived from the profile name: take the first letter of each space-separated word, up to two letters, uppercased (e.g. `"Arthur Gregorio"` → `"AG"`, `"Rafael"` → `"R"`).

The `nu-sub` line shows `email` instead of the hardcoded `"Investidor"` — preparing the slot for future auth context (role/plan).

**Files touched:**
- `client/src/api/profile.ts` *(new)*
- `client/src/components/layout/TheNavbar.vue`

---

## 5 — Sub-table vertical alignment fix

**Problem:** `.sub-table td` has no `vertical-align` declaration, so cells default to `top`. The detail panel rows (lots/contributions) show text pinned to the top with dead space below when a row's delete button makes it taller.

**Fix:** Add `vertical-align: middle` to `.sub-table td` in `styles.css`, matching the `.inv-table td` declaration already present.

**Files touched:** `client/src/assets/styles.css`

---

## 6 — Currency rate rows uniform height

**Problem:** The BRL row (text label only) and the USD row (with a `NumberInput`) render at different heights, giving the settings card a misaligned appearance.

**Fix:** Add `min-height: 52px` to `.rate-row` in `styles.css`. Both rows will expand to the same height; the input row already meets or exceeds this height, so the BRL row stretches to match.

**Files touched:** `client/src/assets/styles.css`

---

## Implementation order

1. CSS fixes (items 5, 6) — purely additive, no logic
2. Overview refresh (item 1) — one-line change
3. Rename wallet (item 3) — UI only, API already wired
4. User info (item 4) — new api file + navbar wiring
5. Update current price (item 2) — new API methods + HoldingDetailPanel UI

No backend changes required; all necessary endpoints exist.
