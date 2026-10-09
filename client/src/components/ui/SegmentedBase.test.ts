import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import SegmentedBase from './SegmentedBase.vue'

const options = [
  { value: 'first', label: 'Primeiro', testId: 'option-first' },
  { value: 'second', label: 'Segundo', testId: 'option-second' },
]

function mountBase(props: Record<string, unknown> = {}, slots = {}) {
  const emittedValues: string[] = []
  const wrapper = mount(SegmentedBase, {
    props: {
      modelValue: 'first',
      options,
      selectionAttribute: 'aria-pressed',
      'onUpdate:modelValue': (value: string) => emittedValues.push(value),
      ...props,
    },
    slots,
  })
  return { wrapper, emittedValues }
}

describe('SegmentedBase', () => {
  it('renders each option label inside its own button', () => {
    const { wrapper } = mountBase()

    expect(wrapper.findAll('button').map((button) => button.text())).toEqual([
      'Primeiro',
      'Segundo',
    ])
    expect(wrapper.find('[data-testid="option-second"]').exists()).toBe(true)
  })

  it('marks the selected option through the requested selection attribute', () => {
    const { wrapper } = mountBase({ modelValue: 'second', selectionAttribute: 'aria-selected' })

    const [first, second] = wrapper.findAll('button')
    expect(first.attributes('aria-selected')).toBe('false')
    expect(second.attributes('aria-selected')).toBe('true')
    expect(second.attributes('aria-pressed')).toBeUndefined()
    expect(second.classes()).toContain('is-active')
    expect(first.classes()).not.toContain('is-active')
  })

  it('gives every option the requested role', () => {
    const { wrapper } = mountBase({ optionRole: 'tab' })

    expect(wrapper.findAll('[role="tab"]')).toHaveLength(2)
  })

  it('renders custom option content from the option slot', () => {
    const { wrapper } = mountBase(
      {},
      { option: ({ option }: { option: { label: string } }) => `→ ${option.label}` },
    )

    expect(wrapper.get('[data-testid="option-first"]').text()).toBe('→ Primeiro')
  })

  it('emits the clicked option value', async () => {
    const { wrapper, emittedValues } = mountBase()

    await wrapper.get('[data-testid="option-second"]').trigger('click')

    expect(emittedValues).toEqual(['second'])
  })
})
