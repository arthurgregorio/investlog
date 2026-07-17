import { describe, expect, it, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { reactive } from 'vue'
import Buefy from 'buefy'
import AddInvestmentForm from './AddInvestmentForm.vue'
import type { AddInvestmentForm as AddInvestmentFormState } from '@/composables/useAddInvestmentForm'

function buildForm(overrides: Partial<AddInvestmentFormState> = {}): AddInvestmentFormState {
  return reactive({
    kind: 'STOCKS',
    walletId: 'wallet-1',
    stockTypeId: '',
    fundTypeId: '',
    ticker: '',
    name: '',
    date: new Date(),
    quantity: '',
    price: '',
    currentPrice: '',
    amount: '',
    currentValue: '',
    submitting: false,
    walletsOfKind: [
      {
        id: 'wallet-1',
        name: 'Corretora',
        kind: 'STOCKS',
        currency: 'BRL',
        holdingCount: 0,
        totalInvested: 0,
        currentValue: null,
        gain: null,
        gainPct: null,
        createdAt: '2024-01-01',
      },
    ],
    valid: true,
    ...overrides,
  }) as AddInvestmentFormState
}

describe('AddInvestmentForm ticker input', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('uppercases and strips non-alphanumeric characters as the user types', async () => {
    const form = buildForm()
    const wrapper = mount(AddInvestmentForm, {
      props: { form },
      global: { plugins: [Buefy] },
    })

    const input = wrapper.find('input[placeholder="PETR4"]')
    await input.setValue('petr-4 ab')

    expect(form.ticker).toBe('PETR4AB')
  })
})
