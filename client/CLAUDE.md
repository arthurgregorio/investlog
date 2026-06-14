# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
npm install
npm run dev        # dev server at http://localhost:5173
npm run build      # vue-tsc --noEmit, then vite build (fails on type errors)
npm run preview    # preview the production build
npm run type-check # vue-tsc --noEmit only
```

There is no lint or test setup configured. `tsconfig.app.json` has `strict`, `noUnusedLocals`
and `noUnusedParameters` enabled, so unused imports/variables fail `npm run build` /
`npm run type-check`.

## Architecture

InvestLog is a manual (PT-BR) investment logbook: stocks, crypto and funds, entered by hand —
there is no live market feed. Stack: Vue 3 `<script setup>` + TypeScript, Pinia, Vue Router 4,
Vite 6. Path alias `@` → `src/`. See `README.md` for the screen list and directory layout.

The UI was ported pixel-for-pixel from a Claude Design React/Babel prototype handoff. `src/assets/styles.css`
is that ported CSS spec (Tabler visual language) and is the single source of styling truth —
components rely on its classes and CSS custom properties rather than scoped/component styles.

### Domain model (`src/types.ts`)

- `WalletKind` = `'stocks' | 'crypto' | 'funds'`.
- `Holding` is a discriminated union on `kind`: `StockHolding`/`CryptoHolding` hold `lots`
  (dated qty+price purchases); `FundHolding` holds `contributions` (dated amounts).
- "Money invested" = cost basis, derived from lots/contributions. `currentPrice` /
  `currentValue` are **optional** manual fields — gain/loss (`Gain`) is only computed and
  shown for holdings where the user filled one in.
- Multi-currency: each `Wallet` has a `currency`; `CurrencyConfig.base` + `rates` convert
  wallet-currency amounts to a base currency for consolidated totals/charts.

### State (`src/stores`)

- `portfolio` (Pinia setup store) is the business-logic layer: owns wallets/holdings/config
  (seeded from `src/data/seed.ts`), and exposes actions (`addWallet`, `addStock`, `addCrypto`,
  `addFund`, `addLot`, `addContribution`, `setRate`, ...) plus selectors. Convention: no-arg
  derived values are `computed` getters (`totalsByType`, `grandInvestedBase`, `currentSummary`,
  `cumulativeSeries`); selectors that take an id/holding (`holdingCost`, `holdingGain`,
  `walletInvested`, ...) are plain functions reading reactive state, kept reactive by calling
  them directly in templates. All other in-session state resets on page refresh.
- `appearance` holds dark mode + accent color and is the one store persisted to
  `localStorage` (`investlog.appearance`).

### App-shell modals (`src/composables/useModals.ts`)

`AddInvestmentModal` and `CreateWalletModal` are rendered once in `App.vue`, outside the
router views, and controlled via `provide`/`inject`. Any view calls `useModals()` to get
`{ openAddInvestment, openCreateWallet }` instead of navigating or prop-drilling.
Add-investment is **always** a modal — there is no dedicated route/page for it.

### Theming

`App.vue` sets `data-theme` (`light`/`dark`), `data-accent` (`blue`/`indigo`/`teal`/`green`)
and a fixed `data-density="comfortable"` on `.app-root`; all variants are CSS custom
properties in `styles.css`. Accent color is the only user-configurable appearance setting
(set on the Configurações/Settings view) — density and the add-flow style are intentionally
not configurable.

### Composables

- `useFormat` — pure pt-BR formatting helpers (money, signed money, percent, quantity, date).
- `useAddInvestmentForm` — shared reactive form state/validation/submit logic for the
  add-investment modal (stocks/crypto/funds).
- `useModals` — app-shell modal injection (above).
