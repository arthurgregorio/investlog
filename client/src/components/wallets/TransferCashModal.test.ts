import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import TransferCashModal from './TransferCashModal.vue'
import AppModal from '@/components/ui/AppModal.vue'
import { cashApi } from '@/api/cash'
import { useRatesStore } from '@/stores/rates'
import { useWalletsStore } from '@/stores/wallets'
import type { WalletResponse } from '@/types'

vi.mock('@/api/cash', () => ({ cashApi: { transfer: vi.fn() } }))

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

let activeWrapper: VueWrapper | undefined

function mountModal() {
  const pinia = createTestingPinia()
  useWalletsStore().wallets = [
    walletOf('wallet-us', 'Ações EUA', 'USD'),
    walletOf('wallet-br', 'Ações Brasil', 'BRL'),
    walletOf('wallet-reserve', 'Reserva', 'USD'),
  ]
  useRatesStore().rates = [
    { currencyCode: 'BRL', rate: 1, isBase: true },
    { currencyCode: 'USD', rate: 5.32, isBase: false },
  ]
  activeWrapper = mount(TransferCashModal, {
    props: { walletId: 'wallet-us', walletName: 'Ações EUA', currency: 'USD', cash: 800 },
    global: { plugins: [pinia] },
    attachTo: document.body,
  })
  return activeWrapper
}

function inputByTestId(wrapper: VueWrapper, testId: string) {
  const element = wrapper.get(`[data-testid="${testId}"]`)
  return element.element.tagName === 'INPUT' ? element : element.get('input')
}

async function chooseDestination(wrapper: VueWrapper, walletId: string) {
  const element = wrapper.get('[data-testid="transfer-destination"]')
  const select = element.element.tagName === 'SELECT' ? element : element.get('select')
  await select.setValue(walletId)
}

function submitButton(wrapper: VueWrapper) {
  return wrapper.get('[data-testid="transfer-submit"]')
}

afterEach(() => {
  activeWrapper?.unmount()
  activeWrapper = undefined
  vi.clearAllMocks()
})

describe('TransferCashModal', () => {
  it('loads wallets and rates, and offers every other wallet as the destination', () => {
    const wrapper = mountModal()

    expect(useWalletsStore().load).toHaveBeenCalled()
    expect(useRatesStore().load).toHaveBeenCalled()
    expect(wrapper.findAll('option[value]').map((option) => option.text())).toEqual([
      'Ações Brasil · BRL',
      'Reserva · USD',
    ])
  })

  it('asks for the exchange rate only across currencies, prefilled from the rates', async () => {
    const wrapper = mountModal()

    await chooseDestination(wrapper, 'wallet-reserve')
    expect(wrapper.find('[data-testid="transfer-rate"]').exists()).toBe(false)

    await chooseDestination(wrapper, 'wallet-br')
    expect((inputByTestId(wrapper, 'transfer-rate').element as HTMLInputElement).value).toBe('5.32')
  })

  it('summarises what leaves and what arrives converted at the rate', async () => {
    const wrapper = mountModal()

    await chooseDestination(wrapper, 'wallet-br')
    await inputByTestId(wrapper, 'transfer-amount').setValue('100')

    const summary = wrapper.get('[data-testid="transfer-summary"]').text()
    expect(summary).toContain('Caixa disponível em Ações EUAUS$ 800,00')
    expect(summary).toContain('Sai de Ações EUAUS$ 100,00')
    expect(summary).toContain('Entra no caixa de Ações BrasilR$ 532,00')
  })

  it('fills the whole cash on "Tudo" and flags an amount above it', async () => {
    const wrapper = mountModal()
    await chooseDestination(wrapper, 'wallet-reserve')

    await inputByTestId(wrapper, 'transfer-amount').setValue('900')
    expect(wrapper.text()).toContain('Maior que o caixa disponível (US$ 800,00)')
    expect(submitButton(wrapper).attributes('disabled')).toBeDefined()

    await inputByTestId(wrapper, 'transfer-whole-cash').setValue(true)
    expect((inputByTestId(wrapper, 'transfer-amount').element as HTMLInputElement).value).toBe(
      '800.00',
    )
    expect(submitButton(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('sends a cross-currency transfer with its rate and closes', async () => {
    vi.mocked(cashApi.transfer).mockResolvedValue()
    const wrapper = mountModal()

    await chooseDestination(wrapper, 'wallet-br')
    await inputByTestId(wrapper, 'transfer-amount').setValue('100')
    await inputByTestId(wrapper, 'transfer-rate').setValue('5.5')
    await submitButton(wrapper).trigger('click')
    await flushPromises()

    expect(cashApi.transfer).toHaveBeenCalledWith('wallet-us', {
      destinationWalletId: 'wallet-br',
      transferDate: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
      amount: 100,
      rate: 5.5,
    })
    expect(wrapper.emitted('transferred')).toHaveLength(1)
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('sends no rate in the same currency', async () => {
    vi.mocked(cashApi.transfer).mockResolvedValue()
    const wrapper = mountModal()

    await chooseDestination(wrapper, 'wallet-reserve')
    await inputByTestId(wrapper, 'transfer-amount').setValue('100')
    await submitButton(wrapper).trigger('click')
    await flushPromises()

    expect(vi.mocked(cashApi.transfer).mock.calls[0]![1]).not.toHaveProperty('rate')
  })

  it('shows a server refusal inline and stays open', async () => {
    vi.mocked(cashApi.transfer).mockRejectedValue({
      isAxiosError: true,
      response: { status: 400, data: { detail: 'Caixa insuficiente' } },
    })
    const wrapper = mountModal()

    await chooseDestination(wrapper, 'wallet-reserve')
    await inputByTestId(wrapper, 'transfer-amount').setValue('100')
    await submitButton(wrapper).trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="transfer-error"]').text()).toBe('Caixa insuficiente')
    expect(wrapper.emitted('close')).toBeUndefined()
  })

  it('falls back to a generic message when the refusal has no detail', async () => {
    vi.mocked(cashApi.transfer).mockRejectedValue(new Error('network'))
    const wrapper = mountModal()

    await chooseDestination(wrapper, 'wallet-reserve')
    await inputByTestId(wrapper, 'transfer-amount').setValue('100')
    await submitButton(wrapper).trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="transfer-error"]').text()).toBe(
      'Não foi possível registrar a transferência.',
    )
  })

  it('keeps "Transferir" disabled until a destination and an amount are set', async () => {
    const wrapper = mountModal()
    expect(submitButton(wrapper).attributes('disabled')).toBeDefined()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Cancelar')!
      .trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})

describe('closing', () => {
  it('closes when the dialog itself is dismissed', () => {
    const wrapper = mountModal()

    wrapper.findComponent(AppModal).vm.$emit('close')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})
