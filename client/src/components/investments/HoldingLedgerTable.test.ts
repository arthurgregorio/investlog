import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import HoldingLedgerTable from './HoldingLedgerTable.vue'
import { useCurrencyStore } from '@/stores/currency'
import type { LedgerRow } from '@/utils/holdingLedger'
import type { HoldingRow } from '@/types'

const row: HoldingRow = {
  id: 'holding-1',
  kind: 'STOCKS',
  name: 'Apple',
  ticker: 'AAPL',
  typeLabel: 'Ação',
  segmentLabel: null,
  walletId: 'wallet-1',
  walletName: 'Carteira EUA',
  walletCurrency: 'USD',
  quantity: 10,
  costBasis: 1500,
  currentPrice: 200,
  currentValue: 2000,
  gain: 500,
  gainPct: 33.33,
  frozen: false,
}

const purchase: LedgerRow = {
  type: 'PURCHASE',
  id: 'lot-1',
  date: '2026-01-12',
  quantity: 10,
  unitPrice: 150,
  fees: null,
  taxes: null,
  costs: null,
  amount: 1500,
  profit: null,
  balance: 10,
}

const withdrawal: LedgerRow = {
  type: 'WITHDRAWAL',
  id: 'w-1',
  date: '2026-05-10',
  quantity: -4,
  unitPrice: 200,
  fees: 2,
  taxes: 3,
  costs: 5,
  amount: 795,
  profit: 195,
  balance: 6,
}

const losingWithdrawal: LedgerRow = { ...withdrawal, id: 'w-2', profit: -40 }

const reinvestment: LedgerRow = { ...withdrawal, type: 'REINVESTMENT', id: 'r-1' }

const fundContribution: LedgerRow = {
  ...purchase,
  id: 'c-1',
  quantity: null,
  unitPrice: null,
  balance: null,
}

interface ListProps {
  entries: LedgerRow[]
  isFund: boolean
  isAdmin: boolean
  row: HoldingRow
  editingEntryId: string | null
}

function mountList(props: Partial<ListProps> = {}) {
  const pinia = createTestingPinia()
  const currencyStore = useCurrencyStore()
  currencyStore.displayCurrency = 'BRL'
  vi.mocked(currencyStore.convert).mockImplementation((amount) => amount * 5)
  return mount(HoldingLedgerTable, {
    props: { entries: [purchase, withdrawal], isFund: false, isAdmin: true, row, ...props },
    global: { plugins: [pinia] },
  })
}

function items(wrapper: ReturnType<typeof mountList>) {
  return wrapper.findAll('.ledger-item')
}

describe('HoldingLedgerTable', () => {
  it('heads the movements with their count', () => {
    const wrapper = mountList()

    expect(wrapper.get('.ledger-title').text()).toBe('Movimentações')
    expect(wrapper.get('.ledger-count').text()).toBe('2')
  })

  it('lists a purchase with its signed quantity and unit price in the display currency', () => {
    const [purchaseItem] = items(mountList())

    expect(purchaseItem.get('.ledger-tag').text()).toBe('Compra')
    expect(purchaseItem.get('button.date-edit').text()).toBe('12 jan 2026')
    expect(purchaseItem.get('[data-testid="ledger-detail"]').text()).toBe('+10 × R$ 750,00')
    expect(purchaseItem.get('[data-testid="ledger-amount"]').text()).toBe('R$ 7.500,00')
    expect(purchaseItem.find('[data-testid="ledger-costs"]').exists()).toBe(false)
    expect(purchaseItem.find('[data-testid="ledger-result"]').exists()).toBe(false)
  })

  it('lists a sale with its costs and a signed result without a label', () => {
    const saleItem = items(mountList())[1]

    expect(saleItem.get('.ledger-tag').text()).toBe('Venda')
    expect(saleItem.get('[data-testid="ledger-detail"]').text()).toBe('−4 × R$ 1.000,00')
    expect(saleItem.get('[data-testid="ledger-costs"]').text()).toBe('taxas + impostos R$ 25,00')
    expect(saleItem.get('[data-testid="ledger-amount"]').text()).toBe('R$ 3.975,00')
    const result = saleItem.get('[data-testid="ledger-result"]')
    expect(result.text()).toBe('+R$ 975,00')
    expect(result.classes()).toContain('gl-up')
    expect(saleItem.text()).not.toContain('Resultado')
  })

  it('colours a losing sale as a loss', () => {
    const result = items(mountList({ entries: [losingWithdrawal] }))[0].get(
      '[data-testid="ledger-result"]',
    )

    expect(result.text()).toBe('−R$ 200,00')
    expect(result.classes()).toContain('gl-down')
  })

  it('words a fund movement as Aporte and Resgate and describes it by its amount', () => {
    const wrapper = mountList({
      isFund: true,
      entries: [fundContribution, { ...withdrawal, quantity: null, unitPrice: null }],
    })

    expect(items(wrapper).map((item) => item.get('.ledger-tag').text())).toEqual([
      'Aporte',
      'Resgate',
    ])
    expect(items(wrapper).map((item) => item.get('[data-testid="ledger-detail"]').text())).toEqual([
      'Valor aportado',
      'Valor resgatado',
    ])
  })

  it('tags a reinvestment, describes an amount-only one and shows its date as plain text', () => {
    const wrapper = mountList({
      entries: [{ ...reinvestment, quantity: null, unitPrice: null }],
    })

    const reinvestmentItem = items(wrapper)[0]
    expect(reinvestmentItem.get('.ledger-tag').text()).toBe('Reinvestimento')
    expect(reinvestmentItem.get('.ledger-tag').classes()).toContain('lt-reinvestment')
    expect(reinvestmentItem.get('[data-testid="ledger-detail"]').text()).toBe('Valor reinvestido')
    expect(reinvestmentItem.find('button.date-edit').exists()).toBe(false)
  })

  it('shows a quantity without a unit price on its own', () => {
    const wrapper = mountList({ entries: [{ ...withdrawal, unitPrice: null }] })

    expect(items(wrapper)[0].get('[data-testid="ledger-detail"]').text()).toBe('−4')
  })

  it('emits delete-purchase and delete-withdrawal from the delete buttons', async () => {
    const wrapper = mountList()

    await items(wrapper)[0].get('[data-testid="ledger-delete"]').trigger('click')
    await items(wrapper)[1].get('[data-testid="ledger-delete"]').trigger('click')

    expect(wrapper.emitted('delete-purchase')).toEqual([['lot-1']])
    expect(wrapper.emitted('delete-withdrawal')).toEqual([['w-1']])
  })

  it('labels each delete button with the movement it removes', () => {
    const wrapper = mountList()

    expect(items(wrapper)[1].get('[data-testid="ledger-delete"]').attributes('aria-label')).toBe(
      'Remover venda de 10 mai 2026',
    )
  })

  it('offers no delete button for a reinvestment or to a non-admin', () => {
    expect(
      mountList({ entries: [reinvestment] })
        .find('[data-testid="ledger-delete"]')
        .exists(),
    ).toBe(false)
    expect(mountList({ isAdmin: false }).find('[data-testid="ledger-delete"]').exists()).toBe(false)
  })

  it('asks to edit a purchase date and shows the picker for the entry being edited', async () => {
    const wrapper = mountList()

    expect(items(wrapper)[1].find('button.date-edit').exists()).toBe(false)
    await wrapper.get('button.date-edit').trigger('click')

    expect(wrapper.emitted('update:editingEntryId')).toEqual([['lot-1']])
    await wrapper.setProps({ editingEntryId: 'lot-1' })
    expect(wrapper.find('button.date-edit').exists()).toBe(false)
    expect(wrapper.find('.datepicker').exists()).toBe(true)
  })

  it('emits change-date for a picked date and nothing when the picker is cleared', async () => {
    const pickedDate = new Date(Date.UTC(2026, 1, 3, 12))
    const wrapper = mountList({ editingEntryId: 'lot-1' })

    wrapper.getComponent({ name: 'BDatepicker' }).vm.$emit('update:modelValue', null)
    wrapper.getComponent({ name: 'BDatepicker' }).vm.$emit('update:modelValue', pickedDate)

    expect(wrapper.emitted('change-date')).toEqual([['lot-1', pickedDate]])
  })
})
