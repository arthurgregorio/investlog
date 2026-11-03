import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import OverviewHealthCard from './OverviewHealthCard.vue'
import type { PortfolioSummary } from '@/types'

const summary: PortfolioSummary = {
  displayCurrency: 'BRL',
  totalCostBasis: 450000,
  totalCurrentValue: 485800,
  totalCash: 30000,
  totalGain: -4300,
  totalGainPct: -0.95,
  positionsValue: 455800,
  ownMoney: 452000,
  realizedProfit: 38100,
  lifetimeResult: 33800,
  lifetimeResultPct: 7.48,
  unpricedHoldingCount: 0,
  cashWalletCount: 2,
  kindSummaries: [],
}

function mountCard(overrides: Partial<PortfolioSummary> = {}) {
  return mount(OverviewHealthCard, {
    props: { summary: { ...summary, ...overrides }, currency: 'BRL' },
    attachTo: document.body,
  })
}

describe('OverviewHealthCard', () => {
  it('shows patrimônio with the lifetime and current results side by side, each on its basis', () => {
    const wrapper = mountCard()

    expect(wrapper.get('[data-testid="health-patrimony"] .kpi-value').text()).toBe('R$ 485,8k')
    expect(wrapper.get('[data-testid="lifetime-value"]').text()).toBe('+R$ 33,8k')
    expect(wrapper.get('[data-testid="lifetime-value"]').classes()).toContain('gl-up')
    expect(wrapper.get('[data-testid="health-lifetime"]').text()).toContain(
      'sobre R$ 452,0k de capital próprio',
    )
    expect(wrapper.get('[data-testid="current-value"]').text()).toBe('−R$ 4,3k')
    expect(wrapper.get('[data-testid="current-value"]').classes()).toContain('gl-down')
    expect(wrapper.get('[data-testid="health-current"]').text()).toContain(
      'sobre R$ 450,0k pagos nas posições abertas',
    )
    expect(wrapper.get('[data-testid="health-current"]').text()).toContain('Posições em queda')
    expect(wrapper.get('[data-testid="health-current"] .result-value').text()).toBe('R$ 455,8k')
    expect(wrapper.get('[data-testid="health-lifetime"] .gl').text()).toBe('+R$ 38,1k')
  })

  it('splits patrimônio into own money and result with matching legend shares', () => {
    const wrapper = mountCard()

    const bars = wrapper.get('[data-testid="health-split"]').findAll('div')
    expect(bars[0]!.classes()).toContain('has-background-primary')
    expect(bars[1]!.classes()).toContain('has-background-success')
    expect(wrapper.findAll('.legend-pct').map((share) => share.text())).toEqual(['93,04%', '6,96%'])
    expect(wrapper.get('[data-testid="health-tag"]').text()).toBe('Acima do capital próprio')
  })

  it('turns red and relabels the result when the money is below its own base', () => {
    const wrapper = mountCard({
      lifetimeResult: -12000,
      lifetimeResultPct: -2.65,
      totalGain: 3000,
      totalGainPct: 0.7,
      realizedProfit: -15000,
    })

    expect(wrapper.get('[data-testid="health-tag"]').text()).toBe('Abaixo do capital próprio')
    expect(wrapper.get('[data-testid="lifetime-value"]').classes()).toContain('gl-down')
    expect(wrapper.get('[data-testid="health-split"]').findAll('div')[1]!.classes()).toContain(
      'has-background-danger',
    )
    expect(wrapper.findAll('[data-testid="allocation-legend-entry"]')[1]!.text()).toContain(
      'Perda total',
    )
    expect(wrapper.get('[data-testid="health-current"]').text()).toContain('Posições em alta')
    expect(wrapper.get('[data-testid="health-lifetime"] .gl').classes()).toContain('gl-down')
  })

  it('treats missing percentages and an empty split as zero', () => {
    const wrapper = mountCard({
      ownMoney: 0,
      lifetimeResult: 0,
      lifetimeResultPct: null,
      totalGainPct: null,
    })

    expect(wrapper.get('[data-testid="health-lifetime"]').text()).toContain('+0,00%')
    expect(wrapper.get('[data-testid="health-current"]').text()).toContain('+0,00%')
    expect(wrapper.findAll('.legend-pct').map((share) => share.text())).toEqual(['0,00%', '0,00%'])
  })

  it('explains both results behind a focusable help icon', () => {
    const wrapper = mountCard()

    const helps = wrapper.findAll('.health-help')
    expect(helps).toHaveLength(2)
    expect(helps[0]!.attributes('tabindex')).toBe('0')
    expect(helps[0]!.attributes('aria-label')).toContain('desde o primeiro aporte')
    expect(helps[1]!.attributes('aria-label')).toContain('a preço de hoje')
  })

  it('emits the links to results and investments', async () => {
    const wrapper = mountCard()

    await wrapper.get('[data-testid="view-results"]').trigger('click')
    await wrapper.get('[data-testid="view-investments"]').trigger('click')

    expect(wrapper.emitted('view-results')).toHaveLength(1)
    expect(wrapper.emitted('view-investments')).toHaveLength(1)
  })
})
