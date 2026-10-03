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
