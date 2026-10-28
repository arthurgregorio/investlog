<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useToast } from 'buefy'
import AppModal from '@/components/ui/AppModal.vue'
import DateInput from '@/components/ui/DateInput.vue'
import NumberInput from '@/components/ui/NumberInput.vue'
import { cashApi } from '@/api/cash'
import { fmt } from '@/composables/useFormat'
import { problemDetailMessage } from '@/utils/apiErrors'

const props = defineProps<{
  walletId: string
  walletName: string
  currency: string
  cash: number
}>()

const emit = defineEmits<{ withdrawn: []; close: [] }>()

const BANK_NAME_MAX_LENGTH = 60

const toast = useToast()

const date = ref<Date | null>(new Date())
const amount = ref<number | ''>('')
const wholeCash = ref(false)
const bankName = ref('')
const submitting = ref(false)
const serverError = ref('')

watch(wholeCash, (checked) => {
  if (checked) amount.value = props.cash
})

watch(amount, () => {
  serverError.value = ''
})

const withdrawnAmount = computed(() => Number(amount.value || 0))
const exceedsCash = computed(() => withdrawnAmount.value > props.cash)
const amountError = computed(() =>
  exceedsCash.value
    ? `Maior que o caixa disponível (${fmt.money(props.cash, props.currency)})`
    : serverError.value,
)

const valid = computed(() => !!date.value && withdrawnAmount.value > 0 && !exceedsCash.value)

async function submit() {
  if (!valid.value || !date.value) return
  serverError.value = ''
  submitting.value = true
  const trimmedBankName = bankName.value.trim()
  try {
    await cashApi.withdrawToBank(props.walletId, {
      withdrawalDate: date.value.toISOString().slice(0, 10),
      amount: withdrawnAmount.value,
      ...(trimmedBankName ? { bankName: trimmedBankName } : {}),
    })
    toast.open({ message: 'Saque registrado!', type: 'is-success' })
    emit('withdrawn')
    emit('close')
  } catch (caughtError) {
    serverError.value = problemDetailMessage(caughtError) ?? 'Não foi possível registrar o saque.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AppModal
    title="Sacar para o banco"
    subtitle="Registre o caixa que saiu da corretora para a sua conta bancária."
    @close="emit('close')"
  >
    <div class="fixed-grid has-2-cols">
      <div class="grid is-gap-2">
        <b-field label="Data" class="cell is-col-span-2">
          <DateInput v-model="date" />
        </b-field>

        <b-field
          label="Valor sacado"
          class="cell is-col-span-2"
          :type="amountError ? 'is-danger' : ''"
          :message="amountError"
        >
          <div class="field is-grouped">
            <div class="control is-expanded">
              <NumberInput
                v-model="amount"
                :prefix="fmt.sym(currency)"
                placeholder="0,00"
                min="0"
                :disabled="wholeCash"
                data-testid="withdraw-cash-amount"
              />
            </div>
            <div class="control is-flex is-align-items-center">
              <b-checkbox v-model="wholeCash" data-testid="withdraw-cash-whole">Tudo</b-checkbox>
            </div>
          </div>
        </b-field>

        <b-field label="Banco (opcional)" class="cell is-col-span-2">
          <b-input
            v-model="bankName"
            :maxlength="BANK_NAME_MAX_LENGTH"
            :has-counter="false"
            placeholder="Ex.: Itaú, Nubank"
            data-testid="withdraw-cash-bank"
          />
        </b-field>

        <div
          class="cell is-col-span-2 notification is-size-7 mb-0"
          data-testid="withdraw-cash-summary"
        >
          <div class="is-flex is-justify-content-space-between">
            <span>Caixa disponível em {{ walletName }}</span>
            <strong>{{ fmt.money(cash, currency) }}</strong>
          </div>
          <div class="is-flex is-justify-content-space-between mt-1">
            <span>Sai do caixa</span>
            <strong>{{ exceedsCash ? '—' : fmt.money(withdrawnAmount, currency) }}</strong>
          </div>
          <div class="is-flex is-justify-content-space-between mt-1">
            <span>Caixa depois do saque</span>
            <strong>{{ exceedsCash ? '—' : fmt.money(cash - withdrawnAmount, currency) }}</strong>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <b-button outlined type="is-danger" :disabled="submitting" @click="emit('close')">
        Cancelar
      </b-button>
      <b-button
        type="is-success"
        icon-left="check"
        :disabled="!valid"
        :loading="submitting"
        data-testid="withdraw-cash-submit"
        @click="submit"
      >
        Sacar
      </b-button>
    </template>
  </AppModal>
</template>
