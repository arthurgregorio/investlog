<script setup lang="ts">
import { computed } from 'vue'
import HoldingDetailPanel from '@/components/investments/HoldingDetailPanel.vue'
import HoldingsTableRow from '@/components/investments/HoldingsTableRow.vue'
import SortTh from '@/components/ui/SortTh.vue'
import TablePagination from '@/components/ui/TablePagination.vue'
import { useExpandedRow } from '@/composables/useExpandedRow'
import type { HoldingRow } from '@/types'

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
  }>(),
  {
    showWalletColumn: false,
    convertToDisplayCurrency: false,
    sortable: false,
    sortKey: null,
    sortDirection: 'desc',
  },
)
const emit = defineEmits<{
  'page-change': [page: number]
  sort: [key: string]
  'holding-changed': []
  'position-added': []
  relocated: []
}>()

const numericColumns = [
  { key: 'price', label: 'Preço atual' },
  { key: 'invested', label: 'Investido' },
  { key: 'current', label: 'Valor atual' },
  { key: 'gain', label: 'Resultado' },
]

const { isExpanded, toggle, collapse } = useExpandedRow()

const columnCount = computed(() => (props.showWalletColumn ? 8 : 7))

function onPageChange(page: number) {
  collapse()
  emit('page-change', page)
}

function onHoldingDeleted() {
  collapse()
  emit('holding-changed')
}

function onRelocated() {
  collapse()
  emit('relocated')
}

defineExpose({ collapse })
</script>

<template>
  <div class="table-wrap">
    <b-loading :is-full-page="false" :model-value="loading" />
    <div class="table-scroll">
      <table class="inv-table">
        <thead>
          <tr>
            <th>Investimento</th>
            <template v-if="showWalletColumn">
              <SortTh
                v-if="sortable"
                sort-key="wallet"
                :active-key="sortKey"
                :direction="sortDirection"
                align="left"
                @toggle="emit('sort', $event)"
                >Carteira</SortTh
              >
              <th v-else>Carteira</th>
            </template>
            <th class="c-num has-text-right">Qtd.</th>
            <template v-for="column in numericColumns" :key="column.key">
              <SortTh
                v-if="sortable"
                :sort-key="column.key"
                :active-key="sortKey"
                :direction="sortDirection"
                @toggle="emit('sort', $event)"
                >{{ column.label }}</SortTh
              >
              <th v-else class="c-num has-text-right">{{ column.label }}</th>
            </template>
            <th class="c-act"></th>
          </tr>
        </thead>
        <tbody>
          <template v-for="row in rows" :key="row.id">
            <HoldingsTableRow
              :row="row"
              :expanded="isExpanded(row.id)"
              :show-wallet-column="showWalletColumn"
              :convert-to-display-currency="convertToDisplayCurrency"
              @toggle="toggle(row.id)"
            />
            <tr v-if="isExpanded(row.id)" class="detail-row">
              <td :colspan="columnCount">
                <HoldingDetailPanel
                  :row="row"
                  @deleted="onHoldingDeleted"
                  @position-added="emit('position-added')"
                  @relocated="onRelocated"
                />
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
    <TablePagination
      :page="page"
      :page-size="pageSize"
      :total-elements="totalElements"
      @page-change="onPageChange"
    />
  </div>
</template>

<style scoped>
.detail-row > td {
  background: var(--surface-2);
}
</style>
