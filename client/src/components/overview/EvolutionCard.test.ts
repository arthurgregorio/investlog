import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import EvolutionCard from './EvolutionCard.vue'
import PatrimonyChart from '@/components/charts/PatrimonyChart.vue'
import type { SeriesPoint } from '@/types'

const PatrimonyChartStub = {
  props: ['ownMoney', 'result', 'labels', 'markerIndex', 'markerLabel', 'fmtY'],
  template: '<div class="patrimony-chart-stub" />',
}

function pointOf(
  month: string,
  ownMoney: number,
  result: number,
  marketValued = false,
): SeriesPoint {
  return {
    month,
    ownMoney,
    reinvestedProfit: 0,
    totalInvested: ownMoney,
    result,
    marketValued,
  }
}

function mountCard(series: SeriesPoint[], currency = 'BRL') {
  return mount(EvolutionCard, {
    props: { series, currency },
    global: { stubs: { PatrimonyChart: PatrimonyChartStub } },
    attachTo: document.body,
  })
}

describe('EvolutionCard', () => {
  it('draws own money and the result per month and marks where the daily records start', () => {
    const wrapper = mountCard([
      pointOf('2026-08', 1000, 50),
      pointOf('2026-09', 3000, 200, true),
      pointOf('2026-10', 3000, 400, true),
    ])

    const chart = wrapper.findComponent(PatrimonyChart)

    expect(chart.props('ownMoney')).toEqual([1000, 3000, 3000])
    expect(chart.props('result')).toEqual([50, 200, 400])
    expect(chart.props('labels')).toEqual(['ago/26', 'set/26', 'out/26'])
    expect(chart.props('markerIndex')).toBe(1)
    expect(chart.props('markerLabel')).toBe('início dos registros diários')
    expect(wrapper.get('.chart-title').text()).toBe('Patrimônio mês a mês')
    expect(wrapper.get('.sub-caption').text()).toBe('Capital próprio + resultado total · BRL')
    expect(wrapper.get('.chart-big').text()).toBe('R$ 3,4k')
  })

  it('has no marker while no month is market valued', () => {
    const wrapper = mountCard([pointOf('2026-08', 1000, 0), pointOf('2026-09', 2000, 0)])

    expect(wrapper.findComponent(PatrimonyChart).props('markerIndex')).toBeNull()
  })

  it('pads a single point with a leading zero and shifts the marker with it', () => {
    const wrapper = mountCard([pointOf('2026-11', 2500, 100, true)])

    const chart = wrapper.findComponent(PatrimonyChart)
    expect(chart.props('ownMoney')).toEqual([0, 2500])
    expect(chart.props('result')).toEqual([0, 100])
    expect(chart.props('labels')).toEqual(['', 'nov/26'])
    expect(chart.props('markerIndex')).toBe(1)
  })

  it('draws a flat zero line when there is no history', () => {
    const wrapper = mountCard([])

    const chart = wrapper.findComponent(PatrimonyChart)
    expect(chart.props('ownMoney')).toEqual([0, 0])
    expect(chart.props('labels')).toEqual(['', ''])
    expect(wrapper.get('.chart-big').text()).toBe('R$ 0,00')
    expect(wrapper.findAll('.legend-pct').map((share) => share.text())).toEqual(['0%', '0%'])
  })

  it('names the result layer by its sign in the legend', () => {
    const gain = mountCard([pointOf('2026-11', 3000, 1000)])
    const gainRows = gain.findAll('[data-testid="allocation-legend-entry"]')
    expect(gainRows.map((row) => row.get('[data-testid="allocation-legend-hint"]').text())).toEqual(
      ['Capital próprio', 'Resultado total'],
    )
    expect(gainRows.map((row) => row.get('.legend-pct').text())).toEqual(['75,00%', '25,00%'])

    const loss = mountCard([pointOf('2026-11', 3000, -500)])
    expect(loss.findAll('[data-testid="allocation-legend-hint"]')[1]!.text()).toBe('Perda total')
  })

  it('formats the axis ticks in thousands above one thousand, also below zero', () => {
    const wrapper = mountCard([pointOf('2026-11', 3000, 0)], 'USD')

    const formatAxis = wrapper.findComponent(PatrimonyChart).props('fmtY') as (
      value: number,
    ) => string

    expect(formatAxis(500)).toBe('US$ 500')
    expect(formatAxis(12000)).toBe('US$ 12k')
    expect(formatAxis(-2000)).toBe('US$ -2k')
  })
})
