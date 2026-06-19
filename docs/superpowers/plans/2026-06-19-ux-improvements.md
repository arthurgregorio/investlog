# UX Improvements — First-Usage Feedback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship six targeted UX improvements identified after first real usage: CSS alignment fixes, overview auto-refresh, update-price UI, wallet rename, real user info in navbar, and uniform currency row heights.

**Architecture:** All changes are frontend-only (Vue 3 + TypeScript). The backend already exposes every required endpoint. No new stores or composables needed — changes stay in the existing API layer, views, and components.

**Tech Stack:** Vue 3 `<script setup>` + TypeScript, Pinia, Buefy 3.x, Vite, Vitest. Path alias `@` → `src/`. Backend at `http://localhost:8080`, proxied via `/private/v1` by Vite.

## Global Constraints

- No abbreviated variable names (`quantity` not `qty`, `wallet` not `w`, `holding` not `h`, `baseCurrency` not `base`).
- `styles.css` is the single source of styling truth — no scoped component styles.
- All HTTP calls go through `apiClient` from `@/api/client.ts` with `baseURL: '/private/v1'`.
- `tsconfig.app.json` has `strict`, `noUnusedLocals`, `noUnusedParameters` — unused imports fail the build.
- Verify with `npm run type-check` from the `client/` directory after each task.
- Run `npm run dev` from `client/` to start the dev server on `http://localhost:5173`.

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `client/src/assets/styles.css` | Modify | Sub-table alignment + rate-row min-height |
| `client/src/views/OverviewView.vue` | Modify | Call `refresh()` instead of `load()` on mount |
| `client/src/views/WalletsView.vue` | Modify | Add rename button and dialog |
| `client/src/types.ts` | Modify | Add `ProfileResponse` interface |
| `client/src/api/profile.ts` | Create | `profileApi.getProfile()` |
| `client/src/components/layout/TheNavbar.vue` | Modify | Fetch and display real user info |
| `client/src/api/holdings.ts` | Modify | Add `updateStockHolding`, `updateCryptoHolding`, `updateFundHolding` |
| `client/src/components/investments/HoldingDetailPanel.vue` | Modify | Update-price inline editor |

---

## Task 1: CSS fixes — sub-table alignment and rate-row height

**Files:**
- Modify: `client/src/assets/styles.css`

**Interfaces:**
- Produces: `.sub-table td` gains `vertical-align: middle`; `.rate-row` gains `min-height: 52px`

- [ ] **Step 1: Add `vertical-align: middle` to `.sub-table td`**

  Open `client/src/assets/styles.css`. Find the `.sub-table td` block (around line 1216). Add `vertical-align: middle`:

  ```css
  .sub-table td {
      padding: 9px 12px;
      border-bottom: 1px solid var(--border-2);
      color: var(--text-2);
      font-variant-numeric: tabular-nums;
      vertical-align: middle;
  }
  ```

- [ ] **Step 2: Add `min-height: 52px` to `.rate-row`**

  Find the `.rate-row` block (around line 1414). Add `min-height`:

  ```css
  .rate-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 14px;
      padding: 10px 14px;
      border: 1px solid var(--border);
      border-radius: 9px;
      min-height: 52px;
  }
  ```

- [ ] **Step 3: Verify visually**

  Run `npm run dev` from `client/`. Open Settings page — BRL and USD rows should now be the same height. Open Investments, expand a holding — lot/contribution rows should have vertically centred text.

- [ ] **Step 4: Commit**

  ```bash
  git add client/src/assets/styles.css
  git commit -m "fix: align sub-table cells to middle and equalise currency rate row height"
  ```

---

## Task 2: Overview always-fresh on navigate

**Files:**
- Modify: `client/src/views/OverviewView.vue:22-24`

**Interfaces:**
- Consumes: `overviewStore.refresh()` — already defined in `src/stores/overview.ts` (sets `loaded = false` then fetches)
- Produces: OverviewView always fetches fresh data when mounted

- [ ] **Step 1: Replace `load()` with `refresh()` in `onMounted`**

  Open `client/src/views/OverviewView.vue`. Find the `onMounted` block:

  ```typescript
  onMounted(() => {
    Promise.all([overviewStore.load(), walletsStore.load()])
  })
  ```

  Change it to:

  ```typescript
  onMounted(() => {
    Promise.all([overviewStore.refresh(), walletsStore.load()])
  })
  ```

  `walletsStore.load()` stays as `load()` — wallet count per kind is unlikely to change during a session unless the user creates/deletes wallets, which already triggers `walletsStore.refresh()` explicitly.

- [ ] **Step 2: Run type-check**

  ```bash
  cd client && npm run type-check
  ```

  Expected: no errors.

- [ ] **Step 3: Verify visually**

  With `npm run dev` running: add a new investment via the "+ Adicionar investimento" button, then navigate to Visão Geral. The "Total investido" KPI and chart should reflect the new entry without a manual page reload.

- [ ] **Step 4: Commit**

  ```bash
  git add client/src/views/OverviewView.vue
  git commit -m "fix: always refresh overview data when navigating to the overview page"
  ```

---

## Task 3: Rename wallet

**Files:**
- Modify: `client/src/views/WalletsView.vue`

**Interfaces:**
- Consumes: `walletsApi.update(walletId, { name })` — already in `src/api/wallets.ts`
- Consumes: `dialog.prompt(...)` from `useDialog()` (Buefy 3.x)
- Consumes: `toast.open(...)` from `useToast()` — already imported
- Produces: A pencil button on each wallet card that opens a prompt, updates the name, refreshes the list

- [ ] **Step 1: Add `renameWallet` function**

  Open `client/src/views/WalletsView.vue`. The file already imports `useDialog`, `useToast`, `walletsApi`, `useWalletsStore`.

  Add this function after `confirmDeleteWallet`:

  ```typescript
  function renameWallet(walletId: string, currentName: string) {
    dialog.prompt({
      title: 'Renomear carteira',
      message: 'Novo nome:',
      inputAttrs: { value: currentName, placeholder: 'Nome da carteira' },
      confirmText: 'Salvar',
      cancelText: 'Cancelar',
      onConfirm: async (newName: string) => {
        const trimmedName = newName.trim()
        if (!trimmedName || trimmedName === currentName) return
        await walletsApi.update(walletId, { name: trimmedName })
        toast.open({ message: 'Carteira renomeada.', type: 'is-success' })
        await walletsStore.refresh()
      },
    })
  }
  ```

- [ ] **Step 2: Add the rename button to the wallet card template**

  In the `<template>`, find the delete button inside the wallet card header:

  ```html
  <b-button
    type="is-danger"
    size="is-small"
    icon-left="delete"
    outlined
    style="margin-left: auto"
    @click.stop="confirmDeleteWallet(wallet.id, wallet.name)"
  />
  ```

  Replace it with this pair (rename first, then delete):

  ```html
  <div style="display: flex; gap: 6px; margin-left: auto">
    <b-button
      type="is-light"
      size="is-small"
      icon-left="pencil"
      outlined
      @click.stop="renameWallet(wallet.id, wallet.name)"
    />
    <b-button
      type="is-danger"
      size="is-small"
      icon-left="delete"
      outlined
      @click.stop="confirmDeleteWallet(wallet.id, wallet.name)"
    />
  </div>
  ```

- [ ] **Step 3: Run type-check**

  ```bash
  cd client && npm run type-check
  ```

  Expected: no errors.

- [ ] **Step 4: Verify visually**

  Open Carteiras page. Each card should show a pencil button beside the red delete button. Clicking the pencil opens a prompt pre-filled with the wallet name. Saving updates the name on the card.

- [ ] **Step 5: Commit**

  ```bash
  git add client/src/views/WalletsView.vue
  git commit -m "feat: add rename button to wallet cards"
  ```

---

## Task 4: Real user info in navbar

**Files:**
- Modify: `client/src/types.ts`
- Create: `client/src/api/profile.ts`
- Modify: `client/src/components/layout/TheNavbar.vue`

**Interfaces:**
- Produces: `profileApi.getProfile(): Promise<ProfileResponse>`
- Produces: `ProfileResponse` type in `@/types`
- Consumes: `GET /profile` → `{ name, email, avatarUrl }`

- [ ] **Step 1: Add `ProfileResponse` to types**

  Open `client/src/types.ts`. Append after the `PagedResponse` interface at the end of the file:

  ```typescript
  export interface ProfileResponse {
    name: string
    email: string
    avatarUrl: string | null
  }
  ```

- [ ] **Step 2: Create `client/src/api/profile.ts`**

  ```typescript
  import { apiClient } from './client'
  import type { ProfileResponse } from '@/types'

  export const profileApi = {
    getProfile(): Promise<ProfileResponse> {
      return apiClient.get<ProfileResponse>('/profile').then((response) => response.data)
    },
  }
  ```

- [ ] **Step 3: Update `TheNavbar.vue` script**

  Open `client/src/components/layout/TheNavbar.vue`.

  Replace the entire `<script setup>` block with:

  ```typescript
  <script setup lang="ts">
  import { onMounted, ref } from 'vue'
  import { storeToRefs } from 'pinia'
  import AppIcon from '@/components/AppIcon.vue'
  import Avatar from '@/components/ui/Avatar.vue'
  import { useRatesStore } from '@/stores/rates'
  import { useAppearanceStore } from '@/stores/appearance'
  import { profileApi } from '@/api/profile'
  import type { ProfileResponse } from '@/types'

  const ratesStore = useRatesStore()
  const { baseCurrency } = storeToRefs(ratesStore)
  const appearance = useAppearanceStore()
  const { dark } = storeToRefs(appearance)

  const profile = ref<ProfileResponse | null>(null)

  onMounted(async () => {
    profile.value = await profileApi.getProfile()
  })

  function initials(name: string): string {
    return name
      .split(' ')
      .filter((word) => word.length > 0)
      .slice(0, 2)
      .map((word) => word[0].toUpperCase())
      .join('')
  }
  </script>
  ```

- [ ] **Step 4: Update `TheNavbar.vue` template**

  Find the `.navbar-user` block in the template:

  ```html
  <div class="navbar-user">
    <Avatar initials="RT" />
    <div class="nu-meta">
      <div class="nu-name">Rafael T.</div>
      <div class="nu-sub">Investidor</div>
    </div>
  </div>
  ```

  Replace it with:

  ```html
  <div class="navbar-user">
    <Avatar :initials="profile ? initials(profile.name) : '?'" />
    <div class="nu-meta">
      <div class="nu-name">{{ profile?.name ?? '...' }}</div>
      <div class="nu-sub">{{ profile?.email ?? '' }}</div>
    </div>
  </div>
  ```

- [ ] **Step 5: Run type-check**

  ```bash
  cd client && npm run type-check
  ```

  Expected: no errors.

- [ ] **Step 6: Verify visually**

  Open the app. The navbar should show the real user name and email from the database instead of "Rafael T." / "Investidor". The avatar shows derived initials (e.g. `"AG"` for `"Arthur Gregorio"`).

- [ ] **Step 7: Commit**

  ```bash
  git add client/src/types.ts client/src/api/profile.ts client/src/components/layout/TheNavbar.vue
  git commit -m "feat: display real user profile info in navbar from GET /profile"
  ```

---

## Task 5: Holdings API — PATCH methods for current price/value

**Files:**
- Modify: `client/src/api/holdings.ts`

**Interfaces:**
- Produces:
  - `holdingsApi.updateStockHolding(walletId: string, holdingId: string, payload: { currentPrice?: number }): Promise<StockHoldingDetail>`
  - `holdingsApi.updateCryptoHolding(walletId: string, holdingId: string, payload: { currentPrice?: number }): Promise<CryptoHoldingDetail>`
  - `holdingsApi.updateFundHolding(walletId: string, holdingId: string, payload: { currentValue?: number }): Promise<FundHoldingDetail>`

- [ ] **Step 1: Add the three PATCH methods to `holdingsApi`**

  Open `client/src/api/holdings.ts`. After `deleteStockHolding` and before `getCryptoHolding`, add:

  ```typescript
  updateStockHolding(
    walletId: string,
    holdingId: string,
    payload: { currentPrice?: number },
  ): Promise<StockHoldingDetail> {
    return apiClient
      .patch<StockHoldingDetail>(`/wallets/${walletId}/stock-holdings/${holdingId}`, payload)
      .then((r) => r.data)
  },
  ```

  After `deleteCryptoHolding` and before `getFundHolding`, add:

  ```typescript
  updateCryptoHolding(
    walletId: string,
    holdingId: string,
    payload: { currentPrice?: number },
  ): Promise<CryptoHoldingDetail> {
    return apiClient
      .patch<CryptoHoldingDetail>(`/wallets/${walletId}/crypto-holdings/${holdingId}`, payload)
      .then((r) => r.data)
  },
  ```

  After `deleteFundHolding` and before `addContribution`, add:

  ```typescript
  updateFundHolding(
    walletId: string,
    holdingId: string,
    payload: { currentValue?: number },
  ): Promise<FundHoldingDetail> {
    return apiClient
      .patch<FundHoldingDetail>(`/wallets/${walletId}/fund-holdings/${holdingId}`, payload)
      .then((r) => r.data)
  },
  ```

- [ ] **Step 2: Run type-check**

  ```bash
  cd client && npm run type-check
  ```

  Expected: no errors.

- [ ] **Step 3: Commit**

  ```bash
  git add client/src/api/holdings.ts
  git commit -m "feat: add PATCH methods to holdingsApi for updating current price/value"
  ```

---

## Task 6: Update current price — inline editor in HoldingDetailPanel

**Files:**
- Modify: `client/src/components/investments/HoldingDetailPanel.vue`

**Interfaces:**
- Consumes: `holdingsApi.updateStockHolding`, `updateCryptoHolding`, `updateFundHolding` from Task 5
- Consumes: `NumberInput` from `@/components/ui/NumberInput.vue`
- Consumes: `fmt.sym(currency)` from `@/composables/useFormat`

- [ ] **Step 1: Add import for `NumberInput`**

  Open `client/src/components/investments/HoldingDetailPanel.vue`. In the `<script setup>` block, add:

  ```typescript
  import NumberInput from '@/components/ui/NumberInput.vue'
  ```

  Place it after the existing imports.

- [ ] **Step 2: Add reactive state for the price editor**

  After the existing `const adding = ref(false)` line, add:

  ```typescript
  const editingPrice = ref(false)
  const priceInput = ref<number | ''>('')
  ```

- [ ] **Step 3: Add price editor functions**

  After the existing `reloadDetail()` function, add:

  ```typescript
  function startPriceEdit() {
    if (!detail.value) return
    // StockHoldingDetail and CryptoHoldingDetail both have currentPrice — casting to StockHoldingDetail is safe here
    const currentAmount = isFund.value
      ? (detail.value as FundHoldingDetail).currentValue
      : (detail.value as StockHoldingDetail).currentPrice
    priceInput.value = currentAmount ?? ''
    editingPrice.value = true
  }

  function cancelPriceEdit() {
    editingPrice.value = false
    priceInput.value = ''
  }

  async function savePriceUpdate() {
    if (priceInput.value === '') return
    const amount = Number(priceInput.value)
    if (isStock.value) {
      await holdingsApi.updateStockHolding(props.row.walletId, props.row.id, { currentPrice: amount })
    } else if (props.row.kind === 'CRYPTO') {
      await holdingsApi.updateCryptoHolding(props.row.walletId, props.row.id, { currentPrice: amount })
    } else {
      await holdingsApi.updateFundHolding(props.row.walletId, props.row.id, { currentValue: amount })
    }
    toast.open({ message: isFund.value ? 'Valor atual atualizado.' : 'Preço atualizado.', type: 'is-success' })
    cancelPriceEdit()
    await reloadDetail()
    emit('positionAdded')
  }
  ```

  Note: `FundHoldingDetail` and `StockHoldingDetail` are already imported at the top of the file via the existing `import type { ... } from '@/types'` line. `CryptoHoldingDetail` is not imported but is not needed here — the `StockHoldingDetail` cast works for crypto since both types share the same `currentPrice` field.

- [ ] **Step 4: Prevent conflict between `adding` and `editingPrice`**

  The template currently uses `@click="adding = true"` inline. Replace the inline click with a function call to also reset the price editor. Add this function:

  ```typescript
  function startAdding() {
    cancelPriceEdit()
    adding.value = true
  }
  ```

- [ ] **Step 5: Update the template — footer section**

  Find the `<div v-else class="detail-foot">` block in the template:

  ```html
  <div v-else class="detail-foot">
    <b-button type="is-text" icon-left="plus" @click="adding = true">
      {{ isFund ? 'Registrar novo aporte' : 'Registrar nova compra' }}
    </b-button>
    <div class="detail-foot-right">
      <span v-if="!isFund && quantity" class="avg-note">
        Preço médio
        <b>{{ fmt.money(costBasis / quantity, row.walletCurrency) }}</b>
      </span>
      <b-button
        type="is-text"
        size="is-small"
        icon-left="delete"
        style="color: var(--down)"
        @click="confirmRemove"
      >
        Remover
      </b-button>
    </div>
  </div>
  ```

  Replace it with:

  ```html
  <div v-else class="detail-foot">
    <b-button type="is-text" icon-left="plus" @click="startAdding">
      {{ isFund ? 'Registrar novo aporte' : 'Registrar nova compra' }}
    </b-button>

    <div v-if="editingPrice" class="detail-foot-right">
      <NumberInput
        v-model="priceInput"
        :prefix="fmt.sym(row.walletCurrency)"
        :placeholder="isFund ? 'Valor atual' : 'Preço atual'"
        style="width: 170px"
        min="0"
      />
      <b-button
        type="is-primary"
        size="is-small"
        :disabled="priceInput === ''"
        @click="savePriceUpdate"
      >
        Salvar
      </b-button>
      <b-button type="is-text" size="is-small" @click="cancelPriceEdit">
        Cancelar
      </b-button>
    </div>
    <div v-else class="detail-foot-right">
      <span v-if="!isFund && quantity" class="avg-note">
        Preço médio
        <b>{{ fmt.money(costBasis / quantity, row.walletCurrency) }}</b>
      </span>
      <b-button
        type="is-text"
        size="is-small"
        icon-left="pencil"
        @click="startPriceEdit"
      >
        {{ isFund ? 'Atualizar valor atual' : 'Atualizar preço' }}
      </b-button>
      <b-button
        type="is-text"
        size="is-small"
        icon-left="delete"
        style="color: var(--down)"
        @click="confirmRemove"
      >
        Remover
      </b-button>
    </div>
  </div>
  ```

- [ ] **Step 6: Run type-check**

  ```bash
  cd client && npm run type-check
  ```

  Expected: no errors.

- [ ] **Step 7: Verify visually**

  Open the Investments page. Expand a stock holding — the footer should show "Atualizar preço" (pencil) beside "Remover". Clicking it reveals a currency-prefixed number input pre-filled with the current price (or blank if none set). Saving shows a toast and the holding's "Valor atual" column updates. Repeat for a crypto and a fund holding (`"Atualizar valor atual"`).

- [ ] **Step 8: Commit**

  ```bash
  git add client/src/components/investments/HoldingDetailPanel.vue
  git commit -m "feat: add inline current price/value editor to holding detail panel"
  ```
