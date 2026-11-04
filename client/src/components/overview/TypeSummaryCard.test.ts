import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import TypeSummaryCard from './TypeSummaryCard.vue'
import type { WalletKindRow } from '@/utils/walletKindRows'

const stocks: WalletKindRow = {
  key: 'STOCKS',
  label: 'Ações',
  accent: 'var(--wt-stocks)',
  icon: 'trending-up',
  invested: 6000,
  currentValue: 7500,
  gain: 1500,
  gainPct: 25,
  walletCount: 2,
  holdings: 3,
}

function mountCard(row: WalletKindRow, currency = 'BRL') {
  return mount(TypeSummaryCard, { props: { row, currency } })
}

describe('TypeSummaryCard', () => {
  it('shows the current value, the counts and the result of a kind with wallets', () => {
    const wrapper = mountCard(stocks)

    expect(wrapper.get('.type-name').text()).toBe('Ações')
    expect(wrapper.get('[data-testid="type-meta"]').text()).toBe('2 carteiras · 3 ativos')
    expect(wrapper.get('.type-value').text()).toBe('R$ 7,5k')
    expect(wrapper.get('.result-label').text()).toBe('Resultado')
    expect(wrapper.get('.gl').text()).toContain('+R$ 1,5k')
    expect(wrapper.get('.gl').text()).toContain('+25,00%')
  })

  it('uses the singular for one wallet and one holding', () => {
    const wrapper = mountCard({ ...stocks, walletCount: 1, holdings: 1 })

    expect(wrapper.get('[data-testid="type-meta"]').text()).toBe('1 carteira · 1 ativo')
  })

  it('emits goto-type with its kind from Ver', async () => {
    const wrapper = mountCard(stocks)

    await wrapper.get('[data-testid="type-view"]').trigger('click')

    expect(wrapper.emitted('goto-type')).toEqual([['STOCKS']])
  })

  it('keeps its slot for a kind without wallets and offers to create one', async () => {
    const wrapper = mountCard({
      ...stocks,
      key: 'FUNDS',
      label: 'Fundos',
      walletCount: 0,
      holdings: 0,
      gain: null,
      gainPct: null,
    })

    expect(wrapper.get('.type-value').text()).toBe('—')
    expect(wrapper.text()).toContain('Nenhuma carteira de fundos')
    expect(wrapper.find('[data-testid="type-view"]').exists()).toBe(false)

    await wrapper.get('[data-testid="type-create"]').trigger('click')

    expect(wrapper.emitted('create-wallet')).toEqual([['FUNDS']])
  })
})
