# InvestLog Client — Frontend Design Review & Buefy Migration Plan

**Date:** 2026-06-16  
**Scope:** Complete frontend design audit + migration from hand-rolled CSS components to Buefy 3.x (Bulma 1.x)  
**Approach:** CSS-variable bridge in `styles.css` (no SCSS, no Buefy internals touched). Keep current layout, accent colors, and dark mode.

---

## Design Review

### Strengths — keep as-is

- **Solid design token system.** `styles.css` CSS variables (`--primary`, `--bg`, `--surface`, `--border`, `--text`, gain/loss colors, wallet-type accents) are well-structured and the entire UI is consistent with them.
- **Financial data clarity.** Tabular numerics, gain/loss colors, KPI layout, and money formatting are all domain-appropriate.
- **Clean page layouts.** KPI grid → chart/alloc row → type cards → table is a logical progression. Grid breakpoints are handled.
- **App-shell modal pattern.** The `provide`/`inject` modal pattern in `App.vue` is clean and avoids prop-drilling.
- **Responsive.** Mobile breakpoints exist, topnav hides overflow gracefully, card grids collapse.

### Issues — addressed by this plan

| # | Issue | Fix |
|---|-------|-----|
| 1 | **High maintenance burden**: every input, button, select, and modal is hand-rolled | Replace with Buefy components |
| 2 | **No form validation feedback**: `FormField` has `hint` but no error/success styling | `<b-field>` exposes `type` and `message` for validation state |
| 3 | **No user feedback after actions**: add/create/remove produce zero visual response | Buefy `ToastProgrammatic` |
| 4 | **No delete confirmation**: `store.removeHolding` fires immediately | Buefy `DialogProgrammatic.confirm` |
| 5 | **DateInput is a raw `<input type="date">`**: browser-inconsistent, no locale | `<b-datepicker>` with `locale="pt-BR"` |
| 6 | **`SegChoice` active state is CSS-only**: no accessible radio semantics | `<b-radio-button>` provides proper `<input type="radio">` semantics |

---

## Phase 0 — Setup

### 0.1 Install packages

```bash
npm install buefy @mdi/font
```

Buefy 3.x (`^3.0.8`) requires Bulma `^1.0.4` — it ships its own copy, no separate install needed.

### 0.2 Register in `src/main.ts`

```ts
import { createApp } from 'vue'
import { createRouter, ... } from 'vue-router'
import { createPinia } from 'pinia'
import Buefy from 'buefy'
import 'buefy/dist/buefy.css'
import '@mdi/font/css/materialdesignicons.min.css'
import '@/assets/styles.css'          // must come AFTER buefy.css to allow overrides
import App from './App.vue'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(Buefy, { defaultIconPack: 'mdi' })
app.mount('#app')
```

> **Note:** `styles.css` import must remain after `buefy/dist/buefy.css` so our CSS variable bridge and layout overrides take priority.

### 0.3 Bulma 1.x CSS variable bridge in `styles.css`

Add this block near the top of `styles.css`, right after the existing `:root {}` block. Bulma 1.x exposes HSL component variables for every color:

```css
/* ===== Bulma 1.x color bridge ===== */
/* Brand blue #206bc4 → hsl(211, 72%, 45%) */
:root {
  --bulma-primary-h: 211;
  --bulma-primary-s: 72%;
  --bulma-primary-l: 45%;
}
[data-accent="blue"]   { --bulma-primary-h: 211; --bulma-primary-s: 72%; --bulma-primary-l: 45%; }
[data-accent="indigo"] { --bulma-primary-h: 228; --bulma-primary-s: 79%; --bulma-primary-l: 53%; }
[data-accent="teal"]   { --bulma-primary-h: 163; --bulma-primary-s: 87%; --bulma-primary-l: 35%; }
[data-accent="green"]  { --bulma-primary-h: 152; --bulma-primary-s: 74%; --bulma-primary-l: 33%; }

/* Dark mode — Bulma 1.x reads data-theme on :root or closest ancestor */
/* Our app-root uses data-theme; add same to :root via JS, OR rely on Bulma's own dark vars */
[data-theme="dark"] {
  /* Bulma 1.x dark scheme overrides — verify exact variable names from buefy.css output */
  --bulma-scheme-h: 220;
  --bulma-scheme-s: 30%;
  --bulma-background-l: 10%;
  --bulma-surface-l: 14%;
}
```

> **Implementation note:** Bulma 1.x exact dark-mode variable names must be confirmed against the installed `buefy.css` output. Run `grep -r '\-\-bulma' node_modules/buefy/dist/buefy.css | head -30` after install to verify the actual variable names.

---

## Phase 1 — Replace Form Components

This is the highest-value change: remove 6 hand-rolled components and their CSS.

### Mapping

| Current component | Buefy replacement | Notes |
|-------------------|-------------------|-------|
| `FormField.vue` | `<b-field>` | Gains: `type` for validation state, `message` for error/help text |
| `TextInput.vue` | `<b-input>` | Drop-in; v-model targets `modelValue` |
| `NumberInput.vue` | `<b-input type="number">` | Keep `step="any"` and `inputmode="decimal"` via `v-bind`; wrap in prefix slot for currency prefix |
| `DateInput.vue` | `<b-datepicker>` | Emits a `Date` object — need adapter in composable |
| `SelectInput.vue` | `<b-select>` | Map the `Option = T \| { value, label }` pattern to `<option>` elements |
| `SegChoice.vue` | `<b-field grouped>` + `<b-radio-button>` | Preserves radio semantics + icon support via slot |

### DateInput adapter

`DateInput` currently models a `string` (`YYYY-MM-DD`). `<b-datepicker>` models a `Date`. The composable `useAddInvestmentForm` stores `form.date: string`. Two options:
- **A (recommended):** Change `form.date` to `Date | null`, update `fmt.date` usage in display — cleaner.
- **B:** Thin wrapper component that converts between `Date ↔ string` — keeps composable unchanged.

### NumberInput prefix

`<b-input>` doesn't support a left-side text prefix natively. Use `<b-field>` addons:

```vue
<b-field>
  <p class="control"><span class="button is-static">R$</span></p>
  <b-input v-model.number="form.price" type="number" step="any" inputmode="decimal" expanded />
</b-field>
```

Or keep `NumberInput.vue` as a thin wrapper that produces this structure — acceptable since the component can now be just 15 lines instead of 49.

### SegChoice → b-radio-button

```vue
<!-- Before: <SegChoice v-model="form.kind" :options="KIND_OPTS" /> -->

<b-field grouped>
  <b-radio-button
    v-for="opt in KIND_OPTS"
    :key="opt.value"
    v-model="form.kind"
    :native-value="opt.value"
    type="is-primary"
  >
    <AppIcon v-if="opt.icon" :name="opt.icon" :size="17" />
    <span>{{ opt.label }}</span>
  </b-radio-button>
</b-field>
```

### CSS to remove after Phase 1

```
.field  .field-label  .field-hint
.inp  .inp-wrap  .inp-prefix
.sel-wrap  .sel  .sel-chev
.seg-choice  .seg-opt  (and .seg-opt.active)
```

---

## Phase 2 — Replace Action Components

### AppButton → b-button

```vue
<!-- Before: <AppButton variant="primary" icon="plus" @click="...">Label</AppButton> -->
<b-button type="is-primary" icon-left="plus" @click="...">Label</b-button>

<!-- Before: <AppButton variant="soft" icon="wallet">Carteiras</AppButton> -->
<b-button icon-left="wallet" @click="...">Carteiras</b-button>

<!-- Before: <AppButton variant="ghost" @click="...">Cancelar</AppButton> -->
<b-button @click="...">Cancelar</b-button>

<!-- Before: <AppButton size="sm" variant="primary" icon="plus">Criar carteira</AppButton> -->
<b-button type="is-primary" size="is-small" icon-left="plus">Criar carteira</b-button>
```

**Variant → type mapping:**

| AppButton variant | b-button type |
|---|---|
| `primary` | `type="is-primary"` |
| `soft` (border+bg) | *(no type — default Bulma button)* |
| `ghost` | `type="is-ghost"` or `type="is-text"` |

### IconButton → b-button (icon only)

```vue
<!-- Before: <IconButton icon="x" label="Fechar" @click="..." /> -->
<b-button icon-left="close" aria-label="Fechar" @click="..." />
```

### MDI icon name mapping (Tabler → MDI)

| AppIcon name | MDI name for b-button |
|---|---|
| `plus` | `plus` |
| `check` | `check` |
| `x` | `close` |
| `wallet` | `wallet` |
| `layers` | `layers` |
| `trendUp` | `trending-up` |
| `coins` | `bitcoin` |
| `building` | `office-building` |
| `settings` | `cog` |
| `dashboard` | `view-dashboard` |
| `repeat` | `repeat` |
| `sun` | `weather-sunny` |
| `moon` | `weather-night` |
| `chevronDown` | `chevron-down` |
| `chevronUp` | `chevron-up` |
| `chevronRight` | `chevron-right` |
| `info` | `information` |
| `trash` | `delete` |
| `plusCircle` | `plus-circle` |

> Keep `AppIcon.vue` for all non-button contexts (cards, badges, nav tabs, etc.). Only replace the `icon` prop usages inside buttons.

### CSS to remove after Phase 2

```
.btn  .btn-md  .btn-sm  .btn-full  .btn-full
.btn-primary  .btn-soft  .btn-ghost
.icon-btn
.link-btn  (replace usages with <b-button type="is-ghost" tag="a">)
```

---

## Phase 3 — Replace AppModal

### AppModal → b-modal + modal-card

`AppModal.vue` currently uses `Teleport`, Escape key listener, backdrop click. `<b-modal>` handles all of this.

```vue
<!-- Before: <AppModal title="..." subtitle="..." wide @close="..."> -->
<b-modal :model-value="true" has-modal-card trap-focus :can-cancel="['escape', 'outside']" @close="emit('close')">
  <div class="modal-card" :style="wide ? 'width: 600px' : undefined">
    <header class="modal-card-head">
      <p class="modal-card-title">{{ title }}</p>
      <b-button icon-left="close" aria-label="Fechar" @click="emit('close')" />
    </header>
    <section class="modal-card-body">
      <p v-if="subtitle" class="subtitle is-6">{{ subtitle }}</p>
      <slot />
    </section>
    <footer v-if="$slots.footer" class="modal-card-foot">
      <slot name="footer" />
    </footer>
  </div>
</b-modal>
```

The `AddInvestmentModal` and `CreateWalletModal` already manage open state via `v-if` in `App.vue`, so `:model-value="true"` is correct (the whole component unmounts on close).

### CSS to remove after Phase 3

```
.modal-scrim  .modal  .modal-wide
.modal-head  .modal-title  .modal-sub
.modal-body  .modal-foot
@keyframes fade  @keyframes pop
```

---

## Phase 4 — Add UX Patterns (Zero-cost wins)

These are pure additions that require no migration, only new code.

### 4.1 Toast notifications

Import at top of each modal or composable that submits:

```ts
import { ToastProgrammatic as Toast } from 'buefy'
```

| Action | Toast |
|---|---|
| `store.addWallet` | `Toast.open({ message: 'Carteira criada!', type: 'is-success' })` |
| `useAddInvestmentForm` submit | `Toast.open({ message: 'Investimento registrado!', type: 'is-success' })` |
| `store.addLot` / `store.addContribution` | `Toast.open({ message: 'Aporte registrado!', type: 'is-success' })` |
| Any server/validation error | `Toast.open({ message: 'Erro ao salvar.', type: 'is-danger', duration: 5000 })` |

### 4.2 Delete confirmation dialog

In `InvestmentRow.vue`, replace the bare `store.removeHolding` call:

```ts
import { DialogProgrammatic as Dialog } from 'buefy'

function confirmRemove() {
  Dialog.confirm({
    title: 'Remover investimento',
    message: 'Esta ação <strong>não pode ser desfeita</strong>.',
    type: 'is-danger',
    hasIcon: true,
    confirmText: 'Remover',
    cancelText: 'Cancelar',
    onConfirm: () => store.removeHolding(holding.id),
  })
}
```

---

## Phase 5 — Optional: Badge components

Low priority but consistent with Buefy adoption.

| Current | Replacement |
|---|---|
| `TypeBadge.vue` (`.wt-badge`) | `<b-tag :type="kindToType[kind]" size="is-small">` |
| `GainChip.vue` (`.gl-*`) | Keep custom — the arrow icon + signed money format is domain-specific enough to stay |

`kindToType` mapping: `stocks → is-link`, `crypto → is-warning`, `funds → is-success`

---

## What NOT to migrate

| Component | Reason to keep |
|---|---|
| `AppIcon.vue` | Tabler SVG system used in 10+ files; Buefy only needs MDI for button props |
| `TheNavbar.vue` | Layout structure; only the dark-mode toggle button gets changed to `<b-button>` |
| `TheTopNav.vue` | Custom seg-tab is clean; `b-tabs` would need significant rework for the current router-linked pattern |
| `Card.vue` / `CardBody.vue` | Thin wrappers; fine as-is |
| `GainChip.vue` | Domain-specific arrow + signed-money format |
| `TickerBadge.vue` | Colored square avatar badge; no Buefy equivalent |
| `Avatar.vue` | User avatar; no Buefy equivalent |
| `EmptyState.vue` | Custom layout; works well |
| `inv-table` / `InvestmentRow.vue` | The expand/collapse row pattern in a native table is simpler than `b-table`'s detailed slot. Keep. |
| `AreaChart.vue` / `DonutChart.vue` | No Buefy chart components |

---

## Final `styles.css` after migration

### Remove (Buefy owns these now)
```
Buttons:   .btn  .btn-md  .btn-sm  .btn-full  .btn-primary  .btn-soft  .btn-ghost  .link-btn  .icon-btn
Fields:    .field  .field-label  .field-hint
Inputs:    .inp  .inp-wrap  .inp-prefix  .sel-wrap  .sel  .sel-chev
SegChoice: .seg-choice  .seg-opt
Modal:     .modal-scrim  .modal  .modal-wide  .modal-head  .modal-title  .modal-sub  .modal-body  .modal-foot
Keyframes: @keyframes fade  @keyframes pop
```

### Keep (layout, domain visuals, charts)
```
Variables:  :root  [data-accent]  [data-theme]  + new Bulma bridge
Layout:     .app-root  .main  .content  .page  .page-narrow  .page-head  .page-head-row  .page-eyebrow  .page-title  .page-desc  .head-actions  .back-link  .section-label
Navbar:     .navbar  .navbar-inner  .navbar-spacer  .navbar-user  .brand  .brand-mark  .brand-name  .base-chip  .avatar  .nu-*
TopNav:     .topnav  .topnav-inner  .topnav-items  .nav-item  .nav-icon  .topnav-total  .tn-*
KPI:        .kpi-grid  .kpi-label  .kpi-value  .kpi-foot  .kpi-sub
Charts:     .grid-8-4  .chart-wrap  .chart-title  .chart-sub  .chart-big  .alloc-body  .alloc-legend  .legend-*  .donut-*
Type grid:  .type-grid  .type-card  .type-ic  .type-name  .type-share  .type-value  .type-bar  .type-meta
Wallets:    .wallet-grid  .wallet-card  .wallet-stripe  .wallet-head  .wallet-titles  .wallet-name  .wallet-tags  .wallet-invested  .wallet-holdings  .wallet-foot  .wallet-add  .wallet-empty  .wi-*  .wh-*  .cur-chip
Badges:     .wt-badge  .wt-stocks  .wt-crypto  .wt-funds  .ticker-badge  .gl  .gl-up  .gl-down  .gl-flat  .gl-pct  .gl-empty
Table:      .table-card  .table-scroll  .inv-table  .name-cell  .name-meta  .name-line  .t-ticker  .t-name  .type-tag  .tt-*  .cell-strong  .c-num  .wallet-ref  .wref-dot  .chev  .c-act  .detail-row  .detail  .sub-table  .detail-foot  .avg-note  .del-btn
Adder:      .adder  .adder-fields  .ad-f  .adder-actions
Forms:      .form-stack  .form-grid  .form-notice  .form-actions  (layout wrappers, not input chrome)
Settings:   .set-head  .set-title  .set-desc  .rate-list  .rate-row  .rate-cur  .rate-sym  .rate-base  .rate-input  .chip-edit  .edit-chip  .chip-add  .set-note
Appearance: .accent-row  .accent-swatch
Empty:      .empty  .empty-icon  .empty-title  .empty-text
Seg-tabs:   .seg-tabs  .seg-tab  (top-nav tabs, not form choice)
Responsive: @media blocks
```

---

## Execution Order

1. **Phase 0** — Install + register + add Bulma bridge CSS *(unblocks everything; verify dark-mode vars after install)*
2. **Phase 1** — Form components *(biggest reduction in hand-rolled code)*
3. **Phase 2** — Button components *(high frequency, low risk)*
4. **Phase 3** — Modal shell *(wraps phases 1+2)*
5. **Phase 4** — Toasts + dialog *(pure additions, can be done alongside any phase)*
6. **Phase 6** — `styles.css` cleanup *(after all replacements confirmed working)*
7. **Phase 5** — TypeBadge optional migration *(last, lowest priority)*

---

## Risk Notes

- **Buefy 3.x is very new (published 2026-06-09).** Monitor for breaking changes. Pin the version in `package.json` to `"buefy": "3.0.8"` (exact) not `^3.0.8`.
- **Bulma 1.x dark mode variables** must be verified after install — the exact CSS custom property names in `buefy.css` may differ from what's documented. Check before writing the bridge block.
- **`b-datepicker` date model** is a `Date` object, not a string. `useAddInvestmentForm` stores `date: string`. Plan for the adapter before migrating `DateInput`.
- **`NumberInput` prefix**: `<b-input>` has no native prefix slot — use the `<b-field>` addons pattern or keep a thin `NumberInput.vue` wrapper (15 lines instead of 49).
