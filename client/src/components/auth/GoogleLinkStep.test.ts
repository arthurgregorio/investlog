import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import GoogleLinkStep from './GoogleLinkStep.vue'

describe('GoogleLinkStep', () => {
  it('emits the typed password on submit', async () => {
    const wrapper = mount(GoogleLinkStep, { props: { submitting: false } })

    await wrapper.find('input[type="password"]').setValue('senha123')
    await wrapper.find('form').trigger('submit.prevent')

    expect(wrapper.emitted('submit')?.slice(-1)[0]).toEqual(['senha123'])
  })

  it('collects only a password, never an e-mail — the account is already known', () => {
    const wrapper = mount(GoogleLinkStep, { props: { submitting: false } })

    expect(wrapper.find('input[type="password"]').exists()).toBe(true)
    expect(wrapper.find('input[type="email"]').exists()).toBe(false)
  })
})
