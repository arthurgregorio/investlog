<script setup lang="ts">
import EmptyState from '@/components/ui/EmptyState.vue'
import TablePagination from '@/components/ui/TablePagination.vue'
import { fmt } from '@/composables/useFormat'
import { useWalletMovesStore } from '@/stores/walletMoves'
import type { WalletMoveRow } from '@/types'

const props = defineProps<{ walletId: string }>()

const walletMovesStore = useWalletMovesStore()

function moveCounterpart(move: WalletMoveRow): string {
  const walletName = move.direction === 'OUT' ? move.destinationWalletName : move.originWalletName
  return walletName ?? 'Carteira removida'
}

async function onPageChange(page: number) {
  await walletMovesStore.load(props.walletId, page)
}
</script>

<template>
  <div data-testid="move-history">
    <div v-if="walletMovesStore.rows.length > 0" class="table-wrap">
      <b-loading :is-full-page="false" :model-value="walletMovesStore.loading" />
      <div class="table-scroll">
        <table class="inv-table">
          <thead>
            <tr>
              <th>Data</th>
              <th>Investimento</th>
              <th>Direção</th>
              <th>Carteira</th>
              <th class="c-num has-text-right">Qtd.</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="move in walletMovesStore.rows" :key="move.id" data-testid="move-row">
              <td>{{ fmt.date(move.movedAt) }}</td>
              <td>
                <span class="t-ticker">{{ move.ticker ?? move.holdingName }}</span>
              </td>
              <td>
                <span class="move-direction" :class="move.direction === 'IN' ? 'is-in' : 'is-out'">
                  <b-icon
                    :icon="move.direction === 'IN' ? 'arrow-bottom-left' : 'arrow-top-right'"
                    size="is-small"
                  />
                  {{ move.direction === 'IN' ? 'Entrada' : 'Saída' }}
                </span>
              </td>
              <td>{{ moveCounterpart(move) }}</td>
              <td class="c-num has-text-right">
                {{ move.quantity == null ? 'Tudo' : fmt.qty(move.quantity) }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <TablePagination
        :page="walletMovesStore.page"
        :page-size="walletMovesStore.pageSize"
        :total-elements="walletMovesStore.totalElements"
        @page-change="onPageChange"
      />
    </div>
    <div
      v-else-if="walletMovesStore.loaded"
      class="move-history-empty"
      data-testid="move-history-empty"
    >
      <EmptyState
        icon="swap-horizontal"
        title="Nenhuma movimentação"
        text="Investimentos movidos entre carteiras aparecem aqui."
      />
    </div>
  </div>
</template>

<style scoped>
.move-direction {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
}

.move-direction.is-in {
  color: var(--up);
}

.move-direction.is-out {
  color: var(--text-2);
}

.move-history-empty {
  padding: 16px 20px 20px;
}
</style>
