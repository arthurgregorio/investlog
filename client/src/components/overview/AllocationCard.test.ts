import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AllocationCard from './AllocationCard.vue'
import DonutChart from '@/components/charts/DonutChart.vue'
import { walletKindRows } from '@/utils/walletKindRows'

const DonutChartStub = {
  props: ['segments', 'size', 'thickness'],
  template: '<div class="donut-chart-stub"><slot /></div>',
}

const investedRows = walletKindRows(
  [
    {
      kind: 'STOCKS',
      holdingCount: 3,
      totalCostBasis: 6000,
      totalCurrentValue: 7500,
      totalGain: 1500,
      totalGainPct: 25,
    },
    {
      kind: 'CRYPTO',
      holdingCount: 1,
      totalCostBasis: 4000,
      totalCurrentValue: 4500,
      totalGain: 500,
      totalGainPct: 12.5,
    },
  ],
  [],
)

function mountCard(totalInvested: number, rows = investedRows) {
  return mount(AllocationCard, {
    props: { rows, totalInvested, currency: 'BRL' },
    global: { stubs: { DonutChart: DonutChartStub } },
  })
}

describe('AllocationCard', () => {
  it('feeds the donut only the kinds with something invested', () => {
    const wrapper = mountCard(10000)

    expect(wrapper.findComponent(DonutChart).props('segments')).toEqual([
      { value: 6000, color: 'var(--wt-stocks)', label: 'Ações' },
      { value: 4000, color: 'var(--wt-crypto)', label: 'Cripto' },
    ])
    expect(wrapper.get('.donut-center-value').text()).toBe('R$ 10,0k')
  })

  it('lists every kind in the legend with its share of the total', () => {
    const wrapper = mountCard(10000)

    expect(wrapper.findAll('.legend-pct').map((cell) => cell.text())).toEqual([
      '60,00%',
      '40,00%',
      '0,00%',
    ])
  })

  it('shows a placeholder ring and zero shares when nothing is invested', () => {
    const wrapper = mountCard(0, walletKindRows([], []))

    expect(wrapper.findComponent(DonutChart).props('segments')).toEqual([
      { value: 1, color: 'var(--chart-grid)', label: '—' },
    ])
    expect(wrapper.findAll('.legend-pct').map((cell) => cell.text())).toEqual(['0%', '0%', '0%'])
  })
})
