import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AllocationSummary from './AllocationSummary.vue'
import { classAllocation, strategyAllocation } from '@/test/strategyFixtures'

describe('AllocationSummary', () => {
  it('shows the unassigned holdings and the three KPIs', async () => {
    const wrapper = mount(AllocationSummary, { props: { allocation: strategyAllocation() } })

    expect(wrapper.find('[data-testid="unassigned-notice"]').text()).toContain(
      '2 investimentos ainda estão em Sem estratégia · Não definida · R$ 250,00',
    )
    expect(wrapper.find('[data-testid="kpi-strategy-value"]').text()).toBe('R$ 1,0k')
    expect(wrapper.text()).toContain('4 investimentos em 2 classes')
    expect(wrapper.find('[data-testid="kpi-largest-drift"]').text()).toBe('−10,0 pp')
    expect(wrapper.text()).toContain('Ações Brasil · R$ 100,00 abaixo da meta')
    expect(wrapper.find('[data-testid="kpi-frozen"]').text()).toBe('1 investimento')
    expect(wrapper.text()).toContain('contam no desvio, fora do planejador')

    await wrapper.find('[data-testid="notice-distribute"]').trigger('click')
    expect(wrapper.emitted('distribute')).toHaveLength(1)
  })

  it('hides the notice and says Nenhum when nothing is unassigned or frozen', () => {
    const wrapper = mount(AllocationSummary, {
      props: {
        allocation: strategyAllocation({
          unassignedHoldingCount: 0,
          classes: [classAllocation({ holdings: [] })],
        }),
      },
    })

    expect(wrapper.find('[data-testid="unassigned-notice"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="kpi-frozen"]').text()).toBe('Nenhum')
    expect(wrapper.text()).toContain('0 investimentos em 1 classe')
  })

  it('names the one unassigned holding and shows a dash without classes', () => {
    const wrapper = mount(AllocationSummary, {
      props: { allocation: strategyAllocation({ unassignedHoldingCount: 1, classes: [] }) },
    })

    expect(wrapper.find('[data-testid="unassigned-notice"]').text()).toContain(
      '1 investimento ainda está em Sem estratégia',
    )
    expect(wrapper.find('[data-testid="kpi-largest-drift"]').text()).toBe('—')
  })
})
