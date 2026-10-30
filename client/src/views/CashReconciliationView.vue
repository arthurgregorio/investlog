<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import CashReconciliationCard from '@/components/wallets/CashReconciliationCard.vue'
import { cashApi } from '@/api/cash'
import { useRatesStore } from '@/stores/rates'
import { useWalletsStore } from '@/stores/wallets'
import type { CashReconciliation } from '@/types'

const router = useRouter()
const walletsStore = useWalletsStore()
const ratesStore = useRatesStore()

const reconciliations = ref<CashReconciliation[]>([])
const initialCount = ref(0)
const loading = ref(false)
const loaded = ref(false)
const registered = ref<string[]>([])

const reconciledCount = computed(() => initialCount.value - reconciliations.value.length)

async function loadReconciliations() {
  loading.value = true
  try {
    reconciliations.value = await cashApi.findReconciliation()
    loaded.value = true
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadReconciliations(), walletsStore.load(), ratesStore.load()])
  initialCount.value = reconciliations.value.length
})

async function onRegistered(descriptions: string[]) {
  registered.value.push(...descriptions)
  await Promise.all([loadReconciliations(), walletsStore.refresh()])
}
</script>

<template>
  <div class="page">
    <b-loading :is-full-page="false" :model-value="loading" />

    <div>
      <b-button
        type="is-ghost"
        icon-left="arrow-left"
        size="is-small"
        @click="router.push({ name: 'wallets' })"
      >
        Carteiras
      </b-button>
    </div>

    <PageHeader
      title="Conciliar caixa"
      description="Informe para onde foi o dinheiro dos resgates realizados"
    />

    <template v-if="loaded">
      <Card v-if="initialCount > 0" class="mb-0" data-testid="reconciliation-progress">
        <CardBody>
          <div class="is-flex is-justify-content-space-between is-size-7 mb-2">
            <strong>{{ reconciledCount }} de {{ initialCount }} carteiras conciliadas</strong>
          </div>
          <progress
            class="progress is-success is-small mb-0"
            :value="reconciledCount"
            :max="initialCount"
          />
        </CardBody>
      </Card>

      <CashReconciliationCard
        v-for="reconciliation in reconciliations"
        :key="reconciliation.walletId"
        :reconciliation="reconciliation"
        @registered="onRegistered"
      />

      <b-message
        v-if="reconciliations.length === 0"
        type="is-success"
        has-icon
        class="mb-0"
        data-testid="reconciliation-done"
      >
        <strong>Todo o caixa está conciliado.</strong>
        <ul v-if="registered.length > 0" class="mt-2" data-testid="reconciliation-registered">
          <li v-for="description in registered" :key="description">{{ description }}</li>
        </ul>
        <p class="mt-2">
          <RouterLink :to="{ name: 'overview' }">Ver a visão geral</RouterLink>
        </p>
      </b-message>
    </template>
  </div>
</template>
