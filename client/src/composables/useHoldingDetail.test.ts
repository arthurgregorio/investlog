import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useHoldingDetail } from './useHoldingDetail'
import { holdingsApi } from '@/api/holdings'
import { resultsApi } from '@/api/results'
import type {
  CryptoHoldingDetail,
  FundHoldingDetail,
  HoldingDetail,
  HoldingRow,
  StockHoldingDetail,
} from '@/types'

const { dialogConfirm, toastOpen } = vi.hoisted(() => ({
  dialogConfirm: vi.fn(),
  toastOpen: vi.fn(),
}))

vi.mock('buefy', async (importOriginal) => ({
  ...(await importOriginal<typeof import('buefy')>()),
  useDialog: () => ({ confirm: dialogConfirm }),
  useToast: () => ({ open: toastOpen }),
}))

vi.mock('@/api/holdings', () => ({
  holdingsApi: {
    getStockHolding: vi.fn(),
    getCryptoHolding: vi.fn(),
    getFundHolding: vi.fn(),
    updateStockHolding: vi.fn(),
    updateCryptoHolding: vi.fn(),
    updateFundHolding: vi.fn(),
    deleteStockHolding: vi.fn(),
    deleteCryptoHolding: vi.fn(),
    deleteFundHolding: vi.fn(),
    deleteStockLot: vi.fn(),
    deleteCryptoLot: vi.fn(),
    deleteFundContribution: vi.fn(),
    updateStockLotDate: vi.fn(),
    updateCryptoLotDate: vi.fn(),
    updateFundContributionDate: vi.fn(),
  },
}))

vi.mock('@/api/results', () => ({
  resultsApi: {
    deleteStockWithdrawal: vi.fn(),
    deleteCryptoWithdrawal: vi.fn(),
    deleteFundWithdrawal: vi.fn(),
  },
}))

const stockRow: HoldingRow = {
  id: 'holding-1',
  kind: 'STOCKS',
  name: 'Petróleo Brasileiro',
  ticker: 'PETR4',
  typeLabel: 'Ação PN',
  segmentLabel: null,
  walletId: 'wallet-1',
  walletName: 'Carteira B3',
  walletCurrency: 'BRL',
  quantity: 300,
  costBasis: 7530,
  currentPrice: 34.8,
  currentValue: 10440,
  gain: 2910,
  gainPct: 38.6,
  frozen: false,
}

const cryptoRow: HoldingRow = {
  ...stockRow,
  id: 'holding-3',
  kind: 'CRYPTO',
  ticker: 'BTC',
  walletId: 'wallet-3',
}

const fundRow: HoldingRow = {
  ...stockRow,
  id: 'holding-2',
  kind: 'FUNDS',
  ticker: null,
  walletId: 'wallet-2',
  quantity: null,
  currentPrice: null,
}

const stockDetail: StockHoldingDetail = {
  id: 'holding-1',
  walletId: 'wallet-1',
  stockTypeId: 'type-1',
  stockSegmentId: null,
  stockSegmentName: null,
  ticker: 'PETR4',
  name: 'Petróleo Brasileiro',
  currentPrice: 34.8,
  lots: [{ id: 'lot-1', lotDate: '2026-01-12', quantity: 300, price: 25.1 }],
  frozen: false,
  withdrawals: [],
}

const cryptoDetail: CryptoHoldingDetail = {
  id: 'holding-3',
  walletId: 'wallet-3',
  ticker: 'BTC',
  name: 'Bitcoin',
  currentPrice: 180000,
  lots: [{ id: 'lot-9', lotDate: '2026-02-01', quantity: 0.5, price: 200000 }],
  frozen: false,
  withdrawals: [],
}

const fundDetail: FundHoldingDetail = {
  id: 'holding-2',
  walletId: 'wallet-2',
  fundTypeId: 'type-2',
  name: 'Tesouro IPCA+',
  currentValue: 3000,
  administrationFeeRate: null,
  performanceFeeRate: null,
  contributions: [{ id: 'c-1', contributionDate: '2026-01-10', amount: 4000 }],
  frozen: false,
  withdrawals: [],
}

type ApiMock = ReturnType<typeof vi.fn>

interface KindCase {
  label: string
  row: HoldingRow
  detail: HoldingDetail
  ledgerEntryId: string
  fetch: ApiMock
  setFrozen: ApiMock
  removeHolding: ApiMock
  removeEntry: ApiMock
  removeWithdrawal: ApiMock
  updateEntryDate: ApiMock
  dateField: 'lotDate' | 'contributionDate'
  removeEntryTitle: string
  entryRemoved: string
  removeWithdrawalTitle: string
  withdrawalRemoved: string
}

const kindCases: KindCase[] = [
  {
    label: 'stock',
    row: stockRow,
    detail: stockDetail,
    ledgerEntryId: 'lot-1',
    fetch: vi.mocked(holdingsApi.getStockHolding),
    setFrozen: vi.mocked(holdingsApi.updateStockHolding),
    removeHolding: vi.mocked(holdingsApi.deleteStockHolding),
    removeEntry: vi.mocked(holdingsApi.deleteStockLot),
    removeWithdrawal: vi.mocked(resultsApi.deleteStockWithdrawal),
    updateEntryDate: vi.mocked(holdingsApi.updateStockLotDate),
    dateField: 'lotDate',
    removeEntryTitle: 'Remover compra',
    entryRemoved: 'Compra removida.',
    removeWithdrawalTitle: 'Desfazer venda',
    withdrawalRemoved: 'Venda desfeita.',
  },
  {
    label: 'crypto',
    row: cryptoRow,
    detail: cryptoDetail,
    ledgerEntryId: 'lot-9',
    fetch: vi.mocked(holdingsApi.getCryptoHolding),
    setFrozen: vi.mocked(holdingsApi.updateCryptoHolding),
    removeHolding: vi.mocked(holdingsApi.deleteCryptoHolding),
    removeEntry: vi.mocked(holdingsApi.deleteCryptoLot),
    removeWithdrawal: vi.mocked(resultsApi.deleteCryptoWithdrawal),
    updateEntryDate: vi.mocked(holdingsApi.updateCryptoLotDate),
    dateField: 'lotDate',
    removeEntryTitle: 'Remover compra',
    entryRemoved: 'Compra removida.',
    removeWithdrawalTitle: 'Desfazer venda',
    withdrawalRemoved: 'Venda desfeita.',
  },
  {
    label: 'fund',
    row: fundRow,
    detail: fundDetail,
    ledgerEntryId: 'c-1',
    fetch: vi.mocked(holdingsApi.getFundHolding),
    setFrozen: vi.mocked(holdingsApi.updateFundHolding),
    removeHolding: vi.mocked(holdingsApi.deleteFundHolding),
    removeEntry: vi.mocked(holdingsApi.deleteFundContribution),
    removeWithdrawal: vi.mocked(resultsApi.deleteFundWithdrawal),
    updateEntryDate: vi.mocked(holdingsApi.updateFundContributionDate),
    dateField: 'contributionDate',
    removeEntryTitle: 'Remover aporte',
    entryRemoved: 'Aporte removido.',
    removeWithdrawalTitle: 'Desfazer resgate',
    withdrawalRemoved: 'Resgate desfeito.',
  },
]

const otherCalls = (kindCase: KindCase, pick: (other: KindCase) => ApiMock) =>
  kindCases.filter((other) => other !== kindCase).map(pick)

function setup(row: HoldingRow) {
  const onChanged = vi.fn()
  const onRemoved = vi.fn()
  return { onChanged, onRemoved, holding: useHoldingDetail(row, { onChanged, onRemoved }) }
}

function lastConfirm() {
  return dialogConfirm.mock.lastCall![0]
}

describe('useHoldingDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe.each(kindCases)('for a $label holding', (kindCase) => {
    beforeEach(() => {
      kindCase.fetch.mockResolvedValue(kindCase.detail)
    })

    it('loads the detail from its own endpoint and builds the ledger', async () => {
      const { holding } = setup(kindCase.row)

      const loading = holding.load()
      expect(holding.loading.value).toBe(true)
      await loading

      expect(kindCase.fetch).toHaveBeenCalledWith(kindCase.row.walletId, kindCase.row.id)
      for (const other of otherCalls(kindCase, (other) => other.fetch)) {
        expect(other).not.toHaveBeenCalled()
      }
      expect(holding.loading.value).toBe(false)
      expect(holding.detail.value).toEqual(kindCase.detail)
      expect(holding.ledger.value.map((entry) => entry.id)).toEqual([kindCase.ledgerEntryId])
      expect(holding.isFund.value).toBe(kindCase.row.kind === 'FUNDS')
    })

    it('freezes the holding, then reloads and reports the change', async () => {
      kindCase.setFrozen.mockResolvedValue(kindCase.detail)
      const { holding, onChanged } = setup(kindCase.row)

      await holding.toggleFrozen()

      expect(kindCase.setFrozen).toHaveBeenCalledWith(kindCase.row.walletId, kindCase.row.id, {
        frozen: true,
      })
      for (const other of otherCalls(kindCase, (other) => other.setFrozen)) {
        expect(other).not.toHaveBeenCalled()
      }
      expect(toastOpen).toHaveBeenCalledWith({
        message: 'Investimento congelado.',
        type: 'is-success',
      })
      expect(kindCase.fetch).toHaveBeenCalledTimes(1)
      expect(onChanged).toHaveBeenCalledTimes(1)
    })

    it('removes the holding on confirm and reports the removal without a toast', async () => {
      kindCase.removeHolding.mockResolvedValue(undefined)
      const { holding, onRemoved, onChanged } = setup(kindCase.row)

      holding.confirmRemoveHolding()
      expect(lastConfirm()).toMatchObject({ title: 'Remover investimento', type: 'is-danger' })
      await lastConfirm().onConfirm()

      expect(kindCase.removeHolding).toHaveBeenCalledWith(kindCase.row.walletId, kindCase.row.id)
      for (const other of otherCalls(kindCase, (other) => other.removeHolding)) {
        expect(other).not.toHaveBeenCalled()
      }
      expect(onRemoved).toHaveBeenCalledTimes(1)
      expect(onChanged).not.toHaveBeenCalled()
      expect(toastOpen).not.toHaveBeenCalled()
    })

    it('removes a ledger entry with the kind wording', async () => {
      kindCase.removeEntry.mockResolvedValue(undefined)
      const { holding, onChanged } = setup(kindCase.row)

      holding.confirmRemoveEntry('entry-1')
      expect(lastConfirm()).toMatchObject({
        title: kindCase.removeEntryTitle,
        confirmText: 'Remover',
      })
      await lastConfirm().onConfirm()

      expect(kindCase.removeEntry).toHaveBeenCalledWith(
        kindCase.row.walletId,
        kindCase.row.id,
        'entry-1',
      )
      for (const other of otherCalls(kindCase, (other) => other.removeEntry)) {
        expect(other).not.toHaveBeenCalled()
      }
      expect(toastOpen).toHaveBeenCalledWith({
        message: kindCase.entryRemoved,
        type: 'is-success',
      })
      expect(onChanged).toHaveBeenCalledTimes(1)
    })

    it('undoes a withdrawal with the kind wording', async () => {
      kindCase.removeWithdrawal.mockResolvedValue(undefined)
      const { holding, onChanged } = setup(kindCase.row)

      holding.confirmRemoveWithdrawal('result-1')
      expect(lastConfirm()).toMatchObject({
        title: kindCase.removeWithdrawalTitle,
        confirmText: 'Desfazer',
      })
      await lastConfirm().onConfirm()

      expect(kindCase.removeWithdrawal).toHaveBeenCalledWith(
        kindCase.row.walletId,
        kindCase.row.id,
        'result-1',
      )
      for (const other of otherCalls(kindCase, (other) => other.removeWithdrawal)) {
        expect(other).not.toHaveBeenCalled()
      }
      expect(toastOpen).toHaveBeenCalledWith({
        message: kindCase.withdrawalRemoved,
        type: 'is-success',
      })
      expect(onChanged).toHaveBeenCalledTimes(1)
    })

    it('saves an entry date under the kind payload key and closes the editor', async () => {
      kindCase.updateEntryDate.mockResolvedValue(undefined)
      const { holding, onChanged } = setup(kindCase.row)
      holding.editingEntryId.value = 'entry-1'

      await holding.updateEntryDate('entry-1', new Date(Date.UTC(2026, 1, 3, 12)))

      expect(kindCase.updateEntryDate).toHaveBeenCalledWith(
        kindCase.row.walletId,
        kindCase.row.id,
        'entry-1',
        { [kindCase.dateField]: '2026-02-03' },
      )
      for (const other of otherCalls(kindCase, (other) => other.updateEntryDate)) {
        expect(other).not.toHaveBeenCalled()
      }
      expect(holding.editingEntryId.value).toBeNull()
      expect(toastOpen).toHaveBeenCalledWith({ message: 'Data atualizada.', type: 'is-success' })
      expect(onChanged).toHaveBeenCalledTimes(1)
    })
  })

  it('leaves loading once a failed fetch settles', async () => {
    vi.mocked(holdingsApi.getStockHolding).mockRejectedValue(new Error('500'))
    const { holding } = setup(stockRow)

    await expect(holding.load()).rejects.toThrow('500')

    expect(holding.loading.value).toBe(false)
    expect(holding.detail.value).toBeNull()
    expect(holding.ledger.value).toEqual([])
  })

  it('reads the row at call time, so a refreshed frozen flag unfreezes', async () => {
    vi.mocked(holdingsApi.updateStockHolding).mockResolvedValue(stockDetail)
    vi.mocked(holdingsApi.getStockHolding).mockResolvedValue(stockDetail)
    const row = ref(stockRow)
    const holding = useHoldingDetail(row, { onChanged: vi.fn(), onRemoved: vi.fn() })

    row.value = { ...stockRow, frozen: true }
    await holding.toggleFrozen()

    expect(holdingsApi.updateStockHolding).toHaveBeenCalledWith('wallet-1', 'holding-1', {
      frozen: false,
    })
    expect(toastOpen).toHaveBeenCalledWith({
      message: 'Investimento descongelado.',
      type: 'is-success',
    })
  })

  it('reports nothing when freezing is rejected', async () => {
    vi.mocked(holdingsApi.updateStockHolding).mockRejectedValue(new Error('500'))
    const { holding, onChanged } = setup(stockRow)

    await expect(holding.toggleFrozen()).rejects.toThrow('500')

    expect(toastOpen).not.toHaveBeenCalled()
    expect(holdingsApi.getStockHolding).not.toHaveBeenCalled()
    expect(onChanged).not.toHaveBeenCalled()
  })

  it('swallows a rejected withdrawal undo without reloading or reporting', async () => {
    vi.mocked(resultsApi.deleteStockWithdrawal).mockRejectedValue(new Error('409'))
    const { holding, onChanged } = setup(stockRow)

    holding.confirmRemoveWithdrawal('result-1')
    await lastConfirm().onConfirm()

    expect(toastOpen).not.toHaveBeenCalled()
    expect(holdingsApi.getStockHolding).not.toHaveBeenCalled()
    expect(onChanged).not.toHaveBeenCalled()
  })

  it('keeps the date editor open when saving the date is rejected', async () => {
    vi.mocked(holdingsApi.updateStockLotDate).mockRejectedValue(new Error('500'))
    const { holding, onChanged } = setup(stockRow)
    holding.editingEntryId.value = 'lot-1'

    await expect(holding.updateEntryDate('lot-1', new Date())).rejects.toThrow('500')

    expect(holding.editingEntryId.value).toBe('lot-1')
    expect(onChanged).not.toHaveBeenCalled()
  })
})
