<script setup lang="ts">
import { computed } from 'vue'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import AllocationLegend from '@/components/charts/AllocationLegend.vue'
import { fmt } from '@/composables/useFormat'
import type { PortfolioSummary } from '@/types'

const props = defineProps<{ summary: PortfolioSummary; currency: string }>()

const emit = defineEmits<{ 'view-results': []; 'view-investments': [] }>()

const LIFETIME_HINT =
  'O quanto o seu dinheiro rendeu desde o primeiro aporte: lucro já realizado mais a variação do que você tem hoje.'
const CURRENT_HINT =
  'O que você tem hoje, a preço de hoje, contra o que pagou por isso. Lucro reinvestido entra no que foi pago.'

const lifetimeIsUp = computed(() => props.summary.lifetimeResult >= 0)
const currentIsUp = computed(() => props.summary.totalGain >= 0)

const splitBase = computed(() => Math.max(props.summary.ownMoney, 0))
const splitResult = computed(() => Math.abs(props.summary.lifetimeResult))
const splitTotal = computed(() => splitBase.value + splitResult.value)

function shareOf(value: number): number {
  return splitTotal.value ? (value / splitTotal.value) * 100 : 0
}

const legendEntries = computed(() => [
  {
    key: 'own-money',
    label: 'Capital próprio',
    hint: 'Dinheiro novo que você colocou nas carteiras, menos o que sacou para o banco.',
    color: 'var(--primary)',
    share: fmt.pct(shareOf(splitBase.value)),
    value: props.summary.ownMoney,
  },
  {
    key: 'lifetime-result',
    label: lifetimeIsUp.value ? 'Resultado total' : 'Perda total',
    color: lifetimeIsUp.value ? 'var(--up)' : 'var(--down)',
    share: fmt.pct(shareOf(splitResult.value)),
    value: props.summary.lifetimeResult,
  },
])

function signClass(isUp: boolean): string {
  return isUp ? 'gl-up' : 'gl-down'
}
</script>

<template>
  <Card class="mb-0" data-testid="overview-health-card">
    <CardBody>
      <div class="fixed-grid has-1-cols-mobile has-3-cols-desktop mb-0">
        <div class="grid is-gap-5">
          <div class="cell" data-testid="health-patrimony">
            <div class="is-flex is-align-items-center is-justify-content-space-between is-gap-1">
              <div class="kpi-label">Patrimônio</div>
              <b-tag
                :type="lifetimeIsUp ? 'is-success is-light' : 'is-danger is-light'"
                data-testid="health-tag"
              >
                {{ lifetimeIsUp ? 'Acima do capital próprio' : 'Abaixo do capital próprio' }}
              </b-tag>
            </div>
            <div class="kpi-value">
              {{ fmt.money(summary.totalCurrentValue, currency, { compact: true }) }}
            </div>
            <div class="health-split is-flex" data-testid="health-split">
              <div
                class="has-background-primary"
                :style="{ flexBasis: `${shareOf(splitBase)}%` }"
              />
              <div
                :class="lifetimeIsUp ? 'has-background-success' : 'has-background-danger'"
                :style="{ flexBasis: `${shareOf(splitResult)}%` }"
              />
            </div>
            <div class="mt-3">
              <AllocationLegend :entries="legendEntries" :currency="currency" />
            </div>
          </div>

          <div class="cell health-column" data-testid="health-lifetime">
            <div class="is-flex is-align-items-center is-justify-content-space-between is-gap-1">
              <div class="kpi-label">Resultado total</div>
              <b-tooltip
                :label="LIFETIME_HINT"
                position="is-left"
                :triggers="['hover', 'focus']"
                multilined
              >
                <span class="health-help" tabindex="0" :aria-label="LIFETIME_HINT">
                  <b-icon icon="help-circle-outline" size="is-small" />
                </span>
              </b-tooltip>
            </div>
            <div class="kpi-value" :class="signClass(lifetimeIsUp)" data-testid="lifetime-value">
              {{ fmt.moneySigned(summary.lifetimeResult, currency, { compact: true }) }}
            </div>
            <div class="kpi-foot">
              <span class="is-size-7" :class="signClass(lifetimeIsUp)">
                {{ fmt.pctSigned(summary.lifetimeResultPct ?? 0) }}
              </span>
              <span class="kpi-sub">
                sobre {{ fmt.money(summary.ownMoney, currency, { compact: true }) }} de capital
                próprio
              </span>
            </div>
            <div class="result-row is-align-items-flex-end mt-3">
              <div class="result-item">
                <div class="result-label">Lucro realizado</div>
                <span class="gl" :class="signClass(summary.realizedProfit >= 0)">
                  {{ fmt.moneySigned(summary.realizedProfit, currency, { compact: true }) }}
                </span>
              </div>
              <b-button
                type="is-ghost"
                size="is-small"
                class="overview-link"
                icon-right="chevron-right"
                data-testid="view-results"
                @click="emit('view-results')"
                >Ver resultados</b-button
              >
            </div>
          </div>

          <div class="cell health-column" data-testid="health-current">
            <div class="is-flex is-align-items-center is-justify-content-space-between is-gap-1">
              <div class="kpi-label">Resultado atual</div>
              <div class="is-flex is-align-items-center is-gap-1">
                <b-tag :type="currentIsUp ? 'is-success is-light' : 'is-danger is-light'">
                  {{ currentIsUp ? 'Posições em alta' : 'Posições em queda' }}
                </b-tag>
                <b-tooltip
                  :label="CURRENT_HINT"
                  position="is-left"
                  :triggers="['hover', 'focus']"
                  multilined
                >
                  <span class="health-help" tabindex="0" :aria-label="CURRENT_HINT">
                    <b-icon icon="help-circle-outline" size="is-small" />
                  </span>
                </b-tooltip>
              </div>
            </div>
            <div class="kpi-value" :class="signClass(currentIsUp)" data-testid="current-value">
              {{ fmt.moneySigned(summary.totalGain, currency, { compact: true }) }}
            </div>
            <div class="kpi-foot">
              <span class="is-size-7" :class="signClass(currentIsUp)">
                {{ fmt.pctSigned(summary.totalGainPct ?? 0) }}
              </span>
              <span class="kpi-sub">
                sobre {{ fmt.money(summary.totalCostBasis, currency, { compact: true }) }} pagos nas
                posições abertas
              </span>
            </div>
            <div class="result-row is-align-items-flex-end mt-3">
              <div class="result-item">
                <div class="result-label">Valor das posições</div>
                <div class="result-value">
                  {{ fmt.money(summary.positionsValue, currency, { compact: true }) }}
                </div>
              </div>
              <b-button
                type="is-ghost"
                size="is-small"
                class="overview-link"
                icon-right="chevron-right"
                data-testid="view-investments"
                @click="emit('view-investments')"
                >Ver investimentos</b-button
              >
            </div>
          </div>
        </div>
      </div>
    </CardBody>
  </Card>
</template>

<style scoped>
.health-split {
  gap: 3px;
  height: 12px;
  border-radius: 99px;
  overflow: hidden;
  background: var(--surface-2);
}

.health-help {
  display: inline-flex;
  color: var(--text-muted);
  cursor: help;
}

.health-help:hover,
.health-help:focus-visible {
  color: var(--text-2);
}

@media screen and (width >= 1024px) {
  .health-column {
    border-left: 1px solid var(--border-2);
    padding-left: 24px;
  }
}
</style>
