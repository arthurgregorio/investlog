<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ReportHeader from '@/components/report/ReportHeader.vue'
import ReportTotalsLine from '@/components/report/ReportTotalsLine.vue'
import ReportWalletTable from '@/components/report/ReportWalletTable.vue'
import { holdingsApi } from '@/api/holdings'
import { useCurrencyStore } from '@/stores/currency'
import { useRatesStore } from '@/stores/rates'
import { useWalletsStore } from '@/stores/wallets'
import { useReportFilters } from '@/composables/useReportFilters'
import { useReportPageBreaks } from '@/composables/useReportPageBreaks'
import { groupHoldingsForReport } from '@/utils/reportGrouping'
import type { HoldingRow } from '@/types'

const currencyStore = useCurrencyStore()
const ratesStore = useRatesStore()
const walletsStore = useWalletsStore()
const { kindFilter, walletIdFilter, typeLabelFilter, searchFilter, activeFilterLabels } =
  useReportFilters()

const loading = ref(true)
const holdings = ref<HoldingRow[]>([])
const generatedAt = new Date()

const grouping = computed(() => groupHoldingsForReport(holdings.value, currencyStore.convert))

const reportPaperRef = ref<HTMLElement | null>(null)
const { pageBreakOffsets } = useReportPageBreaks(
  reportPaperRef,
  () => [loading.value, holdings.value.length] as const,
)

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
    <div class="no-print">
      <div class="is-flex is-align-items-center is-justify-content-space-between">
        <RouterLink to="/investments" class="back-link">
          <b-icon icon="arrow-left" size="is-small" /> Voltar
        </RouterLink>
        <b-button type="is-primary" icon-left="printer" @click="print"> Imprimir </b-button>
      </div>
    </div>

    <b-loading :is-full-page="false" :model-value="loading" />

    <EmptyState
      v-if="!loading && holdings.length === 0"
      icon="file-document-outline"
      title="Nenhum investimento para este relatório"
      text="Ajuste os filtros na tela de Investimentos e gere o relatório novamente."
    />

    <div v-else-if="!loading" ref="reportPaperRef" class="report-paper" data-theme="light">
      <div
        v-for="(offset, index) in pageBreakOffsets"
        :key="offset"
        class="report-page-break no-print"
        :style="{ top: `${offset}px` }"
      >
        <span class="report-page-break-label">Página {{ index + 2 }}</span>
      </div>

      <ReportHeader
        :generated-at="generatedAt"
        :filter-labels="activeFilterLabels"
        :grand-totals="grouping.grandTotals"
      />

      <section
        v-for="kindGroup in grouping.kindGroups"
        :key="kindGroup.kind"
        class="report-kind-section"
      >
        <div class="report-kind-head">
          <h2>{{ kindGroup.label }}</h2>
          <ReportTotalsLine :totals="kindGroup.totals" variant="kind" />
        </div>

        <div v-for="subGroup in kindGroup.subGroups" :key="subGroup.key" class="report-subgroup">
          <div class="report-subgroup-head">
            <h3 class="report-subgroup-title">{{ subGroup.label }}</h3>
            <ReportTotalsLine :totals="subGroup.totals" variant="subgroup" />
          </div>

          <ReportWalletTable
            v-for="walletGroup in subGroup.walletGroups"
            :key="walletGroup.walletId"
            :wallet-group="walletGroup"
          />
        </div>
      </section>
    </div>
  </div>
</template>
