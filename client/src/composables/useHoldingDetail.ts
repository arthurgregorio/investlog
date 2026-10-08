import { computed, ref, toValue, type MaybeRefOrGetter } from 'vue'
import { useToast } from 'buefy'
import { safeHtml, useConfirmDialog } from '@/composables/useConfirmDialog'
import { holdingsApi } from '@/api/holdings'
import { resultsApi } from '@/api/results'
import { buildLedger } from '@/utils/holdingLedger'
import type { HoldingDetail, HoldingRow, WalletKind } from '@/types'

interface HoldingKindOperations {
  fetch: (walletId: string, holdingId: string) => Promise<HoldingDetail>
  setFrozen: (walletId: string, holdingId: string, frozen: boolean) => Promise<unknown>
  removeHolding: (walletId: string, holdingId: string) => Promise<void>
  removeEntry: (walletId: string, holdingId: string, entryId: string) => Promise<void>
  removeWithdrawal: (walletId: string, holdingId: string, resultId: string) => Promise<void>
  updateEntryDate: (
    walletId: string,
    holdingId: string,
    entryId: string,
    isoDate: string,
  ) => Promise<unknown>
}

interface HoldingKindCopy {
  removeEntryTitle: string
  entryRemoved: string
  removeWithdrawalTitle: string
  withdrawalRemoved: string
}

export const HOLDING_KIND_OPERATIONS: Record<WalletKind, HoldingKindOperations> = {
  STOCKS: {
    fetch: (walletId, holdingId) => holdingsApi.getStockHolding(walletId, holdingId),
    setFrozen: (walletId, holdingId, frozen) =>
      holdingsApi.updateStockHolding(walletId, holdingId, { frozen }),
    removeHolding: (walletId, holdingId) => holdingsApi.deleteStockHolding(walletId, holdingId),
    removeEntry: (walletId, holdingId, entryId) =>
      holdingsApi.deleteStockLot(walletId, holdingId, entryId),
    removeWithdrawal: (walletId, holdingId, resultId) =>
      resultsApi.deleteStockWithdrawal(walletId, holdingId, resultId),
    updateEntryDate: (walletId, holdingId, entryId, isoDate) =>
      holdingsApi.updateStockLotDate(walletId, holdingId, entryId, { lotDate: isoDate }),
  },
  CRYPTO: {
    fetch: (walletId, holdingId) => holdingsApi.getCryptoHolding(walletId, holdingId),
    setFrozen: (walletId, holdingId, frozen) =>
      holdingsApi.updateCryptoHolding(walletId, holdingId, { frozen }),
    removeHolding: (walletId, holdingId) => holdingsApi.deleteCryptoHolding(walletId, holdingId),
    removeEntry: (walletId, holdingId, entryId) =>
      holdingsApi.deleteCryptoLot(walletId, holdingId, entryId),
    removeWithdrawal: (walletId, holdingId, resultId) =>
      resultsApi.deleteCryptoWithdrawal(walletId, holdingId, resultId),
    updateEntryDate: (walletId, holdingId, entryId, isoDate) =>
      holdingsApi.updateCryptoLotDate(walletId, holdingId, entryId, { lotDate: isoDate }),
  },
  FUNDS: {
    fetch: (walletId, holdingId) => holdingsApi.getFundHolding(walletId, holdingId),
    setFrozen: (walletId, holdingId, frozen) =>
      holdingsApi.updateFundHolding(walletId, holdingId, { frozen }),
    removeHolding: (walletId, holdingId) => holdingsApi.deleteFundHolding(walletId, holdingId),
    removeEntry: (walletId, holdingId, entryId) =>
      holdingsApi.deleteFundContribution(walletId, holdingId, entryId),
    removeWithdrawal: (walletId, holdingId, resultId) =>
      resultsApi.deleteFundWithdrawal(walletId, holdingId, resultId),
    updateEntryDate: (walletId, holdingId, entryId, isoDate) =>
      holdingsApi.updateFundContributionDate(walletId, holdingId, entryId, {
        contributionDate: isoDate,
      }),
  },
}

const PURCHASE_COPY: HoldingKindCopy = {
  removeEntryTitle: 'Remover compra',
  entryRemoved: 'Compra removida.',
  removeWithdrawalTitle: 'Desfazer venda',
  withdrawalRemoved: 'Venda desfeita.',
}

const HOLDING_KIND_COPY: Record<WalletKind, HoldingKindCopy> = {
  STOCKS: PURCHASE_COPY,
  CRYPTO: PURCHASE_COPY,
  FUNDS: {
    removeEntryTitle: 'Remover aporte',
    entryRemoved: 'Aporte removido.',
    removeWithdrawalTitle: 'Desfazer resgate',
    withdrawalRemoved: 'Resgate desfeito.',
  },
}

const IRREVERSIBLE = safeHtml`Esta ação <strong>não pode ser desfeita</strong>.`

export interface HoldingDetailCallbacks {
  onChanged: () => void
  onRemoved: () => void
}

export function useHoldingDetail(
  row: MaybeRefOrGetter<HoldingRow>,
  { onChanged, onRemoved }: HoldingDetailCallbacks,
) {
  const toast = useToast()
  const { confirm } = useConfirmDialog()

  const detail = ref<HoldingDetail | null>(null)
  const loading = ref(false)
  const editingEntryId = ref<string | null>(null)
  const isFund = computed(() => toValue(row).kind === 'FUNDS')
  const ledger = computed(() => (detail.value ? buildLedger(detail.value, isFund.value) : []))

  function target() {
    const { kind, walletId, id } = toValue(row)
    return {
      operations: HOLDING_KIND_OPERATIONS[kind],
      copy: HOLDING_KIND_COPY[kind],
      walletId,
      id,
    }
  }

  async function reload() {
    const { operations, walletId, id } = target()
    detail.value = await operations.fetch(walletId, id)
  }

  async function load() {
    loading.value = true
    try {
      await reload()
    } finally {
      loading.value = false
    }
  }

  async function refresh() {
    await reload()
    onChanged()
  }

  function success(message: string) {
    toast.open({ message, type: 'is-success' })
  }

  async function toggleFrozen() {
    const { operations, walletId, id } = target()
    const frozen = !toValue(row).frozen
    await operations.setFrozen(walletId, id, frozen)
    success(frozen ? 'Investimento congelado.' : 'Investimento descongelado.')
    await refresh()
  }

  function confirmRemoveHolding() {
    const { operations, walletId, id } = target()
    confirm({
      title: 'Remover investimento',
      message: IRREVERSIBLE,
      confirmText: 'Remover',
      onConfirm: async () => {
        await operations.removeHolding(walletId, id)
        onRemoved()
      },
    })
  }

  function confirmRemoveEntry(entryId: string) {
    const { operations, copy, walletId, id } = target()
    confirm({
      title: copy.removeEntryTitle,
      message: IRREVERSIBLE,
      confirmText: 'Remover',
      onConfirm: async () => {
        await operations.removeEntry(walletId, id, entryId)
        success(copy.entryRemoved)
        await refresh()
      },
    })
  }

  function confirmRemoveWithdrawal(resultId: string) {
    const { operations, copy, walletId, id } = target()
    confirm({
      title: copy.removeWithdrawalTitle,
      message: IRREVERSIBLE,
      confirmText: 'Desfazer',
      onConfirm: async () => {
        try {
          await operations.removeWithdrawal(walletId, id, resultId)
          success(copy.withdrawalRemoved)
          await refresh()
        } catch {
          // The api client's interceptor already toasts the server's rejection
          // (e.g. "Apenas o resgate mais recente pode ser desfeito.").
        }
      },
    })
  }

  async function updateEntryDate(entryId: string, date: Date) {
    const { operations, walletId, id } = target()
    await operations.updateEntryDate(walletId, id, entryId, date.toISOString().slice(0, 10))
    editingEntryId.value = null
    success('Data atualizada.')
    await refresh()
  }

  return {
    detail,
    loading,
    editingEntryId,
    isFund,
    ledger,
    load,
    refresh,
    toggleFrozen,
    confirmRemoveHolding,
    confirmRemoveEntry,
    confirmRemoveWithdrawal,
    updateEntryDate,
  }
}
