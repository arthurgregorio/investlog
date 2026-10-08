<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import TablePagination from '@/components/ui/TablePagination.vue'
import ReinvestmentsTable from '@/components/investments/ReinvestmentsTable.vue'
import { useResultsStore } from '@/stores/results'
import { useReinvestmentsStore } from '@/stores/reinvestments'
import { fmt } from '@/composables/useFormat'

const REINVESTMENTS_PAGE_SIZE = 10

const resultsStore = useResultsStore()
const reinvestmentsStore = useReinvestmentsStore()
const router = useRouter()

onMounted(() => {
  Promise.all([resultsStore.loadSummary(), reinvestmentsStore.load(0, REINVESTMENTS_PAGE_SIZE)])
})

function onPageChange(page: number) {
  reinvestmentsStore.load(page, REINVESTMENTS_PAGE_SIZE)
}

const summary = computed(() => resultsStore.summary)
const currency = computed(() => summary.value?.displayCurrency ?? 'BRL')
const hasResults = computed(() => (summary.value?.exitCount ?? 0) > 0)
const loading = computed(() => resultsStore.summaryLoading || reinvestmentsStore.loading)
</script>

<template>
  <div class="page">
    <b-loading :is-full-page="false" :model-value="loading" />

    <div class="page-head">
      <h1 class="page-title">Resultados</h1>
      <p class="page-desc">O que já foi resgatado, o lucro realizado e o que foi pago em custos</p>
    </div>

    <template v-if="summary">
      <Card v-if="!hasResults" data-testid="results-empty">
        <div class="p-4">
          <EmptyState
            icon="cash-multiple"
            title="Nenhum resultado realizado ainda"
            text="Resgates e reinvestimentos aparecem aqui depois de registrados."
          >
            <template #action>
              <b-button type="is-primary" @click="router.push({ name: 'wallets' })"
                >Ir para as carteiras</b-button
              >
            </template>
          </EmptyState>
        </div>
      </Card>

      <template v-else>
        <div class="fixed-grid has-4-cols has-1-cols-mobile">
          <div class="grid">
            <div class="cell">
              <Card data-testid="kpi">
                <CardBody>
                  <div class="kpi-label">Total resgatado</div>
                  <div class="kpi-value" data-testid="kpi-withdrawn">
                    {{ fmt.money(summary.totalWithdrawn, currency, { compact: true }) }}
                  </div>
                  <div class="kpi-foot">
                    <span class="kpi-sub">{{ summary.exitCount }} saídas registradas</span>
                  </div>
                </CardBody>
              </Card>
            </div>

            <div class="cell">
              <Card data-testid="kpi">
                <CardBody>
                  <div class="kpi-label">Lucro realizado</div>
                  <div
                    class="kpi-value"
                    :class="summary.totalProfit >= 0 ? 'has-text-success' : 'has-text-danger'"
                    data-testid="kpi-profit"
                  >
                    {{ fmt.moneySigned(summary.totalProfit, currency, { compact: true }) }}
                  </div>
                  <div class="kpi-foot">
                    <span class="kpi-sub">
                      {{ fmt.money(summary.totalNetReceived, currency, { compact: true }) }}
                      líquidos recebidos
                    </span>
                  </div>
                </CardBody>
              </Card>
            </div>

            <div class="cell">
              <Card data-testid="kpi">
                <CardBody>
                  <div class="kpi-label">Taxas pagas</div>
                  <div class="kpi-value" data-testid="kpi-fees">
                    {{ fmt.money(summary.totalFees, currency, { compact: true }) }}
                  </div>
                  <div class="kpi-foot">
                    <span class="kpi-sub">corretagem e custos</span>
                  </div>
                </CardBody>
              </Card>
            </div>

            <div class="cell">
              <Card data-testid="kpi">
                <CardBody>
                  <div class="kpi-label">Impostos pagos</div>
                  <div class="kpi-value" data-testid="kpi-taxes">
                    {{ fmt.money(summary.totalTaxes, currency, { compact: true }) }}
                  </div>
                  <div class="kpi-foot">
                    <span class="kpi-sub">retidos nas saídas</span>
                  </div>
                </CardBody>
              </Card>
            </div>
          </div>
        </div>

        <Card class="table-card" data-testid="reinvestments">
          <div class="px-5 pt-4">
            <div class="chart-title">Reinvestimentos</div>
            <div class="is-size-7 has-text-grey mt-1">Do mais recente para o mais antigo</div>
          </div>
          <div v-if="reinvestmentsStore.rows.length > 0" class="table-wrap">
            <ReinvestmentsTable :rows="reinvestmentsStore.rows" />
            <TablePagination
              :page="reinvestmentsStore.page"
              :page-size="reinvestmentsStore.pageSize"
              :total-elements="reinvestmentsStore.totalElements"
              @page-change="onPageChange"
            />
          </div>
          <div v-else-if="reinvestmentsStore.loaded" class="p-4">
            <EmptyState
              icon="swap-horizontal"
              title="Nenhum reinvestimento"
              text="Reinvestimentos de um investimento em outro aparecem aqui."
            />
          </div>
        </Card>
      </template>
    </template>
  </div>
</template>
