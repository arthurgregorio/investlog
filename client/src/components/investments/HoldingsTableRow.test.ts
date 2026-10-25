import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import HoldingsTableRow from './HoldingsTableRow.vue'
import { fmt } from '@/composables/useFormat'
import { useCurrencyStore } from '@/stores/currency'
import type { HoldingRow } from '@/types'

const stockRow: HoldingRow = {
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

const fundRow: HoldingRow = {
  ...stockRow,
  id: 'holding-2',
  kind: 'FUNDS',
  name: 'Tesouro IPCA+',
  ticker: null,
  typeLabel: null,
  walletCurrency: 'BRL',
  quantity: null,
  currentPrice: null,
  currentValue: 3300,
}

interface RowProps {
  row: HoldingRow
  current: boolean
  showWalletColumn: boolean
  convertToDisplayCurrency: boolean
}

function mountRow(props: Partial<RowProps> = {}) {
  const pinia = createTestingPinia()
  const currencyStore = useCurrencyStore()
  currencyStore.displayCurrency = 'BRL'
  vi.mocked(currencyStore.convert).mockImplementation((amount) => amount * 5)
  const rowProps: RowProps = {
    row: stockRow,
    current: false,
    showWalletColumn: false,
    convertToDisplayCurrency: false,
    ...props,
  }
  return mount(
    {
      components: { HoldingsTableRow },
      setup: () => ({ rowProps }),
      template: '<table><tbody><HoldingsTableRow v-bind="rowProps" /></tbody></table>',
    },
    { global: { plugins: [pinia] } },
  )
}

function numericCells(wrapper: ReturnType<typeof mountRow>) {
  return wrapper.findAll('td.c-num').map((cell) => cell.text())
}

function rowEmitted(wrapper: ReturnType<typeof mountRow>, event: string) {
  return wrapper.findComponent(HoldingsTableRow).emitted(event)
}

describe('HoldingsTableRow', () => {
  it('shows the ticker as the drawer button, the holding name and the type tag', () => {
    const wrapper = mountRow()

    const opener = wrapper.get('button.open-holding')
    expect(opener.text()).toBe('AAPL')
    expect(opener.attributes('aria-haspopup')).toBe('dialog')
    expect(opener.attributes('data-open-holding')).toBe('holding-1')
    expect(wrapper.get('.t-name').text()).toBe('Apple')
    expect(wrapper.get('.type-tag').text()).toBe('Ação')
    expect(wrapper.get('.ticker-badge').text()).toBe('AAPL')
  })

  it('shows quantity, average price, current price, value and result in the wallet currency', () => {
    const wrapper = mountRow()

    const cells = numericCells(wrapper)
    expect(cells).toHaveLength(5)
    expect(cells[0]).toBe('10')
    expect(cells[1]).toBe(fmt.money(150, 'USD'))
    expect(cells[2]).toBe(fmt.money(200, 'USD'))
    expect(cells[3]).toBe(fmt.money(2000, 'USD'))
    expect(cells[4]).toContain(fmt.moneySigned(500, 'USD'))
    expect(wrapper.text()).not.toContain('PM ')
  })

  it('converts every figure into the display currency when asked to', () => {
    const wrapper = mountRow({ convertToDisplayCurrency: true })

    const cells = numericCells(wrapper)
    expect(cells[1]).toBe(fmt.money(750, 'BRL'))
    expect(cells[2]).toBe(fmt.money(1000, 'BRL'))
    expect(cells[3]).toBe(fmt.money(10000, 'BRL'))
    expect(cells[4]).toContain(fmt.moneySigned(2500, 'BRL'))
  })

  it('shows a fund by name with dashes for quantity, average price and current price', () => {
    const wrapper = mountRow({ row: fundRow })

    expect(wrapper.get('button.open-holding').text()).toBe('Tesouro IPCA+')
    expect(wrapper.get('.t-name').text()).toBe('Fundo de investimento')
    expect(wrapper.get('.type-tag').text()).toBe('Fundo')
    const cells = numericCells(wrapper)
    expect(cells.slice(0, 3)).toEqual(['—', '—', '—'])
    expect(cells[3]).toBe(fmt.money(3300, 'BRL'))
  })

  it('labels a typed crypto by its type and an untyped crypto or stock by their kind', () => {
    expect(
      mountRow({ row: { ...stockRow, kind: 'CRYPTO', typeLabel: 'Moeda' } })
        .get('.type-tag')
        .text(),
    ).toBe('Moeda')
    expect(
      mountRow({ row: { ...stockRow, kind: 'CRYPTO', typeLabel: null } })
        .get('.type-tag')
        .text(),
    ).toBe('Cripto')
    expect(
      mountRow({ row: { ...stockRow, typeLabel: null } })
        .get('.type-tag')
        .text(),
    ).toBe('Ação')
  })

  it('shows dashes for a holding without a price, a current value or a gain', () => {
    const wrapper = mountRow({
      row: { ...stockRow, currentPrice: null, currentValue: null, gain: null, gainPct: null },
    })

    const cells = numericCells(wrapper)
    expect(cells[2]).toBe('—')
    expect(cells[3]).toBe('—')
    expect(cells[4]).toBe('—')
  })

  it('shows a dash for the average price when the quantity is zero', () => {
    expect(numericCells(mountRow({ row: { ...stockRow, quantity: 0 } }))[1]).toBe('—')
  })

  it('swaps the ticker square for the frozen badge and marks the row frozen', () => {
    const wrapper = mountRow({ row: { ...stockRow, frozen: true } })

    const row = wrapper.get('tr.inv-row')
    expect(row.classes()).toContain('is-frozen')
    expect(row.findAll('.ticker-badge')).toHaveLength(1)
    expect(row.get('.ticker-badge').attributes('data-testid')).toBe('frozen-tag')
  })

  it('adds the wallet reference above the type tag only when asked to', () => {
    expect(mountRow().find('.wallet-ref').exists()).toBe(false)

    const wrapper = mountRow({ showWalletColumn: true })
    expect(wrapper.get('.wallet-ref').text()).toBe('Carteira EUA')
    expect(wrapper.findAll('td')).toHaveLength(8)
  })

  it('highlights the row whose drawer is open', () => {
    expect(mountRow().get('tr').classes()).not.toContain('is-current')
    expect(mountRow({ current: true }).get('tr').classes()).toContain('is-current')
  })

  it('asks to open the drawer from a row click and from the ticker button', async () => {
    const wrapper = mountRow()

    await wrapper.get('tr').trigger('click')
    await wrapper.get('button.open-holding').trigger('click')

    expect(rowEmitted(wrapper, 'open')).toHaveLength(2)
  })

  it('offers a labelled buy and price button that do not open the drawer', async () => {
    const wrapper = mountRow()

    const addButton = wrapper.get('[data-testid="row-add-position"]')
    const priceButton = wrapper.get('[data-testid="row-update-price"]')
    expect(addButton.attributes('aria-label')).toBe('Registrar nova compra — AAPL')
    expect(priceButton.attributes('aria-label')).toBe('Atualizar preço — AAPL')

    await addButton.trigger('click')
    await priceButton.trigger('click')

    expect(rowEmitted(wrapper, 'add-position')).toHaveLength(1)
    expect(rowEmitted(wrapper, 'update-price')).toHaveLength(1)
    expect(rowEmitted(wrapper, 'open')).toBeUndefined()
  })

  it('words the row buttons for a fund', () => {
    const wrapper = mountRow({ row: fundRow })

    expect(wrapper.get('[data-testid="row-add-position"]').attributes('aria-label')).toBe(
      'Registrar novo aporte — Tesouro IPCA+',
    )
    expect(wrapper.get('[data-testid="row-update-price"]').attributes('aria-label')).toBe(
      'Atualizar valor atual — Tesouro IPCA+',
    )
  })

  it('disables the buy button on a frozen holding', () => {
    const wrapper = mountRow({ row: { ...stockRow, frozen: true } })

    expect(wrapper.get('[data-testid="row-add-position"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="row-update-price"]').attributes('disabled')).toBeUndefined()
  })
})
