import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import TotpVerifyStep from './TotpVerifyStep.vue'

describe('TotpVerifyStep', () => {
  it('submits the code as soon as the sixth digit is entered', async () => {
    const wrapper = mount(TotpVerifyStep, { props: { submitting: false } })

    await wrapper.find('input[maxlength="6"]').setValue('65432')
    expect(wrapper.emitted('submit')).toBeUndefined()

    await wrapper.find('input[maxlength="6"]').setValue('654321')

    expect(wrapper.emitted('submit')).toEqual([['654321', false]])
  })

  it('emits the code with trustDevice false when the checkbox is left unchecked', async () => {
    const wrapper = mount(TotpVerifyStep, { props: { submitting: false } })

    await wrapper.find('input[maxlength="6"]').setValue('654321')
    await wrapper.find('form').trigger('submit.prevent')

    expect(wrapper.emitted('submit')?.slice(-1)[0]).toEqual(['654321', false])
  })

  it('emits trustDevice true when the checkbox is checked', async () => {
    const wrapper = mount(TotpVerifyStep, { props: { submitting: false } })

    await wrapper.find('input[type="checkbox"]').setValue(true)
    await wrapper.find('input[maxlength="6"]').setValue('654321')

    expect(wrapper.emitted('submit')?.slice(-1)[0]).toEqual(['654321', true])
  })

  it('shows "Verificando…" and disables the code while submitting', () => {
    const wrapper = mount(TotpVerifyStep, { props: { submitting: true } })

    expect(wrapper.find('button[type="submit"]').text()).toBe('Verificando…')
    expect(wrapper.find('input[maxlength="6"]').attributes('disabled')).toBeDefined()
  })

  it('shows the boxes in the error state when the code was rejected', () => {
    const wrapper = mount(TotpVerifyStep, { props: { submitting: false, invalid: true } })

    expect(wrapper.find('.totp-code').classes()).toContain('is-invalid')
    expect(wrapper.find('button[type="submit"]').text()).toBe('Entrar')
  })

  it('does not offer the QR code that belongs to the enrollment step', () => {
    const wrapper = mount(TotpVerifyStep, { props: { submitting: false } })

    expect(wrapper.find('img.auth-totp-qr').exists()).toBe(false)
  })
})
