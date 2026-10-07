import { computed, ref, watch } from 'vue'
import { useToast } from 'buefy'
import { holdingsApi } from '@/api/holdings'
import { useReinvestmentsStore } from '@/stores/reinvestments'
import { problemDetailMessage } from '@/utils/apiErrors'
import type { HoldingRow } from '@/types'

const HOLDINGS_PAGE_SIZE = 500

export interface DestinationGroup {
  walletName: string
  holdings: HoldingRow[]
}

export function holdingDisplayName(holding: HoldingRow): string {
  return holding.ticker ?? holding.name
}

export function useReinvestForm(onDone?: () => void) {
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

  const destinations = computed(() => {
    const currentSource = source.value
    if (!currentSource) return []
    return candidates.value.filter(
      (holding) =>
        holding.id !== currentSource.id &&
        holding.walletCurrency === currentSource.walletCurrency &&
        !holding.frozen,
    )
  })

  const destinationGroups = computed(() => {
    const groups = new Map<string, DestinationGroup>()
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
    if (!source.value || !destination.value || !date.value || destinationUnpriced.value) {
      return false
    }
    if (exceedsRemaining.value || netAmount.value <= 0) return false
    return isFundSource.value
      ? Number(amount.value) > 0
      : Number(quantity.value) > 0 && Number(unitPrice.value) > 0
  })

  watch(sourceId, () => {
    if (!destinations.value.some((holding) => holding.id === destinationId.value)) {
      destinationId.value = ''
    }
    quantity.value = ''
    amount.value = ''
    unitPrice.value = source.value?.currentPrice ?? ''
  })

  async function load(walletId: string, preselectedHoldingId?: string) {
    loading.value = true
    try {
      const [walletPage, allPage] = await Promise.all([
        holdingsApi.findAll({ walletId, size: HOLDINGS_PAGE_SIZE }),
        holdingsApi.findAll({ size: HOLDINGS_PAGE_SIZE }),
      ])
      sources.value = walletPage.content
      candidates.value = allPage.content
      if (
        preselectedHoldingId &&
        walletPage.content.some((holding) => holding.id === preselectedHoldingId)
      ) {
        sourceId.value = preselectedHoldingId
      }
    } finally {
      loading.value = false
    }
  }

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
      onDone?.()
    } catch (caughtError) {
      error.value =
        problemDetailMessage(caughtError) ?? 'Não foi possível registrar o reinvestimento.'
    } finally {
      submitting.value = false
    }
  }

  return {
    sources,
    loading,
    sourceId,
    destinationId,
    date,
    quantity,
    unitPrice,
    amount,
    fees,
    taxes,
    submitting,
    error,
    source,
    destination,
    isFundSource,
    currency,
    destinations,
    destinationGroups,
    destinationUnpriced,
    grossAmount,
    netAmount,
    destinationQuantity,
    exceedsRemaining,
    costsExceedGross,
    valid,
    load,
    submit,
  }
}
