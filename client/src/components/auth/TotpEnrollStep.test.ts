import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import TotpEnrollStep from './TotpEnrollStep.vue'

describe('TotpEnrollStep', () => {
  it('renders the enrollment QR code it is given', () => {
    const wrapper = mount(TotpEnrollStep, {
      props: { submitting: false, qrCodeDataUri: 'data:image/png;base64,abc' },
    })

    const qrImage = wrapper.find('img.auth-totp-qr')
    expect(qrImage.exists()).toBe(true)
    expect(qrImage.attributes('src')).toBe('data:image/png;base64,abc')
  })

  it('emits the typed code on submit', async () => {
    const wrapper = mount(TotpEnrollStep, {
      props: { submitting: false, qrCodeDataUri: 'data:image/png;base64,abc' },
    })

    await wrapper.find('input[maxlength="6"]').setValue('123456')
    await wrapper.find('form').trigger('submit.prevent')

    expect(wrapper.emitted('submit')?.slice(-1)[0]).toEqual(['123456'])
  })
})
