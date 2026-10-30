<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useToast } from 'buefy'
import AppModal from '@/components/ui/AppModal.vue'
import DateInput from '@/components/ui/DateInput.vue'
import NumberInput from '@/components/ui/NumberInput.vue'
import { cashApi } from '@/api/cash'
import { fmt } from '@/composables/useFormat'
import { useRatesStore } from '@/stores/rates'
import { useWalletsStore } from '@/stores/wallets'
import { problemDetailMessage } from '@/utils/apiErrors'
import { crossRate } from '@/utils/crossRate'

const props = defineProps<{
  walletId: string
  walletName: string
  currency: string
  cash: number
}>()

const emit = defineEmits<{ transferred: []; close: [] }>()

const toast = useToast()
const walletsStore = useWalletsStore()
const ratesStore = useRatesStore()

const destinationId = ref<string | null>(null)
const date = ref<Date | null>(new Date())
const amount = ref<number | ''>('')
const wholeCash = ref(false)
const rate = ref<number | ''>('')
const submitting = ref(false)
const error = ref('')

onMounted(() => Promise.all([walletsStore.load(), ratesStore.load()]))

const destinations = computed(() =>
  walletsStore.wallets.filter((wallet) => wallet.id !== props.walletId),
)
const destination = computed(() =>
  destinations.value.find((wallet) => wallet.id === destinationId.value),
)
const crossCurrency = computed(
  () => !!destination.value && destination.value.currency !== props.currency,
)

watch(destination, (selected) => {
  rate.value = selected ? crossRate(ratesStore.rates, props.currency, selected.currency) : ''
})

watch(wholeCash, (checked) => {
  if (checked) amount.value = props.cash
})

const exceedsCash = computed(() => Number(amount.value || 0) > props.cash)
const appliedRate = computed(() => (crossCurrency.value ? Number(rate.value || 0) : 1))
const arrivingAmount = computed(() => Number(amount.value || 0) * appliedRate.value)

const valid = computed(
  () =>
    !!destination.value &&
    !!date.value &&
    Number(amount.value) > 0 &&
    !exceedsCash.value &&
    appliedRate.value > 0,
)

async function submit() {
  if (!valid.value || !date.value || !destination.value) return
  error.value = ''
  submitting.value = true
  try {
    await cashApi.transfer(props.walletId, {
      destinationWalletId: destination.value.id,
      transferDate: date.value.toISOString().slice(0, 10),
      amount: Number(amount.value),
      ...(crossCurrency.value ? { rate: Number(rate.value) } : {}),
    })
    toast.open({ message: 'Transferência registrada!', type: 'is-success' })
    emit('transferred')
    emit('close')
  } catch (caughtError) {
    error.value = problemDetailMessage(caughtError) ?? 'Não foi possível registrar a transferência.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AppModal
    title="Transferir caixa"
    subtitle="Mova o caixa desta carteira para outra carteira sua. Entre moedas diferentes, o valor é convertido pelo câmbio informado."
    wide
    @close="emit('close')"
  >
    <b-message v-if="error" type="is-danger" size="is-small" data-testid="transfer-error">
      {{ error }}
    </b-message>

    <div class="fixed-grid has-2-cols mb-3">
      <div class="grid is-gap-2">
        <b-field label="Carteira de destino" class="cell is-col-span-2-mobile mb-0">
          <b-select
            v-model="destinationId"
            placeholder="Selecione a carteira"
            expanded
            data-testid="transfer-destination"
          >
            <option v-for="wallet in destinations" :key="wallet.id" :value="wallet.id">
              {{ wallet.name }} · {{ wallet.currency }}
            </option>
          </b-select>
        </b-field>

        <b-field class="cell is-col-span-2-mobile mb-0">
          <template #label>
            <span class="is-flex is-align-items-center is-gap-1">
              Câmbio
              <b-tooltip
                label="Cotação atual de Preços e Moedas. Ajuste para o câmbio usado; ele fica gravado na transferência."
                multilined
                position="is-top"
              >
                <b-icon icon="information-outline" size="is-small" type="is-grey" />
              </b-tooltip>
            </span>
          </template>
          <NumberInput
            :model-value="crossCurrency ? rate : 1"
            :prefix="`1 ${currency} = ${fmt.sym(destination?.currency ?? currency)}`"
            :disabled="!crossCurrency"
            placeholder="0,00"
            min="0"
            data-testid="transfer-rate"
            @update:model-value="rate = $event"
          />
        </b-field>

        <b-field label="Data" class="cell is-col-span-2-mobile mb-0">
          <DateInput v-model="date" />
        </b-field>

        <b-field
          label="Valor enviado"
          class="cell is-col-span-2-mobile mb-0"
          :type="exceedsCash ? 'is-danger' : ''"
          :message="
            exceedsCash ? `Maior que o caixa disponível (${fmt.money(cash, currency)})` : ''
          "
        >
          <div class="field is-grouped">
            <div class="control is-expanded">
              <NumberInput
                v-model="amount"
                :prefix="fmt.sym(currency)"
                placeholder="0,00"
                min="0"
                :disabled="wholeCash"
                data-testid="transfer-amount"
              />
            </div>
            <div class="control is-flex is-align-items-center">
              <b-checkbox v-model="wholeCash" data-testid="transfer-whole-cash">Tudo</b-checkbox>
            </div>
          </div>
        </b-field>
      </div>
    </div>

    <div class="notification is-size-7 mb-0" data-testid="transfer-summary">
      <div class="is-flex is-justify-content-space-between">
        <span>Caixa disponível em {{ walletName }}</span>
        <strong>{{ fmt.money(cash, currency) }}</strong>
      </div>
      <div class="is-flex is-justify-content-space-between mt-1">
        <span>Sai de {{ walletName }}</span>
        <strong>{{ fmt.money(Number(amount || 0), currency) }}</strong>
      </div>
      <div v-if="destination" class="is-flex is-justify-content-space-between mt-1">
        <span>Entra no caixa de {{ destination.name }}</span>
        <strong>{{ fmt.money(arrivingAmount, destination.currency) }}</strong>
      </div>
    </div>

    <template #footer>
      <b-button outlined type="is-danger" :disabled="submitting" @click="emit('close')">
        Cancelar
      </b-button>
      <b-button
        type="is-primary"
        icon-left="bank-transfer"
        :disabled="!valid"
        :loading="submitting"
        data-testid="transfer-submit"
        @click="submit"
      >
        Transferir
      </b-button>
    </template>
  </AppModal>
</template>
