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
  expanded: boolean
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
    expanded: false,
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

describe('HoldingsTableRow', () => {
  it('shows the ticker, the type tag and the holding name', () => {
    const wrapper = mountRow()

    expect(wrapper.get('.t-ticker').text()).toBe('AAPL')
    expect(wrapper.get('.type-tag').text()).toBe('Ação')
    expect(wrapper.get('.t-name').text()).toBe('Apple')
    expect(wrapper.get('.ticker-badge').text()).toBe('AAPL')
  })

  it('formats the figures in the wallet currency by default', () => {
    const wrapper = mountRow()

    const cells = numericCells(wrapper)
    expect(cells[0]).toBe('10')
    expect(cells[1]).toContain(fmt.money(200, 'USD'))
    expect(cells[1]).toContain(`PM ${fmt.money(150, 'USD')}`)
    expect(cells[2]).toBe(fmt.money(1500, 'USD'))
    expect(cells[3]).toBe(fmt.money(2000, 'USD'))
    expect(cells[4]).toContain(fmt.moneySigned(500, 'USD'))
  })

  it('converts every figure into the display currency when asked to', () => {
    const wrapper = mountRow({ convertToDisplayCurrency: true })

    const cells = numericCells(wrapper)
    expect(cells[1]).toContain(fmt.money(1000, 'BRL'))
    expect(cells[1]).toContain(`PM ${fmt.money(750, 'BRL')}`)
    expect(cells[2]).toBe(fmt.money(7500, 'BRL'))
    expect(cells[3]).toBe(fmt.money(10000, 'BRL'))
    expect(cells[4]).toContain(fmt.moneySigned(2500, 'BRL'))
  })

  it('shows a fund by name with its current value in the price column and no average price', () => {
    const wrapper = mountRow({ row: fundRow })

    expect(wrapper.get('.t-ticker').text()).toBe('Tesouro IPCA+')
    expect(wrapper.get('.type-tag').text()).toBe('Fundo')
    expect(wrapper.find('.t-name').exists()).toBe(false)
    const cells = numericCells(wrapper)
    expect(cells[0]).toBe('—')
    expect(cells[1]).toBe(fmt.money(3300, 'BRL'))
    expect(wrapper.find('.avg-note').exists()).toBe(false)
  })

  it('labels crypto and an untyped stock by their kind', () => {
    expect(
      mountRow({ row: { ...stockRow, kind: 'CRYPTO' } })
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
    expect(cells[1]).toContain('—')
    expect(cells[3]).toBe('—')
    expect(cells[4]).toBe('—')
  })

  it('swaps the ticker square for the frozen badge and marks the row frozen', () => {
    const wrapper = mountRow({ row: { ...stockRow, frozen: true } })

    const row = wrapper.get('tr.inv-row')
    expect(row.classes()).toContain('is-frozen')
    expect(row.findAll('.ticker-badge')).toHaveLength(1)
    expect(row.get('.ticker-badge').attributes('data-testid')).toBe('frozen-tag')
  })

  it('renders the wallet column only when asked to', () => {
    expect(mountRow().find('.wallet-ref').exists()).toBe(false)

    const wrapper = mountRow({ showWalletColumn: true })
    expect(wrapper.get('.wallet-ref').text()).toBe('Carteira EUA')
    expect(wrapper.findAll('td')).toHaveLength(8)
  })

  it('flips the chevron and the open class while expanded', () => {
    const closed = mountRow()
    expect(closed.get('.chev .mdi').classes()).toContain('mdi-chevron-down')
    expect(closed.get('tr').classes()).not.toContain('is-open')

    const open = mountRow({ expanded: true })
    expect(open.get('.chev .mdi').classes()).toContain('mdi-chevron-up')
    expect(open.get('tr').classes()).toContain('is-open')
  })

  it('asks to toggle when the row is clicked', async () => {
    const wrapper = mountRow()

    await wrapper.get('tr').trigger('click')

    expect(wrapper.findComponent(HoldingsTableRow).emitted('toggle')).toHaveLength(1)
  })
})
