import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ClassAllocationDetail from './ClassAllocationDetail.vue'
import DonutChart from '@/components/charts/DonutChart.vue'
import { classAllocation } from '@/test/strategyFixtures'

const DonutChartStub = {
  props: ['segments', 'size', 'thickness', 'spacing'],
  template: '<div class="donut-chart-stub"><slot /></div>',
}

function mountDetail(strategyClass = classAllocation()) {
  return mount(ClassAllocationDetail, {
    props: { strategyClass, currency: 'BRL' },
    global: { stubs: { DonutChart: DonutChartStub } },
  })
}

describe('ClassAllocationDetail', () => {
  it('lists each holding against its target in the class', () => {
    const wrapper = mountDetail()

    expect(wrapper.find('.chart-title').text()).toBe('Ações Brasil')
    expect(wrapper.find('.sub-caption').text()).toContain('meta da classe 60%')
    const rows = wrapper.findAll('[data-testid="allocation-holding-row"]')
    expect(rows[0].text()).toContain('30,00%')
    expect(rows[0].text()).not.toContain('do restante')
    expect(rows[0].find('.tag').text()).toBe('+30,0 pp')
    expect(rows[0].text()).toContain('R$ 150,00 acima')
    expect(rows[1].text()).toContain('do restante')
    expect(rows[1].find('.type-tag').text()).toBe('Ações')
    expect(rows[2].classes()).toContain('is-frozen')
  })

  it('switches to the read-only segment donut', async () => {
    const wrapper = mountDetail()

    await wrapper.find('[data-testid="class-lens-segment"]').trigger('click')

    expect(wrapper.find('.sub-caption').text()).toContain('segmentos não têm meta')
    const segments = wrapper.findComponent(DonutChart).props('segments')
    expect(segments.map((segment: { color: string }) => segment.color)).toEqual([
      'var(--chart-1)',
      'var(--chart-2)',
      'var(--text-muted)',
    ])
    const legend = wrapper.findAll('.legend-row').map((row) => row.text())
    expect(legend[0]).toContain('Energia')
    expect(legend[0]).toContain('1 investimento')
    expect(legend[0]).toContain('60,00%')
    expect(wrapper.text()).toContain('3 ativos')
  })

  it('says so when the class has no holdings, in both lenses', async () => {
    const wrapper = mountDetail(classAllocation({ holdings: [], segments: [] }))

    expect(wrapper.find('[data-testid="class-allocation-empty"]').exists()).toBe(true)
    await wrapper.find('[data-testid="class-lens-segment"]').trigger('click')
    expect(wrapper.find('[data-testid="class-segments"]').text()).toContain(
      'Nenhum investimento nesta classe ainda.',
    )
  })
})
