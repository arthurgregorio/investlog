import { describe, expect, it, vi } from 'vitest'
import { mount, type DOMWrapper } from '@vue/test-utils'
import NumberInput from './NumberInput.vue'

type InputWrapper = Omit<DOMWrapper<HTMLInputElement>, 'exists'>

async function typeInto(input: InputWrapper, text: string) {
  input.element.value = text
  await input.trigger('input')
}

async function leave(input: InputWrapper) {
  await input.trigger('change')
  await input.trigger('blur')
}

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

  describe('money (with a prefix)', () => {
    it('shows a prefilled value with two decimals', () => {
      const wrapper = mount(NumberInput, { props: { modelValue: 26.6, prefix: 'R$' } })

      expect(wrapper.get('input').element.value).toBe('26.60')
    })

    it('pads a whole prefilled value to two decimals', () => {
      const wrapper = mount(NumberInput, { props: { modelValue: 26, prefix: 'R$' } })

      expect(wrapper.get('input').element.value).toBe('26.00')
    })

    it('never truncates extra decimals', () => {
      const wrapper = mount(NumberInput, { props: { modelValue: 0.000123, prefix: 'R$' } })

      expect(wrapper.get('input').element.value).toBe('0.000123')
    })

    it('keeps what the user types while typing', async () => {
      const wrapper = mount(NumberInput, { props: { modelValue: '', prefix: 'R$' } })
      const input = wrapper.get('input')

      await typeInto(input, '26.60')
      await wrapper.setProps({ modelValue: 26.6 })

      expect(input.element.value).toBe('26.60')
      expect(wrapper.emitted('update:modelValue')).toEqual([[26.6]])
    })

    it('keeps the typed decimals after leaving the field', async () => {
      const wrapper = mount(NumberInput, { props: { modelValue: '', prefix: 'R$' } })
      const input = wrapper.get('input')

      await typeInto(input, '26.60')
      await wrapper.setProps({ modelValue: 26.6 })
      await leave(input)

      expect(input.element.value).toBe('26.60')
    })

    it('does not pad a value while it is being typed', async () => {
      const wrapper = mount(NumberInput, { props: { modelValue: '', prefix: 'R$' } })
      const input = wrapper.get('input')

      await typeInto(input, '26')
      await wrapper.setProps({ modelValue: 26 })

      expect(input.element.value).toBe('26')
    })

    it('pads to two decimals on blur', async () => {
      const wrapper = mount(NumberInput, { props: { modelValue: '', prefix: 'R$' } })
      const input = wrapper.get('input')

      await typeInto(input, '26')
      await wrapper.setProps({ modelValue: 26 })
      await leave(input)

      expect(input.element.value).toBe('26.00')
    })

    it('keeps a small price as typed on blur', async () => {
      const wrapper = mount(NumberInput, { props: { modelValue: '', prefix: 'R$' } })
      const input = wrapper.get('input')

      await typeInto(input, '0.000123')
      await leave(input)

      expect(input.element.value).toBe('0.000123')
    })

    it('leaves an empty field empty on blur', async () => {
      const wrapper = mount(NumberInput, { props: { modelValue: '', prefix: 'R$' } })
      const input = wrapper.get('input')

      await input.trigger('blur')

      expect(input.element.value).toBe('')
    })

    it('re-syncs and pads when the value is set from outside', async () => {
      const wrapper = mount(NumberInput, { props: { modelValue: '', prefix: 'R$' } })
      const input = wrapper.get('input')

      await typeInto(input, '10')
      await wrapper.setProps({ modelValue: 42.5 })

      expect(input.element.value).toBe('42.50')
    })

    it('clears the field when the value is reset from outside', async () => {
      const wrapper = mount(NumberInput, { props: { modelValue: 12, prefix: 'R$' } })

      await wrapper.setProps({ modelValue: '' })

      expect(wrapper.get('input').element.value).toBe('')
    })

    it('still notifies a caller listening for blur', async () => {
      const onBlur = vi.fn()
      const wrapper = mount(NumberInput, {
        props: { modelValue: 5, prefix: 'R$' },
        attrs: { onBlur },
      })

      await wrapper.get('input').trigger('blur')

      expect(onBlur).toHaveBeenCalled()
    })
  })

  describe('quantity (without a prefix)', () => {
    it('does not pad a prefilled value', () => {
      const wrapper = mount(NumberInput, { props: { modelValue: 26.6 } })

      expect(wrapper.get('input').element.value).toBe('26.6')
    })

    it('does not pad on blur', async () => {
      const wrapper = mount(NumberInput, { props: { modelValue: '' } })
      const input = wrapper.get('input')

      await typeInto(input, '26')
      await leave(input)

      expect(input.element.value).toBe('26')
    })

    it('re-syncs when the value is set from outside', async () => {
      const wrapper = mount(NumberInput, { props: { modelValue: 3 } })

      await wrapper.setProps({ modelValue: 7 })

      expect(wrapper.get('input').element.value).toBe('7')
    })
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
