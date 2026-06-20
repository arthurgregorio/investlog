<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { BButton, useDialog, useToast } from 'buefy'
import AddPositionModal from '@/components/investments/AddPositionModal.vue'
import UpdatePriceModal from '@/components/investments/UpdatePriceModal.vue'
import { holdingsApi } from '@/api/holdings'
import { fmt } from '@/composables/useFormat'
import type { FundHoldingDetail, HoldingDetail, HoldingRow, StockHoldingDetail } from '@/types'

const props = defineProps<{ row: HoldingRow }>()
const emit = defineEmits<{
  deleted: []
  positionAdded: []
}>()

const dialog = useDialog()
const toast = useToast()

const detail = ref<HoldingDetail | null>(null)
const loading = ref(false)
const showAddPositionModal = ref(false)
const showUpdatePriceModal = ref(false)

const isFund = computed(() => props.row.kind === 'FUNDS')
const isStock = computed(() => props.row.kind === 'STOCKS')

const fundDetail = computed(() =>
  detail.value && isFund.value ? (detail.value as FundHoldingDetail) : null,
)
const tradeDetail = computed(() =>
  detail.value && !isFund.value ? (detail.value as StockHoldingDetail) : null,
)

const costBasis = computed(() => props.row.costBasis)
const quantity = computed(() => props.row.quantity)

const currentAmount = computed<number | null>(() => {
  if (!detail.value) return null
  return isFund.value
    ? (detail.value as FundHoldingDetail).currentValue
    : (detail.value as StockHoldingDetail).currentPrice
})

onMounted(async () => {
  loading.value = true
  try {
    if (isStock.value) {
      detail.value = await holdingsApi.getStockHolding(props.row.walletId, props.row.id)
    } else if (props.row.kind === 'CRYPTO') {
      detail.value = await holdingsApi.getCryptoHolding(props.row.walletId, props.row.id)
    } else {
      detail.value = await holdingsApi.getFundHolding(props.row.walletId, props.row.id)
    }
  } finally {
    loading.value = false
  }
})

async function reloadDetail() {
  if (isStock.value) {
    detail.value = await holdingsApi.getStockHolding(props.row.walletId, props.row.id)
  } else if (props.row.kind === 'CRYPTO') {
    detail.value = await holdingsApi.getCryptoHolding(props.row.walletId, props.row.id)
  } else {
    detail.value = await holdingsApi.getFundHolding(props.row.walletId, props.row.id)
  }
}

async function onPositionAdded() {
  await reloadDetail()
  emit('positionAdded')
}

async function onPriceUpdated() {
  await reloadDetail()
  emit('positionAdded')
}

async function confirmRemove() {
  dialog.confirm({
    title: 'Remover investimento',
    message: 'Esta ação <strong>não pode ser desfeita</strong>.',
    type: 'is-danger',
    hasIcon: true,
    confirmText: 'Remover',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      if (isStock.value) {
        await holdingsApi.deleteStockHolding(props.row.walletId, props.row.id)
      } else if (props.row.kind === 'CRYPTO') {
        await holdingsApi.deleteCryptoHolding(props.row.walletId, props.row.id)
      } else {
        await holdingsApi.deleteFundHolding(props.row.walletId, props.row.id)
      }
      emit('deleted')
    },
  })
}

function confirmDeleteLot(lotId: string) {
  dialog.confirm({
    title: isFund.value ? 'Remover aporte' : 'Remover compra',
    message: 'Esta ação <strong>não pode ser desfeita</strong>.',
    type: 'is-danger',
    hasIcon: true,
    confirmText: 'Remover',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      if (isStock.value) {
        await holdingsApi.deleteStockLot(props.row.walletId, props.row.id, lotId)
      } else {
        await holdingsApi.deleteCryptoLot(props.row.walletId, props.row.id, lotId)
      }
      toast.open({ message: 'Compra removida.', type: 'is-success' })
      await reloadDetail()
      emit('positionAdded')
    },
  })
}

function confirmDeleteContribution(contributionId: string) {
  dialog.confirm({
    title: 'Remover aporte',
    message: 'Esta ação <strong>não pode ser desfeita</strong>.',
    type: 'is-danger',
    hasIcon: true,
    confirmText: 'Remover',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      await holdingsApi.deleteFundContribution(props.row.walletId, props.row.id, contributionId)
      toast.open({ message: 'Aporte removido.', type: 'is-success' })
      await reloadDetail()
      emit('positionAdded')
    },
  })
}
</script>

<template>
  <div class="detail">
    <b-loading :is-full-page="false" :active="loading" />

    <table v-if="detail" class="sub-table">
      <thead>
        <tr>
          <th>{{ isFund ? 'Data do aporte' : 'Data da compra' }}</th>
          <th v-if="!isFund" class="c-num">Qtd.</th>
          <th v-if="!isFund" class="c-num">Preço</th>
          <th class="c-num">{{ isFund ? 'Valor' : 'Subtotal' }}</th>
          <th class="c-act"></th>
        </tr>
      </thead>
      <tbody>
        <template v-if="fundDetail">
          <tr v-for="contribution in fundDetail.contributions" :key="contribution.id">
            <td>{{ fmt.date(contribution.contributionDate) }}</td>
            <td class="c-num">{{ fmt.money(contribution.amount, row.walletCurrency) }}</td>
            <td class="c-act">
              <b-button
                outlined
                type="is-danger"
                size="is-small"
                icon-left="delete"
                @click.stop="confirmDeleteContribution(contribution.id)"
              />
            </td>
          </tr>
        </template>
        <template v-else-if="tradeDetail">
          <tr v-for="lot in tradeDetail.lots" :key="lot.id">
            <td>{{ fmt.date(lot.lotDate) }}</td>
            <td class="c-num">{{ fmt.qty(lot.quantity) }}</td>
            <td class="c-num">{{ fmt.money(lot.price, row.walletCurrency) }}</td>
            <td class="c-num">{{ fmt.money(lot.quantity * lot.price, row.walletCurrency) }}</td>
            <td class="c-act">
              <b-button
                outlined
                type="is-danger"
                size="is-small"
                icon-left="delete"
                @click.stop="confirmDeleteLot(lot.id)"
              />
            </td>
          </tr>
        </template>
      </tbody>
    </table>

    <div class="detail-foot">
      <b-button size="is-small" type="is-success" outlined icon-left="plus" @click="showAddPositionModal = true">
        {{ isFund ? 'Registrar novo aporte' : 'Registrar nova compra' }}
      </b-button>
      <b-button size="is-small" type="is-info" outlined icon-left="pencil" @click="showUpdatePriceModal = true">
        {{ isFund ? 'Atualizar valor atual' : 'Atualizar preço' }}
      </b-button>
      <b-button outlined type="is-danger" size="is-small" icon-left="delete" @click="confirmRemove">
        Remover
      </b-button>

      <span v-if="!isFund && quantity" class="avg-note">
        Preço médio
        <b>{{ fmt.money(costBasis / quantity, row.walletCurrency) }}</b>
      </span>
    </div>

    <AddPositionModal
      v-if="showAddPositionModal"
      :holding-id="row.id"
      :wallet-id="row.walletId"
      :kind="row.kind"
      :wallet-currency="row.walletCurrency"
      @added="onPositionAdded"
      @close="showAddPositionModal = false"
    />

    <UpdatePriceModal
      v-if="showUpdatePriceModal"
      :holding-id="row.id"
      :wallet-id="row.walletId"
      :kind="row.kind"
      :wallet-currency="row.walletCurrency"
      :initial-value="currentAmount"
      @updated="onPriceUpdated"
      @close="showUpdatePriceModal = false"
    />
  </div>
</template>
