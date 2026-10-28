import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import CashReconciliationCard from './CashReconciliationCard.vue'
import { cashApi } from '@/api/cash'
import { useRatesStore } from '@/stores/rates'
import { useWalletsStore } from '@/stores/wallets'
import type { CashReconciliation, WalletResponse } from '@/types'

vi.mock('@/api/cash', () => ({
  cashApi: { transfer: vi.fn(), withdrawToBank: vi.fn(), markReconciled: vi.fn() },
}))

function walletOf(id: string, name: string, currency: string): WalletResponse {
  return {
    id,
    name,
    kind: 'STOCKS',
    currency,
    holdingCount: 1,
    totalInvested: 0,
    currentValue: null,
    gain: null,
    gainPct: null,
    cash: 0,
    cashReconciled: true,
    createdAt: '2026-01-01T00:00:00Z',
  }
}

function reconciliationOf(overrides: Partial<CashReconciliation> = {}): CashReconciliation {
  return {
    walletId: 'wallet-us',
    walletName: 'Ações EUA',
    kind: 'STOCKS',
    currency: 'USD',
    cash: 1000,
    sources: [
      {
        resultId: 'result-2',
        date: '2026-10-06',
        holdingName: 'Nvidia',
        ticker: 'NVDA',
        amount: 420,
      },
      { resultId: 'result-1', date: '2026-09-15', holdingName: 'Apple', ticker: null, amount: 580 },
    ],
    suggestion: {
      walletId: 'wallet-br',
      walletName: 'Ações Brasil',
      walletCurrency: 'BRL',
      holdingName: 'Petrobras',
      ticker: 'PETR4',
      purchaseDate: '2026-09-20',
      newMoney: 2660,
      rate: 5.32,
    },
    ...overrides,
  }
}

let activeWrapper: VueWrapper | undefined

function mountCard(reconciliation = reconciliationOf()) {
  const pinia = createTestingPinia()
  useWalletsStore().wallets = [
    walletOf('wallet-us', 'Ações EUA', 'USD'),
    walletOf('wallet-br', 'Ações Brasil', 'BRL'),
    walletOf('wallet-reserve', 'Reserva', 'USD'),
  ]
  useRatesStore().rates = [
    { currencyCode: 'BRL', rate: 1, isBase: true },
    { currencyCode: 'USD', rate: 5.4, isBase: false },
  ]
  activeWrapper = mount(CashReconciliationCard, {
    props: { reconciliation },
    global: { plugins: [pinia] },
    attachTo: document.body,
  })
  return activeWrapper
}

function byTestId(wrapper: VueWrapper, testId: string, index = 0) {
  return wrapper.findAll(`[data-testid="${testId}"]`)[index]!
}

function inputOf(wrapper: VueWrapper, testId: string, index = 0) {
  const element = byTestId(wrapper, testId, index)
  const tag = element.element.tagName
  return tag === 'INPUT' || tag === 'SELECT' ? element : element.get('input, select')
}

async function chooseDestination(wrapper: VueWrapper, label: string, index = 0) {
  const line = byTestId(wrapper, 'reconciliation-line', index)
  await line
    .findAll('button')
    .find((button) => button.text() === label)!
    .trigger('click')
}

function submitButton(wrapper: VueWrapper) {
  return wrapper.get('[data-testid="reconciliation-submit"]')
}

async function submit(wrapper: VueWrapper) {
  await submitButton(wrapper).trigger('click')
  await flushPromises()
}

afterEach(() => {
  activeWrapper?.unmount()
  activeWrapper = undefined
  vi.clearAllMocks()
})

describe('CashReconciliationCard', () => {
  it('shows the wallet, its cash and the exits that formed it', () => {
    const wrapper = mountCard()

    expect(wrapper.text()).toContain('Ações EUA')
    expect(wrapper.get('[data-testid="reconciliation-cash"]').text()).toBe('US$ 1.000,00')
    expect(
      wrapper.findAll('[data-testid="reconciliation-source"]').map((source) => source.text()),
    ).toEqual(['NVDA6 out 2026US$ 420,00', 'Apple15 set 2026US$ 580,00'])
  })

  it('describes the suggestion and fills the first line from it', async () => {
    vi.mocked(cashApi.transfer).mockResolvedValue()
    vi.mocked(cashApi.markReconciled).mockResolvedValue()
    const wrapper = mountCard()

    expect(wrapper.get('[data-testid="reconciliation-suggestion"]').text()).toContain('PETR4')
    await wrapper.get('[data-testid="reconciliation-use-suggestion"]').trigger('click')

    expect((inputOf(wrapper, 'reconciliation-amount').element as HTMLInputElement).value).toBe(
      '500.00',
    )
    expect((inputOf(wrapper, 'reconciliation-rate').element as HTMLInputElement).value).toBe('5.32')
    await submit(wrapper)

    expect(cashApi.transfer).toHaveBeenCalledWith('wallet-us', {
      destinationWalletId: 'wallet-br',
      transferDate: '2026-09-20',
      amount: 500,
      rate: 5.32,
    })
  })

  it('caps the suggested amount at the cash', async () => {
    const wrapper = mountCard(
      reconciliationOf({
        cash: 300,
        suggestion: { ...reconciliationOf().suggestion!, newMoney: 5320 },
      }),
    )

    await wrapper.get('[data-testid="reconciliation-use-suggestion"]').trigger('click')

    expect((inputOf(wrapper, 'reconciliation-amount').element as HTMLInputElement).value).toBe(
      '300.00',
    )
  })

  it('names a suggested holding without a ticker by its name', () => {
    const wrapper = mountCard(
      reconciliationOf({ suggestion: { ...reconciliationOf().suggestion!, ticker: null } }),
    )

    expect(wrapper.get('[data-testid="reconciliation-suggestion"]').text()).toContain('Petrobras')
  })

  it('dates a line today when the wallet lists no exits', async () => {
    vi.mocked(cashApi.withdrawToBank).mockResolvedValue()
    vi.mocked(cashApi.markReconciled).mockResolvedValue()
    const wrapper = mountCard(reconciliationOf({ sources: [] }))

    await chooseDestination(wrapper, 'Banco')
    await inputOf(wrapper, 'reconciliation-amount').setValue('100')
    await submit(wrapper)

    expect(vi.mocked(cashApi.withdrawToBank).mock.calls[0]![1].withdrawalDate).toBe(
      new Date().toISOString().slice(0, 10),
    )
  })

  it('shows no suggestion when the server found none', () => {
    const wrapper = mountCard(reconciliationOf({ suggestion: null }))

    expect(wrapper.find('[data-testid="reconciliation-suggestion"]').exists()).toBe(false)
  })

  it('prefills a transfer at the current rate on the first exit date, and sends no rate in the same currency', async () => {
    vi.mocked(cashApi.transfer).mockResolvedValue()
    vi.mocked(cashApi.markReconciled).mockResolvedValue()
    const wrapper = mountCard()

    await inputOf(wrapper, 'reconciliation-destination').setValue('wallet-br')
    expect((inputOf(wrapper, 'reconciliation-rate').element as HTMLInputElement).value).toBe('5.40')

    await inputOf(wrapper, 'reconciliation-destination').setValue('wallet-reserve')
    expect(wrapper.find('[data-testid="reconciliation-rate"]').exists()).toBe(false)
    await inputOf(wrapper, 'reconciliation-amount').setValue('1000')
    await submit(wrapper)

    expect(cashApi.transfer).toHaveBeenCalledWith('wallet-us', {
      destinationWalletId: 'wallet-reserve',
      transferDate: '2026-09-15',
      amount: 1000,
    })
    expect(cashApi.markReconciled).toHaveBeenCalledWith('wallet-us')
    expect(wrapper.emitted('registered')![0]).toEqual([
      ['Ações EUA: US$ 1.000,00 transferidos para Reserva'],
    ])
  })

  it('splits the cash between a bank withdrawal and what stays in cash', async () => {
    vi.mocked(cashApi.withdrawToBank).mockResolvedValue()
    vi.mocked(cashApi.markReconciled).mockResolvedValue()
    const wrapper = mountCard()

    await chooseDestination(wrapper, 'Banco')
    await inputOf(wrapper, 'reconciliation-bank').setValue(' Itaú ')
    await inputOf(wrapper, 'reconciliation-amount').setValue('600')
    await wrapper.get('[data-testid="reconciliation-add-line"]').trigger('click')
    await chooseDestination(wrapper, 'Continua em caixa', 1)

    expect(wrapper.get('[data-testid="reconciliation-remaining"]').text()).toBe(
      'Fica em caixa: US$ 400,00',
    )
    await submit(wrapper)

    expect(cashApi.withdrawToBank).toHaveBeenCalledWith('wallet-us', {
      withdrawalDate: '2026-09-15',
      amount: 600,
      bankName: 'Itaú',
    })
    expect(wrapper.emitted('registered')![0]).toEqual([
      ['Ações EUA: US$ 600,00 sacados para Itaú', 'Ações EUA: US$ 400,00 continuam em caixa'],
    ])
  })

  it('withdraws to an unnamed bank and keeps everything else on "Continua em caixa"', async () => {
    vi.mocked(cashApi.withdrawToBank).mockResolvedValue()
    vi.mocked(cashApi.markReconciled).mockResolvedValue()
    const wrapper = mountCard()

    await chooseDestination(wrapper, 'Banco')
    await wrapper.get('[data-testid="reconciliation-fill-remaining"]').trigger('click')
    await submit(wrapper)

    expect(vi.mocked(cashApi.withdrawToBank).mock.calls[0]![1]).toEqual({
      withdrawalDate: '2026-09-15',
      amount: 1000,
    })
    expect(wrapper.emitted('registered')![0]).toEqual([
      ['Ações EUA: US$ 1.000,00 sacados para o banco'],
    ])
  })

  it('only marks the wallet reconciled when everything stays in cash', async () => {
    vi.mocked(cashApi.markReconciled).mockResolvedValue()
    const wrapper = mountCard()

    await chooseDestination(wrapper, 'Continua em caixa')
    await submit(wrapper)

    expect(cashApi.transfer).not.toHaveBeenCalled()
    expect(cashApi.withdrawToBank).not.toHaveBeenCalled()
    expect(cashApi.markReconciled).toHaveBeenCalledWith('wallet-us')
  })

  it('blocks a split that exceeds the cash and removes an extra line', async () => {
    const wrapper = mountCard()

    await chooseDestination(wrapper, 'Banco')
    await inputOf(wrapper, 'reconciliation-amount').setValue('800')
    await wrapper.get('[data-testid="reconciliation-add-line"]').trigger('click')
    await chooseDestination(wrapper, 'Banco', 1)
    await inputOf(wrapper, 'reconciliation-amount', 1).setValue('300')

    expect(wrapper.get('[data-testid="reconciliation-remaining"]').text()).toBe(
      'Excede o caixa em US$ 100,00',
    )
    expect(submitButton(wrapper).attributes('disabled')).toBeDefined()

    await wrapper.get('[data-testid="reconciliation-remove-line"]').trigger('click')

    expect(wrapper.findAll('[data-testid="reconciliation-line"]')).toHaveLength(1)
    expect(submitButton(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('keeps "Registrar" disabled until a transfer has a destination and an amount', async () => {
    const wrapper = mountCard()
    expect(submitButton(wrapper).attributes('disabled')).toBeDefined()

    await inputOf(wrapper, 'reconciliation-amount').setValue('100')
    expect(submitButton(wrapper).attributes('disabled')).toBeDefined()

    await inputOf(wrapper, 'reconciliation-destination').setValue('wallet-br')
    await inputOf(wrapper, 'reconciliation-rate').setValue('')
    expect(submitButton(wrapper).attributes('disabled')).toBeDefined()
  })

  it('shows a server refusal inline and emits nothing', async () => {
    vi.mocked(cashApi.withdrawToBank).mockRejectedValue({
      isAxiosError: true,
      response: { status: 400, data: { detail: 'Caixa insuficiente' } },
    })
    const wrapper = mountCard()

    await chooseDestination(wrapper, 'Banco')
    await inputOf(wrapper, 'reconciliation-amount').setValue('100')
    await submit(wrapper)

    expect(wrapper.get('[data-testid="reconciliation-error"]').text()).toBe('Caixa insuficiente')
    expect(wrapper.emitted('registered')).toBeUndefined()
  })

  it('falls back to a generic message when the refusal has no detail', async () => {
    vi.mocked(cashApi.markReconciled).mockRejectedValue(new Error('network'))
    const wrapper = mountCard()

    await chooseDestination(wrapper, 'Continua em caixa')
    await submit(wrapper)

    expect(wrapper.get('[data-testid="reconciliation-error"]').text()).toBe(
      'Não foi possível registrar a conciliação.',
    )
  })
})
