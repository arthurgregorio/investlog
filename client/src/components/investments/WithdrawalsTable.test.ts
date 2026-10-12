import { describe, expect, it } from 'vitest'
import { mount, type DOMWrapper } from '@vue/test-utils'
import WithdrawalsTable from './WithdrawalsTable.vue'
import type { ResultRow } from '@/types'

const stockWithdrawal: ResultRow = {
  id: 'result-stock',
  kind: 'STOCKS',
  resultType: 'WITHDRAWAL',
  holdingName: 'Petrobras',
  ticker: 'PETR4',
  walletId: 'wallet-stocks',
  walletName: 'Ações',
  walletCurrency: 'BRL',
  resultDate: '2026-09-19',
  quantity: 40,
  grossAmount: 2000,
  fees: 10,
  taxes: 90,
  costBasis: 1400,
  netAmount: 1900,
  profit: 500,
}

const fundWithdrawal: ResultRow = {
  ...stockWithdrawal,
  id: 'result-fund',
  kind: 'FUNDS',
  holdingName: 'Tesouro Selic',
  ticker: null,
  walletName: 'Fundos',
  quantity: null,
  grossAmount: 500,
  fees: 0,
  taxes: 0,
  costBasis: 400,
  netAmount: 500,
  profit: 100,
}

const cryptoLoss: ResultRow = {
  ...stockWithdrawal,
  id: 'result-crypto',
  kind: 'CRYPTO',
  holdingName: 'BTC',
  ticker: 'BTC',
  walletName: 'Cripto',
  quantity: 0.5,
  grossAmount: 100,
  fees: 0,
  taxes: 0,
  costBasis: 150,
  netAmount: 100,
  profit: -50,
}

const dollarWithdrawal: ResultRow = {
  ...stockWithdrawal,
  id: 'result-dollar',
  holdingName: 'Apple',
  ticker: 'AAPL',
  walletName: 'Exterior',
  walletCurrency: 'USD',
  quantity: 10,
  grossAmount: 1100,
  fees: 1,
  taxes: 0,
  costBasis: 1000,
  netAmount: 1099,
  profit: 99,
}

function mountTable(rows: ResultRow[]) {
  return mount(WithdrawalsTable, { props: { rows } })
}

function cell(row: Pick<DOMWrapper<Element>, 'get'>, testId: string) {
  return row.get(`[data-testid="${testId}"]`).text()
}

describe('WithdrawalsTable', () => {
  it('renders the withdrawal columns in order', () => {
    const wrapper = mountTable([stockWithdrawal])

    expect(wrapper.findAll('th').map((header) => header.text())).toEqual([
      'Data',
      'Ativo',
      'Quantidade',
      'Preço na venda',
      'Valor bruto',
      'Custos',
      'Valor líquido',
      'Custo',
      'Resultado',
    ])
  })

  it('shows a stock withdrawal with its unit price, costs and profit percentage', () => {
    const row = mountTable([stockWithdrawal]).get('[data-testid="withdrawal-row"]')

    expect(row.text()).toContain('19 set 2026')
    expect(row.text()).toContain('PETR4')
    expect(row.text()).toContain('Petrobras · Ações')
    expect(cell(row, 'withdrawal-quantity')).toBe('40')
    expect(cell(row, 'withdrawal-unit-price')).toBe('R$ 50,00')
    expect(cell(row, 'withdrawal-gross')).toBe('R$ 2.000,00')
    expect(cell(row, 'withdrawal-costs')).toBe('R$ 100,00')
    expect(cell(row, 'withdrawal-net')).toBe('R$ 1.900,00')
    expect(cell(row, 'withdrawal-cost-basis')).toBe('R$ 1.400,00')
    expect(cell(row, 'withdrawal-result')).toContain('+R$ 500,00')
    expect(cell(row, 'withdrawal-result')).toContain('+35,71%')
    expect(row.find('.gl-up').exists()).toBe(true)
  })

  it('shows dashes for the quantity and unit price of a fund withdrawal', () => {
    const row = mountTable([fundWithdrawal]).get('[data-testid="withdrawal-row"]')

    expect(row.text()).toContain('Tesouro Selic')
    expect(row.text()).toContain('Fundos')
    expect(row.text()).not.toContain('·')
    expect(cell(row, 'withdrawal-quantity')).toBe('—')
    expect(cell(row, 'withdrawal-unit-price')).toBe('—')
    expect(cell(row, 'withdrawal-gross')).toBe('R$ 500,00')
    expect(cell(row, 'withdrawal-costs')).toBe('R$ 0,00')
    expect(cell(row, 'withdrawal-result')).toContain('+25,00%')
  })

  it('shows a loss in the loss colour with a negative percentage', () => {
    const row = mountTable([cryptoLoss]).get('[data-testid="withdrawal-row"]')

    expect(row.text()).toContain('BTC')
    expect(row.text()).not.toContain('BTC ·')
    expect(cell(row, 'withdrawal-quantity')).toBe('0,5')
    expect(cell(row, 'withdrawal-unit-price')).toBe('R$ 200,00')
    expect(cell(row, 'withdrawal-result')).toContain('−R$ 50,00')
    expect(cell(row, 'withdrawal-result')).toContain('−33,33%')
    expect(row.find('.gl-down').exists()).toBe(true)
  })

  it('formats every amount of a USD wallet row in dollars', () => {
    const row = mountTable([dollarWithdrawal]).get('[data-testid="withdrawal-row"]')

    expect(cell(row, 'withdrawal-unit-price')).toBe('US$ 110,00')
    expect(cell(row, 'withdrawal-gross')).toBe('US$ 1.100,00')
    expect(cell(row, 'withdrawal-costs')).toBe('US$ 1,00')
    expect(cell(row, 'withdrawal-net')).toBe('US$ 1.099,00')
    expect(cell(row, 'withdrawal-cost-basis')).toBe('US$ 1.000,00')
    expect(cell(row, 'withdrawal-result')).toContain('+US$ 99,00')
    expect(cell(row, 'withdrawal-result')).toContain('+9,90%')
  })

  it('leaves out the percentage when the withdrawal had no cost basis', () => {
    const row = mountTable([{ ...stockWithdrawal, costBasis: 0, profit: 1900 }]).get(
      '[data-testid="withdrawal-row"]',
    )

    expect(cell(row, 'withdrawal-result')).toBe('+R$ 1.900,00')
    expect(row.find('.gl-pct').exists()).toBe(false)
  })

  it('renders one row per withdrawal in the order given', () => {
    const wrapper = mountTable([stockWithdrawal, fundWithdrawal, cryptoLoss])

    expect(
      wrapper
        .findAll('[data-testid="withdrawal-row"]')
        .map((row) => row.get('.has-text-weight-bold').text()),
    ).toEqual(['PETR4', 'Tesouro Selic', 'BTC'])
  })
})
