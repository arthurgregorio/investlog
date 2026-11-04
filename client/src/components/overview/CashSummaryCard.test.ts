import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import CashSummaryCard from './CashSummaryCard.vue'

function mountCard(cash: number, walletCount: number) {
  return mount(CashSummaryCard, { props: { cash, walletCount, currency: 'BRL' } })
}

describe('CashSummaryCard', () => {
  it('shows the cash, how many wallets hold it and that it is available', () => {
    const wrapper = mountCard(87300, 6)

    expect(wrapper.get('.type-name').text()).toBe('Caixa')
    expect(wrapper.get('.type-value').text()).toBe('R$ 87,3k')
    expect(wrapper.get('[data-testid="cash-meta"]').text()).toBe('em 6 carteiras')
    expect(wrapper.text()).toContain('Disponível para comprar')
  })

  it('uses the singular for one wallet', () => {
    expect(mountCard(10, 1).get('[data-testid="cash-meta"]').text()).toBe('em 1 carteira')
  })

  it('says so when no wallet holds cash', () => {
    const wrapper = mountCard(0, 0)

    expect(wrapper.get('[data-testid="cash-meta"]').text()).toBe('nenhuma carteira com caixa')
    expect(wrapper.text()).toContain('Sem caixa parado')
  })

  it('emits view from Ver', async () => {
    const wrapper = mountCard(10, 1)

    await wrapper.get('[data-testid="cash-view"]').trigger('click')

    expect(wrapper.emitted('view')).toHaveLength(1)
  })
})
