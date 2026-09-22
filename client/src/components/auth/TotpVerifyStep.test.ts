import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import TotpVerifyStep from './TotpVerifyStep.vue'

describe('TotpVerifyStep', () => {
  it('emits the code with trustDevice false when the checkbox is left unchecked', async () => {
    const wrapper = mount(TotpVerifyStep, { props: { submitting: false } })

    await wrapper.find('input[maxlength="6"]').setValue('654321')
    await wrapper.find('form').trigger('submit.prevent')

    expect(wrapper.emitted('submit')?.slice(-1)[0]).toEqual(['654321', false])
  })

  it('emits trustDevice true when the checkbox is checked', async () => {
    const wrapper = mount(TotpVerifyStep, { props: { submitting: false } })

    await wrapper.find('input[maxlength="6"]').setValue('654321')
    await wrapper.find('input[type="checkbox"]').setValue(true)
    await wrapper.find('form').trigger('submit.prevent')

    expect(wrapper.emitted('submit')?.slice(-1)[0]).toEqual(['654321', true])
  })

  it('does not offer the QR code that belongs to the enrollment step', () => {
    const wrapper = mount(TotpVerifyStep, { props: { submitting: false } })

    expect(wrapper.find('img.auth-totp-qr').exists()).toBe(false)
  })
})
