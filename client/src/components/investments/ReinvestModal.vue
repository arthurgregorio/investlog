<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useToast } from 'buefy'
import AppModal from '@/components/ui/AppModal.vue'
import NumberInput from '@/components/ui/NumberInput.vue'
import DateInput from '@/components/ui/DateInput.vue'
import { holdingsApi } from '@/api/holdings'
import { useReinvestmentsStore } from '@/stores/reinvestments'
import { fmt } from '@/composables/useFormat'
import { problemDetailMessage } from '@/utils/apiErrors'
import type { HoldingRow } from '@/types'

const HOLDINGS_PAGE_SIZE = 500

const props = defineProps<{
  walletId: string
  preselectedHoldingId?: string
}>()

const emit = defineEmits<{ reinvested: []; close: [] }>()

const toast = useToast()
const reinvestmentsStore = useReinvestmentsStore()

const sources = ref<HoldingRow[]>([])
const candidates = ref<HoldingRow[]>([])
const loading = ref(false)
const sourceId = ref('')
const destinationId = ref('')
const date = ref<Date | null>(new Date())
const quantity = ref<number | ''>('')
const unitPrice = ref<number | ''>('')
const amount = ref<number | ''>('')
const fees = ref<number | ''>('')
const taxes = ref<number | ''>('')
const submitting = ref(false)
const error = ref('')

const source = computed(() => sources.value.find((holding) => holding.id === sourceId.value))
const destination = computed(() =>
  candidates.value.find((holding) => holding.id === destinationId.value),
)
const isFundSource = computed(() => source.value?.kind === 'FUNDS')
const currency = computed(() => source.value?.walletCurrency ?? 'BRL')
const symbol = computed(() => fmt.sym(currency.value))

const destinations = computed(() => {
  const currentSource = source.value
  if (!currentSource) return []
  return candidates.value.filter(
    (holding) =>
      holding.id !== currentSource.id && holding.walletCurrency === currentSource.walletCurrency,
  )
})

const destinationGroups = computed(() => {
  const groups = new Map<string, { walletName: string; holdings: HoldingRow[] }>()
  for (const holding of destinations.value) {
    const group = groups.get(holding.walletId) ?? { walletName: holding.walletName, holdings: [] }
    group.holdings.push(holding)
    groups.set(holding.walletId, group)
  }
  return [...groups.values()]
})

const destinationUnpriced = computed(() => {
  const currentDestination = destination.value
  return (
    !!currentDestination &&
    currentDestination.kind !== 'FUNDS' &&
    !(Number(currentDestination.currentPrice) > 0)
  )
})

const grossAmount = computed(() =>
  isFundSource.value
    ? Number(amount.value || 0)
    : Number(quantity.value || 0) * Number(unitPrice.value || 0),
)

const netAmount = computed(
  () => grossAmount.value - Number(fees.value || 0) - Number(taxes.value || 0),
)

const destinationMessage = computed(() => {
  if (source.value && destinations.value.length === 0) {
    return `Nenhum outro investimento ativo em carteiras de ${currency.value} para receber o reinvestimento.`
  }
  if (destinationUnpriced.value) {
    return `${displayName(destination.value!)} não tem preço atual. Defina o preço atual do investimento de destino antes de reinvestir nele.`
  }
  return ''
})

const destinationQuantity = computed(() => {
  const currentDestination = destination.value
  if (!currentDestination || currentDestination.kind === 'FUNDS' || destinationUnpriced.value) {
    return null
  }
  return netAmount.value > 0 ? netAmount.value / Number(currentDestination.currentPrice) : null
})

const exceedsRemaining = computed(() => {
  const currentSource = source.value
  if (!currentSource) return false
  return isFundSource.value
    ? currentSource.currentValue != null && Number(amount.value) > currentSource.currentValue
    : currentSource.quantity != null && Number(quantity.value) > currentSource.quantity
})

const costsExceedGross = computed(() => grossAmount.value > 0 && netAmount.value <= 0)

const valid = computed(() => {
  if (!source.value || !destination.value || !date.value || destinationUnpriced.value) return false
  if (exceedsRemaining.value || netAmount.value <= 0) return false
  return isFundSource.value
    ? Number(amount.value) > 0
    : Number(quantity.value) > 0 && Number(unitPrice.value) > 0
})

function displayName(holding: HoldingRow): string {
  return holding.ticker ?? holding.name
}

function availableLabel(holding: HoldingRow): string {
  return holding.quantity != null
    ? `${fmt.qty(holding.quantity)} disponíveis`
    : fmt.money(holding.currentValue ?? holding.costBasis, holding.walletCurrency)
}

watch(sourceId, () => {
  if (!destinations.value.some((holding) => holding.id === destinationId.value)) {
    destinationId.value = ''
  }
  quantity.value = ''
  amount.value = ''
  unitPrice.value = source.value?.currentPrice ?? ''
})

onMounted(async () => {
  loading.value = true
  try {
    const [walletPage, allPage] = await Promise.all([
      holdingsApi.findAll({ walletId: props.walletId, size: HOLDINGS_PAGE_SIZE }),
      holdingsApi.findAll({ size: HOLDINGS_PAGE_SIZE }),
    ])
    sources.value = walletPage.content
    candidates.value = allPage.content
    if (
      props.preselectedHoldingId &&
      walletPage.content.some((holding) => holding.id === props.preselectedHoldingId)
    ) {
      sourceId.value = props.preselectedHoldingId
    }
  } finally {
    loading.value = false
  }
})

async function submit() {
  const currentSource = source.value
  const currentDestination = destination.value
  if (!valid.value || !currentSource || !currentDestination || !date.value) return
  error.value = ''
  submitting.value = true
  try {
    await reinvestmentsStore.reinvest({
      sourceKind: currentSource.kind,
      sourceHoldingId: currentSource.id,
      destinationKind: currentDestination.kind,
      destinationHoldingId: currentDestination.id,
      reinvestmentDate: date.value.toISOString().slice(0, 10),
      ...(isFundSource.value
        ? { amount: Number(amount.value) }
        : { quantity: Number(quantity.value), unitPrice: Number(unitPrice.value) }),
      fees: Number(fees.value || 0),
      taxes: Number(taxes.value || 0),
    })
    toast.open({ message: 'Reinvestimento registrado!', type: 'is-success' })
    emit('reinvested')
    emit('close')
  } catch (caughtError) {
    error.value =
      problemDetailMessage(caughtError) ?? 'Não foi possível registrar o reinvestimento.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AppModal
    title="Reinvestir"
    subtitle="Venda parte ou toda a posição e reinvista o valor líquido em outro investimento da mesma moeda."
    wide
    @close="emit('close')"
  >
    <b-loading :is-full-page="false" :model-value="loading" />
    <b-message v-if="error" type="is-danger" size="is-small" data-testid="reinvest-error">
      {{ error }}
    </b-message>

    <b-field label="Investimento de origem">
      <b-select
        v-model="sourceId"
        placeholder="Selecione o investimento"
        expanded
        data-testid="reinvest-source"
      >
        <option v-for="holding in sources" :key="holding.id" :value="holding.id">
          {{ displayName(holding) }} · {{ availableLabel(holding) }}
        </option>
      </b-select>
    </b-field>

    <b-field
      label="Investimento de destino"
      :type="destinationUnpriced ? 'is-danger' : ''"
      :message="destinationMessage"
    >
      <b-select
        v-model="destinationId"
        placeholder="Selecione o investimento"
        expanded
        :disabled="!source"
        data-testid="reinvest-destination"
      >
        <optgroup
          v-for="group in destinationGroups"
          :key="group.walletName"
          :label="group.walletName"
        >
          <option v-for="holding in group.holdings" :key="holding.id" :value="holding.id">
            {{ displayName(holding) }}
          </option>
        </optgroup>
      </b-select>
    </b-field>

    <template v-if="source">
      <b-field label="Data">
        <DateInput v-model="date" />
      </b-field>

      <b-field
        v-if="isFundSource"
        label="Valor reinvestido"
        :type="exceedsRemaining ? 'is-danger' : ''"
        :message="exceedsRemaining ? 'Maior que o valor atual do fundo' : ''"
      >
        <NumberInput
          v-model="amount"
          :prefix="symbol"
          placeholder="0,00"
          min="0"
          data-testid="reinvest-amount"
        />
      </b-field>
      <div v-else class="fixed-grid has-2-cols mb-3">
        <div class="grid">
          <b-field
            class="mb-0"
            label="Quantidade"
            :type="exceedsRemaining ? 'is-danger' : ''"
            :message="exceedsRemaining ? 'Maior que o disponível' : ''"
          >
            <NumberInput
              v-model="quantity"
              :placeholder="`Até ${fmt.qty(source.quantity ?? 0)}`"
              min="0"
              data-testid="reinvest-quantity"
            />
          </b-field>
          <b-field label="Preço unitário de venda">
            <NumberInput
              v-model="unitPrice"
              :prefix="symbol"
              placeholder="0,00"
              min="0"
              data-testid="reinvest-unit-price"
            />
          </b-field>
        </div>
      </div>

      <div class="fixed-grid has-2-cols mb-3">
        <div class="grid">
          <b-field label="Taxas (opcional)" class="mb-0">
            <NumberInput v-model="fees" :prefix="symbol" placeholder="0,00" min="0" />
          </b-field>
          <b-field label="Impostos (opcional)">
            <NumberInput v-model="taxes" :prefix="symbol" placeholder="0,00" min="0" />
          </b-field>
        </div>
      </div>

      <div class="notification is-size-7 mb-0" data-testid="reinvest-summary">
        <div class="is-flex is-justify-content-space-between">
          <span>Valor bruto</span>
          <strong>{{ fmt.money(grossAmount, currency) }}</strong>
        </div>
        <div class="is-flex is-justify-content-space-between mt-1">
          <span>Valor líquido reinvestido</span>
          <strong :class="{ 'has-text-danger': costsExceedGross }">{{
            fmt.money(netAmount, currency)
          }}</strong>
        </div>
        <p v-if="destination && destinationQuantity != null" class="mt-2">
          Compra de ≈ {{ fmt.qty(destinationQuantity) }} {{ displayName(destination) }} a
          {{ fmt.money(Number(destination.currentPrice), currency) }}
        </p>
        <p v-if="costsExceedGross" class="has-text-danger mt-2">
          Taxas e impostos não podem consumir todo o valor reinvestido.
        </p>
      </div>
    </template>

    <template #footer>
      <b-button outlined type="is-danger" :disabled="submitting" @click="emit('close')"
        >Cancelar</b-button
      >
      <b-button
        type="is-primary"
        class="has-text-light"
        icon-left="autorenew"
        :disabled="!valid"
        :loading="submitting"
        data-testid="reinvest-submit"
        @click="submit"
      >
        Reinvestir
      </b-button>
    </template>
  </AppModal>
</template>
