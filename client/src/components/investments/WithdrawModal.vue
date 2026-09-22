<script setup lang="ts">
import { computed, ref } from 'vue'
import { useToast } from 'buefy'
import AppModal from '@/components/ui/AppModal.vue'
import NumberInput from '@/components/ui/NumberInput.vue'
import DateInput from '@/components/ui/DateInput.vue'
import { resultsApi } from '@/api/results'
import { fmt } from '@/composables/useFormat'
import { problemDetailMessage } from '@/utils/apiErrors'
import type { WalletKind } from '@/types'

const props = defineProps<{
  holdingId: string
  walletId: string
  kind: WalletKind
  walletCurrency: string
  remainingQuantity: number | null
  currentValue: number | null
}>()

const emit = defineEmits<{ withdrawn: [completed: boolean]; close: [] }>()

const toast = useToast()
const isFund = computed(() => props.kind === 'FUNDS')
const symbol = computed(() => fmt.sym(props.walletCurrency))

const date = ref<Date | null>(new Date())
const quantity = ref<number | ''>('')
const unitPrice = ref<number | ''>('')
const amount = ref<number | ''>('')
const fees = ref<number | ''>('')
const taxes = ref<number | ''>('')
const submitting = ref(false)
const error = ref('')

const grossAmount = computed(() =>
  isFund.value ? Number(amount.value || 0) : Number(quantity.value || 0) * Number(unitPrice.value || 0),
)

const valid = computed(() =>
  isFund.value
    ? Number(amount.value) > 0 && !!date.value
    : Number(quantity.value) > 0 && Number(unitPrice.value) > 0 && !!date.value,
)

const exitsWholePosition = computed(() =>
  isFund.value
    ? props.currentValue != null && Number(amount.value) === props.currentValue
    : props.remainingQuantity != null && Number(quantity.value) === props.remainingQuantity,
)

async function submit() {
  if (!valid.value || !date.value) return
  error.value = ''
  submitting.value = true
  const resultDate = date.value.toISOString().slice(0, 10)
  const feesValue = Number(fees.value || 0)
  const taxesValue = Number(taxes.value || 0)
  try {
    if (isFund.value) {
      await resultsApi.withdrawFromFundHolding(props.walletId, props.holdingId, {
        resultDate,
        amount: Number(amount.value),
        fees: feesValue,
        taxes: taxesValue,
      })
    } else {
      const payload = {
        resultDate,
        quantity: Number(quantity.value),
        unitPrice: Number(unitPrice.value),
        fees: feesValue,
        taxes: taxesValue,
      }
      if (props.kind === 'STOCKS') {
        await resultsApi.withdrawFromStockHolding(props.walletId, props.holdingId, payload)
      } else {
        await resultsApi.withdrawFromCryptoHolding(props.walletId, props.holdingId, payload)
      }
    }
    toast.open({ message: 'Resgate registrado!', type: 'is-success' })
    emit('withdrawn', exitsWholePosition.value)
    emit('close')
  } catch (caughtError) {
    error.value = problemDetailMessage(caughtError) ?? 'Não foi possível registrar o resgate.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AppModal
    title="Resgatar"
    :subtitle="
      isFund
        ? 'Registre um resgate deste fundo.'
        : 'Registre a saída de parte ou de toda a posição.'
    "
    @close="emit('close')"
  >
    <p v-if="error" class="auth-error" data-testid="withdraw-error">{{ error }}</p>

    <div class="form-grid">
      <b-field label="Data" style="grid-column: 1/-1">
        <DateInput v-model="date" />
      </b-field>

      <b-field v-if="isFund" label="Valor resgatado" style="grid-column: 1/-1">
        <NumberInput v-model="amount" :prefix="symbol" placeholder="0,00" min="0" />
      </b-field>
      <template v-else>
        <b-field label="Quantidade">
          <NumberInput v-model="quantity" placeholder="0" min="0" />
        </b-field>
        <b-field label="Preço unitário">
          <NumberInput v-model="unitPrice" :prefix="symbol" placeholder="0,00" min="0" />
        </b-field>
      </template>

      <b-field label="Taxas (opcional)">
        <NumberInput v-model="fees" :prefix="symbol" placeholder="0,00" min="0" />
      </b-field>
      <b-field label="Impostos (opcional)">
        <NumberInput v-model="taxes" :prefix="symbol" placeholder="0,00" min="0" />
      </b-field>

      <p v-if="!isFund" class="withdraw-gross" style="grid-column: 1/-1">
        Valor bruto:
        <strong>{{ fmt.money(grossAmount, walletCurrency) }}</strong>
      </p>
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
        Resgatar
      </b-button>
    </template>
  </AppModal>
</template>
