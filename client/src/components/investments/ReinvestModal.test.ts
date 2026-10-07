import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import ReinvestModal from './ReinvestModal.vue'
import { holdingsApi } from '@/api/holdings'
import { useReinvestmentsStore } from '@/stores/reinvestments'
import type { HoldingRow } from '@/types'

vi.mock('@/api/holdings', () => ({ holdingsApi: { findAll: vi.fn() } }))
vi.mock('@/api/reinvestments', () => ({
  reinvestmentsApi: { findAll: vi.fn(), reinvest: vi.fn() },
}))

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
const vale = holdingOf({ id: 'holding-vale3', ticker: 'VALE3', name: 'Vale', currentPrice: 60 })
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
const unpricedBitcoin = holdingOf({
  id: 'holding-btc',
  kind: 'CRYPTO',
  ticker: 'BTC',
  name: 'Bitcoin',
  walletId: 'wallet-crypto',
  walletName: 'Cripto',
  currentPrice: null,
})
const apple = holdingOf({
  id: 'holding-aapl',
  ticker: 'AAPL',
  name: 'Apple',
  walletId: 'wallet-dollar',
  walletName: 'Em dólar',
  walletCurrency: 'USD',
})

const walletHoldings = [petrobras, vale]
const allHoldings = [petrobras, vale, treasury, unpricedBitcoin, apple]

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

async function mountModal(
  props: { walletId: string; preselectedHoldingId?: string },
  candidates: HoldingRow[] = allHoldings,
) {
  vi.mocked(holdingsApi.findAll).mockImplementation(async (params) => {
    const content = params.walletId ? walletHoldings : candidates
    return { content, page: { size: 500, number: 0, totalElements: content.length, totalPages: 1 } }
  })
  const pinia = createTestingPinia()
  const reinvestmentsStore = useReinvestmentsStore()

  const wrapper = mount(ReinvestModal, { props, global: { plugins: [pinia] } })
  await flushPromises()
  return { wrapper, reinvestmentsStore }
}

type ModalWrapper = Awaited<ReturnType<typeof mountModal>>['wrapper']

function control(wrapper: ModalWrapper, testId: string) {
  const element = wrapper.find(`[data-testid="${testId}"]`)
  return ['INPUT', 'SELECT'].includes(element.element.tagName)
    ? element
    : element.find('input, select')
}

function optionTexts(wrapper: ModalWrapper, testId: string) {
  return wrapper
    .findAll(`[data-testid="${testId}"] option`)
    .map((option) => option.text())
    .filter((text) => text.length > 0)
}

async function submit(wrapper: ModalWrapper) {
  await wrapper.find('[data-testid="reinvest-submit"]').trigger('click')
  await flushPromises()
}

describe('ReinvestModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('preselects the source and prefills its unit price with the last known price', async () => {
    const { wrapper } = await mountModal({
      walletId: 'wallet-stocks',
      preselectedHoldingId: 'holding-petr4',
    })

    expect((control(wrapper, 'reinvest-source').element as HTMLSelectElement).value).toBe(
      'holding-petr4',
    )
    expect((control(wrapper, 'reinvest-unit-price').element as HTMLInputElement).value).toBe('38.5')
  })

  it('offers the wallet holdings as sources when opened without one', async () => {
    const { wrapper } = await mountModal({ walletId: 'wallet-stocks' })

    expect(holdingsApi.findAll).toHaveBeenCalledWith({ walletId: 'wallet-stocks', size: 500 })
    expect(optionTexts(wrapper, 'reinvest-source')).toEqual([
      'PETR4 · 100 disponíveis',
      'VALE3 · 100 disponíveis',
    ])
    expect(wrapper.find('[data-testid="reinvest-quantity"]').exists()).toBe(false)
  })

  it('lists only other active holdings in the same currency as destinations', async () => {
    const { wrapper } = await mountModal({
      walletId: 'wallet-stocks',
      preselectedHoldingId: 'holding-petr4',
    })

    expect(optionTexts(wrapper, 'reinvest-destination')).toEqual(['VALE3', 'Tesouro Selic', 'BTC'])
  })

  it('asks for a quantity on a stock source and for an amount on a fund source', async () => {
    const stockModal = await mountModal({
      walletId: 'wallet-stocks',
      preselectedHoldingId: 'holding-petr4',
    })
    expect(stockModal.wrapper.find('[data-testid="reinvest-quantity"]').exists()).toBe(true)
    expect(stockModal.wrapper.find('[data-testid="reinvest-amount"]').exists()).toBe(false)

    vi.mocked(holdingsApi.findAll).mockImplementation(async (params) => {
      const content = params.walletId ? [treasury] : allHoldings
      return {
        content,
        page: { size: 500, number: 0, totalElements: content.length, totalPages: 1 },
      }
    })
    const fundModal = mount(ReinvestModal, {
      props: { walletId: 'wallet-funds', preselectedHoldingId: 'holding-treasury' },
      global: { plugins: [createTestingPinia()] },
    })
    await flushPromises()

    expect(fundModal.find('[data-testid="reinvest-amount"]').exists()).toBe(true)
    expect(fundModal.find('[data-testid="reinvest-quantity"]').exists()).toBe(false)
  })

  it('submits with fees and taxes left empty as zero', async () => {
    const { wrapper, reinvestmentsStore } = await mountModal({
      walletId: 'wallet-stocks',
      preselectedHoldingId: 'holding-petr4',
    })

    await control(wrapper, 'reinvest-destination').setValue('holding-treasury')
    await control(wrapper, 'reinvest-quantity').setValue('40')
    await submit(wrapper)

    expect(reinvestmentsStore.reinvest).toHaveBeenCalledWith(
      expect.objectContaining({
        sourceKind: 'STOCKS',
        sourceHoldingId: 'holding-petr4',
        destinationKind: 'FUNDS',
        destinationHoldingId: 'holding-treasury',
        quantity: 40,
        unitPrice: 38.5,
        fees: 0,
        taxes: 0,
      }),
    )
    expect(wrapper.emitted('reinvested')).toHaveLength(1)
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('shows the net amount and what it buys on a priced destination', async () => {
    const { wrapper } = await mountModal({
      walletId: 'wallet-stocks',
      preselectedHoldingId: 'holding-petr4',
    })

    await control(wrapper, 'reinvest-destination').setValue('holding-vale3')
    await control(wrapper, 'reinvest-quantity').setValue('40')
    await control(wrapper, 'reinvest-unit-price').setValue('60')

    const summary = wrapper.find('[data-testid="reinvest-summary"]').text()
    expect(summary).toContain('2.400,00')
    expect(summary).toContain('Compra de ≈ 40 VALE3')
  })

  it('explains and blocks a destination without a current price', async () => {
    const { wrapper } = await mountModal({
      walletId: 'wallet-stocks',
      preselectedHoldingId: 'holding-petr4',
    })

    await control(wrapper, 'reinvest-destination').setValue('holding-btc')
    await control(wrapper, 'reinvest-quantity').setValue('10')

    expect(wrapper.find('.help.is-danger').text()).toContain('BTC não tem preço atual')
    expect(wrapper.find('[data-testid="reinvest-submit"]').attributes('disabled')).toBeDefined()
  })

  it('blocks a quantity larger than what is available', async () => {
    const { wrapper } = await mountModal({
      walletId: 'wallet-stocks',
      preselectedHoldingId: 'holding-petr4',
    })

    await control(wrapper, 'reinvest-destination').setValue('holding-treasury')
    await control(wrapper, 'reinvest-quantity').setValue('101')

    expect(wrapper.text()).toContain('Maior que o disponível')
    expect(wrapper.find('[data-testid="reinvest-submit"]').attributes('disabled')).toBeDefined()
  })

  it("shows the server's rejection and keeps the modal open with the values entered", async () => {
    const { wrapper, reinvestmentsStore } = await mountModal({
      walletId: 'wallet-stocks',
      preselectedHoldingId: 'holding-petr4',
    })
    vi.mocked(reinvestmentsStore.reinvest).mockRejectedValue({
      isAxiosError: true,
      response: {
        status: 400,
        data: {
          detail: 'Os investimentos de origem e destino devem estar em carteiras com a mesma moeda',
        },
      },
    })

    await control(wrapper, 'reinvest-destination').setValue('holding-treasury')
    await control(wrapper, 'reinvest-quantity').setValue('40')
    await submit(wrapper)

    expect(wrapper.find('[data-testid="reinvest-error"]').text()).toBe(
      'Os investimentos de origem e destino devem estar em carteiras com a mesma moeda',
    )
    expect(wrapper.emitted('close')).toBeUndefined()
    expect(wrapper.emitted('reinvested')).toBeUndefined()
    expect((control(wrapper, 'reinvest-quantity').element as HTMLInputElement).value).toBe('40')
  })

  it('does not offer a frozen holding as a destination', async () => {
    const { wrapper } = await mountModal(
      { walletId: 'wallet-stocks', preselectedHoldingId: 'holding-petr4' },
      [petrobras, { ...vale, frozen: true }, treasury],
    )

    expect(optionTexts(wrapper, 'reinvest-destination')).toEqual(['Tesouro Selic'])
  })

  it('keeps the modal open with the values entered when the destination turns out to be frozen', async () => {
    const { wrapper, reinvestmentsStore } = await mountModal({
      walletId: 'wallet-stocks',
      preselectedHoldingId: 'holding-petr4',
    })
    vi.mocked(reinvestmentsStore.reinvest).mockRejectedValue({
      isAxiosError: true,
      response: {
        status: 409,
        data: { detail: 'Tesouro Selic está congelado e não pode receber um reinvestimento' },
      },
    })

    await control(wrapper, 'reinvest-destination').setValue('holding-treasury')
    await control(wrapper, 'reinvest-quantity').setValue('40')
    await submit(wrapper)

    expect(wrapper.find('[data-testid="reinvest-error"]').text()).toBe(
      'Não foi possível registrar o reinvestimento.',
    )
    expect(wrapper.emitted('close')).toBeUndefined()
    expect(wrapper.emitted('reinvested')).toBeUndefined()
    expect((control(wrapper, 'reinvest-quantity').element as HTMLInputElement).value).toBe('40')
  })

  it('covers the form while the holdings load and leaves once they arrive', async () => {
    let resolveHoldings!: (page: Awaited<ReturnType<typeof holdingsApi.findAll>>) => void
    vi.mocked(holdingsApi.findAll).mockReturnValue(
      new Promise((resolve) => {
        resolveHoldings = resolve
      }),
    )

    const wrapper = mount(ReinvestModal, {
      props: { walletId: 'wallet-stocks' },
      global: { plugins: [createTestingPinia()] },
    })
    await flushPromises()
    expect(wrapper.findAll('.loading-overlay')).toHaveLength(1)

    resolveHoldings({
      content: walletHoldings,
      page: { size: 500, number: 0, totalElements: walletHoldings.length, totalPages: 1 },
    })
    await flushPromises()
    expect(wrapper.find('.loading-overlay').exists()).toBe(false)
  })
})
