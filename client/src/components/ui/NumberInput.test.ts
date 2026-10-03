import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import NumberInput from './NumberInput.vue'

describe('NumberInput', () => {
  it('shows the current numeric value', () => {
    const wrapper = mount(NumberInput, { props: { modelValue: 12.5 } })

    expect(wrapper.get('input').element.value).toBe('12.5')
  })

  it('shows an empty field for an empty model value', () => {
    const wrapper = mount(NumberInput, { props: { modelValue: '' } })

    expect(wrapper.get('input').element.value).toBe('')
  })

  it('emits the parsed number when the user types', async () => {
    const wrapper = mount(NumberInput, { props: { modelValue: '' } })

    await wrapper.get('input').setValue('7.25')

    expect(wrapper.emitted('update:modelValue')).toEqual([[7.25]])
  })

  it('emits an empty string rather than NaN when the field is cleared', async () => {
    const wrapper = mount(NumberInput, { props: { modelValue: 3 } })

    await wrapper.get('input').setValue('')

    expect(wrapper.emitted('update:modelValue')).toEqual([['']])
  })

  it('renders the prefix as a static addon before the input', () => {
    const wrapper = mount(NumberInput, { props: { modelValue: '', prefix: 'R$' } })

    expect(wrapper.get('.button.is-static').text()).toBe('R$')
    expect(wrapper.find('input').exists()).toBe(true)
  })

  it('renders no addon without a prefix', () => {
    const wrapper = mount(NumberInput, { props: { modelValue: '' } })

    expect(wrapper.find('.button.is-static').exists()).toBe(false)
  })

  it('emits through the prefixed variant too', async () => {
    const wrapper = mount(NumberInput, { props: { modelValue: '', prefix: 'R$' } })

    await wrapper.get('input').setValue('9')

    expect(wrapper.emitted('update:modelValue')).toEqual([[9]])
  })

  it('forwards the placeholder, step and min to the input', () => {
    const wrapper = mount(NumberInput, {
      props: { modelValue: '', placeholder: '0,00', step: '0.01', min: '0' },
    })

    const input = wrapper.get('input')
    expect(input.attributes('placeholder')).toBe('0,00')
    expect(input.attributes('step')).toBe('0.01')
    expect(input.attributes('min')).toBe('0')
  })
})
