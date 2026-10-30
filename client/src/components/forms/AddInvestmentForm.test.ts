import { describe, expect, it, beforeEach, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia, type TestingPinia } from '@pinia/testing'
import { reactive } from 'vue'
import AddInvestmentForm from './AddInvestmentForm.vue'
import type { AddInvestmentForm as AddInvestmentFormState } from '@/composables/useAddInvestmentForm'
import type { WalletKind, WalletResponse } from '@/types'

function wallet(overrides: Partial<WalletResponse> = {}): WalletResponse {
  return {
    id: 'wallet-1',
    name: 'Corretora',
    kind: 'STOCKS',
    currency: 'BRL',
    holdingCount: 0,
    totalInvested: 0,
    currentValue: null,
    gain: null,
    gainPct: null,
    cash: 0,
    cashReconciled: true,
    createdAt: '2024-01-01',
    ...overrides,
  }
}

function buildForm(overrides: Partial<AddInvestmentFormState> = {}): AddInvestmentFormState {
  return reactive({
    kind: 'STOCKS',
    walletId: 'wallet-1',
    typeId: '',
    segmentId: '',
    ticker: '',
    name: '',
    date: new Date(),
    quantity: '',
    price: '',
    currentPrice: '',
    amount: '',
    currentValue: '',
    administrationFeeRate: '',
    performanceFeeRate: '',
    submitting: false,
    walletsOfKind: [wallet()],
    valid: true,
    ...overrides,
  }) as AddInvestmentFormState
}

let activeWrapper: VueWrapper | undefined
let pinia: TestingPinia

function mountForm(form: AddInvestmentFormState) {
  activeWrapper = mount(AddInvestmentForm, {
    props: { form },
    global: { plugins: [pinia] },
    attachTo: document.body,
  })
  return activeWrapper
}

function labelsOf(wrapper: VueWrapper) {
  return wrapper.findAll('label.label').map((label) => label.text())
}

describe('AddInvestmentForm', () => {
  beforeEach(() => {
    pinia = createTestingPinia({
      initialState: {
        typesList: {
          holdingTypes: [
            { id: 'stock-type-1', name: 'Ação', kinds: ['STOCKS'], usageCount: 0 },
            { id: 'stock-type-2', name: 'FII', kinds: ['STOCKS'], usageCount: 0 },
            { id: 'fund-type-1', name: 'Renda Fixa', kinds: ['FUNDS'], usageCount: 0 },
            { id: 'fund-type-2', name: 'Multimercado', kinds: ['FUNDS'], usageCount: 0 },
            { id: 'crypto-type-1', name: 'Moeda', kinds: ['CRYPTO'], usageCount: 0 },
            { id: 'crypto-type-2', name: 'Token', kinds: ['CRYPTO'], usageCount: 0 },
          ],
          segments: [
            { id: 'segment-1', name: 'Energia', usageCount: 0 },
            { id: 'segment-2', name: 'Tecnologia', usageCount: 0 },
          ],
        },
      },
    })
  })

  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = undefined
  })

  describe('ticker input', () => {
    it('uppercases and strips non-alphanumeric characters as the user types', async () => {
      const form = buildForm()
      const wrapper = mountForm(form)

      await wrapper.find('input[placeholder="PETR4"]').setValue('petr-4 ab')

      expect(form.ticker).toBe('PETR4AB')
    })
  })

  describe('stocks', () => {
    it('shows the stock type selector with the available types and the quantity and price fields', () => {
      const wrapper = mountForm(buildForm({ kind: 'STOCKS' }))

      expect(labelsOf(wrapper)).toEqual([
        'Tipo de investimento',
        'Carteira',
        'Tipo',
        'Segmento (opcional)',
        'Ticker',
        'Nome (opcional)',
        'Data da aquisição',
        'Quantidade',
        'Preço na aquisição',
        'Preço atual (opcional)',
      ])
      const optionLabels = wrapper.findAll('option').map((option) => option.text())
      expect(optionLabels).toContain('Ação')
      expect(optionLabels).toContain('FII')
      expect(optionLabels).not.toContain('Renda Fixa')
    })

    it('offers the optional segment, defaulting to none, and writes the choice back', async () => {
      const form = buildForm()
      const wrapper = mountForm(form)

      const segmentSelect = wrapper.find('select[data-testid="segment-select"]')
      expect(segmentSelect.findAll('option').map((option) => option.text())).toEqual([
        'Sem segmento',
        'Energia',
        'Tecnologia',
      ])
      expect((segmentSelect.element as HTMLSelectElement).value).toBe('')

      await segmentSelect.setValue('segment-2')

      expect(form.segmentId).toBe('segment-2')
    })

    it('offers only the stock types and writes the chosen one back', async () => {
      const form = buildForm()
      const wrapper = mountForm(form)

      const typeSelect = wrapper.find('select[data-testid="type-select"]')
      expect(typeSelect.findAll('option').map((option) => option.text())).toEqual(['Ação', 'FII'])

      await typeSelect.setValue('stock-type-2')

      expect(form.typeId).toBe('stock-type-2')
    })

    it('writes the typed quantity, price and current price back to the form as numbers', async () => {
      const form = buildForm()
      const wrapper = mountForm(form)

      const [quantityInput, priceInput, currentPriceInput] = wrapper.findAll('input[type="number"]')
      await quantityInput.setValue('12.5')
      await priceInput.setValue('36.9')
      await currentPriceInput.setValue('40')

      expect(form.quantity).toBe(12.5)
      expect(form.price).toBe(36.9)
      expect(form.currentPrice).toBe(40)
    })

    it('clears a number field back to an empty string when the user erases it', async () => {
      const form = buildForm({ quantity: 5 })
      const wrapper = mountForm(form)

      await wrapper.findAll('input[type="number"]')[0].setValue('')

      expect(form.quantity).toBe('')
    })

    it('writes the optional name back to the form', async () => {
      const form = buildForm()
      const wrapper = mountForm(form)

      await wrapper.find('input[placeholder="Petrobras"]').setValue('Petrobras PN')

      expect(form.name).toBe('Petrobras PN')
    })

    it('prefixes the price fields with the symbol of the selected wallet currency', () => {
      const wrapper = mountForm(
        buildForm({
          walletId: 'wallet-usd',
          walletsOfKind: [wallet({ id: 'wallet-usd', name: 'Exterior', currency: 'USD' })],
        }),
      )

      expect(wrapper.findAll('.button.is-static').map((prefix) => prefix.text())).toEqual([
        'US$',
        'US$',
      ])
    })

    it('falls back to the BRL symbol when the selected wallet is not in the list', () => {
      const wrapper = mountForm(buildForm({ walletId: 'missing' }))

      expect(wrapper.findAll('.button.is-static').map((prefix) => prefix.text())).toEqual([
        'R$',
        'R$',
      ])
    })
  })

  describe('crypto', () => {
    it('shows the crypto ticker and name placeholders', () => {
      const wrapper = mountForm(buildForm({ kind: 'CRYPTO' }))

      expect(labelsOf(wrapper)).toContain('Sigla / código')
      expect(labelsOf(wrapper)).not.toContain('Ticker')
      expect(wrapper.find('input[placeholder="BTC"]').exists()).toBe(true)
      expect(wrapper.find('input[placeholder="Bitcoin"]').exists()).toBe(true)
      expect(wrapper.findAll('input[type="number"]')).toHaveLength(3)
    })

    it('offers an optional type picker with only the crypto types, and a segment picker', async () => {
      const form = buildForm({ kind: 'CRYPTO' })
      const wrapper = mountForm(form)

      expect(labelsOf(wrapper)).toEqual([
        'Tipo de investimento',
        'Carteira',
        'Tipo (opcional)',
        'Segmento (opcional)',
        'Sigla / código',
        'Nome (opcional)',
        'Data da aquisição',
        'Quantidade',
        'Preço na aquisição',
        'Preço atual (opcional)',
      ])
      const typeSelect = wrapper.find('select[data-testid="type-select"]')
      expect(typeSelect.findAll('option').map((option) => option.text())).toEqual([
        'Sem tipo',
        'Moeda',
        'Token',
      ])
      const segmentSelect = wrapper.find('select[data-testid="segment-select"]')
      expect(segmentSelect.findAll('option').map((option) => option.text())).toEqual([
        'Sem segmento',
        'Energia',
        'Tecnologia',
      ])

      await typeSelect.setValue('crypto-type-2')
      await segmentSelect.setValue('segment-1')

      expect(form.typeId).toBe('crypto-type-2')
      expect(form.segmentId).toBe('segment-1')
    })
  })

  describe('funds', () => {
    it('shows the fund type selector with the amount and current value instead of quantity and price', () => {
      const wrapper = mountForm(buildForm({ kind: 'FUNDS' }))

      expect(labelsOf(wrapper)).toEqual([
        'Tipo de investimento',
        'Carteira',
        'Tipo',
        'Segmento (opcional)',
        'Nome do fundo',
        'Taxa de administração (% a.a.)',
        'Taxa de performance (%)',
        'Data do aporte',
        'Valor aportado',
        'Valor atual (opcional)',
      ])
      const typeSelect = wrapper.find('select[data-testid="type-select"]')
      expect(typeSelect.findAll('option').map((option) => option.text())).toEqual([
        'Renda Fixa',
        'Multimercado',
      ])
      const optionLabels = wrapper.findAll('option').map((option) => option.text())
      expect(optionLabels).not.toContain('FII')
      expect(optionLabels).not.toContain('Moeda')
      expect(wrapper.find('input[placeholder="PETR4"]').exists()).toBe(false)
      expect(wrapper.findAll('input[type="number"]')).toHaveLength(4)
    })

    it('shows the two fee rate fields empty and without a currency prefix', () => {
      const wrapper = mountForm(buildForm({ kind: 'FUNDS' }))

      const [administrationInput, performanceInput] = wrapper.findAll('input[type="number"]')
      expect((administrationInput.element as HTMLInputElement).value).toBe('')
      expect((performanceInput.element as HTMLInputElement).value).toBe('')
      expect(wrapper.findAll('.button.is-static')).toHaveLength(2)
    })

    it('shows the rates already held by the form', () => {
      const wrapper = mountForm(
        buildForm({ kind: 'FUNDS', administrationFeeRate: 1.5, performanceFeeRate: 20 }),
      )

      const [administrationInput, performanceInput] = wrapper.findAll('input[type="number"]')
      expect((administrationInput.element as HTMLInputElement).value).toBe('1.5')
      expect((performanceInput.element as HTMLInputElement).value).toBe('20')
    })

    it('writes the typed fee rates back to the form as numbers', async () => {
      const form = buildForm({ kind: 'FUNDS' })
      const wrapper = mountForm(form)

      const [administrationInput, performanceInput] = wrapper.findAll('input[type="number"]')
      await administrationInput.setValue('1.5')
      await performanceInput.setValue('0')

      expect(form.administrationFeeRate).toBe(1.5)
      expect(form.performanceFeeRate).toBe(0)
    })

    it('clears a fee rate back to an empty string when the user erases it', async () => {
      const form = buildForm({ kind: 'FUNDS', administrationFeeRate: 1.5, performanceFeeRate: 20 })
      const wrapper = mountForm(form)

      const [administrationInput, performanceInput] = wrapper.findAll('input[type="number"]')
      await administrationInput.setValue('')
      await performanceInput.setValue('')

      expect(form.administrationFeeRate).toBe('')
      expect(form.performanceFeeRate).toBe('')
    })

    it('writes the fund name, amount and current value back to the form', async () => {
      const form = buildForm({ kind: 'FUNDS' })
      const wrapper = mountForm(form)

      await wrapper.find('input[placeholder="ex.: Tesouro Selic 2029"]').setValue('Tesouro Selic')
      const [, , amountInput, currentValueInput] = wrapper.findAll('input[type="number"]')
      await amountInput.setValue('1500')
      await currentValueInput.setValue('1520.5')

      expect(form.name).toBe('Tesouro Selic')
      expect(form.amount).toBe(1500)
      expect(form.currentValue).toBe(1520.5)
    })
  })

  describe('kind selector', () => {
    it('switches the form kind when another radio is picked', async () => {
      const form = buildForm()
      const wrapper = mountForm(form)

      await wrapper.findAll('input[type="radio"]')[2].setValue(true)

      expect(form.kind).toBe('FUNDS')
    })
  })

  describe('wallet selection', () => {
    it('lists the wallets of the kind with their currency', () => {
      const wrapper = mountForm(
        buildForm({
          walletsOfKind: [wallet(), wallet({ id: 'wallet-2', name: 'Exterior', currency: 'USD' })],
        }),
      )

      const optionLabels = wrapper.findAll('option').map((option) => option.text())
      expect(optionLabels).toContain('Corretora · BRL')
      expect(optionLabels).toContain('Exterior · USD')
    })

    it.each<[WalletKind, string]>([
      ['STOCKS', 'ações'],
      ['CRYPTO', 'cripto'],
      ['FUNDS', 'fundos'],
    ])(
      'explains there is no %s wallet and offers to create one, emitting the kind',
      async (kind, label) => {
        const wrapper = mountForm(buildForm({ kind, walletsOfKind: [], walletId: '' }))

        expect(wrapper.text()).toContain(`Nenhuma carteira de ${label} ainda.`)
        expect(wrapper.find('.fixed-grid').exists()).toBe(false)

        await wrapper.find('.form-notice button').trigger('click')

        expect(wrapper.emitted('create-wallet')).toEqual([[kind]])
      },
    )
  })
})
