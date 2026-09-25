import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import MoveHoldingsModal from './MoveHoldingsModal.vue'
import { holdingsApi } from '@/api/holdings'
import { useWalletsStore } from '@/stores/wallets'
import { useWalletMovesStore } from '@/stores/walletMoves'
import type { HoldingRow, WalletResponse } from '@/types'

vi.mock('@/api/holdings', () => ({ holdingsApi: { findAll: vi.fn() } }))
vi.mock('@/api/wallets', () => ({ walletsApi: { findAll: vi.fn() } }))
vi.mock('@/api/walletMoves', () => ({ walletMovesApi: { findAll: vi.fn(), move: vi.fn() } }))

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
    walletId: 'wallet-origin',
    walletName: 'Origem',
    walletCurrency: 'BRL',
    quantity: 30,
    costBasis: 900,
    currentPrice: 35,
    currentValue: 1050,
    gain: 150,
    gainPct: 16.67,
    ...overrides,
  }
}

const wallets: WalletResponse[] = [
  walletOf({}),
  walletOf({ id: 'wallet-same', name: 'Mesma moeda' }),
  walletOf({ id: 'wallet-dollar', name: 'Em dólar', currency: 'USD' }),
  walletOf({ id: 'wallet-crypto', name: 'Cripto', kind: 'CRYPTO' }),
]

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

async function mountModal(
  props: { originWalletId?: string; preselectedHoldingId?: string } = {},
  holdings: HoldingRow[] = [holdingOf({}), holdingOf({ id: 'holding-vale3', ticker: 'VALE3', name: 'Vale' })],
) {
  vi.mocked(holdingsApi.findAll).mockResolvedValue({
    content: holdings,
    page: { size: 500, number: 0, totalElements: holdings.length, totalPages: 1 },
  })
  const pinia = createTestingPinia()
  const walletsStore = useWalletsStore()
  walletsStore.wallets = wallets
  walletsStore.walletById = (id: string) => wallets.find((wallet) => wallet.id === id)
  const walletMovesStore = useWalletMovesStore()

  const wrapper = mount(MoveHoldingsModal, { props, global: { plugins: [pinia] } })
  await flushPromises()
  return { wrapper, walletMovesStore }
}

type ModalWrapper = Awaited<ReturnType<typeof mountModal>>['wrapper']

function control(wrapper: ModalWrapper, testId: string) {
  const element = wrapper.find(`[data-testid="${testId}"]`)
  return ['INPUT', 'SELECT'].includes(element.element.tagName)
    ? element
    : element.find('input, select')
}

async function chooseDestination(wrapper: ModalWrapper, id: string) {
  await control(wrapper, 'move-destination').setValue(id)
}

async function submit(wrapper: ModalWrapper) {
  await wrapper.find('[data-testid="move-submit"]').trigger('click')
  await flushPromises()
}

describe('MoveHoldingsModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('asks for an origin wallet when none is given', async () => {
    const { wrapper } = await mountModal()

    expect(wrapper.find('[data-testid="move-origin"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid="move-item"]')).toHaveLength(0)
  })

  it('hides the origin picker when the origin is pre-selected', async () => {
    const { wrapper } = await mountModal({ originWalletId: 'wallet-origin' })

    expect(wrapper.find('[data-testid="move-origin"]').exists()).toBe(false)
    expect(holdingsApi.findAll).toHaveBeenCalledWith({ walletId: 'wallet-origin', size: 500 })
    expect(wrapper.findAll('[data-testid="move-item"]')).toHaveLength(2)
  })

  it('offers only destinations with the same kind and currency, never the origin', async () => {
    const { wrapper } = await mountModal({ originWalletId: 'wallet-origin' })

    const options = wrapper
      .findAll('[data-testid="move-destination"] option')
      .map((option) => option.text())
      .filter((text) => text.length > 0)

    expect(options).toEqual(['Mesma moeda · BRL'])
  })

  it('submits every holding in one call with "Mover tudo"', async () => {
    const { wrapper, walletMovesStore } = await mountModal({ originWalletId: 'wallet-origin' })

    await chooseDestination(wrapper, 'wallet-same')
    await control(wrapper, 'move-all').setValue(true)
    await submit(wrapper)

    expect(walletMovesStore.move).toHaveBeenCalledTimes(1)
    expect(walletMovesStore.move).toHaveBeenCalledWith('wallet-origin', {
      destinationWalletId: 'wallet-same',
      items: [{ holdingId: 'holding-petr4' }, { holdingId: 'holding-vale3' }],
    })
    expect(wrapper.emitted('moved')).toHaveLength(1)
  })

  it('offers a quantity for a selected stock and sends it', async () => {
    const { wrapper, walletMovesStore } = await mountModal({
      originWalletId: 'wallet-origin',
      preselectedHoldingId: 'holding-petr4',
    })

    await chooseDestination(wrapper, 'wallet-same')
    await control(wrapper, 'move-quantity').setValue('10')
    await submit(wrapper)

    expect(walletMovesStore.move).toHaveBeenCalledWith('wallet-origin', {
      destinationWalletId: 'wallet-same',
      items: [{ holdingId: 'holding-petr4', quantity: 10 }],
    })
  })

  it('shows no quantity field for a selected fund', async () => {
    const { wrapper } = await mountModal(
      { originWalletId: 'wallet-origin', preselectedHoldingId: 'holding-fund' },
      [holdingOf({ id: 'holding-fund', kind: 'FUNDS', ticker: null, name: 'HGLG11', quantity: null })],
    )

    expect(wrapper.find('.move-item.is-selected').exists()).toBe(true)
    expect(wrapper.find('[data-testid="move-quantity"]').exists()).toBe(false)
  })

  it('blocks a quantity larger than what is available', async () => {
    const { wrapper } = await mountModal({
      originWalletId: 'wallet-origin',
      preselectedHoldingId: 'holding-petr4',
    })

    await chooseDestination(wrapper, 'wallet-same')
    await control(wrapper, 'move-quantity').setValue('31')

    expect(wrapper.text()).toContain('Maior que o disponível')
    expect(wrapper.find('[data-testid="move-submit"]').attributes('disabled')).toBeDefined()
  })

  it("shows the server's rejection and keeps the modal open with the selection", async () => {
    const { wrapper, walletMovesStore } = await mountModal({
      originWalletId: 'wallet-origin',
      preselectedHoldingId: 'holding-petr4',
    })
    vi.mocked(walletMovesStore.move).mockRejectedValue({
      isAxiosError: true,
      response: { status: 400, data: { detail: 'As carteiras devem ter a mesma moeda' } },
    })

    await chooseDestination(wrapper, 'wallet-same')
    await submit(wrapper)

    expect(wrapper.find('[data-testid="move-error"]').text()).toBe(
      'As carteiras devem ter a mesma moeda',
    )
    expect(wrapper.emitted('close')).toBeUndefined()
    expect(wrapper.find('.move-item.is-selected').exists()).toBe(true)
  })
})
