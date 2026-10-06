import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import SegmentedControl from './SegmentedControl.vue'

const options = [
  { value: 'currentValue', label: 'Valor atual', testId: 'metric-current' },
  { value: 'costBasis', label: 'Investido', testId: 'metric-cost' },
]

const emittedValues: string[] = []

function mountControl(modelValue = 'currentValue') {
  return mount(SegmentedControl, {
    props: {
      modelValue,
      options,
      groupLabel: 'Métrica',
      'onUpdate:modelValue': (value: string) => emittedValues.push(value),
    },
  })
}

describe('SegmentedControl', () => {
  it('renders one labelled button per option inside a named group', () => {
    const wrapper = mountControl()

    expect(wrapper.find('[role="group"]').attributes('aria-label')).toBe('Métrica')
    expect(wrapper.findAll('button').map((button) => button.text())).toEqual([
      'Valor atual',
      'Investido',
    ])
    expect(wrapper.find('[data-testid="metric-cost"]').exists()).toBe(true)
  })

  it('marks only the option matching the model value as pressed and active', () => {
    const wrapper = mountControl('costBasis')

    const [current, cost] = wrapper.findAll('button')
    expect(current.attributes('aria-pressed')).toBe('false')
    expect(current.classes()).not.toContain('is-active')
    expect(cost.attributes('aria-pressed')).toBe('true')
    expect(cost.classes()).toContain('is-active')
  })

  it('emits the clicked option value', async () => {
    emittedValues.length = 0
    const wrapper = mountControl()

    await wrapper.find('[data-testid="metric-cost"]').trigger('click')

    expect(emittedValues).toEqual(['costBasis'])
  })
})
