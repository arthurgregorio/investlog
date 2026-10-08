<script setup lang="ts">
import { computed } from 'vue'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import WalletHighlightCard, {
  type WalletHighlightRail,
} from '@/components/wallets/WalletHighlightCard.vue'
import { fmt } from '@/composables/useFormat'
import { useHoldingsListStore } from '@/stores/holdingsList'
import { WALLET_TYPES } from '@/utils/walletTypes'
import type { WalletDetail, WalletPerformer } from '@/types'

const GAIN_EPSILON = 0.0001

const props = defineProps<{
  detail: WalletDetail
  isAdmin: boolean
}>()

const emit = defineEmits<{
  rename: []
  remove: []
  move: []
  reinvest: []
  'view-holdings': []
}>()

interface Highlight {
  testId: string
  label: string
  rail: WalletHighlightRail
  name: string | null
  value: string
  valueClass: string
}

const holdingsListStore = useHoldingsListStore()

const walletType = computed(() => WALLET_TYPES[props.detail.kind])

const resultDirection = computed(() => {
  const gain = props.detail.gain
  if (gain > GAIN_EPSILON) return 'gl-up'
  if (gain < -GAIN_EPSILON) return 'gl-down'
  return 'gl-flat'
})

const activitySummary = computed(() => {
  const activity = props.detail.activity
  const parts = [
    `${activity.investmentCount} ${activity.investmentCount === 1 ? 'investimento' : 'investimentos'}`,
    `${activity.transactionCount} ${activity.transactionCount === 1 ? 'lançamento' : 'lançamentos'}`,
  ]
  if (activity.walletAgeInDays != null) parts.push(`${activity.walletAgeInDays} dias`)
  return parts.join(' · ')
})

function gainTextClass(gain: number) {
  if (gain > GAIN_EPSILON) return 'has-text-success-on-scheme'
  if (gain < -GAIN_EPSILON) return 'has-text-danger-on-scheme'
  return ''
}

function performerHighlight(
  testId: string,
  label: string,
  rail: WalletHighlightRail,
  performer: WalletPerformer | null,
): Highlight {
  return {
    testId,
    label,
    rail,
    name: performer ? (performer.ticker ?? performer.name) : null,
    value: performer ? fmt.pctSigned(performer.gainPct) : '',
    valueClass: performer ? gainTextClass(performer.gainPct) : '',
  }
}

const largestHoldingTicker = computed(() => {
  const largestHoldingName = props.detail.largestHoldingName
  if (!largestHoldingName) return null
  const match = holdingsListStore.rows.find((row) => row.name === largestHoldingName)
  return match?.ticker ?? largestHoldingName
})

const highlights = computed<Highlight[]>(() => [
  performerHighlight('highlight-best', 'Melhor desempenho', 'up', props.detail.bestPerformer),
  performerHighlight('highlight-worst', 'Pior desempenho', 'down', props.detail.worstPerformer),
  {
    testId: 'highlight-largest',
    label: 'Maior posição',
    rail: 'largest',
    name: largestHoldingTicker.value,
    value:
      props.detail.largestHoldingShare == null ? '' : fmt.pct(props.detail.largestHoldingShare),
    valueClass: '',
  },
])
</script>

<template>
  <Card class="mb-0">
    <CardBody>
      <div class="wd-header-row">
        <div class="wd-identity">
          <span class="wd-kind-mark" :style="{ background: walletType.accent }">
            <b-icon :icon="walletType.icon" size="is-small" />
          </span>
          <div>
            <div class="is-flex is-align-items-center is-gap-1">
              <h1 class="page-title m-0">{{ detail.name }}</h1>
              <b-button
                type="is-ghost"
                size="is-small"
                icon-left="pencil"
                aria-label="Renomear carteira"
                @click="emit('rename')"
              />
            </div>
            <div class="is-flex is-align-items-center is-flex-wrap-wrap is-gap-1 mt-1">
              <span class="type-tag" :class="`tt-${detail.kind.toLowerCase()}`">
                {{ walletType.label }}
              </span>
              <span class="wd-currency">{{ detail.currency }}</span>
              <span class="wd-activity">{{ activitySummary }}</span>
            </div>
          </div>
        </div>

        <div class="wd-figures">
          <div>
            <div class="kpi-label">Total investido</div>
            <div class="wd-figure-value">
              {{ fmt.money(detail.totalInvested, detail.currency) }}
            </div>
          </div>
          <div>
            <div class="kpi-label">Valor atual</div>
            <div class="wd-figure-value">
              {{ fmt.money(detail.currentValue, detail.currency) }}
            </div>
          </div>
          <div>
            <div class="kpi-label">Resultado</div>
            <div class="wd-figure-value" :class="resultDirection" data-testid="wallet-result">
              {{ fmt.moneySigned(detail.gain, detail.currency) }}
              <span v-if="detail.gainPct != null" class="wd-figure-pct">{{
                fmt.pctSigned(detail.gainPct)
              }}</span>
            </div>
          </div>
        </div>

        <div class="wd-actions">
          <b-dropdown aria-role="list" position="is-bottom-left" data-testid="wallet-actions">
            <template #trigger>
              <b-button size="is-small" icon-right="menu-down">Ações</b-button>
            </template>

            <b-dropdown-item aria-role="listitem" @click="emit('view-holdings')">
              <b-icon icon="format-list-bulleted" size="is-small" /> Ver investimentos
            </b-dropdown-item>
            <b-dropdown-item aria-role="listitem" data-testid="wallet-move" @click="emit('move')">
              <b-icon icon="swap-horizontal" size="is-small" /> Mover
            </b-dropdown-item>
            <b-dropdown-item
              aria-role="listitem"
              data-testid="wallet-reinvest"
              @click="emit('reinvest')"
            >
              <b-icon icon="autorenew" size="is-small" /> Reinvestir
            </b-dropdown-item>
            <template v-if="isAdmin">
              <hr class="dropdown-divider" />
              <b-dropdown-item
                aria-role="listitem"
                class="has-text-danger"
                data-testid="wallet-remove"
                @click="emit('remove')"
              >
                <b-icon icon="delete" size="is-small" /> Remover
              </b-dropdown-item>
            </template>
          </b-dropdown>
        </div>
      </div>
    </CardBody>

    <div class="wd-highlight-strip">
      <WalletHighlightCard
        v-for="highlight in highlights"
        :key="highlight.testId"
        :label="highlight.label"
        :rail="highlight.rail"
        :data-testid="highlight.testId"
      >
        <div
          v-if="highlight.name"
          class="is-flex is-align-items-center is-justify-content-space-between mt-1"
        >
          <span class="has-text-weight-bold">{{ highlight.name }}</span>
          <span class="has-text-weight-bold is-size-6" :class="highlight.valueClass">
            {{ highlight.value }}
          </span>
        </div>
      </WalletHighlightCard>
    </div>
  </Card>
</template>

<style scoped>
.wd-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  flex-wrap: wrap;
}

.wd-identity {
  display: flex;
  align-items: center;
  gap: 15px;
  min-width: 0;
}

.wd-kind-mark {
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  border-radius: var(--radius);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--bulma-white);
}

.wd-currency {
  font-size: 12.5px;
  color: var(--text-2);
  font-weight: 500;
}

.wd-activity {
  font-size: 12.5px;
  color: var(--text-muted);
}

.wd-figures {
  display: flex;
  align-items: flex-start;
  gap: 30px;
  margin-left: auto;
}

.wd-figure-value {
  font-size: 15px;
  font-weight: 700;
  margin-top: 5px;
  font-variant-numeric: tabular-nums;
}

.wd-figure-pct {
  font-weight: 600;
  opacity: 0.85;
}

.wd-actions {
  display: flex;
  align-items: center;
  gap: 9px;
  padding-left: 28px;
  border-left: 1px solid var(--border);
  align-self: center;
}

.wd-highlight-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 1px;
  background: var(--bulma-border-weak);
  border-top: 1px solid var(--bulma-border-weak);
  border-radius: 0 0 var(--radius) var(--radius);
  overflow: hidden;
}

@media (width <= 860px) {
  .wd-header-row {
    align-items: flex-start;
  }

  .wd-figures {
    margin-left: 0;
    width: 100%;
    gap: 24px;
  }

  .wd-actions {
    padding-left: 0;
    border-left: 0;
    width: 100%;
    align-self: flex-start;
    justify-content: flex-end;
  }
}

@media (width <= 680px) {
  .wd-figures {
    flex-direction: column;
    gap: 14px;
  }

  .wd-actions {
    flex-wrap: wrap;
  }

  .wd-identity {
    align-items: flex-start;
  }
}
</style>
