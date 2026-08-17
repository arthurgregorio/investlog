<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import LogoMark from '@/components/icons/LogoMark.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import GainChip from '@/components/ui/GainChip.vue'
import { holdingsApi } from '@/api/holdings'
import { useCurrencyStore } from '@/stores/currency'
import { useRatesStore } from '@/stores/rates'
import { useWalletsStore } from '@/stores/wallets'
import { fmt } from '@/composables/useFormat'
import { groupHoldingsForReport } from '@/utils/reportGrouping'
import { WALLET_TYPES } from '@/utils/walletTypes'
import type { HoldingRow, WalletKind } from '@/types'

const VALID_KINDS: WalletKind[] = ['STOCKS', 'CRYPTO', 'FUNDS']
const MONTHS = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']

const route = useRoute()
const currencyStore = useCurrencyStore()
const ratesStore = useRatesStore()
const walletsStore = useWalletsStore()

const loading = ref(true)
const holdings = ref<HoldingRow[]>([])
const generatedAt = new Date()

const kindFilter = computed<WalletKind | undefined>(() => {
  const filterParam = route.query.filter
  return typeof filterParam === 'string' && VALID_KINDS.includes(filterParam as WalletKind)
    ? (filterParam as WalletKind)
    : undefined
})
const walletIdFilter = computed(() =>
  typeof route.query.walletId === 'string' ? route.query.walletId : undefined,
)
const typeLabelFilter = computed(() =>
  typeof route.query.type === 'string' ? route.query.type : undefined,
)
const searchFilter = computed(() =>
  typeof route.query.search === 'string' ? route.query.search : undefined,
)

const grouping = computed(() => groupHoldingsForReport(holdings.value, currencyStore.convert))

const formattedGeneratedAt = computed(() => {
  const hours = String(generatedAt.getHours()).padStart(2, '0')
  const minutes = String(generatedAt.getMinutes()).padStart(2, '0')
  return `${generatedAt.getDate()} ${MONTHS[generatedAt.getMonth()]} ${generatedAt.getFullYear()} às ${hours}:${minutes}`
})

const activeFilterLabels = computed(() => {
  const labels: string[] = []
  if (kindFilter.value) labels.push(WALLET_TYPES[kindFilter.value].label)
  if (typeLabelFilter.value) labels.push(typeLabelFilter.value)
  if (walletIdFilter.value) {
    labels.push(walletsStore.walletById(walletIdFilter.value)?.name ?? 'Carteira selecionada')
  }
  if (searchFilter.value) labels.push(`"${searchFilter.value}"`)
  return labels
})

async function load() {
  loading.value = true
  try {
    await Promise.all([currencyStore.load(), ratesStore.load(), walletsStore.load()])
    holdings.value = await holdingsApi.findAllForReport({
      kind: kindFilter.value,
      typeLabel: typeLabelFilter.value,
      walletId: walletIdFilter.value,
      search: searchFilter.value,
    })
  } finally {
    loading.value = false
  }
}

onMounted(load)

function print() {
  window.print()
}
</script>

<template>
  <div class="page report-page">
    <div class="report-toolbar no-print">
      <RouterLink to="/investments" class="back-link">
        <b-icon icon="arrow-left" size="is-small" /> Voltar
      </RouterLink>
      <b-button type="is-primary" class="has-text-light" icon-left="printer" @click="print">
        Imprimir
      </b-button>
    </div>

    <b-loading :is-full-page="false" :active="loading" />

    <EmptyState
      v-if="!loading && holdings.length === 0"
      icon="file-document-outline"
      title="Nenhum investimento para este relatório"
      text="Ajuste os filtros na tela de Investimentos e gere o relatório novamente."
    />

    <template v-else-if="!loading">
      <header class="report-header">
        <div class="report-header-top">
          <div class="report-brand">
            <span class="brand-mark"><LogoMark :size="22" /></span>
            <span class="brand-name">Invest<b>Log</b></span>
          </div>
          <div class="report-meta">
            <span>Gerado em {{ formattedGeneratedAt }}</span>
            <span v-if="activeFilterLabels.length > 0"
              >· Filtros: {{ activeFilterLabels.join(', ') }}</span
            >
          </div>
        </div>
        <div class="report-title-row">
          <h1 class="report-title">Relatório de investimentos</h1>
          <div class="report-grand-total">
            <div class="rgt-item">
              <span class="rgt-label">Investido</span>
              <span class="rgt-value">{{
                fmt.money(grouping.grandTotals.costBasis, currencyStore.displayCurrency)
              }}</span>
            </div>
            <div class="rgt-item">
              <span class="rgt-label">Valor atual</span>
              <span class="rgt-value">{{
                fmt.money(grouping.grandTotals.currentValue, currencyStore.displayCurrency)
              }}</span>
            </div>
            <div class="rgt-item">
              <span class="rgt-label">Resultado</span>
              <GainChip
                :value="grouping.grandTotals.gain"
                :pct="grouping.grandTotals.gainPct"
                :cur="currencyStore.displayCurrency"
              />
            </div>
          </div>
        </div>
      </header>

      <section
        v-for="kindGroup in grouping.kindGroups"
        :key="kindGroup.kind"
        class="report-kind-section"
      >
        <div class="report-kind-head">
          <h2>{{ kindGroup.label }}</h2>
          <div class="report-subtotal-line">
            <div class="stotal-item">
              <span class="stotal-label">Investido</span>
              <span class="stotal-value">{{
                fmt.money(kindGroup.totals.costBasis, currencyStore.displayCurrency)
              }}</span>
            </div>
            <div class="stotal-item">
              <span class="stotal-label">Valor atual</span>
              <span class="stotal-value">{{
                fmt.money(kindGroup.totals.currentValue, currencyStore.displayCurrency)
              }}</span>
            </div>
            <div class="stotal-item">
              <span class="stotal-label">Resultado</span>
              <GainChip
                :value="kindGroup.totals.gain"
                :pct="kindGroup.totals.gainPct"
                :cur="currencyStore.displayCurrency"
              />
            </div>
          </div>
        </div>

        <div v-for="subGroup in kindGroup.subGroups" :key="subGroup.key" class="report-subgroup">
          <h3 class="report-subgroup-title">{{ subGroup.label }}</h3>

          <div
            v-for="walletGroup in subGroup.walletGroups"
            :key="walletGroup.walletId"
            class="report-wallet-block"
          >
            <div class="report-wallet-name">{{ walletGroup.walletName }}</div>
            <table class="report-table">
              <thead>
                <tr>
                  <th>Investimento</th>
                  <th class="c-num">Qtd.</th>
                  <th class="c-num">Preço atual</th>
                  <th class="c-num">Investido</th>
                  <th class="c-num">Valor atual</th>
                  <th class="c-num">Resultado</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in walletGroup.rows" :key="row.holding.id">
                  <td>{{ row.holding.ticker ?? row.holding.name }}</td>
                  <td class="c-num">
                    {{ row.holding.quantity == null ? '—' : fmt.qty(row.holding.quantity) }}
                  </td>
                  <td class="c-num">
                    {{
                      row.currentPrice == null
                        ? '—'
                        : fmt.money(row.currentPrice, currencyStore.displayCurrency)
                    }}
                  </td>
                  <td class="c-num">
                    {{ fmt.money(row.costBasis, currencyStore.displayCurrency) }}
                  </td>
                  <td class="c-num">
                    {{ fmt.money(row.currentValue, currencyStore.displayCurrency) }}
                  </td>
                  <td class="c-num">
                    <GainChip
                      :value="row.gain"
                      :pct="row.gainPct"
                      :cur="currencyStore.displayCurrency"
                    />
                  </td>
                </tr>
                <tr class="report-subtotal-row">
                  <td colspan="3"></td>
                  <td class="c-num">
                    {{ fmt.money(walletGroup.totals.costBasis, currencyStore.displayCurrency) }}
                  </td>
                  <td class="c-num">
                    {{ fmt.money(walletGroup.totals.currentValue, currencyStore.displayCurrency) }}
                  </td>
                  <td class="c-num">
                    <GainChip
                      :value="walletGroup.totals.gain"
                      :pct="walletGroup.totals.gainPct"
                      :cur="currencyStore.displayCurrency"
                    />
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="report-subtotal-line report-subgroup-subtotal">
            <div class="stotal-item">
              <span class="stotal-label">Investido</span>
              <span class="stotal-value">{{
                fmt.money(subGroup.totals.costBasis, currencyStore.displayCurrency)
              }}</span>
            </div>
            <div class="stotal-item">
              <span class="stotal-label">Valor atual</span>
              <span class="stotal-value">{{
                fmt.money(subGroup.totals.currentValue, currencyStore.displayCurrency)
              }}</span>
            </div>
            <div class="stotal-item">
              <span class="stotal-label">Resultado</span>
              <GainChip
                :value="subGroup.totals.gain"
                :pct="subGroup.totals.gainPct"
                :cur="currencyStore.displayCurrency"
              />
            </div>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>
