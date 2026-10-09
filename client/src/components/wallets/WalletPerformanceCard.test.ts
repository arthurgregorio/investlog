import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import WalletPerformanceCard from './WalletPerformanceCard.vue'
import AreaChart from '@/components/charts/AreaChart.vue'
import { walletDetailOf } from '@/test/walletDetailFixture'
import type { WalletDetail } from '@/types'

const AreaChartStub = {
  props: ['data', 'xLabels', 'fmtY', 'color', 'height'],
  template: '<div class="area-chart-stub" />',
}

function mountCard(detail: WalletDetail) {
  return mount(WalletPerformanceCard, {
    props: { detail },
    global: { stubs: { AreaChart: AreaChartStub } },
  })
}

const withHistory = walletDetailOf({
  currency: 'USD',
  series: [
    { snapshotDate: '2026-09-18', currentValue: 4500, totalInvested: 4500, gain: 0, gainPct: 0 },
    {
      snapshotDate: '2026-09-19',
      currentValue: 4750,
      totalInvested: 4500,
      gain: 250,
      gainPct: 5.5,
    },
  ],
  dayChange: 10,
  weekChange: -20,
  monthChange: null,
})

describe('WalletPerformanceCard', () => {
  it('feeds the chart the current value per snapshot, labelled by date', () => {
    const chart = mountCard(withHistory).findComponent(AreaChart)

    expect(chart.props('data')).toEqual([4500, 4750])
    expect(chart.props('xLabels')).toEqual(['18 set 2026', '19 set 2026'])
  })

  it('labels the axis compactly in the wallet currency', () => {
    const fmtY = mountCard(withHistory).findComponent(AreaChart).props('fmtY')!

    expect(fmtY(4750)).toBe('US$ 4,8k')
  })

  it('shows one labelled delta chip per period', () => {
    const deltas = mountCard(withHistory).get('[data-testid="wallet-deltas"]')

    expect(deltas.text()).toContain('1 dia')
    expect(deltas.text()).toContain('7 dias')
    expect(deltas.text()).toContain('30 dias')
    expect(deltas.find('.gl-empty').exists()).toBe(true)
  })

  it('shows an empty state with no chart and no deltas before the first snapshot', () => {
    const wrapper = mountCard(walletDetailOf())

    expect(wrapper.text()).toContain('Ainda sem histórico')
    expect(wrapper.findComponent(AreaChart).exists()).toBe(false)
    expect(wrapper.find('[data-testid="wallet-deltas"]').exists()).toBe(false)
  })
})
