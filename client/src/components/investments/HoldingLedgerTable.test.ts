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

const reinvestment: LedgerRow = { ...withdrawal, type: 'REINVESTMENT', id: 'r-1' }

interface TableProps {
  entries: LedgerRow[]
  isFund: boolean
  isAdmin: boolean
  row: HoldingRow
  editingEntryId: string | null
}

function mountTable(props: Partial<TableProps> = {}) {
  const pinia = createTestingPinia()
  const currencyStore = useCurrencyStore()
  currencyStore.displayCurrency = 'BRL'
  vi.mocked(currencyStore.convert).mockImplementation((amount) => amount * 5)
  return mount(HoldingLedgerTable, {
    props: { entries: [purchase, withdrawal], isFund: false, isAdmin: true, row, ...props },
    slots: { actions: '<button class="slotted-action">Ações</button>' },
    global: { plugins: [pinia] },
  })
}

function rows(wrapper: ReturnType<typeof mountTable>) {
  return wrapper.findAll('tbody tr')
}

describe('HoldingLedgerTable', () => {
  it('heads the ledger with its count and the actions slot', () => {
    const wrapper = mountTable()

    expect(wrapper.get('.ledger-head').text()).toContain('Movimentações')
    expect(wrapper.get('.ledger-count').text()).toBe('2')
    expect(wrapper.find('.ledger-head .slotted-action').exists()).toBe(true)
    expect(wrapper.find('[data-testid="frozen-tag"]').exists()).toBe(false)
  })

  it('shows the frozen badge for a frozen holding', () => {
    const wrapper = mountTable({ row: { ...row, frozen: true } })

    expect(wrapper.find('.ledger-head [data-testid="frozen-tag"]').exists()).toBe(true)
  })

  it('renders a stock purchase and a sale with signed quantities in the display currency', () => {
    const wrapper = mountTable()

    const purchaseCells = rows(wrapper)[0]
      .findAll('td')
      .map((cell) => cell.text())
    expect(purchaseCells.slice(0, 4)).toEqual(['Compra', '12 jan 2026', '+10', 'R$ 750,00'])
    expect(purchaseCells[4]).toBe('—')
    const saleCells = rows(wrapper)[1]
      .findAll('td')
      .map((cell) => cell.text())
    expect(saleCells[0]).toBe('Venda')
    expect(saleCells[2]).toBe('−4')
    expect(saleCells[4]).toContain('R$ 25,00')
    expect(rows(wrapper)[1].get('.ledger-costs-note').text()).toBe('taxa R$ 10,00 + imp. R$ 15,00')
    expect(saleCells[5]).toBe('R$ 3.975,00')
    expect(saleCells[7]).toBe('6')
  })

  it('drops the quantity, price and balance columns for a fund and words its rows as Aporte and Resgate', () => {
    const wrapper = mountTable({ isFund: true })

    expect(wrapper.findAll('thead th').map((cell) => cell.text())).toEqual([
      'Tipo',
      'Data do aporte',
      'Custos',
      'Valor',
      'Resultado',
      '',
    ])
    expect(rows(wrapper).map((tableRow) => tableRow.get('.ledger-tag').text())).toEqual([
      'Aporte',
      'Resgate',
    ])
  })

  it('emits delete-purchase and delete-withdrawal from the delete buttons', async () => {
    const wrapper = mountTable()

    await rows(wrapper)[0].get('td.c-act button').trigger('click')
    await rows(wrapper)[1].get('td.c-act button').trigger('click')

    expect(wrapper.emitted('delete-purchase')).toEqual([['lot-1']])
    expect(wrapper.emitted('delete-withdrawal')).toEqual([['w-1']])
  })

  it('offers no delete button for a reinvestment or to a non-admin', () => {
    expect(
      rows(mountTable({ entries: [reinvestment] }))[0]
        .find('td.c-act button')
        .exists(),
    ).toBe(false)
    expect(mountTable({ isAdmin: false }).find('td.c-act button').exists()).toBe(false)
  })

  it('asks to edit a purchase date and shows the picker for the entry being edited', async () => {
    const wrapper = mountTable()

    expect(rows(wrapper)[1].find('button.date-edit').exists()).toBe(false)
    await wrapper.get('button.date-edit').trigger('click')

    expect(wrapper.emitted('update:editingEntryId')).toEqual([['lot-1']])
    await wrapper.setProps({ editingEntryId: 'lot-1' })
    expect(wrapper.find('button.date-edit').exists()).toBe(false)
    expect(wrapper.find('.datepicker').exists()).toBe(true)
  })

  it('emits change-date for a picked date and nothing when the picker is cleared', async () => {
    const pickedDate = new Date(Date.UTC(2026, 1, 3, 12))
    const wrapper = mountTable({ editingEntryId: 'lot-1' })

    wrapper.getComponent({ name: 'BDatepicker' }).vm.$emit('update:modelValue', null)
    wrapper.getComponent({ name: 'BDatepicker' }).vm.$emit('update:modelValue', pickedDate)

    expect(wrapper.emitted('change-date')).toEqual([['lot-1', pickedDate]])
  })
})
