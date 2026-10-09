import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import SegmentedTabs from './SegmentedTabs.vue'

const options = [
  { value: 'all', label: 'Todos', icon: 'view-grid-outline', testId: 'tab-all' },
  { value: 'stocks', label: 'Ações', testId: 'tab-stocks' },
]

function mountTabs(modelValue = 'all') {
  const emittedValues: string[] = []
  const wrapper = mount(SegmentedTabs, {
    props: {
      modelValue,
      options,
      listLabel: 'Tipo de investimento',
      'onUpdate:modelValue': (value: string) => emittedValues.push(value),
    },
  })
  return { wrapper, emittedValues }
}

describe('SegmentedTabs', () => {
  it('renders a labelled tab list with one tab per option', () => {
    const { wrapper } = mountTabs()

    expect(wrapper.get('[role="tablist"]').attributes('aria-label')).toBe('Tipo de investimento')
    expect(wrapper.findAll('[role="tab"]').map((tab) => tab.text())).toEqual(['Todos', 'Ações'])
  })

  it('selects only the tab matching the model value', () => {
    const { wrapper } = mountTabs('stocks')

    expect(wrapper.get('[data-testid="tab-all"]').attributes('aria-selected')).toBe('false')
    expect(wrapper.get('[data-testid="tab-stocks"]').attributes('aria-selected')).toBe('true')
    expect(wrapper.get('[data-testid="tab-stocks"]').classes()).toContain('is-active')
  })

  it('shows an icon only for options that define one', () => {
    const { wrapper } = mountTabs()

    expect(wrapper.find('[data-testid="tab-all"] .mdi-view-grid-outline').exists()).toBe(true)
    expect(wrapper.find('[data-testid="tab-stocks"] .icon').exists()).toBe(false)
  })

  it('emits the clicked tab value', async () => {
    const { wrapper, emittedValues } = mountTabs()

    await wrapper.get('[data-testid="tab-stocks"]').trigger('click')

    expect(emittedValues).toEqual(['stocks'])
  })
})
