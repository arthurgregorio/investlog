import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { exceedsRemaining, takesQuantity, useMoveHoldingsForm } from './useMoveHoldingsForm'
import { holdingsApi } from '@/api/holdings'
import { walletsApi } from '@/api/wallets'
import { walletMovesApi } from '@/api/walletMoves'
import { useWalletsStore } from '@/stores/wallets'
import type { HoldingRow, WalletResponse } from '@/types'

vi.mock('@/api/holdings', () => ({ holdingsApi: { findAll: vi.fn() } }))
vi.mock('@/api/wallets', () => ({ walletsApi: { findAll: vi.fn() } }))
vi.mock('@/api/walletMoves', () => ({ walletMovesApi: { findAll: vi.fn(), move: vi.fn() } }))
const toastOpen = vi.fn()
vi.mock('buefy', () => ({ useToast: () => ({ open: toastOpen }) }))

function walletOf(overrides: Partial<WalletResponse>): WalletResponse {
  return {
    id: 'wallet-origin',
    name: 'Origem',
    kind: 'STOCKS',
    currency: 'BRL',
    holdingCount: 2,
    totalInvested: 1000,
    currentValue: 1100,
    gain: 100,
    gainPct: 10,
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

function holdingOf(overrides: Partial<HoldingRow>): HoldingRow {
  return {
    id: 'holding-petr4',
    kind: 'STOCKS',
    name: 'Petrobras',
    ticker: 'PETR4',
    typeLabel: 'Ação ON',
    segmentLabel: null,
    walletId: 'wallet-origin',
    walletName: 'Origem',
    walletCurrency: 'BRL',
    quantity: 30,
    costBasis: 900,
    currentPrice: 35,
    currentValue: 1050,
    gain: 150,
    gainPct: 16.67,
    frozen: false,
    ...overrides,
  }
}

const petrobras = holdingOf({})
const vale = holdingOf({ id: 'holding-vale3', ticker: 'VALE3', name: 'Vale' })
const fund = holdingOf({ id: 'holding-fund', kind: 'FUNDS', ticker: null, quantity: null })

const wallets: WalletResponse[] = [
  walletOf({}),
  walletOf({ id: 'wallet-same', name: 'Mesma moeda' }),
  walletOf({ id: 'wallet-dollar', name: 'Em dólar', currency: 'USD' }),
  walletOf({ id: 'wallet-crypto', name: 'Cripto', kind: 'CRYPTO' }),
]

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

async function loadedForm(
  options: { originWalletId?: string; preselectedHoldingId?: string } = {
    originWalletId: 'wallet-origin',
  },
) {
  vi.mocked(holdingsApi.findAll).mockResolvedValue({
    content: [petrobras, vale],
    page: { size: 500, number: 0, totalElements: 2, totalPages: 1 },
  })
  const onDone = vi.fn()
  const form = useMoveHoldingsForm(options, onDone)
  await form.load()
  return { form, onDone }
}

describe('takesQuantity and exceedsRemaining', () => {
  it('asks a quantity only of stocks and crypto', () => {
    expect(takesQuantity(petrobras)).toBe(true)
    expect(takesQuantity(fund)).toBe(false)
  })

  it('flags only a quantity above the position', () => {
    expect(exceedsRemaining(petrobras, 31)).toBe(true)
    expect(exceedsRemaining(petrobras, 30)).toBe(false)
    expect(exceedsRemaining(petrobras, '')).toBe(false)
    expect(exceedsRemaining(petrobras, undefined)).toBe(false)
    expect(exceedsRemaining(fund, 5)).toBe(false)
  })
})

describe('useMoveHoldingsForm', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    const walletsStore = useWalletsStore()
    walletsStore.wallets = wallets
    walletsStore.loaded = true
  })

  it('loads the origin holdings and lists same-kind, same-currency destinations', async () => {
    const { form } = await loadedForm()

    expect(holdingsApi.findAll).toHaveBeenCalledWith({ walletId: 'wallet-origin', size: 500 })
    expect(form.holdings.value).toHaveLength(2)
    expect(form.destinations.value.map((wallet) => wallet.id)).toEqual(['wallet-same'])
    expect(form.loadingHoldings.value).toBe(false)
  })

  it('loads the wallets when they are not loaded yet', async () => {
    useWalletsStore().loaded = false
    vi.mocked(walletsApi.findAll).mockResolvedValue({
      content: wallets,
      page: { size: 20, number: 0, totalElements: wallets.length, totalPages: 1 },
    })

    await loadedForm()

    expect(walletsApi.findAll).toHaveBeenCalled()
  })

  it('loads nothing without an origin', async () => {
    const { form } = await loadedForm({})

    expect(holdingsApi.findAll).not.toHaveBeenCalled()
    expect(form.destinations.value).toEqual([])
    expect(form.valid.value).toBe(false)
  })

  it('preselects a holding of the origin', async () => {
    const { form } = await loadedForm({
      originWalletId: 'wallet-origin',
      preselectedHoldingId: 'holding-vale3',
    })

    expect(form.selected.value).toEqual({ 'holding-vale3': true })
  })

  it('ignores a preselected holding that is not in the origin', async () => {
    const { form } = await loadedForm({
      originWalletId: 'wallet-origin',
      preselectedHoldingId: 'holding-elsewhere',
    })

    expect(form.selected.value).toEqual({})
  })

  it('builds items from the selection, sending a quantity only when one was typed', async () => {
    const { form } = await loadedForm()
    form.destinationId.value = 'wallet-same'
    form.select('holding-petr4', true)
    form.select('holding-vale3', true)
    form.setQuantity('holding-petr4', 10)

    expect(form.items.value).toEqual([
      { holdingId: 'holding-petr4', quantity: 10 },
      { holdingId: 'holding-vale3' },
    ])
    expect(form.valid.value).toBe(true)
  })

  it('moves every holding with "Mover tudo", ignoring typed quantities', async () => {
    const { form } = await loadedForm()
    form.setQuantity('holding-petr4', 999)
    form.select('holding-petr4', true)
    form.moveAll.value = true

    expect(form.items.value).toEqual([
      { holdingId: 'holding-petr4' },
      { holdingId: 'holding-vale3' },
    ])
  })

  it('is invalid with a quantity above the position or below zero', async () => {
    const { form } = await loadedForm()
    form.destinationId.value = 'wallet-same'
    form.select('holding-petr4', true)

    form.setQuantity('holding-petr4', 31)
    expect(form.valid.value).toBe(false)

    form.setQuantity('holding-petr4', -1)
    expect(form.valid.value).toBe(false)
  })

  it('resets the selection and drops a mismatched destination when the origin changes', async () => {
    const { form } = await loadedForm({})
    form.selectedOriginId.value = 'wallet-origin'
    await nextTick()
    await flushPromises()
    form.destinationId.value = 'wallet-same'
    form.select('holding-petr4', true)

    form.selectedOriginId.value = 'wallet-crypto'
    await nextTick()
    await flushPromises()

    expect(form.destinationId.value).toBe('')
    expect(form.selected.value).toEqual({})
    expect(holdingsApi.findAll).toHaveBeenLastCalledWith({ walletId: 'wallet-crypto', size: 500 })
  })

  it('submits the move and calls onDone', async () => {
    vi.mocked(walletMovesApi.move).mockResolvedValue(undefined as never)
    const { form, onDone } = await loadedForm()
    form.destinationId.value = 'wallet-same'
    form.select('holding-vale3', true)

    await form.submit()

    expect(walletMovesApi.move).toHaveBeenCalledWith('wallet-origin', {
      destinationWalletId: 'wallet-same',
      items: [{ holdingId: 'holding-vale3' }],
    })
    expect(toastOpen).toHaveBeenCalledWith({
      message: 'Investimentos movidos!',
      type: 'is-success',
    })
    expect(onDone).toHaveBeenCalledOnce()
  })

  it('does not submit an invalid form', async () => {
    const { form } = await loadedForm()

    await form.submit()

    expect(walletMovesApi.move).not.toHaveBeenCalled()
  })

  it('shows a server 400 inline and keeps the selection', async () => {
    vi.mocked(walletMovesApi.move).mockRejectedValue({
      isAxiosError: true,
      response: { status: 400, data: { detail: 'Carteira incompatível' } },
    })
    const { form, onDone } = await loadedForm()
    form.destinationId.value = 'wallet-same'
    form.select('holding-vale3', true)

    await form.submit()

    expect(form.error.value).toBe('Carteira incompatível')
    expect(form.selected.value).toEqual({ 'holding-vale3': true })
    expect(form.submitting.value).toBe(false)
    expect(onDone).not.toHaveBeenCalled()
  })

  it('falls back to a generic message when the failure carries no detail', async () => {
    vi.mocked(walletMovesApi.move).mockRejectedValue(new Error('network'))
    const { form } = await loadedForm()
    form.destinationId.value = 'wallet-same'
    form.select('holding-vale3', true)

    await form.submit()

    expect(form.error.value).toBe('Não foi possível mover os investimentos.')
  })
})
