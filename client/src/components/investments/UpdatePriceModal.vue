<script setup lang="ts">
import { computed, ref } from 'vue'
import { useToast } from 'buefy'
import AppModal from '@/components/ui/AppModal.vue'
import NumberInput from '@/components/ui/NumberInput.vue'
import { holdingsApi } from '@/api/holdings'
import { fmt } from '@/composables/useFormat'
import type { WalletKind } from '@/types'

const props = defineProps<{
  holdingId: string
  walletId: string
  kind: WalletKind
  walletCurrency: string
  initialValue: number | null
}>()

const emit = defineEmits<{ updated: []; close: [] }>()

const toast = useToast()
const isFund = computed(() => props.kind === 'FUNDS')
const isStock = computed(() => props.kind === 'STOCKS')
const sym = computed(() => fmt.sym(props.walletCurrency))

const priceInput = ref<number | ''>(props.initialValue ?? '')
const submitting = ref(false)

const valid = computed(() => priceInput.value !== '' && Number(priceInput.value) >= 0)

async function submit() {
  if (!valid.value) return
  submitting.value = true
  const amount = Number(priceInput.value)
  try {
    if (isStock.value) {
      await holdingsApi.updateStockHolding(props.walletId, props.holdingId, { currentPrice: amount })
    } else if (props.kind === 'CRYPTO') {
      await holdingsApi.updateCryptoHolding(props.walletId, props.holdingId, { currentPrice: amount })
    } else {
      await holdingsApi.updateFundHolding(props.walletId, props.holdingId, { currentValue: amount })
    }
    toast.open({ message: isFund.value ? 'Valor atual atualizado.' : 'Preço atualizado.', type: 'is-success' })
    emit('updated')
    emit('close')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AppModal
    :title="isFund ? 'Atualizar valor atual' : 'Atualizar preço'"
    :subtitle="isFund ? 'Informe o valor atual do fundo.' : 'Informe o preço atual do ativo.'"
    @close="emit('close')"
  >
    <div class="form-grid">
      <b-field :label="isFund ? 'Valor atual' : 'Preço atual'" style="grid-column: 1/-1">
        <NumberInput v-model="priceInput" :prefix="sym" placeholder="0,00" min="0" />
      </b-field>
    </div>
    <template #footer>
      <b-button outlined type="is-danger" :disabled="submitting" @click="emit('close')">Cancelar</b-button>
      <b-button
        type="is-success"
        icon-left="check"
        class="has-text-light"
        :disabled="!valid"
        :loading="submitting"
        @click="submit"
      >
        Salvar
      </b-button>
    </template>
  </AppModal>
</template>
