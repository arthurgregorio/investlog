<script setup lang="ts">
import { computed, ref } from 'vue'
import { ToastProgrammatic as Toast } from 'buefy'
import NumberInput from '@/components/ui/NumberInput.vue'
import DateInput from '@/components/ui/DateInput.vue'
import { holdingsApi } from '@/api/holdings'
import { fmt } from '@/composables/useFormat'
import type { WalletKind } from '@/types'

const props = defineProps<{
  holdingId: string
  walletId: string
  kind: WalletKind
  walletCurrency: string
}>()

const emit = defineEmits<{
  added: []
  close: []
}>()

const isFund = computed(() => props.kind === 'FUNDS')
const sym = computed(() => fmt.sym(props.walletCurrency))

const date = ref<Date | null>(new Date())
const quantity = ref<number | ''>('')
const price = ref<number | ''>('')
const amount = ref<number | ''>('')
const submitting = ref(false)

const valid = computed(() =>
  isFund.value
    ? Number(amount.value) > 0 && !!date.value
    : Number(quantity.value) > 0 && Number(price.value) > 0 && !!date.value,
)

async function submit() {
  if (!valid.value || !date.value) return
  submitting.value = true
  const dateStr = date.value.toISOString().slice(0, 10)
  try {
    if (isFund.value) {
      await holdingsApi.addContribution(props.walletId, props.holdingId, {
        contributionDate: dateStr,
        amount: Number(amount.value),
      })
      Toast.open({ message: 'Aporte registrado!', type: 'is-success' })
    } else if (props.kind === 'STOCKS') {
      await holdingsApi.addStockLot(props.walletId, props.holdingId, {
        lotDate: dateStr,
        quantity: Number(quantity.value),
        price: Number(price.value),
      })
      Toast.open({ message: 'Compra registrada!', type: 'is-success' })
    } else {
      await holdingsApi.addCryptoLot(props.walletId, props.holdingId, {
        lotDate: dateStr,
        quantity: Number(quantity.value),
        price: Number(price.value),
      })
      Toast.open({ message: 'Compra registrada!', type: 'is-success' })
    }
    emit('added')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="adder">
    <div class="adder-fields">
      <label class="ad-f"><span>Data</span><DateInput v-model="date" /></label>
      <label v-if="isFund" class="ad-f">
        <span>Valor aportado</span>
        <NumberInput v-model="amount" :prefix="sym" placeholder="0,00" />
      </label>
      <template v-else>
        <label class="ad-f"><span>Quantidade</span><NumberInput v-model="quantity" placeholder="0" /></label>
        <label class="ad-f"><span>Preço</span><NumberInput v-model="price" :prefix="sym" placeholder="0,00" /></label>
      </template>
    </div>
    <div class="adder-actions">
      <b-button size="is-small" type="is-dark" outlined :disabled="submitting" @click="emit('close')">Cancelar</b-button>
      <b-button
        size="is-small"
        type="is-primary"
        icon-left="check"
        :disabled="!valid"
        :loading="submitting"
        @click="submit"
      >
        {{ isFund ? 'Adicionar aporte' : 'Adicionar compra' }}
      </b-button>
    </div>
  </div>
</template>
