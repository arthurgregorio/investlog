<script setup lang="ts">
import ClassHoldingIdentity from '@/components/strategies/ClassHoldingIdentity.vue'
import { fmt } from '@/composables/useFormat'
import { LEFT_OUT_REASONS } from '@/utils/allocationLabels'
import type { PlanHoldingRow } from '@/utils/allocationLabels'

defineProps<{ rows: PlanHoldingRow[]; currency: string; emptyText: string }>()
</script>

<template>
  <p v-if="rows.length === 0" class="card-body has-text-grey" data-testid="plan-holdings-empty">
    {{ emptyText }}
  </p>
  <div v-else class="table-wrap">
    <div class="table-scroll">
      <table class="inv-table">
        <thead>
          <tr>
            <th>Investimento</th>
            <th>Carteira · tipo</th>
            <th>Classe</th>
            <th class="c-num has-text-right">Meta na classe</th>
            <th class="c-num has-text-right">Atual na classe</th>
            <th class="c-num has-text-right">
              {{ rows[0].reason ? 'Motivo' : 'Aporte sugerido' }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in rows"
            :key="row.id"
            class="inv-row"
            :class="{ 'is-frozen': row.holding.frozen }"
            data-testid="plan-holding-row"
          >
            <ClassHoldingIdentity :holding="row.holding" />
            <td>
              <span class="is-size-7">{{ row.className }}</span>
            </td>
            <td class="c-num has-text-right">
              <template v-if="row.holding.typed">{{
                fmt.pct(row.holding.targetPercentage)
              }}</template>
              <template v-else>
                <span class="has-text-grey">{{ fmt.pct(row.holding.targetPercentage) }}</span>
                <div class="is-size-7 has-text-grey">do restante</div>
              </template>
            </td>
            <td class="c-num has-text-right">{{ fmt.pct(row.holding.actualPercentage) }}</td>
            <td class="c-num has-text-right">
              <span v-if="row.reason" class="tag" :class="LEFT_OUT_REASONS[row.reason].type">
                {{ LEFT_OUT_REASONS[row.reason].label }}
              </span>
              <span v-else class="has-text-weight-bold">{{
                fmt.money(row.amount ?? 0, currency)
              }}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
