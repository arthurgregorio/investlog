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

function mountCard(row: WalletKindRow) {
  return mount(TypeSummaryCard, { props: { row, currency: 'BRL' } })
}

describe('TypeSummaryCard', () => {
  it('renders the kind, its counts and its figures', () => {
    const wrapper = mountCard(stocks)

    expect(wrapper.get('.type-name').text()).toBe('Ações')
    expect(wrapper.get('.type-meta').text()).toContain('2 carteiras')
    expect(wrapper.get('.type-meta').text()).toContain('3 ativos')
    expect(wrapper.get('.type-value').text()).toBe('R$ 6,0k')
    expect(wrapper.get('.result-value').text()).toBe('R$ 7,5k')
    expect(wrapper.get('.gl').text()).toContain('+R$ 1,5k')
    expect((wrapper.get('.type-ic').element as HTMLElement).style.background).toBe(
      'var(--wt-stocks)',
    )
  })

  it('uses the singular for one wallet and one holding', () => {
    const meta = mountCard({ ...stocks, walletCount: 1, holdings: 1 })
      .get('.type-meta')
      .text()

    expect(meta).toContain('1 carteira')
    expect(meta).not.toContain('carteiras')
    expect(meta).not.toContain('ativos')
  })

  it('shows an empty result for a kind with no positions', () => {
    const wrapper = mountCard({ ...stocks, gain: null, gainPct: null })

    expect(wrapper.get('.gl-empty').text()).toBe('—')
  })

  it('emits goto-type with its kind when clicked', async () => {
    const wrapper = mountCard(stocks)

    await wrapper.trigger('click')

    expect(wrapper.emitted('goto-type')).toEqual([['STOCKS']])
  })
})
