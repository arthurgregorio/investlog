import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ClassAllocationTable from './ClassAllocationTable.vue'
import { strategyAllocation } from '@/test/strategyFixtures'

function mountTable(allocation = strategyAllocation(), expandedClassId: string | null = null) {
  const wrapper = mount(ClassAllocationTable, {
    props: {
      allocation,
      expandedClassId,
      'onUpdate:expandedClassId': (value: string | null) =>
        wrapper.setProps({ expandedClassId: value }),
    },
  })
  return wrapper
}

describe('ClassAllocationTable', () => {
  it('shows each class against its target, with the drift tag and money to target', () => {
    const wrapper = mountTable()

    const rows = wrapper.findAll('[data-testid="allocation-class-row"]')
    expect(rows[0].text()).toContain('3 ativos · 1 congelado')
    expect(rows[0].text()).toContain('60%')
    expect(rows[0].text()).toContain('50,00%')
    expect(rows[0].find('.tag').text()).toBe('−10,0 pp')
    expect(rows[0].find('.tag').classes()).toContain('is-info')
    expect(rows[0].text()).toContain('R$ 100,00 abaixo')
    expect(rows[1].text()).toContain('1 ativo')
    expect(rows[1].find('.tag').classes()).toContain('is-warning')
    expect(rows[1].text()).toContain('R$ 100,00 acima')
    expect(wrapper.find('[data-testid="allocation-total-row"]').text()).toContain('R$ 1.000,00')
  })

  it('expands and collapses a class from its name or its chevron', async () => {
    const wrapper = mountTable()

    await wrapper.findAll('[data-testid="allocation-class-row"] .open-holding')[1].trigger('click')
    expect(wrapper.emitted('update:expandedClassId')).toEqual([['class-abroad']])
    expect(wrapper.findAll('[data-testid="allocation-class-row"]')[1].classes()).toContain(
      'is-current',
    )

    await wrapper.findAll('[data-testid="expand-allocation-class"]')[1].trigger('click')
    expect(wrapper.emitted('update:expandedClassId')?.[1]).toEqual([null])
  })

  it('points to the classes tab when the strategy has no class', () => {
    const wrapper = mountTable(strategyAllocation({ classes: [] }))

    expect(wrapper.find('[data-testid="allocation-no-classes"]').exists()).toBe(true)
  })

  it('counts several frozen holdings in a class', () => {
    const allocation = strategyAllocation()
    allocation.classes[0].holdings.forEach((holding) => (holding.frozen = true))
    const wrapper = mountTable(allocation)

    expect(wrapper.findAll('[data-testid="allocation-class-row"]')[0].text()).toContain(
      '3 ativos · 3 congelados',
    )
  })
})
