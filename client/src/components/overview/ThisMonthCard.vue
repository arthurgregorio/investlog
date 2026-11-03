<script setup lang="ts">
import { computed } from 'vue'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { fmt } from '@/composables/useFormat'
import type { MonthMover, MonthSummary } from '@/types'

const props = defineProps<{ month: MonthSummary; unpricedCount: number; currency: string }>()

const emit = defineEmits<{ 'view-investments': [] }>()

const monthLabel = computed(() => fmt.dateShort(props.month.month + '-01'))

const isEmpty = computed(
  () =>
    props.month.contributions === 0 &&
    props.month.realizedProfit === 0 &&
    props.month.currentResultChange == null &&
    props.month.bestMover == null &&
    props.month.worstMover == null,
)

const change = computed(() => props.month.currentResultChange)
const changeIsUp = computed(() => (change.value ?? 0) >= 0)

function moverName(mover: MonthMover): string {
  return mover.ticker ?? mover.name ?? '—'
}

function signClass(value: number): string {
  return value >= 0 ? 'gl-up' : 'gl-down'
}
</script>

<template>
  <Card class="month-card" data-testid="this-month-card">
    <CardBody>
      <div class="mb-4">
        <div class="chart-title">Neste mês</div>
        <div class="sub-caption">{{ monthLabel }} até hoje</div>
      </div>
      <div class="is-flex is-flex-direction-column is-gap-1.5 is-flex-grow-1">
        <EmptyState
          v-if="isEmpty"
          class="is-flex-grow-1"
          icon="calendar-blank-outline"
          title="Nada neste mês ainda"
          :text="`Aportes, resgates e a variação das suas posições em ${monthLabel} aparecem aqui assim que acontecerem.`"
        />
        <template v-else>
          <div class="month-row is-flex is-align-items-center is-gap-1.5">
            <span class="type-ic has-background-primary"><b-icon icon="cash-plus" /></span>
            <div>
              <div class="type-name">Aportes no mês</div>
              <div class="is-size-7 has-text-grey">capital próprio novo</div>
            </div>
            <div
              class="type-value month-value month-figure ml-auto"
              data-testid="month-contributions"
            >
              {{ fmt.money(month.contributions, currency, { compact: true }) }}
            </div>
          </div>
          <div class="month-row is-flex is-align-items-center is-gap-1.5">
            <span class="type-ic has-background-success"><b-icon icon="cash-check" /></span>
            <div>
              <div class="type-name">Lucro realizado no mês</div>
              <div class="is-size-7 has-text-grey">vendas e resgates</div>
            </div>
            <div
              v-if="month.realizedProfit !== 0"
              class="type-value month-value month-figure ml-auto"
              :class="signClass(month.realizedProfit)"
              data-testid="month-realized"
            >
              {{ fmt.moneySigned(month.realizedProfit, currency, { compact: true }) }}
            </div>
            <div
              v-else
              class="month-figure ml-auto has-text-grey has-text-weight-semibold"
              data-testid="month-realized"
            >
              Nenhum resgate
            </div>
          </div>
          <div class="month-row is-flex is-align-items-center is-gap-1.5">
            <span
              class="type-ic"
              :class="changeIsUp ? 'has-background-success' : 'has-background-danger'"
              data-testid="month-change-icon"
            >
              <b-icon icon="chart-line" />
            </span>
            <div>
              <div class="type-name">Variação do resultado atual</div>
              <div class="is-size-7 has-text-grey">desde o início do mês</div>
            </div>
            <div
              class="type-value month-value month-figure ml-auto"
              :class="change == null ? 'has-text-grey' : signClass(change)"
              data-testid="month-change"
            >
              {{ change == null ? '—' : fmt.moneySigned(change, currency, { compact: true }) }}
            </div>
          </div>
          <div
            v-if="month.bestMover || month.worstMover"
            class="month-row fixed-grid has-2-cols mb-0"
          >
            <div class="grid is-gap-1.5">
              <div v-if="month.bestMover" class="cell month-tile is-up" data-testid="best-mover">
                <div
                  class="is-flex is-align-items-center is-gap-0.5 is-size-7 has-text-weight-semibold gl-up"
                >
                  <b-icon icon="trending-up" size="is-small" /><span>Maior alta</span>
                </div>
                <div class="type-name mt-1">{{ moverName(month.bestMover) }}</div>
                <div class="type-value month-value" :class="signClass(month.bestMover.changePct)">
                  {{ fmt.pctSigned(month.bestMover.changePct) }}
                </div>
              </div>
              <div
                v-if="month.worstMover"
                class="cell month-tile is-down"
                data-testid="worst-mover"
              >
                <div
                  class="is-flex is-align-items-center is-gap-0.5 is-size-7 has-text-weight-semibold gl-down"
                >
                  <b-icon icon="trending-down" size="is-small" /><span>Maior queda</span>
                </div>
                <div class="type-name mt-1">{{ moverName(month.worstMover) }}</div>
                <div class="type-value month-value" :class="signClass(month.worstMover.changePct)">
                  {{ fmt.pctSigned(month.worstMover.changePct) }}
                </div>
              </div>
            </div>
          </div>
        </template>
        <div
          v-if="unpricedCount > 0"
          class="month-strip is-warning is-flex is-align-items-center is-gap-1"
          data-testid="price-status"
        >
          <b-icon icon="alert-outline" class="month-strip-icon" />
          <span class="is-size-7 has-text-weight-semibold">
            {{ unpricedCount }}
            {{ unpricedCount === 1 ? 'posição sem valor atual' : 'posições sem valor atual' }}
          </span>
          <b-button
            type="is-ghost"
            size="is-small"
            class="overview-link ml-auto"
            icon-right="chevron-right"
            data-testid="price-status-update"
            @click="emit('view-investments')"
            >Atualizar</b-button
          >
        </div>
        <div
          v-else
          class="month-strip is-ok is-flex is-align-items-center is-gap-1"
          data-testid="price-status"
        >
          <b-icon icon="check-circle-outline" class="gl-up" />
          <span class="is-size-7 has-text-weight-semibold">Posições atualizadas</span>
        </div>
      </div>
    </CardBody>
  </Card>
</template>

<style scoped>
.month-card {
  height: 100%;
}

.month-card > :deep(.card-body) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.month-row + .month-row {
  border-top: 1px solid var(--border-2);
  padding-top: 14px;
}

.month-value {
  font-size: 19px;
}

.month-figure {
  flex-shrink: 0;
  white-space: nowrap;
}

.month-tile {
  border-radius: 10px;
  padding: 12px 14px;
}

.month-tile.is-up {
  background: var(--up-soft);
}

.month-tile.is-down {
  background: var(--down-soft);
}

.month-strip {
  border-radius: 10px;
  padding: 10px 12px;
}

.month-strip.is-warning {
  background: var(--wt-crypto-soft);
}

.month-strip.is-ok {
  background: var(--up-soft);
}

.month-strip-icon {
  color: var(--wt-crypto);
}
</style>
