import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import TotpEnrollStep from './TotpEnrollStep.vue'

function mountStep(props: { submitting?: boolean; invalid?: boolean } = {}) {
  return mount(TotpEnrollStep, {
    props: { submitting: false, qrCodeDataUri: 'data:image/png;base64,abc', ...props },
  })
}

describe('TotpEnrollStep', () => {
  it('renders the enrollment QR code it is given', () => {
    const wrapper = mountStep()

    const qrImage = wrapper.find('img.auth-totp-qr')
    expect(qrImage.exists()).toBe(true)
    expect(qrImage.attributes('src')).toBe('data:image/png;base64,abc')
  })

  it('submits the code as soon as the sixth digit is entered', async () => {
    const wrapper = mountStep()

    await wrapper.find('input[maxlength="6"]').setValue('123456')

    expect(wrapper.emitted('submit')).toEqual([['123456']])
  })

  it('emits the typed code when the button is clicked', async () => {
    const wrapper = mountStep()

    await wrapper.find('input[maxlength="6"]').setValue('123456')
    await wrapper.find('form').trigger('submit.prevent')

    expect(wrapper.emitted('submit')?.slice(-1)[0]).toEqual(['123456'])
  })

  it('shows "Verificando…" and disables the code while submitting', () => {
    const wrapper = mountStep({ submitting: true })

    expect(wrapper.find('button[type="submit"]').text()).toBe('Verificando…')
    expect(wrapper.find('input[maxlength="6"]').attributes('disabled')).toBeDefined()
  })

  it('shows the boxes in the error state when the code was rejected', () => {
    const wrapper = mountStep({ invalid: true })

    expect(wrapper.find('.totp-code').classes()).toContain('is-invalid')
    expect(wrapper.find('button[type="submit"]').text()).toBe('Confirmar')
  })
})
