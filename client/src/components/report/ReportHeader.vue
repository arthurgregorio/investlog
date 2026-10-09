<script setup lang="ts">
import { computed } from 'vue'
import LogoMark from '@/components/icons/LogoMark.vue'
import ReportTotalsLine from '@/components/report/ReportTotalsLine.vue'
import type { ReportTotals } from '@/utils/reportGrouping'

const MONTHS = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']

const props = defineProps<{
  generatedAt: Date
  filterLabels: string[]
  grandTotals: ReportTotals
}>()

const formattedGeneratedAt = computed(() => {
  const { generatedAt } = props
  const hours = String(generatedAt.getHours()).padStart(2, '0')
  const minutes = String(generatedAt.getMinutes()).padStart(2, '0')
  return `${generatedAt.getDate()} ${MONTHS[generatedAt.getMonth()]} ${generatedAt.getFullYear()} às ${hours}:${minutes}`
})
</script>

<template>
  <header class="report-header">
    <div class="report-header-top">
      <div class="report-brand">
        <span class="brand-mark"><LogoMark :size="22" /></span>
        <div class="report-brand-text">
          <span class="brand-name">Invest<b>Log</b></span>
          <span class="report-brand-url">investlog.com.br</span>
        </div>
      </div>
      <div class="report-meta">
        <span>Gerado em {{ formattedGeneratedAt }}</span>
        <span v-if="filterLabels.length > 0">Filtros: {{ filterLabels.join(', ') }}</span>
      </div>
    </div>
    <div class="report-title-row">
      <h1 class="report-title">Relatório de investimentos</h1>
      <ReportTotalsLine :totals="grandTotals" variant="grand" />
    </div>
  </header>
</template>

<style scoped>
.report-header {
  padding-bottom: 18px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.report-header-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.report-brand {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--primary);
  font-weight: 700;
  font-size: 15px;
}

.report-brand-text {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.report-brand-url {
  font-size: 11px;
  font-weight: 500;
  color: var(--text-muted);
  margin-top: -5px;
}

.report-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 50px;
}

.report-title {
  font-size: 16px;
  font-weight: 800;
  letter-spacing: -0.02em;
  margin: 0;
  white-space: normal;
}

.report-meta {
  font-size: 12.5px;
  color: var(--text-muted);
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: flex-end;
  text-align: right;
}
</style>
