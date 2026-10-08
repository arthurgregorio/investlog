import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import EvolutionCard from './EvolutionCard.vue'
import AreaChart from '@/components/charts/AreaChart.vue'
import type { SeriesPoint } from '@/types'

const AreaChartStub = {
  props: ['data', 'xLabels', 'fmtY', 'color', 'height'],
  template: '<div class="area-chart-stub" />',
}

function mountCard(series: SeriesPoint[], currency = 'BRL') {
  return mount(EvolutionCard, {
    props: { series, currency },
    global: { stubs: { AreaChart: AreaChartStub } },
  })
}

describe('EvolutionCard', () => {
  it('feeds the chart the invested series with month labels and shows the latest value', () => {
    const wrapper = mountCard([
      { month: '2026-01', totalInvested: 1000 },
      { month: '2026-02', totalInvested: 4000 },
    ])

    const chart = wrapper.findComponent(AreaChart)

    expect(chart.props('data')).toEqual([1000, 4000])
    expect(chart.props('xLabels')).toEqual(['jan/26', 'fev/26'])
    expect(wrapper.get('.chart-big').text()).toBe('R$ 4,0k')
    expect(wrapper.get('.chart-title').text()).toBe('Evolução dos aportes')
  })

  it('pads a single point with a leading zero', () => {
    const chart = mountCard([{ month: '2026-03', totalInvested: 2500 }]).findComponent(AreaChart)

    expect(chart.props('data')).toEqual([0, 2500])
    expect(chart.props('xLabels')).toEqual(['', 'mar/26'])
  })

  it('draws a flat zero line when there is no history', () => {
    const wrapper = mountCard([])

    expect(wrapper.findComponent(AreaChart).props('data')).toEqual([0, 0])
    expect(wrapper.get('.chart-big').text()).toBe('R$ 0,00')
  })

  it('labels the axis in the given currency, in thousands above one thousand', () => {
    const wrapper = mountCard([], 'USD')

    const formatAxis = wrapper.findComponent(AreaChart).props('fmtY') as (value: number) => string

    expect(formatAxis(500)).toBe('US$ 500')
    expect(formatAxis(12000)).toBe('US$ 12k')
    expect(wrapper.get('.sub-caption').text()).toContain('USD')
  })
})
