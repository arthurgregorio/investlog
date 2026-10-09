<script setup lang="ts">
import { computed, onMounted, ref, useTemplateRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AllocationDonut from '@/components/charts/AllocationDonut.vue'
import HoldingsTable from '@/components/investments/HoldingsTable.vue'
import MoveHoldingsModal from '@/components/investments/MoveHoldingsModal.vue'
import ReinvestModal from '@/components/investments/ReinvestModal.vue'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import WalletDetailHeader from '@/components/wallets/WalletDetailHeader.vue'
import WalletDetailTabs from '@/components/wallets/WalletDetailTabs.vue'
import WalletPerformanceCard from '@/components/wallets/WalletPerformanceCard.vue'
import { holdingsApi } from '@/api/holdings'
import { useWalletActions } from '@/composables/useWalletActions'
import { useAuthStore } from '@/stores/auth'
import { useHoldingsListStore } from '@/stores/holdingsList'
import { useWalletDetailStore } from '@/stores/walletDetail'
import { useWalletMovesStore } from '@/stores/walletMoves'
import { useWalletsStore } from '@/stores/wallets'
import type { HoldingRow } from '@/types'

const PAGE_SIZE = 10

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const walletDetailStore = useWalletDetailStore()
const walletsStore = useWalletsStore()
const holdingsListStore = useHoldingsListStore()
const walletMovesStore = useWalletMovesStore()

const walletId = computed(() => route.params.id as string)
const detail = computed(() => walletDetailStore.detail)
const holdingsTable = useTemplateRef('holdingsTable')
const moveModalOpen = ref(false)
const reinvestModalOpen = ref(false)
const allocationRows = ref<HoldingRow[]>([])
const { renameWallet, confirmRemoveWallet } = useWalletActions(detail)

async function loadAllocationRows() {
  const requestedWalletId = walletId.value
  const rows = await holdingsApi.findAllForReport({ walletId: requestedWalletId })
  if (requestedWalletId === walletId.value) allocationRows.value = rows
}

async function loadAll() {
  holdingsTable.value?.collapse()
  await Promise.all([
    loadAllocationRows(),
    walletDetailStore.load(walletId.value),
    loadHoldingsPage(0),
    walletMovesStore.load(walletId.value, 0),
  ])
}

onMounted(loadAll)
watch(walletId, loadAll)

function loadHoldingsPage(page: number) {
  return holdingsListStore.loadKind('all', page, { walletId: walletId.value, size: PAGE_SIZE })
}

async function onPositionsChanged() {
  await Promise.all([loadAll(), walletsStore.refresh()])
}

function goToWallets() {
  router.push({ name: 'wallets' })
}
</script>

<template>
  <div class="page">
    <b-loading :is-full-page="false" :model-value="walletDetailStore.loading" />

    <template v-if="detail">
      <div>
        <b-button type="is-ghost" icon-left="arrow-left" size="is-small" @click="goToWallets">
          Carteiras
        </b-button>
      </div>

      <WalletDetailHeader
        :detail="detail"
        :is-admin="auth.isAdmin"
        @rename="renameWallet"
        @remove="confirmRemoveWallet"
        @move="moveModalOpen = true"
        @reinvest="reinvestModalOpen = true"
        @view-holdings="
          router.push({ name: 'investments', query: { filter: detail.kind, walletId: detail.id } })
        "
      />

      <WalletPerformanceCard :detail="detail" />

      <Card class="mb-0" data-testid="allocation-card">
        <CardBody>
          <AllocationDonut
            :rows="allocationRows"
            :currency="detail.currency"
            :groupable="detail.kind === 'STOCKS'"
          />
        </CardBody>
      </Card>

      <WalletDetailTabs :wallet-id="detail.id" :holdings-count="holdingsListStore.totalElements">
        <EmptyState
          v-if="holdingsListStore.loaded && holdingsListStore.rows.length === 0"
          icon="wallet-outline"
          title="Nenhum investimento nesta carteira"
          text="Adicione um investimento para começar a acompanhar esta carteira."
        />
        <HoldingsTable
          v-else
          ref="holdingsTable"
          :rows="holdingsListStore.rows"
          :loading="holdingsListStore.loading"
          :page="holdingsListStore.page"
          :page-size="holdingsListStore.pageSize"
          :total-elements="holdingsListStore.totalElements"
          @page-change="loadHoldingsPage"
          @holding-changed="loadAll"
          @position-added="loadAll"
          @relocated="onPositionsChanged"
        />
      </WalletDetailTabs>

      <MoveHoldingsModal
        v-if="moveModalOpen"
        :origin-wallet-id="detail.id"
        @moved="onPositionsChanged"
        @close="moveModalOpen = false"
      />

      <ReinvestModal
        v-if="reinvestModalOpen"
        :wallet-id="detail.id"
        @reinvested="onPositionsChanged"
        @close="reinvestModalOpen = false"
      />
    </template>
  </div>
</template>
