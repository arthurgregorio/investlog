<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { DialogProgrammatic as Dialog } from 'buefy'
import PositionAdder from '@/components/investments/PositionAdder.vue'
import { holdingsApi } from '@/api/holdings'
import { fmt } from '@/composables/useFormat'
import type { FundHoldingDetail, HoldingDetail, HoldingRow, StockHoldingDetail } from '@/types'

const props = defineProps<{ row: HoldingRow }>()
const emit = defineEmits<{
  deleted: []
  positionAdded: []
}>()

const detail = ref<HoldingDetail | null>(null)
const loading = ref(false)
const adding = ref(false)

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

async function onPositionAdded() {
  if (isStock.value) {
    detail.value = await holdingsApi.getStockHolding(props.row.walletId, props.row.id)
  } else if (props.row.kind === 'CRYPTO') {
    detail.value = await holdingsApi.getCryptoHolding(props.row.walletId, props.row.id)
  } else {
    detail.value = await holdingsApi.getFundHolding(props.row.walletId, props.row.id)
  }
  adding.value = false
  emit('positionAdded')
}

async function confirmRemove() {
  Dialog.confirm({
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
        </tr>
      </thead>
      <tbody>
        <template v-if="fundDetail">
          <tr v-for="contribution in fundDetail.contributions" :key="contribution.id">
            <td>{{ fmt.date(contribution.contributionDate) }}</td>
            <td class="c-num">{{ fmt.money(contribution.amount, row.walletCurrency) }}</td>
          </tr>
        </template>
        <template v-else-if="tradeDetail">
          <tr v-for="lot in tradeDetail.lots" :key="lot.id">
            <td>{{ fmt.date(lot.lotDate) }}</td>
            <td class="c-num">{{ fmt.qty(lot.quantity) }}</td>
            <td class="c-num">{{ fmt.money(lot.price, row.walletCurrency) }}</td>
            <td class="c-num">{{ fmt.money(lot.quantity * lot.price, row.walletCurrency) }}</td>
          </tr>
        </template>
      </tbody>
    </table>

    <PositionAdder
      v-if="adding"
      :holding-id="row.id"
      :wallet-id="row.walletId"
      :kind="row.kind"
      :wallet-currency="row.walletCurrency"
      @added="onPositionAdded"
      @close="adding = false"
    />

    <div v-else class="detail-foot">
      <b-button type="is-text" icon-left="plus" @click="adding = true">
        {{ isFund ? 'Registrar novo aporte' : 'Registrar nova compra' }}
      </b-button>
      <div class="detail-foot-right">
        <span v-if="!isFund && quantity" class="avg-note">
          Preço médio
          <b>{{ fmt.money(costBasis / quantity, row.walletCurrency) }}</b>
        </span>
        <b-button
          type="is-text"
          size="is-small"
          icon-left="delete"
          style="color: var(--down)"
          @click="confirmRemove"
        >
          Remover
        </b-button>
      </div>
    </div>
  </div>
</template>
