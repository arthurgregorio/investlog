import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import WithdrawCashModal from './WithdrawCashModal.vue'
import AppModal from '@/components/ui/AppModal.vue'
import { cashApi } from '@/api/cash'

vi.mock('@/api/cash', () => ({ cashApi: { withdrawToBank: vi.fn() } }))

let activeWrapper: VueWrapper | undefined

function mountModal() {
  activeWrapper = mount(WithdrawCashModal, {
    props: { walletId: 'wallet-1', walletName: 'Ações Brasil', currency: 'BRL', cash: 1500 },
    attachTo: document.body,
  })
  return activeWrapper
}

function inputByTestId(wrapper: VueWrapper, testId: string) {
  const element = wrapper.get(`[data-testid="${testId}"]`)
  return element.element.tagName === 'INPUT' ? element : element.get('input')
}

function summaryLines(wrapper: VueWrapper) {
  return wrapper
    .get('[data-testid="withdraw-cash-summary"]')
    .findAll('.is-flex')
    .map((line) => line.text())
}

function submitButton(wrapper: VueWrapper) {
  return wrapper.get('[data-testid="withdraw-cash-submit"]')
}

afterEach(() => {
  activeWrapper?.unmount()
  activeWrapper = undefined
  vi.clearAllMocks()
})

describe('WithdrawCashModal', () => {
  it('summarises the cash available, leaving and left after the withdrawal', async () => {
    const wrapper = mountModal()

    await inputByTestId(wrapper, 'withdraw-cash-amount').setValue('400')

    expect(summaryLines(wrapper)).toEqual([
      'Caixa disponível em Ações BrasilR$ 1.500,00',
      'Sai do caixaR$ 400,00',
      'Caixa depois do saqueR$ 1.100,00',
    ])
    expect(submitButton(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('fills and locks the amount with the whole cash on "Tudo"', async () => {
    const wrapper = mountModal()

    await inputByTestId(wrapper, 'withdraw-cash-whole').setValue(true)

    const amountInput = inputByTestId(wrapper, 'withdraw-cash-amount')
    expect((amountInput.element as HTMLInputElement).value).toBe('1500.00')
    expect(amountInput.attributes('disabled')).toBeDefined()
  })

  it('flags an amount above the cash and keeps "Sacar" disabled', async () => {
    const wrapper = mountModal()

    await inputByTestId(wrapper, 'withdraw-cash-amount').setValue('2000')

    expect(wrapper.text()).toContain('Maior que o caixa disponível (R$ 1.500,00)')
    expect(summaryLines(wrapper)[2]).toBe('Caixa depois do saque—')
    expect(submitButton(wrapper).attributes('disabled')).toBeDefined()
  })

  it('sends the withdrawal with the trimmed bank name and closes', async () => {
    vi.mocked(cashApi.withdrawToBank).mockResolvedValue()
    const wrapper = mountModal()

    await inputByTestId(wrapper, 'withdraw-cash-amount').setValue('400')
    await inputByTestId(wrapper, 'withdraw-cash-bank').setValue('  Itaú ')
    await submitButton(wrapper).trigger('click')
    await flushPromises()

    expect(cashApi.withdrawToBank).toHaveBeenCalledWith('wallet-1', {
      withdrawalDate: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
      amount: 400,
      bankName: 'Itaú',
    })
    expect(wrapper.emitted('withdrawn')).toHaveLength(1)
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('leaves the bank name out when it is blank', async () => {
    vi.mocked(cashApi.withdrawToBank).mockResolvedValue()
    const wrapper = mountModal()

    await inputByTestId(wrapper, 'withdraw-cash-amount').setValue('400')
    await submitButton(wrapper).trigger('click')
    await flushPromises()

    expect(vi.mocked(cashApi.withdrawToBank).mock.calls[0]![1]).not.toHaveProperty('bankName')
  })

  it('shows a server refusal under the amount and stays open until the amount changes', async () => {
    vi.mocked(cashApi.withdrawToBank).mockRejectedValue({
      isAxiosError: true,
      response: { status: 400, data: { detail: 'Caixa insuficiente em 10/10/2026' } },
    })
    const wrapper = mountModal()

    await inputByTestId(wrapper, 'withdraw-cash-amount').setValue('400')
    await submitButton(wrapper).trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Caixa insuficiente em 10/10/2026')
    expect(wrapper.emitted('close')).toBeUndefined()

    await inputByTestId(wrapper, 'withdraw-cash-amount').setValue('300')

    expect(wrapper.text()).not.toContain('Caixa insuficiente em 10/10/2026')
  })

  it('falls back to a generic message when the refusal has no detail', async () => {
    vi.mocked(cashApi.withdrawToBank).mockRejectedValue(new Error('network'))
    const wrapper = mountModal()

    await inputByTestId(wrapper, 'withdraw-cash-amount').setValue('400')
    await submitButton(wrapper).trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Não foi possível registrar o saque.')
  })

  it('closes on Cancelar', async () => {
    const wrapper = mountModal()

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
