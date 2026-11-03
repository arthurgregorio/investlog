import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ThisMonthCard from './ThisMonthCard.vue'
import type { MonthSummary } from '@/types'

const month: MonthSummary = {
  month: '2026-11',
  contributions: 18100,
  realizedProfit: 0,
  currentResultChange: -13000,
  bestMover: { kind: 'STOCKS', ticker: 'ITUB4', name: 'Itaú', changePct: 6.82 },
  worstMover: { kind: 'CRYPTO', ticker: 'BTC', name: 'Bitcoin', changePct: -14.3 },
}

function mountCard(overrides: Partial<MonthSummary> = {}, unpricedCount = 0) {
  return mount(ThisMonthCard, {
    props: { month: { ...month, ...overrides }, unpricedCount, currency: 'BRL' },
  })
}

describe('ThisMonthCard', () => {
  it('lists the month figures under month-specific labels', () => {
    const wrapper = mountCard()

    expect(wrapper.get('.sub-caption').text()).toBe('nov/26 até hoje')
    expect(wrapper.findAll('.type-name').map((name) => name.text())).toEqual([
      'Aportes no mês',
      'Lucro realizado no mês',
      'Variação do resultado atual',
      'ITUB4',
      'BTC',
    ])
    expect(wrapper.get('[data-testid="month-contributions"]').text()).toBe('R$ 18,1k')
    expect(wrapper.get('[data-testid="month-realized"]').text()).toBe('Nenhum resgate')
    expect(wrapper.get('[data-testid="month-change"]').text()).toBe('−R$ 13,0k')
    expect(wrapper.get('[data-testid="month-change"]').classes()).toContain('gl-down')
    expect(wrapper.get('[data-testid="month-change-icon"]').classes()).toContain(
      'has-background-danger',
    )
  })

  it('shows realized profit and a rising current result in green', () => {
    const wrapper = mountCard({ realizedProfit: 2500, currentResultChange: 800 })

    expect(wrapper.get('[data-testid="month-realized"]').text()).toBe('+R$ 2,5k')
    expect(wrapper.get('[data-testid="month-realized"]').classes()).toContain('gl-up')
    expect(wrapper.get('[data-testid="month-change"]').classes()).toContain('gl-up')
    expect(wrapper.get('[data-testid="month-change-icon"]').classes()).toContain(
      'has-background-success',
    )
  })

  it('shows a dash when the change cannot be measured yet', () => {
    const wrapper = mountCard({ currentResultChange: null, contributions: 100 })

    expect(wrapper.get('[data-testid="month-change"]').text()).toBe('—')
    expect(wrapper.get('[data-testid="month-change"]').classes()).toContain('has-text-grey')
  })

  it('shows the best and worst movers of the month, falling back to the name', () => {
    const wrapper = mountCard({
      bestMover: { kind: 'STOCKS', ticker: null, name: 'Fundo X', changePct: 3 },
      worstMover: null,
    })

    expect(wrapper.get('[data-testid="best-mover"]').text()).toContain('Maior alta')
    expect(wrapper.get('[data-testid="best-mover"]').text()).toContain('Fundo X')
    expect(wrapper.get('[data-testid="best-mover"]').text()).toContain('+3,00%')
    expect(wrapper.find('[data-testid="worst-mover"]').exists()).toBe(false)

    const unnamed = mountCard({
      bestMover: null,
      worstMover: { kind: 'STOCKS', ticker: null, name: null, changePct: -1 },
    })
    expect(unnamed.get('[data-testid="worst-mover"] .type-name').text()).toBe('—')
  })

  it('hides the movers row when there is no mover', () => {
    const wrapper = mountCard({ bestMover: null, worstMover: null, contributions: 100 })

    expect(wrapper.find('[data-testid="best-mover"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="worst-mover"]').exists()).toBe(false)
  })

  it('shows the empty state when nothing happened this month, keeping the price status', () => {
    const wrapper = mountCard({
      contributions: 0,
      realizedProfit: 0,
      currentResultChange: null,
      bestMover: null,
      worstMover: null,
    })

    expect(wrapper.get('.empty-title').text()).toBe('Nada neste mês ainda')
    expect(wrapper.get('.empty-text').text()).toContain('em nov/26')
    expect(wrapper.find('[data-testid="month-contributions"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="price-status"]').text()).toBe('Posições atualizadas')
  })

  it('warns about positions without a current price and emits the update link', async () => {
    const single = mountCard({}, 1)
    expect(single.get('[data-testid="price-status"]').text()).toContain('1 posição sem valor atual')

    const wrapper = mountCard({}, 2)
    expect(wrapper.get('[data-testid="price-status"]').classes()).toContain('is-warning')
    expect(wrapper.get('[data-testid="price-status"]').text()).toContain(
      '2 posições sem valor atual',
    )

    await wrapper.get('[data-testid="price-status-update"]').trigger('click')

    expect(wrapper.emitted('view-investments')).toHaveLength(1)
  })

  it('shows a green strip when every position is priced', () => {
    const wrapper = mountCard()

    expect(wrapper.get('[data-testid="price-status"]').classes()).toContain('is-ok')
    expect(wrapper.find('[data-testid="price-status-update"]').exists()).toBe(false)
  })
})
