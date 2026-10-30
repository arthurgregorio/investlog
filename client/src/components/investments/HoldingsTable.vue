<script setup lang="ts">
import { computed, nextTick, ref, useTemplateRef, watch } from 'vue'
import AddPositionModal from '@/components/investments/AddPositionModal.vue'
import HoldingDrawer from '@/components/investments/HoldingDrawer.vue'
import CashTableRow from '@/components/investments/CashTableRow.vue'
import HoldingsTableRow from '@/components/investments/HoldingsTableRow.vue'
import UpdatePriceModal from '@/components/investments/UpdatePriceModal.vue'
import SortTh from '@/components/ui/SortTh.vue'
import TablePagination from '@/components/ui/TablePagination.vue'
import type { HoldingRow } from '@/types'

type RowModal = { kind: 'addPosition' | 'updatePrice'; row: HoldingRow }

const props = withDefaults(
  defineProps<{
    rows: HoldingRow[]
    loading: boolean
    page: number
    pageSize: number
    totalElements: number
    showWalletColumn?: boolean
    convertToDisplayCurrency?: boolean
    sortable?: boolean
    sortKey?: string | null
    sortDirection?: 'asc' | 'desc'
    cash?: number
    cashCurrency?: string
  }>(),
  {
    showWalletColumn: false,
    convertToDisplayCurrency: false,
    sortable: false,
    sortKey: null,
    sortDirection: 'desc',
    cash: 0,
    cashCurrency: 'BRL',
  },
)
const emit = defineEmits<{
  'page-change': [page: number]
  sort: [key: string]
  'holding-changed': []
  'position-added': []
  relocated: []
  'transfer-cash': []
  'withdraw-cash': []
}>()

const numericColumns = [
  { key: null, label: 'Qtd.' },
  { key: null, label: 'Preço médio' },
  { key: 'price', label: 'Preço atual' },
  { key: 'current', label: 'Valor atual' },
  { key: 'gainPct', label: 'Resultado' },
]

const tableWrap = useTemplateRef<HTMLElement>('tableWrap')
const openHoldingId = ref<string | null>(null)
const rowModal = ref<RowModal | null>(null)

const openRow = computed(() => props.rows.find((row) => row.id === openHoldingId.value) ?? null)

watch(
  () => props.rows,
  (rows) => {
    if (openHoldingId.value && !rows.some((row) => row.id === openHoldingId.value)) {
      openHoldingId.value = null
    }
  },
)

function openDrawer(row: HoldingRow) {
  openHoldingId.value = row.id
}

async function closeDrawer() {
  const closedId = openHoldingId.value
  openHoldingId.value = null
  if (!closedId) return
  await nextTick()
  tableWrap.value?.querySelector<HTMLButtonElement>(`[data-open-holding="${closedId}"]`)?.focus()
}

function onPageChange(page: number) {
  closeDrawer()
  emit('page-change', page)
}

function onHoldingDeleted() {
  closeDrawer()
  emit('holding-changed')
}

function onRelocated() {
  closeDrawer()
  emit('relocated')
}

function rowCurrentAmount(row: HoldingRow) {
  return row.kind === 'FUNDS' ? row.currentValue : row.currentPrice
}

defineExpose({ closeDrawer })
</script>

<template>
  <div ref="tableWrap" class="table-wrap">
    <b-loading :is-full-page="false" :model-value="loading" />
    <div class="table-scroll">
      <table class="inv-table">
        <thead>
          <tr>
            <SortTh
              v-if="sortable"
              sort-key="asset"
              :active-key="sortKey"
              :direction="sortDirection"
              align="left"
              @toggle="emit('sort', $event)"
              >Ativo</SortTh
            >
            <th v-else>Ativo</th>
            <SortTh
              v-if="sortable && showWalletColumn"
              sort-key="wallet"
              :active-key="sortKey"
              :direction="sortDirection"
              align="left"
              @toggle="emit('sort', $event)"
              >Carteira · tipo</SortTh
            >
            <th v-else>{{ showWalletColumn ? 'Carteira · tipo' : 'Tipo' }}</th>
            <template v-for="column in numericColumns" :key="column.label">
              <SortTh
                v-if="sortable && column.key"
                :sort-key="column.key"
                :active-key="sortKey"
                :direction="sortDirection"
                @toggle="emit('sort', $event)"
                >{{ column.label }}</SortTh
              >
              <th v-else class="c-num has-text-right">{{ column.label }}</th>
            </template>
            <th class="c-act is-wide"><span class="is-sr-only">Ações</span></th>
          </tr>
        </thead>
        <tbody>
          <CashTableRow
            v-if="cash > 0"
            :cash="cash"
            :currency="cashCurrency"
            @transfer="emit('transfer-cash')"
            @withdraw-to-bank="emit('withdraw-cash')"
          />
          <HoldingsTableRow
            v-for="row in rows"
            :key="row.id"
            :row="row"
            :current="row.id === openHoldingId"
            :show-wallet-column="showWalletColumn"
            :convert-to-display-currency="convertToDisplayCurrency"
            @open="openDrawer(row)"
            @add-position="rowModal = { kind: 'addPosition', row }"
            @update-price="rowModal = { kind: 'updatePrice', row }"
          />
        </tbody>
      </table>
    </div>
    <TablePagination
      :page="page"
      :page-size="pageSize"
      :total-elements="totalElements"
      @page-change="onPageChange"
    />

    <HoldingDrawer
      v-if="openRow"
      :key="openRow.id"
      :row="openRow"
      :convert-to-display-currency="convertToDisplayCurrency"
      @close="closeDrawer"
      @deleted="onHoldingDeleted"
      @position-added="emit('position-added')"
      @relocated="onRelocated"
    />

    <AddPositionModal
      v-if="rowModal?.kind === 'addPosition'"
      :holding-id="rowModal.row.id"
      :wallet-id="rowModal.row.walletId"
      :kind="rowModal.row.kind"
      :wallet-currency="rowModal.row.walletCurrency"
      @added="emit('position-added')"
      @close="rowModal = null"
    />

    <UpdatePriceModal
      v-if="rowModal?.kind === 'updatePrice'"
      :holding-id="rowModal.row.id"
      :wallet-id="rowModal.row.walletId"
      :kind="rowModal.row.kind"
      :wallet-currency="rowModal.row.walletCurrency"
      :initial-value="rowCurrentAmount(rowModal.row)"
      @updated="emit('position-added')"
      @close="rowModal = null"
    />
  </div>
</template>
