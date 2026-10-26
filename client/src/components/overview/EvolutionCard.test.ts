import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import EvolutionCard from './EvolutionCard.vue'
import AreaChart from '@/components/charts/AreaChart.vue'
import type { SeriesPoint } from '@/types'

const AreaChartStub = {
  props: ['data', 'stackedData', 'xLabels', 'fmtY', 'color', 'stackedColor', 'height'],
  template: '<div class="area-chart-stub" />',
}

function pointOf(month: string, ownMoney: number, reinvestedProfit: number): SeriesPoint {
  return { month, ownMoney, reinvestedProfit, totalInvested: ownMoney + reinvestedProfit }
}

function mountCard(series: SeriesPoint[], currency = 'BRL') {
  return mount(EvolutionCard, {
    props: { series, currency },
    global: { stubs: { AreaChart: AreaChartStub } },
    attachTo: document.body,
  })
}

describe('EvolutionCard', () => {
  it('stacks reinvested profit on own money with month labels and shows the latest total', () => {
    const wrapper = mountCard([pointOf('2026-01', 1000, 0), pointOf('2026-02', 3000, 1000)])

    const chart = wrapper.findComponent(AreaChart)

    expect(chart.props('data')).toEqual([1000, 3000])
    expect(chart.props('stackedData')).toEqual([0, 1000])
    expect(chart.props('color')).toBe('var(--primary)')
    expect(chart.props('stackedColor')).toBe('var(--chart-6)')
    expect(chart.props('xLabels')).toEqual(['jan/26', 'fev/26'])
    expect(wrapper.get('.chart-big').text()).toBe('R$ 4,0k')
    expect(wrapper.get('.chart-title').text()).toBe('Evolução dos aportes')
    expect(wrapper.get('.sub-caption').text()).toBe('Capital próprio + lucro reinvestido · BRL')
  })

  it('lists both layers below the chart with their share and latest value', () => {
    const wrapper = mountCard([pointOf('2026-02', 3000, 1000)])

    const rows = wrapper.findAll('[data-testid="allocation-legend-entry"]')
    expect(rows.map((row) => row.get('[data-testid="allocation-legend-hint"]').text())).toEqual([
      'Capital próprio',
      'Lucro reinvestido',
    ])
    expect(rows.map((row) => row.get('.legend-pct').text())).toEqual(['75,00%', '25,00%'])
    expect(rows.map((row) => row.get('.legend-value').text())).toEqual(['R$ 3,0k', 'R$ 1,0k'])
  })

  it('explains each layer in a tooltip on its label', () => {
    const wrapper = mountCard([pointOf('2026-02', 3000, 1000)])

    const tooltips = wrapper.findAll('.b-tooltip')
    expect(tooltips).toHaveLength(2)
    expect(tooltips[0]!.text()).toContain(
      'Dinheiro novo que você colocou nas carteiras, menos o que sacou para o banco.',
    )
    expect(tooltips[1]!.text()).toContain(
      'Lucro de resgates que voltou para compras em vez de sair do InvestLog.',
    )
  })

  it('pads a single point with a leading zero in both layers', () => {
    const chart = mountCard([pointOf('2026-03', 2000, 500)]).findComponent(AreaChart)

    expect(chart.props('data')).toEqual([0, 2000])
    expect(chart.props('stackedData')).toEqual([0, 500])
    expect(chart.props('xLabels')).toEqual(['', 'mar/26'])
  })

  it('draws flat zero layers and zero shares when there is no history', () => {
    const wrapper = mountCard([])

    expect(wrapper.findComponent(AreaChart).props('data')).toEqual([0, 0])
    expect(wrapper.findComponent(AreaChart).props('stackedData')).toEqual([0, 0])
    expect(wrapper.get('.chart-big').text()).toBe('R$ 0,00')
    expect(wrapper.findAll('.legend-pct').map((cell) => cell.text())).toEqual(['0%', '0%'])
  })

  it('labels the axis in the given currency, in thousands above one thousand', () => {
    const wrapper = mountCard([], 'USD')

    const formatAxis = wrapper.findComponent(AreaChart).props('fmtY') as (value: number) => string

    expect(formatAxis(500)).toBe('US$ 500')
    expect(formatAxis(12000)).toBe('US$ 12k')
    expect(wrapper.get('.sub-caption').text()).toContain('USD')
  })
})
