import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { holdingDisplayName, useReinvestForm } from './useReinvestForm'
import { holdingsApi } from '@/api/holdings'
import { reinvestmentsApi } from '@/api/reinvestments'
import type { HoldingRow } from '@/types'

vi.mock('@/api/holdings', () => ({ holdingsApi: { findAll: vi.fn() } }))
vi.mock('@/api/reinvestments', () => ({
  reinvestmentsApi: { findAll: vi.fn(), reinvest: vi.fn() },
}))
const toastOpen = vi.fn()
vi.mock('buefy', () => ({ useToast: () => ({ open: toastOpen }) }))

function holdingOf(overrides: Partial<HoldingRow>): HoldingRow {
  return {
    id: 'holding-petr4',
    kind: 'STOCKS',
    name: 'Petrobras',
    ticker: 'PETR4',
    typeLabel: 'Ação ON',
    segmentLabel: null,
    walletId: 'wallet-stocks',
    walletName: 'Ações',
    walletCurrency: 'BRL',
    quantity: 100,
    costBasis: 3500,
    currentPrice: 38.5,
    currentValue: 3850,
    gain: 350,
    gainPct: 10,
    frozen: false,
    ...overrides,
  }
}

const petrobras = holdingOf({})
const vale = holdingOf({ id: 'holding-vale3', ticker: 'VALE3', name: 'Vale', currentPrice: 50 })
const treasury = holdingOf({
  id: 'holding-treasury',
  kind: 'FUNDS',
  ticker: null,
  name: 'Tesouro Selic',
  walletId: 'wallet-funds',
  walletName: 'Fundos',
  quantity: null,
  currentPrice: null,
  currentValue: 1200,
  costBasis: 1000,
})
const frozenItau = holdingOf({ id: 'holding-itub4', ticker: 'ITUB4', frozen: true })
const unpricedBitcoin = holdingOf({
  id: 'holding-btc',
  kind: 'CRYPTO',
  ticker: 'BTC',
  walletId: 'wallet-crypto',
  walletName: 'Cripto',
  currentPrice: null,
})
const apple = holdingOf({ id: 'holding-aapl', ticker: 'AAPL', walletCurrency: 'USD' })

function pageOf(content: HoldingRow[]) {
  return { content, page: { size: 500, number: 0, totalElements: content.length, totalPages: 1 } }
}

async function loadedForm(
  preselectedHoldingId?: string,
  walletHoldings: HoldingRow[] = [petrobras, vale, treasury],
) {
  vi.mocked(holdingsApi.findAll).mockImplementation(async (params) =>
    pageOf(
      params.walletId
        ? walletHoldings
        : [petrobras, vale, treasury, frozenItau, unpricedBitcoin, apple],
    ),
  )
  const onDone = vi.fn()
  const form = useReinvestForm(onDone)
  await form.load('wallet-stocks', preselectedHoldingId)
  await nextTick()
  return { form, onDone }
}

describe('useReinvestForm', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('names a holding by ticker, falling back to its name', () => {
    expect(holdingDisplayName(petrobras)).toBe('PETR4')
    expect(holdingDisplayName(treasury)).toBe('Tesouro Selic')
  })

  it('loads the wallet holdings as sources and leaves the source empty without a preselection', async () => {
    const { form } = await loadedForm()

    expect(form.sources.value.map((holding) => holding.id)).toEqual([
      'holding-petr4',
      'holding-vale3',
      'holding-treasury',
    ])
    expect(form.sourceId.value).toBe('')
    expect(form.destinations.value).toEqual([])
    expect(form.loading.value).toBe(false)
  })

  it('preselects a holding of the wallet and pre-fills the unit price with its current price', async () => {
    const { form } = await loadedForm('holding-petr4')

    expect(form.source.value?.id).toBe('holding-petr4')
    expect(form.unitPrice.value).toBe(38.5)
  })

  it('ignores a preselected holding that is not in the wallet', async () => {
    const { form } = await loadedForm('holding-aapl')

    expect(form.sourceId.value).toBe('')
  })

  it('offers same-currency destinations other than the source, excluding frozen holdings, grouped by wallet', async () => {
    const { form } = await loadedForm('holding-petr4')

    expect(form.destinations.value.map((holding) => holding.id)).toEqual([
      'holding-vale3',
      'holding-treasury',
      'holding-btc',
    ])
    expect(form.destinationGroups.value.map((group) => group.walletName)).toEqual([
      'Ações',
      'Fundos',
      'Cripto',
    ])
  })

  it('computes gross, net and the destination quantity for a stock source', async () => {
    const { form } = await loadedForm('holding-petr4')
    form.destinationId.value = 'holding-vale3'
    form.quantity.value = 10
    form.unitPrice.value = 40
    form.fees.value = 50
    form.taxes.value = 50

    expect(form.grossAmount.value).toBe(400)
    expect(form.netAmount.value).toBe(300)
    expect(form.destinationQuantity.value).toBe(6)
    expect(form.valid.value).toBe(true)
  })

  it('has no destination quantity for a fund destination', async () => {
    const { form } = await loadedForm('holding-petr4')
    form.destinationId.value = 'holding-treasury'
    form.quantity.value = 10

    expect(form.destinationQuantity.value).toBeNull()
    expect(form.valid.value).toBe(true)
  })

  it('blocks an unpriced stock or crypto destination', async () => {
    const { form } = await loadedForm('holding-petr4')
    form.destinationId.value = 'holding-btc'
    form.quantity.value = 10

    expect(form.destinationUnpriced.value).toBe(true)
    expect(form.destinationQuantity.value).toBeNull()
    expect(form.valid.value).toBe(false)
  })

  it('rejects a quantity above the source position', async () => {
    const { form } = await loadedForm('holding-petr4')
    form.destinationId.value = 'holding-vale3'
    form.quantity.value = 101

    expect(form.exceedsRemaining.value).toBe(true)
    expect(form.valid.value).toBe(false)
  })

  it('flags costs that consume the whole gross amount', async () => {
    const { form } = await loadedForm('holding-petr4')
    form.destinationId.value = 'holding-vale3'
    form.quantity.value = 1
    form.fees.value = 50

    expect(form.costsExceedGross.value).toBe(true)
    expect(form.valid.value).toBe(false)
  })

  it('reads an amount instead of quantity for a fund source and caps it at the current value', async () => {
    const { form } = await loadedForm('holding-treasury')
    form.destinationId.value = 'holding-vale3'
    form.amount.value = 1000

    expect(form.isFundSource.value).toBe(true)
    expect(form.grossAmount.value).toBe(1000)
    expect(form.valid.value).toBe(true)

    form.amount.value = 1300
    expect(form.exceedsRemaining.value).toBe(true)
    expect(form.valid.value).toBe(false)
  })

  it('keeps a destination still valid for the new source and clears the inputs when the source changes', async () => {
    const { form } = await loadedForm('holding-petr4')
    form.destinationId.value = 'holding-treasury'
    form.quantity.value = 5

    form.sourceId.value = 'holding-vale3'
    await nextTick()

    expect(form.destinationId.value).toBe('holding-treasury')
    expect(form.quantity.value).toBe('')
    expect(form.unitPrice.value).toBe(50)

    form.destinationId.value = 'holding-petr4'
    form.sourceId.value = 'holding-petr4'
    await nextTick()

    expect(form.destinationId.value).toBe('')
  })

  it('submits a stock reinvestment and calls onDone', async () => {
    vi.mocked(reinvestmentsApi.reinvest).mockResolvedValue(undefined as never)
    const { form, onDone } = await loadedForm('holding-petr4')
    form.destinationId.value = 'holding-vale3'
    form.date.value = new Date('2026-03-10T12:00:00Z')
    form.quantity.value = 10
    form.unitPrice.value = 40

    await form.submit()

    expect(reinvestmentsApi.reinvest).toHaveBeenCalledWith({
      sourceKind: 'STOCKS',
      sourceHoldingId: 'holding-petr4',
      destinationKind: 'STOCKS',
      destinationHoldingId: 'holding-vale3',
      reinvestmentDate: '2026-03-10',
      quantity: 10,
      unitPrice: 40,
      fees: 0,
      taxes: 0,
    })
    expect(toastOpen).toHaveBeenCalledWith({
      message: 'Reinvestimento registrado!',
      type: 'is-success',
    })
    expect(onDone).toHaveBeenCalledOnce()
    expect(form.submitting.value).toBe(false)
  })

  it('submits a fund reinvestment with its amount', async () => {
    vi.mocked(reinvestmentsApi.reinvest).mockResolvedValue(undefined as never)
    const { form } = await loadedForm('holding-treasury')
    form.destinationId.value = 'holding-vale3'
    form.amount.value = 500

    await form.submit()

    expect(reinvestmentsApi.reinvest).toHaveBeenCalledWith(
      expect.objectContaining({ sourceKind: 'FUNDS', amount: 500 }),
    )
  })

  it('does not submit an invalid form', async () => {
    const { form, onDone } = await loadedForm('holding-petr4')

    await form.submit()

    expect(reinvestmentsApi.reinvest).not.toHaveBeenCalled()
    expect(onDone).not.toHaveBeenCalled()
  })

  it('shows a server 400 inline and keeps the selection', async () => {
    vi.mocked(reinvestmentsApi.reinvest).mockRejectedValue({
      isAxiosError: true,
      response: { status: 400, data: { detail: 'Moedas diferentes' } },
    })
    const { form, onDone } = await loadedForm('holding-petr4')
    form.destinationId.value = 'holding-vale3'
    form.quantity.value = 10

    await form.submit()

    expect(form.error.value).toBe('Moedas diferentes')
    expect(form.sourceId.value).toBe('holding-petr4')
    expect(form.destinationId.value).toBe('holding-vale3')
    expect(onDone).not.toHaveBeenCalled()
  })

  it('falls back to a generic message when the failure carries no detail', async () => {
    vi.mocked(reinvestmentsApi.reinvest).mockRejectedValue(new Error('network'))
    const { form } = await loadedForm('holding-petr4')
    form.destinationId.value = 'holding-vale3'
    form.quantity.value = 10

    await form.submit()

    expect(form.error.value).toBe('Não foi possível registrar o reinvestimento.')
  })
})
