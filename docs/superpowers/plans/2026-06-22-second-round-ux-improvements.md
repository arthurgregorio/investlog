# Second Round UX Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the 12 post-usage UX improvement items from `docs/superpowers/specs/2026-06-22-second-round-ux-improvements-design.md` (numbered 1–14 in the spec; item 7 dropped, item 12 merged into item 6) across the InvestLog Vue 3 client and Spring Boot/Kotlin server.

**Architecture:** No new modules or services. Every task is a small, additive change to an existing file: CSS-only tweaks, frontend behavior fixes, two backend payload/query-param extensions (`holdings_overview` listing, `wallets` listing), and three new PATCH endpoints (one per holding kind) for editing a lot/contribution date in place. Tasks are grouped into 5 phases matching the spec's stated implementation order, each phase independently shippable.

**Tech Stack:** Vue 3 `<script setup>` + TypeScript, Pinia, Vue Router 4, Buefy, Vitest. Kotlin 2.3 / Spring Boot 4, jOOQ, JUnit 5 + `RestTestClient`.

## Global Constraints

- **No abbreviated names** anywhere (client and server) — e.g. `quantity` not `qty`, `wallets` not `w`, `baseCurrency` never shortened to `base`. See `client/CLAUDE.md` / `server/CLAUDE.md`.
- **Never globally override `.button`** in `styles.css` — it breaks Buefy's CSS-variable cascade for every type/modifier class. Use `is-ghost` (not `is-text`) for transparent/icon-only Buefy buttons.
- `client/src/assets/styles.css` is the single source of styling truth. Components rely on its classes and CSS custom properties — no scoped/component `<style>` blocks.
- `tsconfig.app.json` has `strict`, `noUnusedLocals`, `noUnusedParameters` — unused imports/variables fail `npm run build` / `npm run type-check`. Remove any import/const that a task's edit makes obsolete.
- Backend mutating service methods are `@Transactional`. Not-found lookups throw `br.com.investlog.server.shared.exceptions.NotFoundException`, mapped to HTTP 404 by `GlobalExceptionHandler` — never `null`/`Optional` leaking to the controller layer.
- jOOQ aggregate fields on a one-to-many relationship use the correlated-scalar-subquery pattern: `DSL.field(DSL.select(...).from(...).where(...)).`as`("alias")`, read back via `record.get("alias", Type::class.java)`.
- New/extended backend controller test classes use `@TestInstance(TestInstance.Lifecycle.PER_CLASS)` + `@BeforeAll fun setup()` (when the class shares fixture data across tests) + `@Order(n)` on every `@Test`, following `HoldingsOverviewControllerTest.kt` / `StockHoldingControllerTest.kt`.
- Pinia stores load lazily: a `.load()`/`.loadKind()` action guarded by a `loaded` ref, called from the consuming view's `onMounted`. Mutations are pessimistic — always `await` the API call before updating local store state.
- Commit messages are short, lowercase, present-tense, with a `feat:`/`fix:`/`test:` prefix, matching the existing log (`fix css issue`, `migrations adjustments`).

---

## Phase 1 — CSS-only / pure frontend (items 10, 11, 13, 14)

### Task 1: Item 10 — abbreviate large compact money values (`useFormat.ts`)

**Files:**
- Modify: `client/src/composables/useFormat.ts`
- Test: `client/src/composables/useFormat.spec.ts` (new file)

**Interfaces:**
- Consumes: nothing new.
- Produces: `fmt.money(value, currency, { compact: true })` now returns `"R$ 239k"` for values ≥ 100,000 and `"R$ 1,2M"` for values ≥ 1,000,000, instead of a plain non-abbreviated integer. Non-compact calls are unaffected. Consumed as-is by `OverviewView.vue`'s `chart-big`, `donut-center-value`, and `legend-value` (no changes needed there — behavior swaps transparently under the existing `{ compact: true }` call sites).

- [ ] **Step 1: Write the failing test**

Create `client/src/composables/useFormat.spec.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { fmt } from './useFormat'

describe('fmt.money compact abbreviation', () => {
  it('formats values under 100,000 normally even when compact is requested', () => {
    expect(fmt.money(1234.5, 'BRL', { compact: true })).toBe('R$ 1.234,50')
  })

  it('abbreviates values >= 100,000 to thousands with a k suffix', () => {
    expect(fmt.money(239000, 'BRL', { compact: true })).toBe('R$ 239k')
  })

  it('abbreviates values >= 1,000,000 to one decimal with an M suffix', () => {
    expect(fmt.money(1234000, 'BRL', { compact: true })).toBe('R$ 1,2M')
  })

  it('does not abbreviate when compact is not requested', () => {
    expect(fmt.money(239000, 'BRL')).toBe('R$ 239.000,00')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test -- src/composables/useFormat.spec.ts`
Expected: FAIL — the `>= 100,000` case currently returns `'R$ 239.000'` (via `num0`, no `k` suffix), not `'R$ 239k'`.

- [ ] **Step 3: Write minimal implementation**

In `client/src/composables/useFormat.ts`, the current file is:

```ts
/* Formatting helpers — multi-currency money, percentages, quantities, dates.
   Pure functions; exposed as a composable for ergonomic use in <script setup>. */

const SYM: Record<string, string> = { BRL: 'R$', USD: 'US$', EUR: '€' }
const num2 = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const num0 = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 0 })
const numQ = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 8 })
const MONTHS = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']

export const fmt = {
  sym: (cur = 'BRL') => SYM[cur] || cur + ' ',
  money: (v: number, cur = 'BRL', opts: { compact?: boolean } = {}) => {
    const s = SYM[cur] || cur + ' '
    const big = opts.compact && Math.abs(v) >= 100000
    return s + ' ' + (big ? num0.format(v) : num2.format(v))
  },
  moneySigned: (v: number, cur = 'BRL') => (v >= 0 ? '+' : '−') + fmt.money(Math.abs(v), cur),
  pct: (v: number) => num2.format(Math.abs(v)) + '%',
  pctSigned: (v: number) => (v >= 0 ? '+' : '−') + num2.format(Math.abs(v)) + '%',
  qty: (v: number) => (Number.isInteger(v) ? String(v) : numQ.format(v)),
  date: (iso: string) => { const d = new Date(iso + 'T00:00:00'); return `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}` },
  dateShort: (iso: string) => { const d = new Date(iso + 'T00:00:00'); return `${MONTHS[d.getMonth()]}/${String(d.getFullYear()).slice(2)}` },
}

export function useFormat() { return fmt }
```

Replace it with:

```ts
/* Formatting helpers — multi-currency money, percentages, quantities, dates.
   Pure functions; exposed as a composable for ergonomic use in <script setup>. */

const SYM: Record<string, string> = { BRL: 'R$', USD: 'US$', EUR: '€' }
const num2 = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const numQ = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 8 })
const MONTHS = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']

export const fmt = {
  sym: (cur = 'BRL') => SYM[cur] || cur + ' ',
  money: (v: number, cur = 'BRL', opts: { compact?: boolean } = {}) => {
    const s = SYM[cur] || cur + ' '
    if (opts.compact) {
      const abs = Math.abs(v)
      if (abs >= 1_000_000) return s + ' ' + (v / 1_000_000).toFixed(1).replace('.', ',') + 'M'
      if (abs >= 100_000) return s + ' ' + Math.round(v / 1_000) + 'k'
    }
    return s + ' ' + num2.format(v)
  },
  moneySigned: (v: number, cur = 'BRL') => (v >= 0 ? '+' : '−') + fmt.money(Math.abs(v), cur),
  pct: (v: number) => num2.format(Math.abs(v)) + '%',
  pctSigned: (v: number) => (v >= 0 ? '+' : '−') + num2.format(Math.abs(v)) + '%',
  qty: (v: number) => (Number.isInteger(v) ? String(v) : numQ.format(v)),
  date: (iso: string) => { const d = new Date(iso + 'T00:00:00'); return `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}` },
  dateShort: (iso: string) => { const d = new Date(iso + 'T00:00:00'); return `${MONTHS[d.getMonth()]}/${String(d.getFullYear()).slice(2)}` },
}

export function useFormat() { return fmt }
```

Note: `num0` is removed entirely along with its only use site — leaving it in place would fail `npm run build` under `noUnusedLocals`.

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test -- src/composables/useFormat.spec.ts`
Expected: PASS (4 tests)

- [ ] **Step 5: Run type-check to confirm no unused-local regression**

Run: `npm run type-check`
Expected: no errors.

- [ ] **Step 6: Commit**

```bash
git add client/src/composables/useFormat.ts client/src/composables/useFormat.spec.ts
git commit -m "feat: abbreviate large compact money values with k/M suffixes"
```

---

### Task 2: Item 11 — make the navbar brand/logo a link back to the home route

**Files:**
- Modify: `client/src/components/layout/TheNavbar.vue`

**Interfaces:**
- Consumes: Vue Router's globally-registered `<RouterLink>` (already used unimported elsewhere in this codebase, e.g. `<RouterView />` in `App.vue`).
- Produces: nothing new consumed by other tasks.

- [ ] **Step 1: Manual repro before the fix**

Run `npm run dev`, open the app, click the "InvestLog" logo in the top-left of the navbar. Confirm nothing happens (no navigation). This is the bug item 11 fixes.

- [ ] **Step 2: Apply the fix**

In `client/src/components/layout/TheNavbar.vue`, the current template has:

```html
  <header class="navbar">
    <div class="navbar-inner">
      <div class="brand">
        <span class="brand-mark"><AppIcon name="trendUp" :size="20" :stroke="2.4" /></span>
        <span class="brand-name">Invest<b>Log</b></span>
      </div>
```

Replace the `<div class="brand">...</div>` block with:

```html
  <header class="navbar">
    <div class="navbar-inner">
      <RouterLink to="/" class="brand">
        <span class="brand-mark"><AppIcon name="trendUp" :size="20" :stroke="2.4" /></span>
        <span class="brand-name">Invest<b>Log</b></span>
      </RouterLink>
```

No CSS change is needed: `styles.css` has a global `a { text-decoration: none; color: inherit; }` reset (`client/src/assets/styles.css:127-130`), and `.brand`/`.brand-mark`/`.brand-name` (`client/src/assets/styles.css:157-184`) are plain class selectors that apply identically whether the element is a `<div>` or the `<a>` that `RouterLink` renders.

- [ ] **Step 3: Verify with type-check**

Run: `npm run type-check`
Expected: no errors.

- [ ] **Step 4: Manual verification**

With `npm run dev` running, navigate to any non-home page (e.g. `/wallets`), click the "InvestLog" logo, confirm it navigates to `/`.

- [ ] **Step 5: Commit**

```bash
git add client/src/components/layout/TheNavbar.vue
git commit -m "fix: make navbar brand logo link back to home"
```

---

### Task 3: Item 13 — remove the redundant "Logbook" page eyebrow

**Files:**
- Modify: `client/src/views/OverviewView.vue`
- Modify: `client/src/views/InvestmentsView.vue`
- Modify: `client/src/views/SettingsView.vue`
- Modify: `client/src/views/WalletsView.vue`

**Interfaces:**
- Consumes: nothing.
- Produces: nothing consumed by other tasks (this is a pure deletion; later tasks that also touch `InvestmentsView.vue` — Tasks 6, 7, 13 — target disjoint regions of the file and are unaffected by this removal).

- [ ] **Step 1: Remove the eyebrow from `OverviewView.vue`**

Current (`client/src/views/OverviewView.vue`):

```html
    <div class="page-head page-head-row">
      <div>
        <div class="page-eyebrow">Logbook</div>
        <h1 class="page-title">Visão geral</h1>
        <p class="page-desc">Uma visão consolidada dos seus investimentos</p>
      </div>
```

Replace with:

```html
    <div class="page-head page-head-row">
      <div>
        <h1 class="page-title">Visão geral</h1>
        <p class="page-desc">Uma visão consolidada dos seus investimentos</p>
      </div>
```

- [ ] **Step 2: Remove the eyebrow from `InvestmentsView.vue`**

Current:

```html
    <div class="page-head page-head-row">
      <div>
        <div class="page-eyebrow">Logbook</div>
        <h1 class="page-title">Investimentos</h1>
        <p class="page-desc">Aqui você gerencia seus investimentos</p>
      </div>
```

Replace with:

```html
    <div class="page-head page-head-row">
      <div>
        <h1 class="page-title">Investimentos</h1>
        <p class="page-desc">Aqui você gerencia seus investimentos</p>
      </div>
```

- [ ] **Step 3: Remove the eyebrow from `SettingsView.vue`**

Current:

```html
    <div class="page-head">
      <div class="page-eyebrow">Logbook</div>
      <h1 class="page-title">Configurações</h1>
      <p class="page-desc">Defina as taxas de conversão e os tipos de ativo usados no cadastro.</p>
    </div>
```

Replace with:

```html
    <div class="page-head">
      <h1 class="page-title">Configurações</h1>
      <p class="page-desc">Defina as taxas de conversão e os tipos de ativo usados no cadastro.</p>
    </div>
```

- [ ] **Step 4: Remove the eyebrow from `WalletsView.vue`**

Current:

```html
    <div class="page-head page-head-row">
      <div>
        <div class="page-eyebrow">Logbook</div>
        <h1 class="page-title">Carteiras</h1>
        <p class="page-desc">Carteiras podem ter tipos e moedas distintas</p>
      </div>
```

Replace with:

```html
    <div class="page-head page-head-row">
      <div>
        <h1 class="page-title">Carteiras</h1>
        <p class="page-desc">Carteiras podem ter tipos e moedas distintas</p>
      </div>
```

- [ ] **Step 5: Verify with type-check**

Run: `npm run type-check`
Expected: no errors.

- [ ] **Step 6: Manual verification**

With `npm run dev` running, visit `/overview`, `/investments`, `/settings`, `/wallets` and confirm none of them show a "Logbook" label above the page title.

- [ ] **Step 7: Commit**

```bash
git add client/src/views/OverviewView.vue client/src/views/InvestmentsView.vue client/src/views/SettingsView.vue client/src/views/WalletsView.vue
git commit -m "fix: remove redundant Logbook eyebrow from page headers"
```

---

### Task 4: Item 14 — show gain/result instead of an invested-share bar on the per-type overview cards

**Files:**
- Modify: `client/src/views/OverviewView.vue`
- Modify: `client/src/assets/styles.css`

**Interfaces:**
- Consumes: `GainChip.vue` (`client/src/components/ui/GainChip.vue`, existing component — props `value: number | null`, `pct?: number | null`, `cur?: string`). `KindSummary.totalGain: number` and `KindSummary.totalGainPct: number | null` (already returned by the `/overview` endpoint, already in `client/src/types.ts` — no backend change needed).
- Produces: nothing consumed by other tasks.

- [ ] **Step 1: Extend the `typeRows` computed with gain data**

In `client/src/views/OverviewView.vue`, current:

```ts
const typeRows = computed(() => {
  const summaryByKind = Object.fromEntries(
    (summary.value?.kindSummaries ?? []).map((kindSummary) => [kindSummary.kind, kindSummary]),
  )
  return ALL_KINDS.map((kind) => {
    const kindSummary = summaryByKind[kind]
    return {
      key: kind,
      label: WALLET_TYPES[kind].label,
      accent: WALLET_TYPES[kind].accent,
      invested: kindSummary?.totalCostBasis ?? 0,
      walletCount: walletCountByKind.value[kind] ?? 0,
      holdings: kindSummary?.holdingCount ?? 0,
    }
  })
})
```

Replace with:

```ts
const typeRows = computed(() => {
  const summaryByKind = Object.fromEntries(
    (summary.value?.kindSummaries ?? []).map((kindSummary) => [kindSummary.kind, kindSummary]),
  )
  return ALL_KINDS.map((kind) => {
    const kindSummary = summaryByKind[kind]
    return {
      key: kind,
      label: WALLET_TYPES[kind].label,
      accent: WALLET_TYPES[kind].accent,
      invested: kindSummary?.totalCostBasis ?? 0,
      gain: kindSummary ? kindSummary.totalGain : null,
      gainPct: kindSummary ? kindSummary.totalGainPct : null,
      walletCount: walletCountByKind.value[kind] ?? 0,
      holdings: kindSummary?.holdingCount ?? 0,
    }
  })
})
```

- [ ] **Step 2: Import `GainChip`**

Current imports:

```ts
import AppIcon from '@/components/AppIcon.vue'
import type { IconName } from '@/components/AppIcon.vue'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import AreaChart from '@/components/charts/AreaChart.vue'
import DonutChart from '@/components/charts/DonutChart.vue'
```

Replace with:

```ts
import AppIcon from '@/components/AppIcon.vue'
import type { IconName } from '@/components/AppIcon.vue'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import GainChip from '@/components/ui/GainChip.vue'
import AreaChart from '@/components/charts/AreaChart.vue'
import DonutChart from '@/components/charts/DonutChart.vue'
```

- [ ] **Step 3: Swap the progress bar for a result row in the template**

Current:

```html
                <div class="type-value">{{ fmt.money(typeRow.invested, baseCurrency) }}</div>
                <div class="type-bar">
                  <span
                    :style="{
                      width: (grandInvestedBase ? (typeRow.invested / grandInvestedBase) * 100 : 0) + '%',
                      background: typeRow.accent,
                    }"
                  />
                </div>
```

Replace with:

```html
                <div class="type-value">{{ fmt.money(typeRow.invested, baseCurrency) }}</div>
                <div class="type-result-row">
                  <GainChip :value="typeRow.gain" :pct="typeRow.gainPct" :cur="baseCurrency" />
                </div>
```

- [ ] **Step 4: Replace the `.type-bar` CSS with `.type-result-row`**

In `client/src/assets/styles.css`, current (`.type-bar` is used nowhere else in the codebase — confirmed by grep — so it is safe to remove entirely):

```css
.type-bar {
    height: 6px;
    border-radius: 4px;
    background: var(--surface-2);
    overflow: hidden;
    margin: 11px 0 11px;
}

.type-bar span {
    display: block;
    height: 100%;
    border-radius: 4px;
}
```

Replace with:

```css
.type-result-row {
    display: flex;
    align-items: center;
    margin: 11px 0;
}
```

- [ ] **Step 5: Verify with type-check**

Run: `npm run type-check`
Expected: no errors.

- [ ] **Step 6: Manual verification**

With `npm run dev` running, visit `/overview`. Under "Distribuição por tipo", each type card should show a colored gain/loss chip (amount + percentage) where the progress bar used to be, matching the styling already used in the investments table's "Resultado" column.

- [ ] **Step 7: Commit**

```bash
git add client/src/views/OverviewView.vue client/src/assets/styles.css
git commit -m "feat: show gain/result instead of invested-share bar on type cards"
```

---

## Phase 2 — Frontend-only behavior fixes (items 1, 4, 5, 2)

### Task 5: Item 1 — uppercase the ticker field as the user types

**Files:**
- Modify: `client/src/components/forms/AddInvestmentForm.vue`

**Interfaces:**
- Consumes: `props.form: AddInvestmentForm` (from `useAddInvestmentForm.ts`, unchanged — `form.ticker: string`).
- Produces: nothing consumed by other tasks. The existing submit-time `form.ticker.trim().toUpperCase()` safety net in `useAddInvestmentForm.ts` is left as-is (defends against any path that bypasses this input, e.g. programmatic form fills).

- [ ] **Step 1: Manual repro before the fix**

Run `npm run dev`, open "Adicionar investimento" for stocks or crypto, type a lowercase ticker like `petr4`. Confirm it stays lowercase in the field (only gets uppercased after submitting).

- [ ] **Step 2: Add a computed get/set wrapper around `form.ticker`**

In `client/src/components/forms/AddInvestmentForm.vue`, current:

```ts
const walletCurrency = computed(() => {
  const wallet = props.form.walletsOfKind.find((wallet) => wallet.id === props.form.walletId)
  return wallet?.currency ?? 'BRL'
})
const sym = computed(() => fmt.sym(walletCurrency.value))
```

Replace with:

```ts
const walletCurrency = computed(() => {
  const wallet = props.form.walletsOfKind.find((wallet) => wallet.id === props.form.walletId)
  return wallet?.currency ?? 'BRL'
})
const sym = computed(() => fmt.sym(walletCurrency.value))

const ticker = computed({
  get: () => props.form.ticker,
  set: (value: string) => {
    props.form.ticker = value.toUpperCase()
  },
})
```

- [ ] **Step 3: Bind the ticker input to the new computed**

Current:

```html
        <b-field :label="form.kind === 'CRYPTO' ? 'Sigla / código' : 'Ticker'">
          <b-input v-model="form.ticker" :placeholder="form.kind === 'CRYPTO' ? 'BTC' : 'PETR4'" />
        </b-field>
```

Replace with:

```html
        <b-field :label="form.kind === 'CRYPTO' ? 'Sigla / código' : 'Ticker'">
          <b-input v-model="ticker" :placeholder="form.kind === 'CRYPTO' ? 'BTC' : 'PETR4'" />
        </b-field>
```

- [ ] **Step 4: Verify with type-check**

Run: `npm run type-check`
Expected: no errors.

- [ ] **Step 5: Manual verification**

With `npm run dev` running, open "Adicionar investimento" for stocks, type `petr4` into the ticker field. Confirm it appears as `PETR4` immediately, character by character, as you type.

- [ ] **Step 6: Commit**

```bash
git add client/src/components/forms/AddInvestmentForm.vue
git commit -m "feat: uppercase ticker input live as the user types"
```

---

### Task 6: Item 4 — keep the investments tab filter in sync with the URL

**Files:**
- Modify: `client/src/views/InvestmentsView.vue`

**Interfaces:**
- Consumes: `useRouter()` from `vue-router` (already a project dependency, used elsewhere e.g. `WalletsView.vue`).
- Produces: nothing consumed by other tasks (Task 13's full-file rewrite of this same file supersedes this change, already incorporating it).

- [ ] **Step 1: Manual repro before the fix**

Run `npm run dev`, open `/investments`, click the "Ações" tab. Confirm the browser's address bar still shows `/investments` (no `?filter=STOCKS`), so the tab can't be bookmarked/shared/restored via back-button.

- [ ] **Step 2: Import `useRouter` and create a `router` instance**

Current:

```ts
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
```

Replace with:

```ts
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
```

Current:

```ts
const holdingsListStore = useHoldingsListStore()
const route = useRoute()
const modals = useModals()
```

Replace with:

```ts
const holdingsListStore = useHoldingsListStore()
const route = useRoute()
const router = useRouter()
const modals = useModals()
```

- [ ] **Step 3: Make `selectTab` navigate instead of mutating state directly**

Current:

```ts
function selectTab(filter: Filter) {
  if (filter === activeFilter.value) return
  activeFilter.value = filter
  openedDetails.value = []
  holdingsListStore.loadKind(filter, 0)
}
```

Replace with:

```ts
function selectTab(filter: Filter) {
  if (filter === activeFilter.value) return
  router.replace({ query: { ...route.query, filter: filter === 'all' ? undefined : filter } })
}
```

The existing `watch(() => route.query.filter, ...)` (unchanged, just above this function) already reacts to the query change by updating `activeFilter` and reloading — so this is the single source of truth for both URL-driven and click-driven tab changes, with no double-fetch.

- [ ] **Step 4: Verify with type-check**

Run: `npm run type-check`
Expected: no errors.

- [ ] **Step 5: Manual verification**

With `npm run dev` running, open `/investments`, click "Ações". Confirm the address bar now shows `/investments?filter=STOCKS`. Click "Cripto", confirm it becomes `?filter=CRYPTO`. Click the browser back button twice, confirm the tab selection follows along and the table reloads accordingly.

- [ ] **Step 6: Commit**

```bash
git add client/src/views/InvestmentsView.vue
git commit -m "fix: sync investments tab filter to the URL on tab change"
```

---

### Task 7: Item 5 — default the add-investment modal's kind to the active tab

**Files:**
- Modify: `client/src/views/InvestmentsView.vue`

**Interfaces:**
- Consumes: `modals.openAddInvestment(kind?: WalletKind)` (`client/src/composables/useModals.ts` — already accepts an optional kind; `App.vue` already wires `addModal.value = { kind }` and defaults to `'STOCKS'` when omitted; `AddInvestmentModal.vue` already passes `initialKind` straight into `useAddInvestmentForm`. No changes needed in any of those three files — the plumbing already exists end-to-end, only the call sites in `InvestmentsView.vue` need to pass the current tab.)
- Produces: nothing consumed by other tasks (Task 13 supersedes this, already incorporating it).

- [ ] **Step 1: Manual repro before the fix**

Run `npm run dev`, open `/investments`, click "Cripto" tab, then click "Adicionar investimento". Confirm the modal opens defaulted to "Ações" (stocks) instead of crypto.

- [ ] **Step 2: Add an `openAddInvestment` wrapper**

In `client/src/views/InvestmentsView.vue`, current end of `<script setup>`:

```ts
function subLabel(row: HoldingRow): string {
  if (row.kind === 'FUNDS') return row.typeLabel ?? 'Fundo'
  if (row.kind === 'CRYPTO') return 'Cripto'
  return row.typeLabel ?? 'Ação'
}
</script>
```

Replace with:

```ts
function subLabel(row: HoldingRow): string {
  if (row.kind === 'FUNDS') return row.typeLabel ?? 'Fundo'
  if (row.kind === 'CRYPTO') return 'Cripto'
  return row.typeLabel ?? 'Ação'
}

function openAddInvestment() {
  modals.openAddInvestment(activeFilter.value !== 'all' ? activeFilter.value : undefined)
}
</script>
```

- [ ] **Step 3: Use the wrapper at both call sites**

Current (header button):

```html
      <b-button type="is-primary" class="has-text-light" icon-left="plus" @click="modals.openAddInvestment()">
        Adicionar investimento
      </b-button>
```

Replace with:

```html
      <b-button type="is-primary" class="has-text-light" icon-left="plus" @click="openAddInvestment">
        Adicionar investimento
      </b-button>
```

Current (empty-state action button):

```html
        <b-button type="is-primary" class="has-text-light" icon-left="plus" @click="modals.openAddInvestment()">
          Adicionar investimento
        </b-button>
```

Replace with:

```html
        <b-button type="is-primary" class="has-text-light" icon-left="plus" @click="openAddInvestment">
          Adicionar investimento
        </b-button>
```

- [ ] **Step 4: Verify with type-check**

Run: `npm run type-check`
Expected: no errors.

- [ ] **Step 5: Manual verification**

With `npm run dev` running, open `/investments`, click "Cripto" tab, click "Adicionar investimento". Confirm the modal now defaults to the "Cripto" radio option. Click "Todos" tab, click "Adicionar investimento" again, confirm it defaults back to "Ações" (the `'STOCKS'` fallback in `App.vue`'s `openAddInvestment`).

- [ ] **Step 6: Commit**

```bash
git add client/src/views/InvestmentsView.vue
git commit -m "fix: default add-investment modal kind to the active investments tab"
```

---

### Task 8: Item 2 — toast feedback for settings actions

**Files:**
- Modify: `client/src/views/SettingsView.vue`

**Interfaces:**
- Consumes: `useToast()` from `buefy` (already used the same way in `WalletsView.vue` and `useAddInvestmentForm.ts`: `toast.open({ message: '...', type: 'is-success' })`).
- Produces: nothing consumed by other tasks.

- [ ] **Step 1: Manual repro before the fix**

Run `npm run dev`, open `/settings`, add a stock type, remove a stock type, set a currency rate. Confirm none of these actions show any toast/confirmation — the only feedback is the list updating silently.

- [ ] **Step 2: Import and instantiate `useToast`**

Current:

```ts
import { onMounted, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import NumberInput from '@/components/ui/NumberInput.vue'
import { useTypesListStore } from '@/stores/typesList'
import { useRatesStore } from '@/stores/rates'
import { useAppearanceStore } from '@/stores/appearance'
import { fmt } from '@/composables/useFormat'
import type { AccentKey } from '@/types'

const typesListStore = useTypesListStore()
const ratesStore = useRatesStore()
const appearance = useAppearanceStore()
```

Replace with:

```ts
import { onMounted, ref } from 'vue'
import { useToast } from 'buefy'
import AppIcon from '@/components/AppIcon.vue'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import NumberInput from '@/components/ui/NumberInput.vue'
import { useTypesListStore } from '@/stores/typesList'
import { useRatesStore } from '@/stores/rates'
import { useAppearanceStore } from '@/stores/appearance'
import { fmt } from '@/composables/useFormat'
import type { AccentKey } from '@/types'

const toast = useToast()
const typesListStore = useTypesListStore()
const ratesStore = useRatesStore()
const appearance = useAppearanceStore()
```

- [ ] **Step 3: Add toasts to the add/set actions, and wrapper functions for the remove actions**

Current:

```ts
async function addStockType() {
  const name = newStockType.value.trim()
  if (!name) return
  await typesListStore.addStockType(name)
  newStockType.value = ''
}

async function addFundType() {
  const name = newFundType.value.trim()
  if (!name) return
  await typesListStore.addFundType(name)
  newFundType.value = ''
}

async function setRate(currencyCode: string, value: number | '') {
  if (value === '' || value <= 0) return
  await ratesStore.upsertRate(currencyCode, Number(value), false)
}
```

Replace with:

```ts
async function addStockType() {
  const name = newStockType.value.trim()
  if (!name) return
  await typesListStore.addStockType(name)
  newStockType.value = ''
  toast.open({ message: 'Tipo de ação adicionado.', type: 'is-success' })
}

async function addFundType() {
  const name = newFundType.value.trim()
  if (!name) return
  await typesListStore.addFundType(name)
  newFundType.value = ''
  toast.open({ message: 'Tipo de fundo adicionado.', type: 'is-success' })
}

async function removeStockType(stockTypeId: string) {
  await typesListStore.removeStockType(stockTypeId)
  toast.open({ message: 'Tipo de ação removido.', type: 'is-success' })
}

async function removeFundType(fundTypeId: string) {
  await typesListStore.removeFundType(fundTypeId)
  toast.open({ message: 'Tipo de fundo removido.', type: 'is-success' })
}

async function setRate(currencyCode: string, value: number | '') {
  if (value === '' || value <= 0) return
  await ratesStore.upsertRate(currencyCode, Number(value), false)
  toast.open({ message: 'Taxa de conversão atualizada.', type: 'is-success' })
}
```

- [ ] **Step 4: Use the new wrapper functions in the template**

Current:

```html
            <button :aria-label="`Remover ${stockType.name}`" @click="typesListStore.removeStockType(stockType.id)">
```

Replace with:

```html
            <button :aria-label="`Remover ${stockType.name}`" @click="removeStockType(stockType.id)">
```

Current:

```html
            <button :aria-label="`Remover ${fundType.name}`" @click="typesListStore.removeFundType(fundType.id)">
```

Replace with:

```html
            <button :aria-label="`Remover ${fundType.name}`" @click="removeFundType(fundType.id)">
```

- [ ] **Step 5: Verify with type-check**

Run: `npm run type-check`
Expected: no errors.

- [ ] **Step 6: Manual verification**

With `npm run dev` running, open `/settings`. Add a stock type, confirm a green "Tipo de ação adicionado." toast appears. Remove it, confirm "Tipo de ação removido." appears. Repeat for fund types. Set a currency rate, confirm "Taxa de conversão atualizada." appears.

- [ ] **Step 7: Commit**

```bash
git add client/src/views/SettingsView.vue
git commit -m "feat: add toast feedback for settings type and rate actions"
```

---

## Phase 3 — Backend + frontend additive fields (item 9)

### Task 9: Item 9 backend — add `currentValue`/`gain`/`gainPct` to the wallet response

**Files:**
- Modify: `server/src/main/kotlin/br/com/investlog/server/wallets/rest/payloads/WalletResponse.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/wallets/domain/repositories/WalletRepository.kt`
- Test: `server/src/test/kotlin/br/com/investlog/server/wallets/rest/controllers/WalletControllerTest.kt`

**Interfaces:**
- Consumes: `br.com.investlog.server.jooq.finances.tables.references.HOLDINGS_OVERVIEW` (existing generated jOOQ table, already used by `holdingCountField()`/`totalInvestedField()` in this same repository).
- Produces: `WalletResponse` gains three new nullable fields — `currentValue: BigDecimal?`, `gain: BigDecimal?`, `gainPct: BigDecimal?` — consumed by Task 10 (frontend `types.ts` + `WalletsView.vue`).

- [ ] **Step 1: Write the failing test**

In `server/src/test/kotlin/br/com/investlog/server/wallets/rest/controllers/WalletControllerTest.kt`, current imports and last test:

```kotlin
import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.wallets.rest.payloads.WalletKind
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import org.junit.jupiter.api.Order
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
```

and:

```kotlin
    @Test
    @Order(6)
    fun `returns 400 when kind is missing`() {
        restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"X","currency":"BRL"}""")
            .exchange()
            .expectStatus().isBadRequest()
    }
}
```

Add `br.com.investlog.server.typelists.rest.payloads.TypeResponse` to the imports:

```kotlin
import br.com.investlog.server.BaseIntegrationTest
import br.com.investlog.server.typelists.rest.payloads.TypeResponse
import br.com.investlog.server.wallets.rest.payloads.WalletKind
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import org.junit.jupiter.api.Order
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.returnResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
```

Add a new `@Order(7)` test after the `@Order(6)` test (note: this test class has no `@BeforeAll`/`PER_CLASS`, so it builds its own fixture data inline, following the existing `createWallet(...)` helper pattern already in this file):

```kotlin
    @Test
    @Order(6)
    fun `returns 400 when kind is missing`() {
        restTestClient.post()
            .uri("/private/v1/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"X","currency":"BRL"}""")
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(7)
    fun `wallet response includes currentValue and gain computed from its holdings`() {
        val wallet = createWallet("Wallet With Holdings", WalletKind.STOCKS, "BRL")

        val stockTypeId = restTestClient.post()
            .uri("/private/v1/stock-types")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"name":"Wallet Gain Test Type"}""")
            .exchange()
            .returnResult<TypeResponse>()
            .responseBody!!
            .id

        restTestClient.post()
            .uri("/private/v1/wallets/${wallet.id}/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"stockTypeId":"$stockTypeId","ticker":"WGAIN3",
                   "currentPrice":60.00,
                   "lot":{"lotDate":"2025-01-01","quantity":10,"price":50.00}}"""
            )
            .exchange()
            .expectStatus().isCreated()

        restTestClient.get()
            .uri("/private/v1/wallets/${wallet.id}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.totalInvested").isEqualTo(500.0)
            .jsonPath("$.currentValue").isEqualTo(600.0)
            .jsonPath("$.gain").isEqualTo(100.0)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "br.com.investlog.server.wallets.rest.controllers.WalletControllerTest"`
Expected: FAIL — `jsonPath("$.currentValue")` does not exist in the current `WalletResponse` JSON.

- [ ] **Step 3: Add the new fields to `WalletResponse`**

Current `server/src/main/kotlin/br/com/investlog/server/wallets/rest/payloads/WalletResponse.kt`:

```kotlin
package br.com.investlog.server.wallets.rest.payloads

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class WalletResponse(
    val id: UUID,
    val name: String,
    val kind: WalletKind,
    val currency: String,
    val holdingCount: Int,
    val totalInvested: BigDecimal,
    val createdAt: OffsetDateTime,
)
```

Replace with:

```kotlin
package br.com.investlog.server.wallets.rest.payloads

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class WalletResponse(
    val id: UUID,
    val name: String,
    val kind: WalletKind,
    val currency: String,
    val holdingCount: Int,
    val totalInvested: BigDecimal,
    val currentValue: BigDecimal?,
    val gain: BigDecimal?,
    val gainPct: BigDecimal?,
    val createdAt: OffsetDateTime,
)
```

- [ ] **Step 4: Add a `currentValueField()` and compute gain in `WalletRepository`**

Current `server/src/main/kotlin/br/com/investlog/server/wallets/domain/repositories/WalletRepository.kt`:

```kotlin
package br.com.investlog.server.wallets.domain.repositories

import br.com.investlog.server.jooq.finances.tables.references.HOLDINGS_OVERVIEW
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.shared.persistence.pagedModelOf
import br.com.investlog.server.wallets.rest.payloads.WalletKind
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import br.com.investlog.server.jooq.finances.enums.WalletKind as JooqWalletKind

@Repository
class WalletRepository(private val dsl: DSLContext) {

    fun findAll(userId: Long, pageable: Pageable): PagedModel<WalletResponse> {
        val content = dsl.select(
            WALLETS.EXTERNAL_ID, WALLETS.NAME, WALLETS.KIND, WALLETS.CURRENCY, WALLETS.CREATED_AT,
            holdingCountField(), totalInvestedField(),
        )
            .from(WALLETS)
            .where(WALLETS.USER_ID.eq(userId))
            .orderBy(WALLETS.CREATED_AT.desc())
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch { record -> record.toResponse() }

        val total = dsl.fetchCount(dsl.selectFrom(WALLETS).where(WALLETS.USER_ID.eq(userId)))
        return pagedModelOf(content, pageable, total.toLong())
    }

    fun create(userId: Long, name: String, kind: WalletKind, currency: String): WalletResponse {
        val wallet = dsl.insertInto(WALLETS)
            .set(WALLETS.USER_ID, userId)
            .set(WALLETS.NAME, name)
            .set(WALLETS.KIND, JooqWalletKind.valueOf(kind.name))
            .set(WALLETS.CURRENCY, currency)
            .returning()
            .fetchSingle()

        return dsl.select(
            WALLETS.EXTERNAL_ID, WALLETS.NAME, WALLETS.KIND, WALLETS.CURRENCY, WALLETS.CREATED_AT,
            holdingCountField(), totalInvestedField(),
        )
            .from(WALLETS)
            .where(WALLETS.ID.eq(wallet.id))
            .fetchSingle { record -> record.toResponse() }
    }

    fun findByExternalId(userId: Long, externalId: UUID): WalletResponse? =
        dsl.select(
            WALLETS.EXTERNAL_ID, WALLETS.NAME, WALLETS.KIND, WALLETS.CURRENCY, WALLETS.CREATED_AT,
            holdingCountField(), totalInvestedField(),
        )
            .from(WALLETS)
            .where(WALLETS.USER_ID.eq(userId))
            .and(WALLETS.EXTERNAL_ID.eq(externalId))
            .fetchOne { record -> record.toResponse() }

    fun findInternalId(userId: Long, externalId: UUID): Long? =
        dsl.select(WALLETS.ID)
            .from(WALLETS)
            .where(WALLETS.USER_ID.eq(userId))
            .and(WALLETS.EXTERNAL_ID.eq(externalId))
            .fetchOne(WALLETS.ID)

    fun update(userId: Long, externalId: UUID, name: String): WalletResponse? {
        val updated = dsl.update(WALLETS)
            .set(WALLETS.NAME, name)
            .set(WALLETS.UPDATED_AT, OffsetDateTime.now())
            .where(WALLETS.USER_ID.eq(userId))
            .and(WALLETS.EXTERNAL_ID.eq(externalId))
            .returning(WALLETS.ID)
            .fetchOne() ?: return null

        return dsl.select(
            WALLETS.EXTERNAL_ID, WALLETS.NAME, WALLETS.KIND, WALLETS.CURRENCY, WALLETS.CREATED_AT,
            holdingCountField(), totalInvestedField(),
        )
            .from(WALLETS)
            .where(WALLETS.ID.eq(updated.id))
            .fetchSingle { record -> record.toResponse() }
    }

    fun deleteByExternalId(userId: Long, externalId: UUID): Int =
        dsl.deleteFrom(WALLETS)
            .where(WALLETS.USER_ID.eq(userId))
            .and(WALLETS.EXTERNAL_ID.eq(externalId))
            .execute()

    private fun holdingCountField() =
        DSL.field(
            DSL.selectCount()
                .from(HOLDINGS_OVERVIEW)
                .where(HOLDINGS_OVERVIEW.WALLET_ID.eq(WALLETS.ID))
        ).`as`("holding_count")

    private fun totalInvestedField() =
        DSL.field(
            DSL.select(DSL.coalesce(DSL.sum(HOLDINGS_OVERVIEW.COST_BASIS), BigDecimal.ZERO))
                .from(HOLDINGS_OVERVIEW)
                .where(HOLDINGS_OVERVIEW.WALLET_ID.eq(WALLETS.ID))
        ).`as`("total_invested")

    private fun org.jooq.Record.toResponse() = WalletResponse(
        id = get(WALLETS.EXTERNAL_ID)!!,
        name = get(WALLETS.NAME)!!,
        kind = WalletKind.fromText(get(WALLETS.KIND)!!.literal),
        currency = get(WALLETS.CURRENCY)!!,
        holdingCount = get("holding_count", Int::class.java) ?: 0,
        totalInvested = get("total_invested", BigDecimal::class.java) ?: BigDecimal.ZERO,
        createdAt = get(WALLETS.CREATED_AT)!!,
    )
}
```

Replace it with:

```kotlin
package br.com.investlog.server.wallets.domain.repositories

import br.com.investlog.server.jooq.finances.tables.references.HOLDINGS_OVERVIEW
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.shared.persistence.pagedModelOf
import br.com.investlog.server.wallets.rest.payloads.WalletKind
import br.com.investlog.server.wallets.rest.payloads.WalletResponse
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime
import java.util.UUID
import br.com.investlog.server.jooq.finances.enums.WalletKind as JooqWalletKind

@Repository
class WalletRepository(private val dsl: DSLContext) {

    fun findAll(userId: Long, pageable: Pageable): PagedModel<WalletResponse> {
        val content = dsl.select(
            WALLETS.EXTERNAL_ID, WALLETS.NAME, WALLETS.KIND, WALLETS.CURRENCY, WALLETS.CREATED_AT,
            holdingCountField(), totalInvestedField(), currentValueField(),
        )
            .from(WALLETS)
            .where(WALLETS.USER_ID.eq(userId))
            .orderBy(WALLETS.CREATED_AT.desc())
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch { record -> record.toResponse() }

        val total = dsl.fetchCount(dsl.selectFrom(WALLETS).where(WALLETS.USER_ID.eq(userId)))
        return pagedModelOf(content, pageable, total.toLong())
    }

    fun create(userId: Long, name: String, kind: WalletKind, currency: String): WalletResponse {
        val wallet = dsl.insertInto(WALLETS)
            .set(WALLETS.USER_ID, userId)
            .set(WALLETS.NAME, name)
            .set(WALLETS.KIND, JooqWalletKind.valueOf(kind.name))
            .set(WALLETS.CURRENCY, currency)
            .returning()
            .fetchSingle()

        return dsl.select(
            WALLETS.EXTERNAL_ID, WALLETS.NAME, WALLETS.KIND, WALLETS.CURRENCY, WALLETS.CREATED_AT,
            holdingCountField(), totalInvestedField(), currentValueField(),
        )
            .from(WALLETS)
            .where(WALLETS.ID.eq(wallet.id))
            .fetchSingle { record -> record.toResponse() }
    }

    fun findByExternalId(userId: Long, externalId: UUID): WalletResponse? =
        dsl.select(
            WALLETS.EXTERNAL_ID, WALLETS.NAME, WALLETS.KIND, WALLETS.CURRENCY, WALLETS.CREATED_AT,
            holdingCountField(), totalInvestedField(), currentValueField(),
        )
            .from(WALLETS)
            .where(WALLETS.USER_ID.eq(userId))
            .and(WALLETS.EXTERNAL_ID.eq(externalId))
            .fetchOne { record -> record.toResponse() }

    fun findInternalId(userId: Long, externalId: UUID): Long? =
        dsl.select(WALLETS.ID)
            .from(WALLETS)
            .where(WALLETS.USER_ID.eq(userId))
            .and(WALLETS.EXTERNAL_ID.eq(externalId))
            .fetchOne(WALLETS.ID)

    fun update(userId: Long, externalId: UUID, name: String): WalletResponse? {
        val updated = dsl.update(WALLETS)
            .set(WALLETS.NAME, name)
            .set(WALLETS.UPDATED_AT, OffsetDateTime.now())
            .where(WALLETS.USER_ID.eq(userId))
            .and(WALLETS.EXTERNAL_ID.eq(externalId))
            .returning(WALLETS.ID)
            .fetchOne() ?: return null

        return dsl.select(
            WALLETS.EXTERNAL_ID, WALLETS.NAME, WALLETS.KIND, WALLETS.CURRENCY, WALLETS.CREATED_AT,
            holdingCountField(), totalInvestedField(), currentValueField(),
        )
            .from(WALLETS)
            .where(WALLETS.ID.eq(updated.id))
            .fetchSingle { record -> record.toResponse() }
    }

    fun deleteByExternalId(userId: Long, externalId: UUID): Int =
        dsl.deleteFrom(WALLETS)
            .where(WALLETS.USER_ID.eq(userId))
            .and(WALLETS.EXTERNAL_ID.eq(externalId))
            .execute()

    private fun holdingCountField() =
        DSL.field(
            DSL.selectCount()
                .from(HOLDINGS_OVERVIEW)
                .where(HOLDINGS_OVERVIEW.WALLET_ID.eq(WALLETS.ID))
        ).`as`("holding_count")

    private fun totalInvestedField() =
        DSL.field(
            DSL.select(DSL.coalesce(DSL.sum(HOLDINGS_OVERVIEW.COST_BASIS), BigDecimal.ZERO))
                .from(HOLDINGS_OVERVIEW)
                .where(HOLDINGS_OVERVIEW.WALLET_ID.eq(WALLETS.ID))
        ).`as`("total_invested")

    private fun currentValueField() =
        DSL.field(
            DSL.select(DSL.sum(HOLDINGS_OVERVIEW.CURRENT_VALUE))
                .from(HOLDINGS_OVERVIEW)
                .where(HOLDINGS_OVERVIEW.WALLET_ID.eq(WALLETS.ID))
        ).`as`("current_value")

    private fun org.jooq.Record.toResponse(): WalletResponse {
        val totalInvested = get("total_invested", BigDecimal::class.java) ?: BigDecimal.ZERO
        val currentValue = get("current_value", BigDecimal::class.java)
        val gain = currentValue?.let { it - totalInvested }
        val gainPct = if (gain != null && totalInvested.signum() != 0) {
            gain.divide(totalInvested, 10, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
        } else null

        return WalletResponse(
            id = get(WALLETS.EXTERNAL_ID)!!,
            name = get(WALLETS.NAME)!!,
            kind = WalletKind.fromText(get(WALLETS.KIND)!!.literal),
            currency = get(WALLETS.CURRENCY)!!,
            holdingCount = get("holding_count", Int::class.java) ?: 0,
            totalInvested = totalInvested,
            currentValue = currentValue,
            gain = gain,
            gainPct = gainPct,
            createdAt = get(WALLETS.CREATED_AT)!!,
        )
    }
}
```

`currentValueField()` is deliberately **not** wrapped in `DSL.coalesce(...)`: `SUM()` over an all-NULL group returns `NULL`, which correctly means "this wallet has no holdings with a tracked current value" — the same semantics `HoldingsOverviewRepository.kt` already uses per-row for `gain`/`gainPct`.

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "br.com.investlog.server.wallets.rest.controllers.WalletControllerTest"`
Expected: PASS (7 tests)

- [ ] **Step 6: Run the full backend test suite to catch any other consumer of `WalletResponse`**

Run: `./gradlew test`
Expected: PASS — `WalletResponse` is a `data class`, so adding fields is source-compatible everywhere it's constructed (only `WalletRepository.toResponse()` constructs it) and everywhere it's read (Jackson serialization picks up the new fields automatically).

- [ ] **Step 7: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/wallets/rest/payloads/WalletResponse.kt server/src/main/kotlin/br/com/investlog/server/wallets/domain/repositories/WalletRepository.kt server/src/test/kotlin/br/com/investlog/server/wallets/rest/controllers/WalletControllerTest.kt
git commit -m "feat: include current value and gain in the wallet response"
```

---

### Task 10: Item 9 frontend — show gain/result on wallet cards

**Files:**
- Modify: `client/src/types.ts`
- Modify: `client/src/views/WalletsView.vue`

**Interfaces:**
- Consumes: `WalletResponse.currentValue: number | null`, `.gain: number | null`, `.gainPct: number | null` (from Task 9). `GainChip.vue` (existing component, same as Task 4).
- Produces: nothing consumed by other tasks.

- [ ] **Step 1: Add the new fields to the `WalletResponse` TypeScript type**

Current `client/src/types.ts`:

```ts
export interface WalletResponse {
  id: string
  name: string
  kind: WalletKind
  currency: string
  holdingCount: number
  totalInvested: number
  createdAt: string
}
```

Replace with:

```ts
export interface WalletResponse {
  id: string
  name: string
  kind: WalletKind
  currency: string
  holdingCount: number
  totalInvested: number
  currentValue: number | null
  gain: number | null
  gainPct: number | null
  createdAt: string
}
```

- [ ] **Step 2: Import `GainChip` in `WalletsView.vue`**

Current:

```ts
import AppIcon, { type IconName } from '@/components/AppIcon.vue'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
```

Replace with:

```ts
import AppIcon, { type IconName } from '@/components/AppIcon.vue'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import GainChip from '@/components/ui/GainChip.vue'
```

- [ ] **Step 3: Render the gain chip under the invested amount**

Current:

```html
          <div class="wallet-invested">
            <div class="wi-value">{{ fmt.money(wallet.totalInvested, wallet.currency) }}</div>
          </div>
```

Replace with:

```html
          <div class="wallet-invested">
            <div class="wi-value">{{ fmt.money(wallet.totalInvested, wallet.currency) }}</div>
            <div class="wi-base">
              <GainChip :value="wallet.gain" :pct="wallet.gainPct" :cur="wallet.currency" />
            </div>
          </div>
```

`.wi-base` already exists in `styles.css` (`font-size: 12px; color: var(--text-muted); margin-top: 2px;`) and composes fine as a wrapper around `GainChip`, which carries its own `.gl-up`/`.gl-down`/`.gl-flat` coloring.

- [ ] **Step 4: Verify with type-check**

Run: `npm run type-check`
Expected: no errors.

- [ ] **Step 5: Manual verification**

With both `./gradlew bootRun` (backend) and `npm run dev` (frontend) running, visit `/wallets`. Each wallet card should show a gain/loss chip under the invested amount. Create a wallet with no holdings yet and confirm it shows "—" (empty state) rather than "+R$ 0,00".

- [ ] **Step 6: Commit**

```bash
git add client/src/types.ts client/src/views/WalletsView.vue
git commit -m "feat: show gain/result on wallet cards"
```

---

## Phase 4 — Backend + frontend new query params (items 6/12, 8)

### Task 11: Backend — `typeLabel`, `search` and `sort` query params on `GET /holdings`

**Files:**
- Modify: `server/src/main/kotlin/br/com/investlog/server/holdingsoverview/rest/controllers/HoldingsOverviewController.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/holdingsoverview/domain/services/HoldingsOverviewService.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/holdingsoverview/domain/repositories/HoldingsOverviewRepository.kt`
- Test: `server/src/test/kotlin/br/com/investlog/server/holdingsoverview/rest/controllers/HoldingsOverviewControllerTest.kt`

**Interfaces:**
- Consumes: `org.springframework.data.domain.Pageable` (already injected by the controller; `pageable.sort` is a Spring `Sort` of `Sort.Order` elements with `.property` and `.isAscending`).
- Produces: `GET /private/v1/holdings?typeLabel=...&search=...&sort=invested,asc` — `typeLabel` is an exact, case-sensitive match against `overview.TYPE_LABEL`; `search` is a case-insensitive substring match against `overview.NAME` OR `overview.TICKER`; `sort` accepts one of `wallet|price|invested|current|gain` with `,asc`/`,desc`, defaulting to `invested,desc` (i.e. `overview.COST_BASIS.desc()`) when absent or unrecognized. Consumed by Task 12 (`holdings.ts` + `holdingsList.ts`).

- [ ] **Step 1: Write the failing tests**

In `server/src/test/kotlin/br/com/investlog/server/holdingsoverview/rest/controllers/HoldingsOverviewControllerTest.kt`, current last test:

```kotlin
    @Test
    @Order(4)
    fun `GET holdings with invalid kind returns 400`() {
        restTestClient.get()
            .uri("/private/v1/holdings?kind=invalid")
            .exchange()
            .expectStatus().isBadRequest()
    }
}
```

Replace with (adds five new `@Order` tests after `@Order(4)`):

```kotlin
    @Test
    @Order(4)
    fun `GET holdings with invalid kind returns 400`() {
        restTestClient.get()
            .uri("/private/v1/holdings?kind=invalid")
            .exchange()
            .expectStatus().isBadRequest()
    }

    @Test
    @Order(5)
    fun `GET holdings with matching typeLabel includes the holding`() {
        restTestClient.get()
            .uri("/private/v1/holdings?typeLabel=Overview%20Test%20Type")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[?(@.id == '$holdingId')]").isNotEmpty()
    }

    @Test
    @Order(6)
    fun `GET holdings with non-matching typeLabel excludes the holding`() {
        restTestClient.get()
            .uri("/private/v1/holdings?typeLabel=Nonexistent%20Type")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[?(@.id == '$holdingId')]").isEmpty()
    }

    @Test
    @Order(7)
    fun `GET holdings with matching search includes the holding`() {
        restTestClient.get()
            .uri("/private/v1/holdings?search=ovtst")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[?(@.id == '$holdingId')]").isNotEmpty()
    }

    @Test
    @Order(8)
    fun `GET holdings with non-matching search excludes the holding`() {
        restTestClient.get()
            .uri("/private/v1/holdings?search=nomatch")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[?(@.id == '$holdingId')]").isEmpty()
    }

    @Test
    @Order(9)
    fun `GET holdings sorted by invested ascending orders the smaller position first`() {
        // costBasis = 1 * 10.00 = 10.00, versus the @BeforeAll fixture's OVTST3 at 450.00 —
        // scoping by typeLabel to just these two stockTypeId siblings keeps content[0]
        // deterministic regardless of any other holdings the dev user already has.
        restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """{"stockTypeId":"$stockTypeId","ticker":"OVSML3",
                   "currentPrice":12.00,
                   "lot":{"lotDate":"2025-02-01","quantity":1,"price":10.00}}"""
            )
            .exchange()
            .expectStatus().isCreated()

        restTestClient.get()
            .uri("/private/v1/holdings?typeLabel=Overview%20Test%20Type&sort=invested,asc")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content[0].ticker").isEqualTo("OVSML3")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "br.com.investlog.server.holdingsoverview.rest.controllers.HoldingsOverviewControllerTest"`
Expected: FAIL — `typeLabel`/`search`/`sort` are not yet recognized params, so all five new tests fail (the filters are silently ignored, so `Order(6)` and `Order(8)` — the exclusion tests — fail because the holding is still included; `Order(5)`, `Order(7)` may coincidentally pass already since the unfiltered query already includes the holding, but `Order(9)`'s explicit sort assertion fails since the default order is unaffected by `?sort=`).

- [ ] **Step 3: Extend the controller**

Current `HoldingsOverviewController.kt`:

```kotlin
package br.com.investlog.server.holdingsoverview.rest.controllers

import br.com.investlog.server.holdingsoverview.domain.services.HoldingsOverviewService
import br.com.investlog.server.holdingsoverview.rest.payloads.HoldingRowResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/holdings")
class HoldingsOverviewController(private val holdingsOverviewService: HoldingsOverviewService) {

    @GetMapping
    fun findAll(@RequestParam(required = false) kind: String?, pageable: Pageable): PagedModel<HoldingRowResponse> =
        holdingsOverviewService.findAll(kind, pageable)
}
```

Replace with:

```kotlin
package br.com.investlog.server.holdingsoverview.rest.controllers

import br.com.investlog.server.holdingsoverview.domain.services.HoldingsOverviewService
import br.com.investlog.server.holdingsoverview.rest.payloads.HoldingRowResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/holdings")
class HoldingsOverviewController(private val holdingsOverviewService: HoldingsOverviewService) {

    @GetMapping
    fun findAll(
        @RequestParam(required = false) kind: String?,
        @RequestParam(required = false) typeLabel: String?,
        @RequestParam(required = false) search: String?,
        pageable: Pageable,
    ): PagedModel<HoldingRowResponse> = holdingsOverviewService.findAll(kind, typeLabel, search, pageable)
}
```

- [ ] **Step 4: Extend the service**

Current `HoldingsOverviewService.kt`:

```kotlin
package br.com.investlog.server.holdingsoverview.domain.services

import br.com.investlog.server.holdingsoverview.domain.repositories.HoldingsOverviewRepository
import br.com.investlog.server.holdingsoverview.rest.payloads.HoldingRowResponse
import br.com.investlog.server.shared.security.CurrentUserProvider
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import br.com.investlog.server.jooq.finances.enums.WalletKind as JooqWalletKind

@Service
class HoldingsOverviewService(
    private val currentUserProvider: CurrentUserProvider,
    private val holdingsOverviewRepository: HoldingsOverviewRepository,
) {

    fun findAll(kind: String?, pageable: Pageable): PagedModel<HoldingRowResponse> {
        val userId = currentUserProvider.getCurrentUser().id
        val jooqKind = kind?.let {
            try {
                JooqWalletKind.valueOf(it)
            } catch (ex: IllegalArgumentException) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid kind: $it", ex)
            }
        }
        return holdingsOverviewRepository.findAll(userId, jooqKind, pageable)
    }
}
```

Replace with:

```kotlin
package br.com.investlog.server.holdingsoverview.domain.services

import br.com.investlog.server.holdingsoverview.domain.repositories.HoldingsOverviewRepository
import br.com.investlog.server.holdingsoverview.rest.payloads.HoldingRowResponse
import br.com.investlog.server.shared.security.CurrentUserProvider
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import br.com.investlog.server.jooq.finances.enums.WalletKind as JooqWalletKind

@Service
class HoldingsOverviewService(
    private val currentUserProvider: CurrentUserProvider,
    private val holdingsOverviewRepository: HoldingsOverviewRepository,
) {

    fun findAll(kind: String?, typeLabel: String?, search: String?, pageable: Pageable): PagedModel<HoldingRowResponse> {
        val userId = currentUserProvider.getCurrentUser().id
        val jooqKind = kind?.let {
            try {
                JooqWalletKind.valueOf(it)
            } catch (ex: IllegalArgumentException) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid kind: $it", ex)
            }
        }
        return holdingsOverviewRepository.findAll(userId, jooqKind, typeLabel, search, pageable)
    }
}
```

- [ ] **Step 5: Extend the repository with the new conditions and dynamic sort**

Current `HoldingsOverviewRepository.kt`:

```kotlin
package br.com.investlog.server.holdingsoverview.domain.repositories

import br.com.investlog.server.holdingsoverview.rest.payloads.HoldingRowResponse
import br.com.investlog.server.jooq.finances.tables.references.HOLDINGS_OVERVIEW
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.shared.persistence.pagedModelOf
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.math.RoundingMode
import br.com.investlog.server.jooq.finances.enums.WalletKind as JooqWalletKind

@Repository
class HoldingsOverviewRepository(private val dsl: DSLContext) {

    fun findAll(userId: Long, kind: JooqWalletKind?, pageable: Pageable): PagedModel<HoldingRowResponse> {
        val wallets = WALLETS.`as`("wallets")
        val overview = HOLDINGS_OVERVIEW.`as`("overview")

        val baseCondition = wallets.USER_ID.eq(userId)
        val kindCondition = if (kind != null) overview.KIND.eq(kind) else DSL.noCondition()

        val content = dsl.select(
            overview.EXTERNAL_ID,
            overview.KIND,
            overview.NAME,
            overview.TICKER,
            overview.TYPE_LABEL,
            wallets.EXTERNAL_ID,
            wallets.NAME,
            wallets.CURRENCY,
            overview.QUANTITY,
            overview.COST_BASIS,
            overview.CURRENT_PRICE,
            overview.CURRENT_VALUE,
        )
            .from(overview)
            .join(wallets).on(wallets.ID.eq(overview.WALLET_ID))
            .where(baseCondition).and(kindCondition)
            .orderBy(overview.COST_BASIS.desc())
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch { record ->
                val costBasis = record.get(overview.COST_BASIS) ?: BigDecimal.ZERO
                val currentValue = record.get(overview.CURRENT_VALUE)
                val gain = currentValue?.let { it - costBasis }
                val gainPct = if (gain != null && costBasis.signum() != 0) {
                    gain.divide(costBasis, 10, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
                } else null

                HoldingRowResponse(
                    id = record.get(overview.EXTERNAL_ID)!!,
                    kind = record.get(overview.KIND)!!.literal,
                    name = record.get(overview.NAME)!!,
                    ticker = record.get(overview.TICKER),
                    typeLabel = record.get(overview.TYPE_LABEL),
                    walletId = record.get(wallets.EXTERNAL_ID)!!,
                    walletName = record.get(wallets.NAME)!!,
                    walletCurrency = record.get(wallets.CURRENCY)!!,
                    quantity = record.get(overview.QUANTITY),
                    costBasis = costBasis,
                    currentPrice = record.get(overview.CURRENT_PRICE),
                    currentValue = currentValue,
                    gain = gain,
                    gainPct = gainPct,
                )
            }

        val total = dsl.fetchCount(
            dsl.select(DSL.one())
                .from(overview)
                .join(wallets).on(wallets.ID.eq(overview.WALLET_ID))
                .where(baseCondition).and(kindCondition)
        )

        return pagedModelOf(content, pageable, total.toLong())
    }
}
```

Replace it with:

```kotlin
package br.com.investlog.server.holdingsoverview.domain.repositories

import br.com.investlog.server.holdingsoverview.rest.payloads.HoldingRowResponse
import br.com.investlog.server.jooq.finances.tables.references.HOLDINGS_OVERVIEW
import br.com.investlog.server.jooq.finances.tables.references.WALLETS
import br.com.investlog.server.shared.persistence.pagedModelOf
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.SortField
import org.jooq.impl.DSL
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PagedModel
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.math.RoundingMode
import br.com.investlog.server.jooq.finances.enums.WalletKind as JooqWalletKind

@Repository
class HoldingsOverviewRepository(private val dsl: DSLContext) {

    fun findAll(
        userId: Long,
        kind: JooqWalletKind?,
        typeLabel: String?,
        search: String?,
        pageable: Pageable,
    ): PagedModel<HoldingRowResponse> {
        val wallets = WALLETS.`as`("wallets")
        val overview = HOLDINGS_OVERVIEW.`as`("overview")

        val baseCondition = wallets.USER_ID.eq(userId)
        val kindCondition = if (kind != null) overview.KIND.eq(kind) else DSL.noCondition()
        val typeLabelCondition = if (typeLabel != null) overview.TYPE_LABEL.eq(typeLabel) else DSL.noCondition()
        val searchCondition = if (!search.isNullOrBlank()) {
            overview.NAME.likeIgnoreCase("%$search%").or(overview.TICKER.likeIgnoreCase("%$search%"))
        } else {
            DSL.noCondition()
        }

        val sortFields: List<SortField<*>> = pageable.sort.mapNotNull { order ->
            val field: Field<*>? = when (order.property) {
                "wallet" -> wallets.NAME
                "price" -> overview.CURRENT_PRICE
                "invested" -> overview.COST_BASIS
                "current" -> overview.CURRENT_VALUE
                "gain" -> overview.CURRENT_VALUE.sub(overview.COST_BASIS)
                else -> null
            }
            field?.let { if (order.isAscending) it.asc().nullsLast() else it.desc().nullsLast() }
        }.ifEmpty { listOf(overview.COST_BASIS.desc().nullsLast()) }

        val content = dsl.select(
            overview.EXTERNAL_ID,
            overview.KIND,
            overview.NAME,
            overview.TICKER,
            overview.TYPE_LABEL,
            wallets.EXTERNAL_ID,
            wallets.NAME,
            wallets.CURRENCY,
            overview.QUANTITY,
            overview.COST_BASIS,
            overview.CURRENT_PRICE,
            overview.CURRENT_VALUE,
        )
            .from(overview)
            .join(wallets).on(wallets.ID.eq(overview.WALLET_ID))
            .where(baseCondition).and(kindCondition).and(typeLabelCondition).and(searchCondition)
            .orderBy(sortFields)
            .limit(pageable.pageSize)
            .offset(pageable.offset.toInt())
            .fetch { record ->
                val costBasis = record.get(overview.COST_BASIS) ?: BigDecimal.ZERO
                val currentValue = record.get(overview.CURRENT_VALUE)
                val gain = currentValue?.let { it - costBasis }
                val gainPct = if (gain != null && costBasis.signum() != 0) {
                    gain.divide(costBasis, 10, RoundingMode.HALF_UP).multiply(BigDecimal("100"))
                } else null

                HoldingRowResponse(
                    id = record.get(overview.EXTERNAL_ID)!!,
                    kind = record.get(overview.KIND)!!.literal,
                    name = record.get(overview.NAME)!!,
                    ticker = record.get(overview.TICKER),
                    typeLabel = record.get(overview.TYPE_LABEL),
                    walletId = record.get(wallets.EXTERNAL_ID)!!,
                    walletName = record.get(wallets.NAME)!!,
                    walletCurrency = record.get(wallets.CURRENCY)!!,
                    quantity = record.get(overview.QUANTITY),
                    costBasis = costBasis,
                    currentPrice = record.get(overview.CURRENT_PRICE),
                    currentValue = currentValue,
                    gain = gain,
                    gainPct = gainPct,
                )
            }

        val total = dsl.fetchCount(
            dsl.select(DSL.one())
                .from(overview)
                .join(wallets).on(wallets.ID.eq(overview.WALLET_ID))
                .where(baseCondition).and(kindCondition).and(typeLabelCondition).and(searchCondition)
        )

        return pagedModelOf(content, pageable, total.toLong())
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew test --tests "br.com.investlog.server.holdingsoverview.rest.controllers.HoldingsOverviewControllerTest"`
Expected: PASS (9 tests)

- [ ] **Step 7: Run the full backend test suite**

Run: `./gradlew test`
Expected: PASS — both call sites of `HoldingsOverviewRepository.findAll` (only `HoldingsOverviewService`) and `HoldingsOverviewService.findAll` (only the controller) were updated together.

- [ ] **Step 8: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/holdingsoverview server/src/test/kotlin/br/com/investlog/server/holdingsoverview
git commit -m "feat: add typeLabel, search and sort query params to GET /holdings"
```

---

### Task 12: Frontend — extend `holdings.ts` and `holdingsList.ts` with `typeLabel`/`search`/`sort`

**Files:**
- Modify: `client/src/api/holdings.ts`
- Modify: `client/src/stores/holdingsList.ts`
- Test: `client/src/stores/holdingsList.spec.ts`

**Interfaces:**
- Consumes: `GET /holdings?typeLabel=&search=&sort=` (Task 11).
- Produces: `holdingsApi.findAll(params)` accepts optional `typeLabel`, `search`, `sort` string params. `useHoldingsListStore().loadKind(kind, pageNumber, options)` accepts an optional third argument `{ typeLabel?: string; search?: string; sort?: string }`; `refresh()` replays the last-used `typeLabel`/`search`/`sort` alongside the last `kind`/`page`. Consumed by Task 13 (`InvestmentsView.vue`).

- [ ] **Step 1: Write the failing tests**

In `client/src/stores/holdingsList.spec.ts`, current last two tests:

```ts
  it('loadKind with page number passes it to the API', async () => {
    vi.mocked(holdingsApiModule.holdingsApi.findAll).mockResolvedValue(makePagedResponse([]))

    const store = useHoldingsListStore()
    await store.loadKind('STOCKS', 2)

    expect(holdingsApiModule.holdingsApi.findAll).toHaveBeenCalledWith({
      kind: 'STOCKS',
      page: 2,
      size: 20,
    })
    expect(store.page).toBe(2)
  })

  it('refresh re-fetches with same kind and page', async () => {
    vi.mocked(holdingsApiModule.holdingsApi.findAll).mockResolvedValue(makePagedResponse([]))

    const store = useHoldingsListStore()
    await store.loadKind('CRYPTO', 1)
    await store.refresh()

    expect(holdingsApiModule.holdingsApi.findAll).toHaveBeenCalledTimes(2)
    expect(holdingsApiModule.holdingsApi.findAll).toHaveBeenLastCalledWith({
      kind: 'CRYPTO',
      page: 1,
      size: 20,
    })
  })
})
```

Replace with (adds two new tests after the `refresh` test; the existing four tests are unchanged and keep passing because Jest/Vitest's `toEqual`-based matchers ignore `undefined`-valued object keys, so the new `typeLabel`/`search`/`sort: undefined` keys in the actual call don't break their assertions):

```ts
  it('loadKind with page number passes it to the API', async () => {
    vi.mocked(holdingsApiModule.holdingsApi.findAll).mockResolvedValue(makePagedResponse([]))

    const store = useHoldingsListStore()
    await store.loadKind('STOCKS', 2)

    expect(holdingsApiModule.holdingsApi.findAll).toHaveBeenCalledWith({
      kind: 'STOCKS',
      page: 2,
      size: 20,
    })
    expect(store.page).toBe(2)
  })

  it('refresh re-fetches with same kind and page', async () => {
    vi.mocked(holdingsApiModule.holdingsApi.findAll).mockResolvedValue(makePagedResponse([]))

    const store = useHoldingsListStore()
    await store.loadKind('CRYPTO', 1)
    await store.refresh()

    expect(holdingsApiModule.holdingsApi.findAll).toHaveBeenCalledTimes(2)
    expect(holdingsApiModule.holdingsApi.findAll).toHaveBeenLastCalledWith({
      kind: 'CRYPTO',
      page: 1,
      size: 20,
    })
  })

  it('loadKind passes typeLabel, search and sort through to the API', async () => {
    vi.mocked(holdingsApiModule.holdingsApi.findAll).mockResolvedValue(makePagedResponse([]))

    const store = useHoldingsListStore()
    await store.loadKind('STOCKS', 0, { typeLabel: 'Ação ON', search: 'PETR', sort: 'invested,asc' })

    expect(holdingsApiModule.holdingsApi.findAll).toHaveBeenCalledWith({
      kind: 'STOCKS',
      typeLabel: 'Ação ON',
      search: 'PETR',
      sort: 'invested,asc',
      page: 0,
      size: 20,
    })
  })

  it('refresh re-fetches with the same typeLabel, search and sort', async () => {
    vi.mocked(holdingsApiModule.holdingsApi.findAll).mockResolvedValue(makePagedResponse([]))

    const store = useHoldingsListStore()
    await store.loadKind('STOCKS', 0, { typeLabel: 'Ação ON', search: 'PETR', sort: 'invested,asc' })
    await store.refresh()

    expect(holdingsApiModule.holdingsApi.findAll).toHaveBeenLastCalledWith({
      kind: 'STOCKS',
      typeLabel: 'Ação ON',
      search: 'PETR',
      sort: 'invested,asc',
      page: 0,
      size: 20,
    })
  })
})
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npm run test -- src/stores/holdingsList.spec.ts`
Expected: FAIL — the two new tests fail because `loadKind` currently ignores its third argument entirely, so the actual call to `findAll` has no `typeLabel`/`search`/`sort` keys at all... wait, it does have no third argument effect, the assertion expects those keys present with real values, so it fails with a mismatch (expected object has `typeLabel: 'Ação ON'`, actual call lacks meaningful values for it). The four pre-existing tests still pass.

- [ ] **Step 3: Extend `holdingsApi.findAll`'s params type**

Current `client/src/api/holdings.ts`:

```ts
export const holdingsApi = {
  findAll(params: { kind?: string; page?: number; size?: number }): Promise<PagedResponse<HoldingRow>> {
    return apiClient.get<PagedResponse<HoldingRow>>('/holdings', { params }).then((r) => r.data)
  },
```

Replace with:

```ts
export const holdingsApi = {
  findAll(params: {
    kind?: string
    typeLabel?: string
    search?: string
    sort?: string
    page?: number
    size?: number
  }): Promise<PagedResponse<HoldingRow>> {
    return apiClient.get<PagedResponse<HoldingRow>>('/holdings', { params }).then((r) => r.data)
  },
```

- [ ] **Step 4: Extend `holdingsList.ts`'s `loadKind`/`refresh`**

Current `client/src/stores/holdingsList.ts`:

```ts
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { holdingsApi } from '@/api/holdings'
import type { HoldingRow, WalletKind } from '@/types'

type KindFilter = 'all' | WalletKind

export const useHoldingsListStore = defineStore('holdingsList', () => {
  const rows = ref<HoldingRow[]>([])
  const currentKind = ref<KindFilter>('all')
  const page = ref(0)
  const pageSize = ref(20)
  const totalElements = ref(0)
  const totalPages = ref(0)
  const loading = ref(false)
  const loaded = ref(false)

  async function loadKind(kind: KindFilter, pageNumber = 0) {
    loading.value = true
    currentKind.value = kind
    page.value = pageNumber
    try {
      const result = await holdingsApi.findAll({
        kind: kind === 'all' ? undefined : kind,
        page: pageNumber,
        size: pageSize.value,
      })
      rows.value = result.content
      totalElements.value = result.page.totalElements
      totalPages.value = result.page.totalPages
    } finally {
      loading.value = false
      loaded.value = true
    }
  }

  async function refresh() {
    await loadKind(currentKind.value, page.value)
  }

  return {
    rows,
    currentKind,
    page,
    pageSize,
    totalElements,
    totalPages,
    loading,
    loaded,
    loadKind,
    refresh,
  }
})
```

Replace with:

```ts
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { holdingsApi } from '@/api/holdings'
import type { HoldingRow, WalletKind } from '@/types'

type KindFilter = 'all' | WalletKind

export interface HoldingsListOptions {
  typeLabel?: string
  search?: string
  sort?: string
}

export const useHoldingsListStore = defineStore('holdingsList', () => {
  const rows = ref<HoldingRow[]>([])
  const currentKind = ref<KindFilter>('all')
  const currentTypeLabel = ref<string | undefined>(undefined)
  const currentSearch = ref<string | undefined>(undefined)
  const currentSort = ref<string | undefined>(undefined)
  const page = ref(0)
  const pageSize = ref(20)
  const totalElements = ref(0)
  const totalPages = ref(0)
  const loading = ref(false)
  const loaded = ref(false)

  async function loadKind(kind: KindFilter, pageNumber = 0, options: HoldingsListOptions = {}) {
    loading.value = true
    currentKind.value = kind
    currentTypeLabel.value = options.typeLabel
    currentSearch.value = options.search
    currentSort.value = options.sort
    page.value = pageNumber
    try {
      const result = await holdingsApi.findAll({
        kind: kind === 'all' ? undefined : kind,
        typeLabel: options.typeLabel,
        search: options.search,
        sort: options.sort,
        page: pageNumber,
        size: pageSize.value,
      })
      rows.value = result.content
      totalElements.value = result.page.totalElements
      totalPages.value = result.page.totalPages
    } finally {
      loading.value = false
      loaded.value = true
    }
  }

  async function refresh() {
    await loadKind(currentKind.value, page.value, {
      typeLabel: currentTypeLabel.value,
      search: currentSearch.value,
      sort: currentSort.value,
    })
  }

  return {
    rows,
    currentKind,
    page,
    pageSize,
    totalElements,
    totalPages,
    loading,
    loaded,
    loadKind,
    refresh,
  }
})
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `npm run test -- src/stores/holdingsList.spec.ts`
Expected: PASS (6 tests)

- [ ] **Step 6: Verify with type-check**

Run: `npm run type-check`
Expected: no errors.

- [ ] **Step 7: Commit**

```bash
git add client/src/api/holdings.ts client/src/stores/holdingsList.ts client/src/stores/holdingsList.spec.ts
git commit -m "feat: support typeLabel, search and sort in the holdings list store"
```

---

### Task 13: Frontend — toolbar (type filter + search) and sortable column headers on the investments table

**Files:**
- Create: `client/src/components/ui/SortTh.vue`
- Modify: `client/src/views/InvestmentsView.vue`
- Modify: `client/src/assets/styles.css`

**Interfaces:**
- Consumes: `useHoldingsListStore().loadKind(kind, page, { typeLabel, search, sort })` (Task 12). `useTypesListStore()` (existing store, `stockTypes`/`fundTypes: AssetType[]`, `load()` idempotent). `AppIcon.vue`'s `chevronUp`/`chevronDown` icons (existing).
- Produces: `SortTh.vue` — a reusable sortable `<th>`: props `sortKey: string`, `activeKey: string | null`, `direction: 'asc' | 'desc'`, `align?: 'left' | 'right'` (default `'right'`); emits `toggle: [string]`; renders its slot content plus a chevron icon that's highlighted when `activeKey === sortKey`. This task supersedes Tasks 3 (item 13, eyebrow), 6 (item 4, URL sync) and 7 (item 5, default kind) for this same file — the full rewrite below already includes their effects, so it is safe to run regardless of whether those tasks already landed.

- [ ] **Step 1: Manual repro before the fix**

Run `npm run dev`, open `/investments`. Confirm there is no way to filter by sub-type (e.g. only "Ação ON" stocks), no search box, and clicking a column header does nothing.

- [ ] **Step 2: Create the `SortTh` component**

Create `client/src/components/ui/SortTh.vue`:

```html
<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from '@/components/AppIcon.vue'

const props = withDefaults(
  defineProps<{
    sortKey: string
    activeKey: string | null
    direction: 'asc' | 'desc'
    align?: 'left' | 'right'
  }>(),
  { align: 'right' },
)
const emit = defineEmits<{ toggle: [string] }>()

const isActive = computed(() => props.activeKey === props.sortKey)
</script>

<template>
  <th :class="align === 'right' ? 'c-num sort-th' : 'sort-th'" @click="emit('toggle', sortKey)">
    <span class="sort-th-inner">
      <slot />
      <AppIcon
        :name="isActive && direction === 'asc' ? 'chevronUp' : 'chevronDown'"
        :size="13"
        :class="isActive ? 'sort-icon-active' : 'sort-icon-idle'"
      />
    </span>
  </th>
</template>
```

- [ ] **Step 3: Add toolbar and sort-header CSS**

In `client/src/assets/styles.css`, current:

```css
.seg-tab.active {
    background: var(--surface);
    color: var(--text);
    box-shadow: var(--shadow);
}

/* ===== Tables ===== */
```

Replace with:

```css
.seg-tab.active {
    background: var(--surface);
    color: var(--text);
    box-shadow: var(--shadow);
}

.inv-toolbar {
    display: flex;
    gap: 10px;
    align-items: center;
    margin: 14px 0 4px;
}

.inv-toolbar .search-input {
    max-width: 280px;
}

/* ===== Tables ===== */
```

Current:

```css
.inv-table tbody tr:last-child td {
    border-bottom: none;
}

.name-cell {
```

Replace with:

```css
.inv-table tbody tr:last-child td {
    border-bottom: none;
}

.sort-th {
    cursor: pointer;
    user-select: none;
}

.sort-th-inner {
    display: inline-flex;
    align-items: center;
    gap: 3px;
}

.sort-icon-idle {
    opacity: .35;
}

.sort-icon-active {
    opacity: 1;
    color: var(--primary);
}

.name-cell {
```

- [ ] **Step 4: Replace `InvestmentsView.vue` with its final state**

Replace the entire contents of `client/src/views/InvestmentsView.vue` with:

```html
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon, { type IconName } from '@/components/AppIcon.vue'
import Card from '@/components/ui/Card.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import TickerBadge from '@/components/ui/TickerBadge.vue'
import GainChip from '@/components/ui/GainChip.vue'
import SortTh from '@/components/ui/SortTh.vue'
import HoldingDetailPanel from '@/components/investments/HoldingDetailPanel.vue'
import { useHoldingsListStore } from '@/stores/holdingsList'
import { useTypesListStore } from '@/stores/typesList'
import { useModals } from '@/composables/useModals'
import { fmt } from '@/composables/useFormat'
import { WALLET_TYPES, badgeColor } from '@/utils/walletTypes'
import type { HoldingRow, WalletKind } from '@/types'

type Filter = 'all' | WalletKind
type SortKey = 'wallet' | 'price' | 'invested' | 'current' | 'gain'

const holdingsListStore = useHoldingsListStore()
const typesListStore = useTypesListStore()
const route = useRoute()
const router = useRouter()
const modals = useModals()

const tabs: { key: Filter; label: string; icon: IconName }[] = [
  { key: 'all', label: 'Todos', icon: 'layers' },
  { key: 'STOCKS', label: 'Ações', icon: 'trendUp' },
  { key: 'CRYPTO', label: 'Cripto', icon: 'coins' },
  { key: 'FUNDS', label: 'Fundos', icon: 'building' },
]
const validFilters: Filter[] = ['all', 'STOCKS', 'CRYPTO', 'FUNDS']

function filterFromRoute(): Filter {
  const filterParam = route.query.filter
  return typeof filterParam === 'string' && validFilters.includes(filterParam as Filter)
    ? (filterParam as Filter)
    : 'all'
}

const activeFilter = ref<Filter>(filterFromRoute())
const openedDetails = ref<HoldingRow[]>([])
const typeLabelFilter = ref<string | undefined>(undefined)
const searchQuery = ref('')
const sortKey = ref<SortKey | null>(null)
const sortDirection = ref<'asc' | 'desc'>('desc')

let searchDebounceHandle: ReturnType<typeof setTimeout> | undefined

const typeLabelOptions = computed(() => {
  if (activeFilter.value === 'STOCKS') return typesListStore.stockTypes
  if (activeFilter.value === 'FUNDS') return typesListStore.fundTypes
  return []
})

function reload(pageNumber = 0) {
  holdingsListStore.loadKind(activeFilter.value, pageNumber, {
    typeLabel: typeLabelFilter.value,
    search: searchQuery.value.trim() || undefined,
    sort: sortKey.value ? `${sortKey.value},${sortDirection.value}` : undefined,
  })
}

watch(() => route.query.filter, () => {
  activeFilter.value = filterFromRoute()
  typeLabelFilter.value = undefined
  searchQuery.value = ''
  openedDetails.value = []
  reload(0)
})

onMounted(() => {
  typesListStore.load()
  reload(0)
})

function selectTab(filter: Filter) {
  if (filter === activeFilter.value) return
  router.replace({ query: { ...route.query, filter: filter === 'all' ? undefined : filter } })
}

function onTypeLabelChange(value: string) {
  typeLabelFilter.value = value || undefined
  openedDetails.value = []
  reload(0)
}

function onSearchChange(value: string) {
  searchQuery.value = value
  if (searchDebounceHandle) clearTimeout(searchDebounceHandle)
  searchDebounceHandle = setTimeout(() => {
    openedDetails.value = []
    reload(0)
  }, 300)
}

function toggleSort(key: string) {
  if (sortKey.value === key) {
    sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortKey.value = key as SortKey
    sortDirection.value = 'asc'
  }
  reload(0)
}

function onPageChange(newPage: number) {
  openedDetails.value = []
  reload(newPage - 1)
}

function toggleRow(row: HoldingRow) {
  const alreadyOpen = openedDetails.value.some((openRow) => openRow.id === row.id)
  openedDetails.value = alreadyOpen ? [] : [row]
}

function isOpen(row: HoldingRow): boolean {
  return openedDetails.value.some((openRow) => openRow.id === row.id)
}

function onHoldingDeleted() {
  openedDetails.value = []
  holdingsListStore.refresh()
}

function displayName(row: HoldingRow): string {
  return row.ticker ?? row.name
}

function subLabel(row: HoldingRow): string {
  if (row.kind === 'FUNDS') return row.typeLabel ?? 'Fundo'
  if (row.kind === 'CRYPTO') return 'Cripto'
  return row.typeLabel ?? 'Ação'
}

function openAddInvestment() {
  modals.openAddInvestment(activeFilter.value !== 'all' ? activeFilter.value : undefined)
}
</script>

<template>
  <div class="page">
    <div class="page-head page-head-row">
      <div>
        <h1 class="page-title">Investimentos</h1>
        <p class="page-desc">Aqui você gerencia seus investimentos</p>
      </div>
      <b-button type="is-primary" class="has-text-light" icon-left="plus" @click="openAddInvestment">
        Adicionar investimento
      </b-button>
    </div>

    <div class="seg-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="seg-tab"
        :class="{ active: tab.key === activeFilter }"
        @click="selectTab(tab.key)"
      >
        <AppIcon :name="tab.icon" :size="16" />{{ tab.label }}
      </button>
    </div>

    <div class="inv-toolbar">
      <b-select
        v-if="typeLabelOptions.length > 0"
        :model-value="typeLabelFilter ?? ''"
        @update:model-value="onTypeLabelChange"
      >
        <option value="">Todos os tipos</option>
        <option v-for="assetType in typeLabelOptions" :key="assetType.id" :value="assetType.name">
          {{ assetType.name }}
        </option>
      </b-select>
      <b-input
        class="search-input"
        :model-value="searchQuery"
        icon="magnify"
        placeholder="Buscar por nome ou ticker"
        @update:model-value="onSearchChange"
      />
    </div>

    <EmptyState
      v-if="holdingsListStore.loaded && !holdingsListStore.loading && holdingsListStore.rows.length === 0"
      icon="layers"
      title="Nenhum investimento aqui"
      text="Registre uma aquisição para vê-la no seu logbook."
    >
      <template #action>
        <b-button type="is-primary" class="has-text-light" icon-left="plus" @click="openAddInvestment">
          Adicionar investimento
        </b-button>
      </template>
    </EmptyState>

    <Card v-else class="table-card">
      <div class="table-wrap">
        <b-loading :is-full-page="false" :active="holdingsListStore.loading" />
        <div class="table-scroll">
          <table class="inv-table">
            <thead>
              <tr>
                <th>Investimento</th>
                <SortTh sort-key="wallet" :active-key="sortKey" :direction="sortDirection" align="left" @toggle="toggleSort">Carteira</SortTh>
                <th class="c-num">Qtd.</th>
                <SortTh sort-key="price" :active-key="sortKey" :direction="sortDirection" @toggle="toggleSort">Preço atual</SortTh>
                <SortTh sort-key="invested" :active-key="sortKey" :direction="sortDirection" @toggle="toggleSort">Investido</SortTh>
                <SortTh sort-key="current" :active-key="sortKey" :direction="sortDirection" @toggle="toggleSort">Valor atual</SortTh>
                <SortTh sort-key="gain" :active-key="sortKey" :direction="sortDirection" @toggle="toggleSort">Resultado</SortTh>
                <th class="c-act"></th>
              </tr>
            </thead>
            <tbody>
              <template v-for="row in holdingsListStore.rows" :key="row.id">
                <tr
                  class="inv-row"
                  :class="{ 'is-open': isOpen(row) }"
                  @click="toggleRow(row)"
                >
                  <td>
                    <div class="name-cell">
                      <TickerBadge :ticker="displayName(row)" :color="badgeColor(row.ticker, row.kind)" />
                      <div class="name-meta">
                        <div class="name-line">
                          <span class="t-ticker">{{ displayName(row) }}</span>
                          <span class="type-tag" :class="`tt-${row.kind.toLowerCase()}`">{{ subLabel(row) }}</span>
                        </div>
                        <div v-if="row.kind !== 'FUNDS' && row.name" class="t-name">{{ row.name }}</div>
                      </div>
                    </div>
                  </td>
                  <td>
                    <span class="wallet-ref">
                      <span class="wref-dot" :style="{ background: WALLET_TYPES[row.kind].accent }" />
                      {{ row.walletName }}
                    </span>
                  </td>
                  <td class="c-num">{{ row.quantity == null ? '—' : fmt.qty(row.quantity) }}</td>
                  <td class="c-num">
                    <template v-if="row.kind !== 'FUNDS' && row.currentPrice != null">{{ fmt.money(row.currentPrice, row.walletCurrency) }}</template>
                    <template v-else-if="row.kind === 'FUNDS' && row.currentValue != null">{{ fmt.money(row.currentValue, row.walletCurrency) }}</template>
                    <span v-else class="gl-empty">—</span>
                  </td>
                  <td class="c-num">
                    <div class="cell-strong">{{ fmt.money(row.costBasis, row.walletCurrency) }}</div>
                  </td>
                  <td class="c-num">
                    <span v-if="row.currentValue == null" class="gl-empty">—</span>
                    <template v-else>{{ fmt.money(row.currentValue, row.walletCurrency) }}</template>
                  </td>
                  <td class="c-num">
                    <GainChip :value="row.gain" :pct="row.gainPct" :cur="row.walletCurrency" />
                  </td>
                  <td class="c-act">
                    <span class="chev">
                      <AppIcon :name="isOpen(row) ? 'chevronUp' : 'chevronDown'" :size="18" />
                    </span>
                  </td>
                </tr>
                <tr v-if="isOpen(row)" class="detail-row">
                  <td colspan="8">
                    <HoldingDetailPanel
                      :row="row"
                      @deleted="onHoldingDeleted"
                      @position-added="holdingsListStore.refresh()"
                    />
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
        <div v-if="holdingsListStore.totalPages > 1" class="table-foot">
          <b-pagination
            :model-value="holdingsListStore.page + 1"
            :total="holdingsListStore.totalElements"
            :per-page="holdingsListStore.pageSize"
            order="is-right"
            simple
            @change="onPageChange"
          />
        </div>
      </div>
    </Card>
  </div>
</template>
```

- [ ] **Step 5: Verify with type-check**

Run: `npm run type-check`
Expected: no errors.

- [ ] **Step 6: Manual verification**

With both backend and `npm run dev` running, visit `/investments`. Click "Ações" tab, confirm a "Todos os tipos" select appears populated with stock types from Settings; pick one, confirm the table filters to only that type. Type into the search box, confirm the table filters (debounced) by name/ticker. Click "Investido" header, confirm the table re-sorts ascending with an up chevron highlighted; click again, confirm it flips to descending. Click "Carteira" header, confirm it sorts by wallet name. Confirm the previously-fixed items 4/5/13 behaviors (URL sync, modal default kind, no eyebrow) are all still present.

- [ ] **Step 7: Commit**

```bash
git add client/src/components/ui/SortTh.vue client/src/views/InvestmentsView.vue client/src/assets/styles.css
git commit -m "feat: add type/search toolbar and sortable headers to the investments table"
```

---

## Phase 5 — New PATCH endpoints (item 3)

### Task 14: Backend Stock — `PATCH .../stock-holdings/{holdingId}/lots/{lotId}` to edit a lot's date

**Files:**
- Create: `server/src/main/kotlin/br/com/investlog/server/stockholdings/rest/payloads/LotUpdateRequest.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/stockholdings/domain/repositories/StockLotRepository.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/stockholdings/domain/services/StockHoldingService.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/stockholdings/rest/controllers/StockHoldingController.kt`
- Test: `server/src/test/kotlin/br/com/investlog/server/stockholdings/rest/controllers/StockHoldingControllerTest.kt`

**Interfaces:**
- Consumes: `NotFoundException` (`br.com.investlog.server.shared.exceptions`, existing), `WalletService.resolveId` (existing), `StockHoldingRepository.findInternalId` (existing).
- Produces: `LotUpdateRequest(val lotDate: LocalDate)` in `br.com.investlog.server.stockholdings.rest.payloads` — reused as-is by Task 15 (Crypto). `StockLotRepository.updateLotDate(holdingInternalId: Long, externalId: UUID, lotDate: LocalDate): LotResponse?`. `StockHoldingService.updateLotDate(walletExternalId: UUID, holdingExternalId: UUID, lotExternalId: UUID, request: LotUpdateRequest): LotResponse`. `PATCH /private/v1/wallets/{walletId}/stock-holdings/{holdingId}/lots/{lotId}` — consumed by Task 17 (`holdings.ts`).

- [ ] **Step 1: Write the failing tests**

In `server/src/test/kotlin/br/com/investlog/server/stockholdings/rest/controllers/StockHoldingControllerTest.kt`, current last test:

```kotlin
    @Test
    @Order(7)
    fun `returns 404 for unknown wallet`() {
        restTestClient.get()
            .uri("/private/v1/wallets/${UUID.randomUUID()}/stock-holdings")
            .exchange()
            .expectStatus().isNotFound()
    }
}
```

Replace with:

```kotlin
    @Test
    @Order(7)
    fun `returns 404 for unknown wallet`() {
        restTestClient.get()
            .uri("/private/v1/wallets/${UUID.randomUUID()}/stock-holdings")
            .exchange()
            .expectStatus().isNotFound()
    }

    @Test
    @Order(8)
    fun `updates a lot's date`() {
        val h = createHolding("RENT3")
        val lot = restTestClient.post()
            .uri("/private/v1/wallets/$walletId/stock-holdings/${h.id}/lots")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"lotDate":"2024-02-01","quantity":20,"price":18.00}""")
            .exchange()
            .returnResult<LotResponse>()
            .responseBody!!

        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/stock-holdings/${h.id}/lots/${lot.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"lotDate":"2024-02-15"}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.lotDate").isEqualTo("2024-02-15")
    }

    @Test
    @Order(9)
    fun `returns 404 when updating an unknown lot's date`() {
        val h = createHolding("CSAN3")
        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/stock-holdings/${h.id}/lots/${UUID.randomUUID()}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"lotDate":"2024-02-15"}""")
            .exchange()
            .expectStatus().isNotFound()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "br.com.investlog.server.stockholdings.rest.controllers.StockHoldingControllerTest"`
Expected: FAIL — `PATCH .../lots/{lotId}` doesn't exist yet, so both new requests return 404/405 rather than the expected outcomes (the 404 test may even fail differently than expected since the endpoint itself doesn't exist).

- [ ] **Step 3: Create `LotUpdateRequest`**

Create `server/src/main/kotlin/br/com/investlog/server/stockholdings/rest/payloads/LotUpdateRequest.kt`:

```kotlin
package br.com.investlog.server.stockholdings.rest.payloads

import jakarta.validation.constraints.NotNull
import java.time.LocalDate

data class LotUpdateRequest(
    @field:NotNull val lotDate: LocalDate,
)
```

- [ ] **Step 4: Add `updateLotDate` to `StockLotRepository`**

Current `server/src/main/kotlin/br/com/investlog/server/stockholdings/domain/repositories/StockLotRepository.kt`:

```kotlin
package br.com.investlog.server.stockholdings.domain.repositories

import br.com.investlog.server.jooq.finances.tables.references.STOCK_LOTS
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class StockLotRepository(
    private val dsl: DSLContext
) {

    fun addLot(holdingInternalId: Long, request: LotCreateRequest): LotResponse {
        val rec = dsl.insertInto(STOCK_LOTS)
            .set(STOCK_LOTS.STOCK_HOLDING_ID, holdingInternalId)
            .set(STOCK_LOTS.LOT_DATE, request.lotDate)
            .set(STOCK_LOTS.QUANTITY, request.quantity)
            .set(STOCK_LOTS.PRICE, request.price)
            .returning()
            .fetchSingle()
        return LotResponse(
            id = rec.externalId!!,
            lotDate = rec.lotDate!!,
            quantity = rec.quantity!!,
            price = rec.price!!,
        )
    }

    fun deleteByExternalId(holdingInternalId: Long, externalId: UUID): Int =
        dsl.deleteFrom(STOCK_LOTS)
            .where(STOCK_LOTS.STOCK_HOLDING_ID.eq(holdingInternalId))
            .and(STOCK_LOTS.EXTERNAL_ID.eq(externalId))
            .execute()
}
```

Replace with:

```kotlin
package br.com.investlog.server.stockholdings.domain.repositories

import br.com.investlog.server.jooq.finances.tables.references.STOCK_LOTS
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

@Repository
class StockLotRepository(
    private val dsl: DSLContext
) {

    fun addLot(holdingInternalId: Long, request: LotCreateRequest): LotResponse {
        val rec = dsl.insertInto(STOCK_LOTS)
            .set(STOCK_LOTS.STOCK_HOLDING_ID, holdingInternalId)
            .set(STOCK_LOTS.LOT_DATE, request.lotDate)
            .set(STOCK_LOTS.QUANTITY, request.quantity)
            .set(STOCK_LOTS.PRICE, request.price)
            .returning()
            .fetchSingle()
        return LotResponse(
            id = rec.externalId!!,
            lotDate = rec.lotDate!!,
            quantity = rec.quantity!!,
            price = rec.price!!,
        )
    }

    fun updateLotDate(holdingInternalId: Long, externalId: UUID, lotDate: LocalDate): LotResponse? {
        val rec = dsl.update(STOCK_LOTS)
            .set(STOCK_LOTS.LOT_DATE, lotDate)
            .where(STOCK_LOTS.STOCK_HOLDING_ID.eq(holdingInternalId))
            .and(STOCK_LOTS.EXTERNAL_ID.eq(externalId))
            .returning()
            .fetchOne() ?: return null
        return LotResponse(
            id = rec.externalId!!,
            lotDate = rec.lotDate!!,
            quantity = rec.quantity!!,
            price = rec.price!!,
        )
    }

    fun deleteByExternalId(holdingInternalId: Long, externalId: UUID): Int =
        dsl.deleteFrom(STOCK_LOTS)
            .where(STOCK_LOTS.STOCK_HOLDING_ID.eq(holdingInternalId))
            .and(STOCK_LOTS.EXTERNAL_ID.eq(externalId))
            .execute()
}
```

- [ ] **Step 5: Add `updateLotDate` to `StockHoldingService`**

Current `server/src/main/kotlin/br/com/investlog/server/stockholdings/domain/services/StockHoldingService.kt`, the imports and the `deleteLot` method:

```kotlin
import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.stockholdings.domain.repositories.StockHoldingRepository
import br.com.investlog.server.stockholdings.domain.repositories.StockLotRepository
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingUpdateRequest
```

and:

```kotlin
    @Transactional
    fun deleteLot(walletExternalId: UUID, holdingExternalId: UUID, lotExternalId: UUID) {
        val walletId = walletService.resolveId(walletExternalId)
        val holdingId = holdingRepo.findInternalId(walletId, holdingExternalId)
            ?: throw NotFoundException("Stock holding not found: $holdingExternalId")
        val deleted = lotRepo.deleteByExternalId(holdingId, lotExternalId)
        if (deleted == 0) throw NotFoundException("Lot not found: $lotExternalId")
    }
}
```

Replace the imports with:

```kotlin
import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.stockholdings.domain.repositories.StockHoldingRepository
import br.com.investlog.server.stockholdings.domain.repositories.StockLotRepository
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import br.com.investlog.server.stockholdings.rest.payloads.LotUpdateRequest
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingUpdateRequest
```

Replace the end of the class with:

```kotlin
    @Transactional
    fun deleteLot(walletExternalId: UUID, holdingExternalId: UUID, lotExternalId: UUID) {
        val walletId = walletService.resolveId(walletExternalId)
        val holdingId = holdingRepo.findInternalId(walletId, holdingExternalId)
            ?: throw NotFoundException("Stock holding not found: $holdingExternalId")
        val deleted = lotRepo.deleteByExternalId(holdingId, lotExternalId)
        if (deleted == 0) throw NotFoundException("Lot not found: $lotExternalId")
    }

    @Transactional
    fun updateLotDate(
        walletExternalId: UUID,
        holdingExternalId: UUID,
        lotExternalId: UUID,
        request: LotUpdateRequest,
    ): LotResponse {
        val walletId = walletService.resolveId(walletExternalId)
        val holdingId = holdingRepo.findInternalId(walletId, holdingExternalId)
            ?: throw NotFoundException("Stock holding not found: $holdingExternalId")
        return lotRepo.updateLotDate(holdingId, lotExternalId, request.lotDate)
            ?: throw NotFoundException("Lot not found: $lotExternalId")
    }
}
```

- [ ] **Step 6: Add the PATCH endpoint to `StockHoldingController`**

Current imports and end of class:

```kotlin
import br.com.investlog.server.stockholdings.domain.services.StockHoldingService
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingUpdateRequest
```

and:

```kotlin
    @DeleteMapping("/{holdingId}/lots/{lotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteLot(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @PathVariable lotId: UUID,
    ) = service.deleteLot(walletId, holdingId, lotId)
}
```

Replace the imports with:

```kotlin
import br.com.investlog.server.stockholdings.domain.services.StockHoldingService
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import br.com.investlog.server.stockholdings.rest.payloads.LotUpdateRequest
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingResponse
import br.com.investlog.server.stockholdings.rest.payloads.StockHoldingUpdateRequest
```

Replace the end of the class with:

```kotlin
    @DeleteMapping("/{holdingId}/lots/{lotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteLot(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @PathVariable lotId: UUID,
    ) = service.deleteLot(walletId, holdingId, lotId)

    @PatchMapping("/{holdingId}/lots/{lotId}")
    fun updateLotDate(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @PathVariable lotId: UUID,
        @Valid @RequestBody request: LotUpdateRequest,
    ): LotResponse = service.updateLotDate(walletId, holdingId, lotId, request)
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew test --tests "br.com.investlog.server.stockholdings.rest.controllers.StockHoldingControllerTest"`
Expected: PASS (9 tests)

- [ ] **Step 8: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/stockholdings server/src/test/kotlin/br/com/investlog/server/stockholdings
git commit -m "feat: add PATCH endpoint to edit a stock lot's date"
```

---

### Task 15: Backend Crypto — `PATCH .../crypto-holdings/{holdingId}/lots/{lotId}` to edit a lot's date

**Files:**
- Modify: `server/src/main/kotlin/br/com/investlog/server/cryptoholdings/domain/repositories/CryptoLotRepository.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/cryptoholdings/domain/services/CryptoHoldingService.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/cryptoholdings/rest/controllers/CryptoHoldingController.kt`
- Test: `server/src/test/kotlin/br/com/investlog/server/cryptoholdings/rest/controllers/CryptoHoldingControllerTest.kt`

**Interfaces:**
- Consumes: `LotUpdateRequest` from `br.com.investlog.server.stockholdings.rest.payloads` (Task 14 — reused exactly as `CryptoHoldingController`/`CryptoHoldingService` already reuse `LotCreateRequest`/`LotResponse` from that same package).
- Produces: `CryptoLotRepository.updateLotDate(holdingInternalId: Long, externalId: UUID, lotDate: LocalDate): LotResponse?`. `CryptoHoldingService.updateLotDate(walletExternalId: UUID, holdingExternalId: UUID, lotExternalId: UUID, request: LotUpdateRequest): LotResponse`. `PATCH /private/v1/wallets/{walletId}/crypto-holdings/{holdingId}/lots/{lotId}` — consumed by Task 17.

- [ ] **Step 1: Write the failing tests**

In `server/src/test/kotlin/br/com/investlog/server/cryptoholdings/rest/controllers/CryptoHoldingControllerTest.kt`, current last test:

```kotlin
    @Test
    @Order(7)
    fun `returns 404 for unknown wallet`() {
        restTestClient.get()
            .uri("/private/v1/wallets/${UUID.randomUUID()}/crypto-holdings")
            .exchange()
            .expectStatus().isNotFound()
    }
}
```

Replace with:

```kotlin
    @Test
    @Order(7)
    fun `returns 404 for unknown wallet`() {
        restTestClient.get()
            .uri("/private/v1/wallets/${UUID.randomUUID()}/crypto-holdings")
            .exchange()
            .expectStatus().isNotFound()
    }

    @Test
    @Order(8)
    fun `updates a lot's date`() {
        val h = createHolding("DOGE")
        val lot = restTestClient.post()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/${h.id}/lots")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"lotDate":"2024-03-01","quantity":1000.0,"price":0.10}""")
            .exchange()
            .returnResult<LotResponse>()
            .responseBody!!

        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/${h.id}/lots/${lot.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"lotDate":"2024-03-20"}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.lotDate").isEqualTo("2024-03-20")
    }

    @Test
    @Order(9)
    fun `returns 404 when updating an unknown lot's date`() {
        val h = createHolding("LTC")
        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/crypto-holdings/${h.id}/lots/${UUID.randomUUID()}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"lotDate":"2024-03-20"}""")
            .exchange()
            .expectStatus().isNotFound()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "br.com.investlog.server.cryptoholdings.rest.controllers.CryptoHoldingControllerTest"`
Expected: FAIL — `PATCH .../lots/{lotId}` doesn't exist yet.

- [ ] **Step 3: Add `updateLotDate` to `CryptoLotRepository`**

Current `server/src/main/kotlin/br/com/investlog/server/cryptoholdings/domain/repositories/CryptoLotRepository.kt`:

```kotlin
package br.com.investlog.server.cryptoholdings.domain.repositories

import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_LOTS
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class CryptoLotRepository(private val dsl: DSLContext) {

    fun addLot(holdingInternalId: Long, request: LotCreateRequest): LotResponse {
        val rec = dsl.insertInto(CRYPTO_LOTS)
            .set(CRYPTO_LOTS.CRYPTO_HOLDING_ID, holdingInternalId)
            .set(CRYPTO_LOTS.LOT_DATE, request.lotDate)
            .set(CRYPTO_LOTS.QUANTITY, request.quantity)
            .set(CRYPTO_LOTS.PRICE, request.price)
            .returning()
            .fetchSingle()

        return LotResponse(
            id = rec.externalId!!,
            lotDate = rec.lotDate!!,
            quantity = rec.quantity!!,
            price = rec.price!!,
        )
    }

    fun deleteByExternalId(holdingInternalId: Long, externalId: UUID): Int =
        dsl.deleteFrom(CRYPTO_LOTS)
            .where(CRYPTO_LOTS.CRYPTO_HOLDING_ID.eq(holdingInternalId))
            .and(CRYPTO_LOTS.EXTERNAL_ID.eq(externalId))
            .execute()
}
```

Replace with:

```kotlin
package br.com.investlog.server.cryptoholdings.domain.repositories

import br.com.investlog.server.jooq.finances.tables.references.CRYPTO_LOTS
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

@Repository
class CryptoLotRepository(private val dsl: DSLContext) {

    fun addLot(holdingInternalId: Long, request: LotCreateRequest): LotResponse {
        val rec = dsl.insertInto(CRYPTO_LOTS)
            .set(CRYPTO_LOTS.CRYPTO_HOLDING_ID, holdingInternalId)
            .set(CRYPTO_LOTS.LOT_DATE, request.lotDate)
            .set(CRYPTO_LOTS.QUANTITY, request.quantity)
            .set(CRYPTO_LOTS.PRICE, request.price)
            .returning()
            .fetchSingle()

        return LotResponse(
            id = rec.externalId!!,
            lotDate = rec.lotDate!!,
            quantity = rec.quantity!!,
            price = rec.price!!,
        )
    }

    fun updateLotDate(holdingInternalId: Long, externalId: UUID, lotDate: LocalDate): LotResponse? {
        val rec = dsl.update(CRYPTO_LOTS)
            .set(CRYPTO_LOTS.LOT_DATE, lotDate)
            .where(CRYPTO_LOTS.CRYPTO_HOLDING_ID.eq(holdingInternalId))
            .and(CRYPTO_LOTS.EXTERNAL_ID.eq(externalId))
            .returning()
            .fetchOne() ?: return null

        return LotResponse(
            id = rec.externalId!!,
            lotDate = rec.lotDate!!,
            quantity = rec.quantity!!,
            price = rec.price!!,
        )
    }

    fun deleteByExternalId(holdingInternalId: Long, externalId: UUID): Int =
        dsl.deleteFrom(CRYPTO_LOTS)
            .where(CRYPTO_LOTS.CRYPTO_HOLDING_ID.eq(holdingInternalId))
            .and(CRYPTO_LOTS.EXTERNAL_ID.eq(externalId))
            .execute()
}
```

- [ ] **Step 4: Add `updateLotDate` to `CryptoHoldingService`**

Current imports and end of class in `CryptoHoldingService.kt`:

```kotlin
import br.com.investlog.server.cryptoholdings.domain.repositories.CryptoHoldingRepository
import br.com.investlog.server.cryptoholdings.domain.repositories.CryptoLotRepository
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingCreateRequest
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingUpdateRequest
import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
```

and:

```kotlin
    @Transactional
    fun deleteLot(walletExternalId: UUID, holdingExternalId: UUID, lotExternalId: UUID) {
        val walletId = walletService.resolveId(walletExternalId)
        val holdingId = holdingRepo.findInternalId(walletId, holdingExternalId)
            ?: throw NotFoundException("Crypto holding not found: $holdingExternalId")
        if (lotRepo.deleteByExternalId(holdingId, lotExternalId) == 0) {
            throw NotFoundException("Lot not found: $lotExternalId")
        }
    }
}
```

Replace the imports with:

```kotlin
import br.com.investlog.server.cryptoholdings.domain.repositories.CryptoHoldingRepository
import br.com.investlog.server.cryptoholdings.domain.repositories.CryptoLotRepository
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingCreateRequest
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingUpdateRequest
import br.com.investlog.server.shared.exceptions.NotFoundException
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import br.com.investlog.server.stockholdings.rest.payloads.LotUpdateRequest
```

Replace the end of the class with:

```kotlin
    @Transactional
    fun deleteLot(walletExternalId: UUID, holdingExternalId: UUID, lotExternalId: UUID) {
        val walletId = walletService.resolveId(walletExternalId)
        val holdingId = holdingRepo.findInternalId(walletId, holdingExternalId)
            ?: throw NotFoundException("Crypto holding not found: $holdingExternalId")
        if (lotRepo.deleteByExternalId(holdingId, lotExternalId) == 0) {
            throw NotFoundException("Lot not found: $lotExternalId")
        }
    }

    @Transactional
    fun updateLotDate(
        walletExternalId: UUID,
        holdingExternalId: UUID,
        lotExternalId: UUID,
        request: LotUpdateRequest,
    ): LotResponse {
        val walletId = walletService.resolveId(walletExternalId)
        val holdingId = holdingRepo.findInternalId(walletId, holdingExternalId)
            ?: throw NotFoundException("Crypto holding not found: $holdingExternalId")
        return lotRepo.updateLotDate(holdingId, lotExternalId, request.lotDate)
            ?: throw NotFoundException("Lot not found: $lotExternalId")
    }
}
```

- [ ] **Step 5: Add the PATCH endpoint to `CryptoHoldingController`**

Current imports and end of class:

```kotlin
import br.com.investlog.server.cryptoholdings.domain.services.CryptoHoldingService
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingCreateRequest
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingUpdateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
```

and:

```kotlin
    @DeleteMapping("/{holdingId}/lots/{lotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteLot(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @PathVariable lotId: UUID,
    ) = service.deleteLot(walletId, holdingId, lotId)
}
```

Replace the imports with:

```kotlin
import br.com.investlog.server.cryptoholdings.domain.services.CryptoHoldingService
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingCreateRequest
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingResponse
import br.com.investlog.server.cryptoholdings.rest.payloads.CryptoHoldingUpdateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotCreateRequest
import br.com.investlog.server.stockholdings.rest.payloads.LotResponse
import br.com.investlog.server.stockholdings.rest.payloads.LotUpdateRequest
```

Replace the end of the class with:

```kotlin
    @DeleteMapping("/{holdingId}/lots/{lotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteLot(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @PathVariable lotId: UUID,
    ) = service.deleteLot(walletId, holdingId, lotId)

    @PatchMapping("/{holdingId}/lots/{lotId}")
    fun updateLotDate(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @PathVariable lotId: UUID,
        @Valid @RequestBody request: LotUpdateRequest,
    ): LotResponse = service.updateLotDate(walletId, holdingId, lotId, request)
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew test --tests "br.com.investlog.server.cryptoholdings.rest.controllers.CryptoHoldingControllerTest"`
Expected: PASS (9 tests)

- [ ] **Step 7: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/cryptoholdings server/src/test/kotlin/br/com/investlog/server/cryptoholdings
git commit -m "feat: add PATCH endpoint to edit a crypto lot's date"
```

---

### Task 16: Backend Fund — `PATCH .../fund-holdings/{holdingId}/contributions/{contributionId}` to edit a contribution's date

**Files:**
- Create: `server/src/main/kotlin/br/com/investlog/server/fundholdings/rest/payloads/ContributionUpdateRequest.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/fundholdings/domain/repositories/FundContributionRepository.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/fundholdings/domain/services/FundHoldingService.kt`
- Modify: `server/src/main/kotlin/br/com/investlog/server/fundholdings/rest/controllers/FundHoldingController.kt`
- Test: `server/src/test/kotlin/br/com/investlog/server/fundholdings/rest/controllers/FundHoldingControllerTest.kt`

**Interfaces:**
- Consumes: `NotFoundException`, `WalletService.resolveId`, `FundHoldingRepository.findInternalId` (all existing).
- Produces: `ContributionUpdateRequest(val contributionDate: LocalDate)` in `br.com.investlog.server.fundholdings.rest.payloads`. `FundContributionRepository.updateContributionDate(holdingInternalId: Long, externalId: UUID, contributionDate: LocalDate): ContributionResponse?`. `FundHoldingService.updateContributionDate(walletExternalId: UUID, holdingExternalId: UUID, contributionExternalId: UUID, request: ContributionUpdateRequest): ContributionResponse`. `PATCH /private/v1/wallets/{walletId}/fund-holdings/{holdingId}/contributions/{contributionId}` — consumed by Task 17.

- [ ] **Step 1: Write the failing tests**

In `server/src/test/kotlin/br/com/investlog/server/fundholdings/rest/controllers/FundHoldingControllerTest.kt`, current last test:

```kotlin
    @Test
    @Order(7)
    fun `returns 404 for unknown wallet`() {
        restTestClient.get()
            .uri("/private/v1/wallets/${UUID.randomUUID()}/fund-holdings")
            .exchange()
            .expectStatus().isNotFound()
    }
}
```

Replace with:

```kotlin
    @Test
    @Order(7)
    fun `returns 404 for unknown wallet`() {
        restTestClient.get()
            .uri("/private/v1/wallets/${UUID.randomUUID()}/fund-holdings")
            .exchange()
            .expectStatus().isNotFound()
    }

    @Test
    @Order(8)
    fun `updates a contribution's date`() {
        val h = createHolding("Tesouro Selic 2027")
        val contribution = restTestClient.post()
            .uri("/private/v1/wallets/$walletId/fund-holdings/${h.id}/contributions")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"contributionDate":"2024-05-01","amount":3000.00}""")
            .exchange()
            .returnResult<ContributionResponse>()
            .responseBody!!

        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/fund-holdings/${h.id}/contributions/${contribution.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"contributionDate":"2024-05-20"}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.contributionDate").isEqualTo("2024-05-20")
    }

    @Test
    @Order(9)
    fun `returns 404 when updating an unknown contribution's date`() {
        val h = createHolding("Tesouro Prefixado 2030")
        restTestClient.patch()
            .uri("/private/v1/wallets/$walletId/fund-holdings/${h.id}/contributions/${UUID.randomUUID()}")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"contributionDate":"2024-05-20"}""")
            .exchange()
            .expectStatus().isNotFound()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "br.com.investlog.server.fundholdings.rest.controllers.FundHoldingControllerTest"`
Expected: FAIL — `PATCH .../contributions/{contributionId}` doesn't exist yet.

- [ ] **Step 3: Create `ContributionUpdateRequest`**

Create `server/src/main/kotlin/br/com/investlog/server/fundholdings/rest/payloads/ContributionUpdateRequest.kt`:

```kotlin
package br.com.investlog.server.fundholdings.rest.payloads

import jakarta.validation.constraints.NotNull
import java.time.LocalDate

data class ContributionUpdateRequest(
    @field:NotNull
    val contributionDate: LocalDate,
)
```

- [ ] **Step 4: Add `updateContributionDate` to `FundContributionRepository`**

Current `server/src/main/kotlin/br/com/investlog/server/fundholdings/domain/repositories/FundContributionRepository.kt`:

```kotlin
package br.com.investlog.server.fundholdings.domain.repositories

import br.com.investlog.server.fundholdings.rest.payloads.ContributionCreateRequest
import br.com.investlog.server.fundholdings.rest.payloads.ContributionResponse
import br.com.investlog.server.jooq.finances.tables.references.FUND_CONTRIBUTIONS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class FundContributionRepository(
    private val dsl: DSLContext
) {

    fun addContribution(holdingInternalId: Long, request: ContributionCreateRequest): ContributionResponse {
        val rec = dsl.insertInto(FUND_CONTRIBUTIONS)
            .set(FUND_CONTRIBUTIONS.FUND_HOLDING_ID, holdingInternalId)
            .set(FUND_CONTRIBUTIONS.CONTRIBUTION_DATE, request.contributionDate)
            .set(FUND_CONTRIBUTIONS.AMOUNT, request.amount)
            .returning()
            .fetchSingle()
        return ContributionResponse(
            id = rec.externalId!!,
            contributionDate = rec.contributionDate!!,
            amount = rec.amount!!,
        )
    }

    fun deleteByExternalId(holdingInternalId: Long, externalId: UUID): Int =
        dsl.deleteFrom(FUND_CONTRIBUTIONS)
            .where(FUND_CONTRIBUTIONS.FUND_HOLDING_ID.eq(holdingInternalId))
            .and(FUND_CONTRIBUTIONS.EXTERNAL_ID.eq(externalId))
            .execute()
}
```

Replace with:

```kotlin
package br.com.investlog.server.fundholdings.domain.repositories

import br.com.investlog.server.fundholdings.rest.payloads.ContributionCreateRequest
import br.com.investlog.server.fundholdings.rest.payloads.ContributionResponse
import br.com.investlog.server.jooq.finances.tables.references.FUND_CONTRIBUTIONS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

@Repository
class FundContributionRepository(
    private val dsl: DSLContext
) {

    fun addContribution(holdingInternalId: Long, request: ContributionCreateRequest): ContributionResponse {
        val rec = dsl.insertInto(FUND_CONTRIBUTIONS)
            .set(FUND_CONTRIBUTIONS.FUND_HOLDING_ID, holdingInternalId)
            .set(FUND_CONTRIBUTIONS.CONTRIBUTION_DATE, request.contributionDate)
            .set(FUND_CONTRIBUTIONS.AMOUNT, request.amount)
            .returning()
            .fetchSingle()
        return ContributionResponse(
            id = rec.externalId!!,
            contributionDate = rec.contributionDate!!,
            amount = rec.amount!!,
        )
    }

    fun updateContributionDate(holdingInternalId: Long, externalId: UUID, contributionDate: LocalDate): ContributionResponse? {
        val rec = dsl.update(FUND_CONTRIBUTIONS)
            .set(FUND_CONTRIBUTIONS.CONTRIBUTION_DATE, contributionDate)
            .where(FUND_CONTRIBUTIONS.FUND_HOLDING_ID.eq(holdingInternalId))
            .and(FUND_CONTRIBUTIONS.EXTERNAL_ID.eq(externalId))
            .returning()
            .fetchOne() ?: return null
        return ContributionResponse(
            id = rec.externalId!!,
            contributionDate = rec.contributionDate!!,
            amount = rec.amount!!,
        )
    }

    fun deleteByExternalId(holdingInternalId: Long, externalId: UUID): Int =
        dsl.deleteFrom(FUND_CONTRIBUTIONS)
            .where(FUND_CONTRIBUTIONS.FUND_HOLDING_ID.eq(holdingInternalId))
            .and(FUND_CONTRIBUTIONS.EXTERNAL_ID.eq(externalId))
            .execute()
}
```

- [ ] **Step 5: Add `updateContributionDate` to `FundHoldingService`**

Current imports and end of class in `FundHoldingService.kt`:

```kotlin
import br.com.investlog.server.fundholdings.domain.repositories.FundContributionRepository
import br.com.investlog.server.fundholdings.domain.repositories.FundHoldingRepository
import br.com.investlog.server.fundholdings.rest.payloads.ContributionCreateRequest
import br.com.investlog.server.fundholdings.rest.payloads.ContributionResponse
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingCreateRequest
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingResponse
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingUpdateRequest
import br.com.investlog.server.shared.exceptions.NotFoundException
```

and:

```kotlin
    @Transactional
    fun deleteContribution(walletExternalId: UUID, holdingExternalId: UUID, contributionExternalId: UUID) {
        val walletId = walletService.resolveId(walletExternalId)
        val holdingId = holdingRepo.findInternalId(walletId, holdingExternalId)
            ?: throw NotFoundException("Fund holding not found: $holdingExternalId")
        if (contributionRepo.deleteByExternalId(holdingId, contributionExternalId) == 0) {
            throw NotFoundException("Contribution not found: $contributionExternalId")
        }
    }
}
```

Replace the imports with:

```kotlin
import br.com.investlog.server.fundholdings.domain.repositories.FundContributionRepository
import br.com.investlog.server.fundholdings.domain.repositories.FundHoldingRepository
import br.com.investlog.server.fundholdings.rest.payloads.ContributionCreateRequest
import br.com.investlog.server.fundholdings.rest.payloads.ContributionResponse
import br.com.investlog.server.fundholdings.rest.payloads.ContributionUpdateRequest
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingCreateRequest
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingResponse
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingUpdateRequest
import br.com.investlog.server.shared.exceptions.NotFoundException
```

Replace the end of the class with:

```kotlin
    @Transactional
    fun deleteContribution(walletExternalId: UUID, holdingExternalId: UUID, contributionExternalId: UUID) {
        val walletId = walletService.resolveId(walletExternalId)
        val holdingId = holdingRepo.findInternalId(walletId, holdingExternalId)
            ?: throw NotFoundException("Fund holding not found: $holdingExternalId")
        if (contributionRepo.deleteByExternalId(holdingId, contributionExternalId) == 0) {
            throw NotFoundException("Contribution not found: $contributionExternalId")
        }
    }

    @Transactional
    fun updateContributionDate(
        walletExternalId: UUID,
        holdingExternalId: UUID,
        contributionExternalId: UUID,
        request: ContributionUpdateRequest,
    ): ContributionResponse {
        val walletId = walletService.resolveId(walletExternalId)
        val holdingId = holdingRepo.findInternalId(walletId, holdingExternalId)
            ?: throw NotFoundException("Fund holding not found: $holdingExternalId")
        return contributionRepo.updateContributionDate(holdingId, contributionExternalId, request.contributionDate)
            ?: throw NotFoundException("Contribution not found: $contributionExternalId")
    }
}
```

- [ ] **Step 6: Add the PATCH endpoint to `FundHoldingController`**

Current imports and end of class:

```kotlin
import br.com.investlog.server.fundholdings.domain.services.FundHoldingService
import br.com.investlog.server.fundholdings.rest.payloads.ContributionCreateRequest
import br.com.investlog.server.fundholdings.rest.payloads.ContributionResponse
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingCreateRequest
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingResponse
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingUpdateRequest
```

and:

```kotlin
    @DeleteMapping("/{holdingId}/contributions/{contributionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteContribution(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @PathVariable contributionId: UUID,
    ) = service.deleteContribution(walletId, holdingId, contributionId)
}
```

Replace the imports with:

```kotlin
import br.com.investlog.server.fundholdings.domain.services.FundHoldingService
import br.com.investlog.server.fundholdings.rest.payloads.ContributionCreateRequest
import br.com.investlog.server.fundholdings.rest.payloads.ContributionResponse
import br.com.investlog.server.fundholdings.rest.payloads.ContributionUpdateRequest
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingCreateRequest
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingResponse
import br.com.investlog.server.fundholdings.rest.payloads.FundHoldingUpdateRequest
```

Replace the end of the class with:

```kotlin
    @DeleteMapping("/{holdingId}/contributions/{contributionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteContribution(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @PathVariable contributionId: UUID,
    ) = service.deleteContribution(walletId, holdingId, contributionId)

    @PatchMapping("/{holdingId}/contributions/{contributionId}")
    fun updateContributionDate(
        @PathVariable walletId: UUID,
        @PathVariable holdingId: UUID,
        @PathVariable contributionId: UUID,
        @Valid @RequestBody request: ContributionUpdateRequest,
    ): ContributionResponse = service.updateContributionDate(walletId, holdingId, contributionId, request)
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew test --tests "br.com.investlog.server.fundholdings.rest.controllers.FundHoldingControllerTest"`
Expected: PASS (9 tests)

- [ ] **Step 8: Run the full backend test suite**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add server/src/main/kotlin/br/com/investlog/server/fundholdings server/src/test/kotlin/br/com/investlog/server/fundholdings
git commit -m "feat: add PATCH endpoint to edit a fund contribution's date"
```

---

### Task 17: Frontend API — `holdings.ts` methods for the three new PATCH endpoints

**Files:**
- Modify: `client/src/api/holdings.ts`

**Interfaces:**
- Consumes: `PATCH /wallets/{walletId}/stock-holdings/{holdingId}/lots/{lotId}`, `.../crypto-holdings/{holdingId}/lots/{lotId}`, `.../fund-holdings/{holdingId}/contributions/{contributionId}` (Tasks 14–16).
- Produces: `holdingsApi.updateStockLotDate(walletId, holdingId, lotId, { lotDate })`, `holdingsApi.updateCryptoLotDate(walletId, holdingId, lotId, { lotDate })`, `holdingsApi.updateFundContributionDate(walletId, holdingId, contributionId, { contributionDate })` — all `Promise<LotDetail>`/`Promise<ContributionDetail>`. Consumed by Task 18 (`HoldingDetailPanel.vue`).

- [ ] **Step 1: Add the three new methods**

In `client/src/api/holdings.ts`, current end of file:

```ts
  deleteFundContribution(walletId: string, holdingId: string, contributionId: string): Promise<void> {
    return apiClient
      .delete(`/wallets/${walletId}/fund-holdings/${holdingId}/contributions/${contributionId}`)
      .then(() => undefined)
  },
}
```

Replace with:

```ts
  deleteFundContribution(walletId: string, holdingId: string, contributionId: string): Promise<void> {
    return apiClient
      .delete(`/wallets/${walletId}/fund-holdings/${holdingId}/contributions/${contributionId}`)
      .then(() => undefined)
  },

  updateStockLotDate(
    walletId: string,
    holdingId: string,
    lotId: string,
    payload: { lotDate: string },
  ): Promise<LotDetail> {
    return apiClient
      .patch<LotDetail>(`/wallets/${walletId}/stock-holdings/${holdingId}/lots/${lotId}`, payload)
      .then((r) => r.data)
  },

  updateCryptoLotDate(
    walletId: string,
    holdingId: string,
    lotId: string,
    payload: { lotDate: string },
  ): Promise<LotDetail> {
    return apiClient
      .patch<LotDetail>(`/wallets/${walletId}/crypto-holdings/${holdingId}/lots/${lotId}`, payload)
      .then((r) => r.data)
  },

  updateFundContributionDate(
    walletId: string,
    holdingId: string,
    contributionId: string,
    payload: { contributionDate: string },
  ): Promise<ContributionDetail> {
    return apiClient
      .patch<ContributionDetail>(
        `/wallets/${walletId}/fund-holdings/${holdingId}/contributions/${contributionId}`,
        payload,
      )
      .then((r) => r.data)
  },
}
```

`LotDetail` and `ContributionDetail` are already imported at the top of this file (used by `addStockLot`/`addCryptoLot`/`addContribution`), so no import changes are needed.

- [ ] **Step 2: Verify with type-check**

Run: `npm run type-check`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add client/src/api/holdings.ts
git commit -m "feat: add API methods to edit lot and contribution dates"
```

---

### Task 18: Frontend UI — click-to-edit lot/contribution dates in the holding detail panel

**Files:**
- Modify: `client/src/components/investments/HoldingDetailPanel.vue`
- Modify: `client/src/assets/styles.css`

**Interfaces:**
- Consumes: `holdingsApi.updateStockLotDate`/`updateCryptoLotDate`/`updateFundContributionDate` (Task 17). `DateInput.vue` (existing component, props `modelValue: Date | null`, emits `update:modelValue: [Date | null]`). `LotDetail`/`ContributionDetail` (existing types, already used elsewhere in this same file's parent data shapes).
- Produces: nothing consumed by other tasks (last task in the plan).

- [ ] **Step 1: Manual repro before the fix**

With both backend and `npm run dev` running, open `/investments`, expand any holding's row. Confirm the lot/contribution dates in the expanded sub-table are plain text with no way to edit them (only quantity/price/amount can be changed, and only by adding a new lot/contribution or deleting one).

- [ ] **Step 2: Import `DateInput` and the new types**

Current imports:

```ts
import { computed, onMounted, ref } from 'vue'
import { BButton, useDialog, useToast } from 'buefy'
import AddPositionModal from '@/components/investments/AddPositionModal.vue'
import UpdatePriceModal from '@/components/investments/UpdatePriceModal.vue'
import { holdingsApi } from '@/api/holdings'
import { fmt } from '@/composables/useFormat'
import type { FundHoldingDetail, HoldingDetail, HoldingRow, StockHoldingDetail } from '@/types'
```

Replace with:

```ts
import { computed, onMounted, ref } from 'vue'
import { BButton, useDialog, useToast } from 'buefy'
import AddPositionModal from '@/components/investments/AddPositionModal.vue'
import UpdatePriceModal from '@/components/investments/UpdatePriceModal.vue'
import DateInput from '@/components/ui/DateInput.vue'
import { holdingsApi } from '@/api/holdings'
import { fmt } from '@/composables/useFormat'
import type { ContributionDetail, FundHoldingDetail, HoldingDetail, HoldingRow, LotDetail, StockHoldingDetail } from '@/types'
```

- [ ] **Step 3: Add edit-state and date-saving logic**

Current:

```ts
function confirmDeleteContribution(contributionId: string) {
  dialog.confirm({
    title: 'Remover aporte',
    message: 'Esta ação <strong>não pode ser desfeita</strong>.',
    type: 'is-danger',
    hasIcon: true,
    confirmText: 'Remover',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      await holdingsApi.deleteFundContribution(props.row.walletId, props.row.id, contributionId)
      toast.open({ message: 'Aporte removido.', type: 'is-success' })
      await reloadDetail()
      emit('positionAdded')
    },
  })
}
</script>
```

Replace with:

```ts
function confirmDeleteContribution(contributionId: string) {
  dialog.confirm({
    title: 'Remover aporte',
    message: 'Esta ação <strong>não pode ser desfeita</strong>.',
    type: 'is-danger',
    hasIcon: true,
    confirmText: 'Remover',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      await holdingsApi.deleteFundContribution(props.row.walletId, props.row.id, contributionId)
      toast.open({ message: 'Aporte removido.', type: 'is-success' })
      await reloadDetail()
      emit('positionAdded')
    },
  })
}

const editingDateId = ref<string | null>(null)

function parseDate(iso: string): Date {
  return new Date(iso + 'T00:00:00')
}

function toIsoDate(date: Date): string {
  return date.toISOString().slice(0, 10)
}

function startEditDate(id: string) {
  editingDateId.value = id
}

async function saveLotDate(lot: LotDetail, date: Date | null) {
  if (!date) return
  editingDateId.value = null
  if (isStock.value) {
    await holdingsApi.updateStockLotDate(props.row.walletId, props.row.id, lot.id, { lotDate: toIsoDate(date) })
  } else {
    await holdingsApi.updateCryptoLotDate(props.row.walletId, props.row.id, lot.id, { lotDate: toIsoDate(date) })
  }
  toast.open({ message: 'Data atualizada.', type: 'is-success' })
  await reloadDetail()
  emit('positionAdded')
}

async function saveContributionDate(contribution: ContributionDetail, date: Date | null) {
  if (!date) return
  editingDateId.value = null
  await holdingsApi.updateFundContributionDate(props.row.walletId, props.row.id, contribution.id, {
    contributionDate: toIsoDate(date),
  })
  toast.open({ message: 'Data atualizada.', type: 'is-success' })
  await reloadDetail()
  emit('positionAdded')
}
</script>
```

- [ ] **Step 4: Make the date cells clickable in the template**

Current:

```html
        <template v-if="fundDetail">
          <tr v-for="contribution in fundDetail.contributions" :key="contribution.id">
            <td>{{ fmt.date(contribution.contributionDate) }}</td>
            <td class="c-num">{{ fmt.money(contribution.amount, row.walletCurrency) }}</td>
```

Replace with:

```html
        <template v-if="fundDetail">
          <tr v-for="contribution in fundDetail.contributions" :key="contribution.id">
            <td>
              <DateInput
                v-if="editingDateId === contribution.id"
                :model-value="parseDate(contribution.contributionDate)"
                @update:model-value="(date) => saveContributionDate(contribution, date)"
              />
              <button v-else class="date-edit" @click.stop="startEditDate(contribution.id)">
                {{ fmt.date(contribution.contributionDate) }}
              </button>
            </td>
            <td class="c-num">{{ fmt.money(contribution.amount, row.walletCurrency) }}</td>
```

Current:

```html
        <template v-else-if="tradeDetail">
          <tr v-for="lot in tradeDetail.lots" :key="lot.id">
            <td>{{ fmt.date(lot.lotDate) }}</td>
            <td class="c-num">{{ fmt.qty(lot.quantity) }}</td>
```

Replace with:

```html
        <template v-else-if="tradeDetail">
          <tr v-for="lot in tradeDetail.lots" :key="lot.id">
            <td>
              <DateInput
                v-if="editingDateId === lot.id"
                :model-value="parseDate(lot.lotDate)"
                @update:model-value="(date) => saveLotDate(lot, date)"
              />
              <button v-else class="date-edit" @click.stop="startEditDate(lot.id)">
                {{ fmt.date(lot.lotDate) }}
              </button>
            </td>
            <td class="c-num">{{ fmt.qty(lot.quantity) }}</td>
```

- [ ] **Step 5: Add `.date-edit` CSS**

In `client/src/assets/styles.css`, current:

```css
.sub-table tbody tr:last-child td {
    border-bottom: none;
}
```

Replace with:

```css
.sub-table tbody tr:last-child td {
    border-bottom: none;
}

.date-edit {
    background: none;
    border: none;
    padding: 0;
    font: inherit;
    color: inherit;
    cursor: pointer;
    border-bottom: 1px dashed var(--border);
}

.date-edit:hover {
    border-bottom-color: var(--primary);
    color: var(--primary-d);
}
```

- [ ] **Step 6: Verify with type-check**

Run: `npm run type-check`
Expected: no errors.

- [ ] **Step 7: Manual verification**

With both backend and `npm run dev` running, open `/investments`, expand a stock holding's row. Click a lot's date, confirm it turns into a date picker; pick a new date, confirm a "Data atualizada." toast appears and the row shows the new date. Repeat for a crypto holding's lot and a fund holding's contribution.

- [ ] **Step 8: Commit**

```bash
git add client/src/components/investments/HoldingDetailPanel.vue client/src/assets/styles.css
git commit -m "feat: allow inline editing of lot and contribution dates"
```

---

## Self-Review

**1. Spec coverage** — every effective spec item maps to at least one task:

| Item | Task(s) |
|------|---------|
| 1 — ticker live-uppercase | Task 5 |
| 2 — settings toasts | Task 8 |
| 3 — edit lot/contribution date | Tasks 14–18 |
| 4 — URL sync on tab change | Task 6 (superseded/re-incorporated by Task 13) |
| 5 — add-investment modal default kind | Task 7 (superseded/re-incorporated by Task 13) |
| 6/12 — sub-type + search filter | Tasks 11–13 |
| 8 — sortable investments columns | Tasks 11–13 |
| 9 — wallet current value/gain | Tasks 9–10 |
| 10 — compact money abbreviation | Task 1 |
| 11 — navbar logo links home | Task 2 |
| 13 — remove "Logbook" eyebrow | Task 3 (the `InvestmentsView.vue` instance is also re-incorporated by Task 13's full rewrite) |
| 14 — type-card gain instead of bar | Task 4 |

Item 7 is dropped per the spec; item 12 is merged into item 6 (both covered by Tasks 11–13).

**2. Placeholder scan** — every step shows the literal before/after code or the literal command and its expected result; no "TBD"/"add appropriate handling"/"similar to Task N" phrasing appears anywhere in this plan.

**3. Type consistency** — cross-checked the names and signatures that cross task boundaries:
- `LotUpdateRequest` (Task 14) and `ContributionUpdateRequest` (Task 16) are each defined once and reused with identical names/fields everywhere they're imported (Tasks 14/15 share `LotUpdateRequest`; Task 16 alone defines/uses `ContributionUpdateRequest`).
- `holdingsApi.updateStockLotDate`/`updateCryptoLotDate`/`updateFundContributionDate` (Task 17) are called with matching names and argument order in Task 18.
- `HoldingsListOptions` (Task 12) — `{ typeLabel?, search?, sort? }` — is the exact shape both `loadKind`'s third parameter and `InvestmentsView.vue`'s `reload()` (Task 13) construct and pass.
- `WalletResponse.currentValue/gain/gainPct` (Task 9, Kotlin) and `WalletResponse.currentValue/gain/gainPct` (Task 10, TypeScript) agree in name, nullability, and meaning.
- `GainChip` props (`value`, `pct`, `cur`) are used identically in Tasks 4, 10, and 13 (pre-existing usage).
- `SortTh` (Task 13) props (`sortKey`, `activeKey`, `direction`, `align`) and its `toggle` emit are used consistently in the one place it's consumed (also Task 13, same task).

No gaps found.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-22-second-round-ux-improvements.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
