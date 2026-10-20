import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import DateInput from './DateInput.vue'

describe('DateInput', () => {
  it('shows the formatted date for its model value', () => {
    const wrapper = mount(DateInput, {
      props: { modelValue: new Date(2026, 2, 15) },
      attachTo: document.body,
    })

    expect(wrapper.get('input').element.value).toContain('15')
    wrapper.unmount()
  })

  it('emits the picked date when the underlying datepicker changes', async () => {
    const wrapper = mount(DateInput, {
      props: { modelValue: null },
      attachTo: document.body,
    })
    const picked = new Date(2026, 5, 1)

    wrapper.getComponent({ name: 'BDatepicker' }).vm.$emit('update:modelValue', picked)

    expect(wrapper.emitted('update:modelValue')).toEqual([[picked]])
    wrapper.unmount()
  })

  it('reads digits typed without slashes as DDMMYYYY and shows them formatted when the field is left', async () => {
    const wrapper = mount(DateInput, {
      props: {
        modelValue: null,
        'onUpdate:modelValue': (value: Date | null) => wrapper.setProps({ modelValue: value }),
      },
      attachTo: document.body,
    })
    const input = wrapper.get('input')

    await input.setValue('20012026')
    await input.trigger('change')
    await input.trigger('blur')

    expect(wrapper.emitted('update:modelValue')?.slice(-1)).toEqual([[new Date(2026, 0, 20)]])
    expect(input.element.value).toBe('20/01/2026')
    wrapper.unmount()
  })

  it('emits null instead of a rolled-over date for a day the month does not have', async () => {
    const wrapper = mount(DateInput, {
      props: { modelValue: new Date(2026, 2, 15) },
      attachTo: document.body,
    })
    const input = wrapper.get('input')

    await input.setValue('31022026')
    await input.trigger('change')

    expect(wrapper.emitted('update:modelValue')?.slice(-1)).toEqual([[null]])
    wrapper.unmount()
  })

  it('emits null when the date is cleared', async () => {
    const wrapper = mount(DateInput, {
      props: { modelValue: new Date(2026, 2, 15) },
      attachTo: document.body,
    })

    wrapper.getComponent({ name: 'BDatepicker' }).vm.$emit('update:modelValue', null)

    expect(wrapper.emitted('update:modelValue')).toEqual([[null]])
    wrapper.unmount()
  })
})
