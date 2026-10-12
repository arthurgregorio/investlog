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

    const googleButton = wrapper.find('[data-testid="google-login"]')
    expect(googleButton.exists()).toBe(true)
    // Must be a real anchor causing a full browser navigation, not a button/click handler —
    // an OAuth2 authorization-code flow requires the browser to actually leave the SPA.
    expect(googleButton.element.tagName).toBe('A')
    expect(googleButton.attributes('href')).toBe('/private/oauth2/authorization/google')
  })

  it('labels the Google button with the multicolor G and the branded text', () => {
    const wrapper = mountStep({ googleAuthEnabled: true })

    const googleButton = wrapper.find('[data-testid="google-login"]')
    expect(googleButton.text()).toBe('Continuar com o Google')
    expect(googleButton.findAll('svg path').map((path) => path.attributes('fill'))).toEqual([
      '#EA4335',
      '#4285F4',
      '#FBBC05',
      '#34A853',
    ])
    expect(googleButton.find('[data-testid="google-spinner"]').exists()).toBe(false)
  })

  it('swaps the G for a spinner and shows "Redirecionando…" once clicked', async () => {
    const wrapper = mountStep({ googleAuthEnabled: true })
    const googleButton = wrapper.find('[data-testid="google-login"]')
    googleButton.element.addEventListener('click', (event) => event.preventDefault())

    await googleButton.trigger('click')

    expect(googleButton.text()).toBe('Redirecionando…')
    expect(googleButton.find('[data-testid="google-spinner"]').exists()).toBe(true)
    expect(googleButton.attributes('href')).toBe('/private/oauth2/authorization/google')
  })

  it('restores the Google button when the page comes back from the back-forward cache', async () => {
    const wrapper = mountStep({ googleAuthEnabled: true })
    const googleButton = wrapper.find('[data-testid="google-login"]')
    googleButton.element.addEventListener('click', (event) => event.preventDefault())
    await googleButton.trigger('click')

    window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: false }))
    await wrapper.vm.$nextTick()
    expect(googleButton.text()).toBe('Redirecionando…')

    window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true }))
    await wrapper.vm.$nextTick()
    expect(googleButton.text()).toBe('Continuar com o Google')

    wrapper.unmount()
  })

  it('separates the Google button from the form with the "ou" divider', () => {
    const wrapper = mountStep({ googleAuthEnabled: true })

    expect(wrapper.find('.auth-divider').text()).toBe('ou')
  })

  it('hides the Google button when Google auth is disabled', () => {
    const wrapper = mountStep()

    expect(wrapper.find('[data-testid="google-login"]').exists()).toBe(false)
    expect(wrapper.find('.auth-divider').exists()).toBe(false)
  })
})
