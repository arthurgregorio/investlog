import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import CredentialsStep from './CredentialsStep.vue'

function mountStep(props: Partial<InstanceType<typeof CredentialsStep>['$props']> = {}) {
  return mount(CredentialsStep, {
    props: {
      email: '',
      password: '',
      submitting: false,
      googleAuthEnabled: false,
      ...props,
    },
  })
}

describe('CredentialsStep', () => {
  it('emits the typed e-mail and password through the models before submitting', async () => {
    const wrapper = mountStep()

    await wrapper.find('input[type="email"]').setValue('admin@admin.com')
    await wrapper.find('input[type="password"]').setValue('admin')
    await wrapper.find('form').trigger('submit.prevent')

    expect(wrapper.emitted('update:email')?.slice(-1)[0]).toEqual(['admin@admin.com'])
    expect(wrapper.emitted('update:password')?.slice(-1)[0]).toEqual(['admin'])
    expect(wrapper.emitted('submit')).toHaveLength(1)
  })

  it('emits toggleRegister when the register link is clicked', async () => {
    const wrapper = mountStep()

    await wrapper.find('[data-testid="toggle-register"]').trigger('click')

    expect(wrapper.emitted('toggleRegister')).toHaveLength(1)
  })

  it('shows the Google button as a real anchor when Google auth is enabled', () => {
    const wrapper = mountStep({ googleAuthEnabled: true })

    const googleButton = wrapper.find('.auth-google-button')
    expect(googleButton.exists()).toBe(true)
    // Must be a real anchor causing a full browser navigation, not a button/click handler —
    // an OAuth2 authorization-code flow requires the browser to actually leave the SPA.
    expect(googleButton.element.tagName).toBe('A')
    expect(googleButton.attributes('href')).toBe('/private/oauth2/authorization/google')
  })

  it('hides the Google button when Google auth is disabled', () => {
    const wrapper = mountStep()

    expect(wrapper.find('.auth-google-button').exists()).toBe(false)
  })
})
