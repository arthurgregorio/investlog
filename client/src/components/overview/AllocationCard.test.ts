import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import AllocationCard from './AllocationCard.vue'
import DonutChart from '@/components/charts/DonutChart.vue'
import { overviewApi } from '@/api/overview'
import { useStrategiesStore } from '@/stores/strategies'
import { defaultStrategy, strategy } from '@/test/strategyFixtures'
import type { AllocationGroupBy, OverviewAllocation, Strategy } from '@/types'

vi.mock('@/api/overview', () => ({ overviewApi: { getAllocation: vi.fn() } }))

const DonutChartStub = {
  props: ['segments', 'size', 'thickness'],
  template: '<div class="donut-chart-stub"><slot /></div>',
}

const RESPONSES: Record<AllocationGroupBy, OverviewAllocation> = {
  STRATEGY: {
    groupBy: 'STRATEGY',
    displayCurrency: 'BRL',
    totalValue: 1000,
    groups: [
      { id: 'strategy-long', label: 'Longo prazo', holdingCount: 4, value: 800, percentage: 80 },
      {
        id: 'strategy-default',
        label: 'Sem estratégia',
        holdingCount: 2,
        value: 200,
        percentage: 20,
      },
    ],
  },
  CLASS: {
    groupBy: 'CLASS',
    displayCurrency: 'BRL',
    totalValue: 800,
    groups: [
      { id: 'class-brazil', label: 'Ações Brasil', holdingCount: 3, value: 600, percentage: 75 },
      { id: 'class-abroad', label: 'Exterior', holdingCount: 1, value: 200, percentage: 25 },
    ],
  },
  TYPE: {
    groupBy: 'TYPE',
    displayCurrency: 'BRL',
    totalValue: 1000,
    groups: [
      { id: null, label: 'Ações', holdingCount: 5, value: 900, percentage: 90 },
      { id: null, label: 'Sem tipo', holdingCount: 1, value: 100, percentage: 10 },
    ],
  },
}

async function mountCard(strategies: Strategy[] = [strategy(), defaultStrategy()]) {
  const wrapper = mount(AllocationCard, {
    props: { currency: 'BRL' },
    global: {
      plugins: [createTestingPinia({ initialState: { strategies: { strategies } } })],
      stubs: { DonutChart: DonutChartStub },
    },
  })
  await flushPromises()
  return wrapper
}

function legendRows(wrapper: Awaited<ReturnType<typeof mountCard>>) {
  return wrapper.findAll('.legend-row').map((row) => row.text())
}

function segmentColors(wrapper: Awaited<ReturnType<typeof mountCard>>) {
  return wrapper
    .findComponent(DonutChart)
    .props('segments')
    .map((segment: { color: string }) => segment.color)
}

beforeEach(() => {
  vi.mocked(overviewApi.getAllocation).mockImplementation(async (groupBy) => RESPONSES[groupBy])
})

describe('AllocationCard', () => {
  it('groups current value by strategy, with Sem estratégia in the neutral colour', async () => {
    const wrapper = await mountCard()

    expect(overviewApi.getAllocation).toHaveBeenCalledWith('STRATEGY', undefined)
    expect(wrapper.find('.chart-title').text()).toBe('Alocação')
    expect(wrapper.find('.donut-center-label').text()).toBe('Valor atual')
    expect(wrapper.find('.donut-center-value').text()).toBe('R$ 1,0k')
    expect(legendRows(wrapper)[0]).toContain('Longo prazo')
    expect(legendRows(wrapper)[0]).toContain('80,00%')
    expect(segmentColors(wrapper)).toEqual(['var(--chart-1)', 'var(--text-muted)'])
  })

  it('groups by the classes of the chosen strategy', async () => {
    const wrapper = await mountCard([
      strategy(),
      strategy({ id: 'strategy-retirement', name: 'Aposentadoria' }),
      defaultStrategy(),
    ])

    await wrapper.find('[data-testid="allocation-by-class"]').trigger('click')
    await flushPromises()

    expect(overviewApi.getAllocation).toHaveBeenLastCalledWith('CLASS', 'strategy-long')
    expect(wrapper.find('.donut-center-label').text()).toBe('Longo prazo')
    const select = wrapper.find('select[data-testid="allocation-strategy"]')
    expect(select.findAll('option').map((option) => option.text())).toEqual([
      'Longo prazo',
      'Aposentadoria',
    ])

    await select.setValue('strategy-retirement')
    await flushPromises()
    expect(overviewApi.getAllocation).toHaveBeenLastCalledWith('CLASS', 'strategy-retirement')
  })

  it('groups by holding type, with Sem tipo in the neutral colour', async () => {
    const wrapper = await mountCard()

    await wrapper.find('[data-testid="allocation-by-type"]').trigger('click')
    await flushPromises()

    expect(overviewApi.getAllocation).toHaveBeenLastCalledWith('TYPE', undefined)
    expect(segmentColors(wrapper)).toEqual(['var(--chart-1)', 'var(--text-muted)'])
  })

  it('asks for a strategy before grouping by class when there is none', async () => {
    const wrapper = await mountCard([defaultStrategy()])
    vi.mocked(overviewApi.getAllocation).mockClear()

    await wrapper.find('[data-testid="allocation-by-class"]').trigger('click')
    await flushPromises()

    expect(overviewApi.getAllocation).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="allocation-no-strategies"]').exists()).toBe(true)
  })

  it('shows a placeholder ring while nothing is allocated', async () => {
    vi.mocked(overviewApi.getAllocation).mockResolvedValue({
      ...RESPONSES.STRATEGY,
      totalValue: 0,
      groups: [],
    })
    const wrapper = await mountCard()

    const segments = wrapper.findComponent(DonutChart).props('segments')
    expect(segments).toEqual([{ value: 1, color: 'var(--chart-grid)', label: '—' }])
    expect(legendRows(wrapper)).toEqual([])
  })

  it('moves to the first remaining strategy when the chosen one goes away', async () => {
    const wrapper = await mountCard([
      strategy(),
      strategy({ id: 'strategy-retirement', name: 'Aposentadoria' }),
      defaultStrategy(),
    ])
    await wrapper.find('[data-testid="allocation-by-class"]').trigger('click')
    await flushPromises()

    useStrategiesStore().strategies = [
      strategy({ id: 'strategy-retirement', name: 'Aposentadoria' }),
      defaultStrategy(),
    ]
    await flushPromises()

    expect(overviewApi.getAllocation).toHaveBeenLastCalledWith('CLASS', 'strategy-retirement')
  })
})
