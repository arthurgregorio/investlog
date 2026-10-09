import { computed, ref, watch } from 'vue'
import { useToast } from 'buefy'
import { holdingsApi } from '@/api/holdings'
import type { WalletMoveItemPayload } from '@/api/walletMoves'
import { useWalletsStore } from '@/stores/wallets'
import { useWalletMovesStore } from '@/stores/walletMoves'
import { problemDetailMessage } from '@/utils/apiErrors'
import type { HoldingRow } from '@/types'

const MOVABLE_HOLDINGS_PAGE_SIZE = 500

export type MoveQuantity = number | ''

export function takesQuantity(holding: HoldingRow): boolean {
  return holding.kind !== 'FUNDS'
}

export function exceedsRemaining(holding: HoldingRow, quantity: MoveQuantity | undefined): boolean {
  return (
    quantity !== '' && quantity != null && holding.quantity != null && quantity > holding.quantity
  )
}

export function useMoveHoldingsForm(
  options: { originWalletId?: string; preselectedHoldingId?: string },
  onDone?: () => void,
) {
  const toast = useToast()
  const walletsStore = useWalletsStore()
  const walletMovesStore = useWalletMovesStore()

  const selectedOriginId = ref(options.originWalletId ?? '')
  const destinationId = ref('')
  const holdings = ref<HoldingRow[]>([])
  const loadingHoldings = ref(false)
  const moveAll = ref(false)
  const selected = ref<Record<string, boolean>>({})
  const quantities = ref<Record<string, MoveQuantity>>({})
  const submitting = ref(false)
  const error = ref('')

  const origin = computed(() => walletsStore.walletById(selectedOriginId.value))

  const destinations = computed(() => {
    const currentOrigin = origin.value
    if (!currentOrigin) return []
    return walletsStore.wallets.filter(
      (wallet) =>
        wallet.id !== currentOrigin.id &&
        wallet.kind === currentOrigin.kind &&
        wallet.currency === currentOrigin.currency,
    )
  })

  const items = computed<WalletMoveItemPayload[]>(() => {
    if (moveAll.value) return holdings.value.map((holding) => ({ holdingId: holding.id }))
    return holdings.value
      .filter((holding) => selected.value[holding.id])
      .map((holding) => {
        const quantity = quantities.value[holding.id]
        return takesQuantity(holding) && quantity !== '' && quantity != null
          ? { holdingId: holding.id, quantity }
          : { holdingId: holding.id }
      })
  })

  const hasInvalidQuantity = computed(
    () =>
      !moveAll.value &&
      holdings.value.some(
        (holding) =>
          selected.value[holding.id] &&
          (exceedsRemaining(holding, quantities.value[holding.id]) ||
            Number(quantities.value[holding.id]) < 0),
      ),
  )

  const valid = computed(
    () =>
      !!origin.value &&
      !!destinationId.value &&
      items.value.length > 0 &&
      !hasInvalidQuantity.value,
  )

  function select(holdingId: string, checked: boolean) {
    selected.value[holdingId] = checked
  }

  function setQuantity(holdingId: string, quantity: MoveQuantity) {
    quantities.value[holdingId] = quantity
  }

  async function loadHoldings(walletId: string) {
    holdings.value = []
    selected.value = {}
    quantities.value = {}
    moveAll.value = false
    if (!walletId) return
    loadingHoldings.value = true
    try {
      const page = await holdingsApi.findAll({ walletId, size: MOVABLE_HOLDINGS_PAGE_SIZE })
      holdings.value = page.content
      const preselectedHoldingId = options.preselectedHoldingId
      if (
        preselectedHoldingId &&
        page.content.some((holding) => holding.id === preselectedHoldingId)
      ) {
        selected.value = { [preselectedHoldingId]: true }
      }
    } finally {
      loadingHoldings.value = false
    }
  }

  watch(selectedOriginId, (walletId) => {
    if (!destinations.value.some((wallet) => wallet.id === destinationId.value)) {
      destinationId.value = ''
    }
    loadHoldings(walletId)
  })

  function load() {
    walletsStore.load()
    return loadHoldings(selectedOriginId.value)
  }

  async function submit() {
    if (!valid.value) return
    error.value = ''
    submitting.value = true
    try {
      await walletMovesStore.move(selectedOriginId.value, {
        destinationWalletId: destinationId.value,
        items: items.value,
      })
      toast.open({ message: 'Investimentos movidos!', type: 'is-success' })
      onDone?.()
    } catch (caughtError) {
      error.value = problemDetailMessage(caughtError) ?? 'Não foi possível mover os investimentos.'
    } finally {
      submitting.value = false
    }
  }

  return {
    selectedOriginId,
    destinationId,
    holdings,
    loadingHoldings,
    moveAll,
    selected,
    quantities,
    submitting,
    error,
    origin,
    destinations,
    items,
    valid,
    select,
    setQuantity,
    load,
    submit,
  }
}
