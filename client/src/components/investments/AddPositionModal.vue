<script setup lang="ts">
import { computed, ref } from 'vue'
import { useToast } from 'buefy'
import AppModal from '@/components/ui/AppModal.vue'
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

const emit = defineEmits<{ added: []; close: [] }>()

const toast = useToast()
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
      toast.open({ message: 'Aporte registrado!', type: 'is-success' })
    } else if (props.kind === 'STOCKS') {
      await holdingsApi.addStockLot(props.walletId, props.holdingId, {
        lotDate: dateStr,
        quantity: Number(quantity.value),
        price: Number(price.value),
      })
      toast.open({ message: 'Compra registrada!', type: 'is-success' })
    } else {
      await holdingsApi.addCryptoLot(props.walletId, props.holdingId, {
        lotDate: dateStr,
        quantity: Number(quantity.value),
        price: Number(price.value),
      })
      toast.open({ message: 'Compra registrada!', type: 'is-success' })
    }
    emit('added')
    emit('close')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AppModal
    :title="isFund ? 'Registrar aporte' : 'Registrar compra'"
    :subtitle="
      isFund ? 'Registre um novo aporte neste fundo.' : 'Registre uma nova compra para este ativo.'
    "
    @close="emit('close')"
  >
    <div class="form-grid">
      <b-field label="Data" style="grid-column: 1/-1">
        <DateInput v-model="date" />
      </b-field>
      <b-field v-if="isFund" label="Valor aportado" style="grid-column: 1/-1">
        <NumberInput v-model="amount" :prefix="sym" placeholder="0,00" />
      </b-field>
      <template v-else>
        <b-field label="Quantidade">
          <NumberInput v-model="quantity" placeholder="0" />
        </b-field>
        <b-field label="Preço">
          <NumberInput v-model="price" :prefix="sym" placeholder="0,00" />
        </b-field>
      </template>
    </div>
    <template #footer>
      <b-button outlined type="is-danger" :disabled="submitting" @click="emit('close')"
        >Cancelar</b-button
      >
      <b-button
        type="is-success"
        class="has-text-light"
        icon-left="check"
        :disabled="!valid"
        :loading="submitting"
        @click="submit"
      >
        {{ isFund ? 'Registrar aporte' : 'Registrar compra' }}
      </b-button>
    </template>
  </AppModal>
</template>
