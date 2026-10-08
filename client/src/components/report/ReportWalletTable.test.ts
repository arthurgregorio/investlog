import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import ReportWalletTable from './ReportWalletTable.vue'
import { useCurrencyStore } from '@/stores/currency'
import type { HoldingRow } from '@/types'
import type { ReportRow, WalletGroup } from '@/utils/reportGrouping'

function rowOf(holding: Partial<HoldingRow>, figures: Partial<ReportRow> = {}): ReportRow {
  return {
    holding: {
      id: 'holding-1',
      kind: 'STOCKS',
      name: 'Itaú',
      ticker: 'ITUB4',
      typeLabel: 'Preferencial',
      segmentLabel: null,
      walletId: 'wallet-1',
      walletName: 'Corretora B',
      walletCurrency: 'BRL',
      quantity: 2.5,
      costBasis: 100,
      currentPrice: 50,
      currentValue: 125,
      gain: 25,
      gainPct: 25,
      frozen: false,
      ...holding,
    },
    currentPrice: 50,
    costBasis: 100,
    currentValue: 125,
    gain: 25,
    gainPct: 25,
    ...figures,
  }
}

const walletGroup: WalletGroup = {
  walletId: 'wallet-1',
  walletName: 'Corretora B',
  rows: [
    rowOf({}),
    rowOf(
      { id: 'holding-2', name: 'Tesouro Selic', ticker: null, quantity: null },
      { currentPrice: null, costBasis: 500, currentValue: 520, gain: 20, gainPct: 4 },
    ),
  ],
  totals: { costBasis: 600, currentValue: 645, gain: 45, gainPct: 7.5 },
}

function mountTable(displayCurrency = 'BRL') {
  const pinia = createTestingPinia()
  useCurrencyStore().displayCurrency = displayCurrency
  return mount(ReportWalletTable, { props: { walletGroup }, global: { plugins: [pinia] } })
}

function cellsOf(wrapper: ReturnType<typeof mountTable>, rowIndex: number) {
  return wrapper
    .findAll('tbody tr')
    [rowIndex].findAll('td')
    .map((cell) => cell.text())
}

describe('ReportWalletTable', () => {
  it('names the wallet above its table', () => {
    expect(mountTable().find('.report-wallet-name').text()).toBe('Corretora B')
  })

  it('renders a holding with quantity, price, cost, value and result', () => {
    const cells = cellsOf(mountTable(), 0)

    expect(cells.slice(0, 5)).toEqual(['ITUB4', '2,5', 'R$ 50,00', 'R$ 100,00', 'R$ 125,00'])
    expect(cells[5]).toContain('+R$ 25,00')
  })

  it('falls back to the name and dashes when ticker, quantity and price are missing', () => {
    const cells = cellsOf(mountTable(), 1)

    expect(cells.slice(0, 4)).toEqual(['Tesouro Selic', '—', '—', 'R$ 500,00'])
  })

  it('closes the table with the wallet subtotal row', () => {
    const subtotal = mountTable().find('.report-subtotal-row')

    expect(subtotal.findAll('td').map((cell) => cell.text())).toEqual([
      '',
      'R$ 600,00',
      'R$ 645,00',
      expect.stringContaining('+R$ 45,00'),
    ])
  })

  it('formats money in the display currency', () => {
    expect(cellsOf(mountTable('USD'), 0)[2]).toBe('US$ 50,00')
  })
})
