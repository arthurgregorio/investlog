import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import RegisterStep from './RegisterStep.vue'

function mountStep(props: Partial<InstanceType<typeof RegisterStep>['$props']> = {}) {
  return mount(RegisterStep, {
    props: {
      email: '',
      password: '',
      submitting: false,
      ...props,
    },
  })
}

describe('RegisterStep', () => {
  it('emits the typed name on submit and the e-mail and password through the models', async () => {
    const wrapper = mountStep()

    await wrapper.find('input[type="text"]').setValue('Nova Usuária')
    await wrapper.find('input[type="email"]').setValue('nova@example.com')
    await wrapper.find('input[type="password"]').setValue('Senha123')
    await wrapper.find('form').trigger('submit.prevent')

    expect(wrapper.emitted('update:email')?.slice(-1)[0]).toEqual(['nova@example.com'])
    expect(wrapper.emitted('update:password')?.slice(-1)[0]).toEqual(['Senha123'])
    expect(wrapper.emitted('submit')?.slice(-1)[0]).toEqual(['Nova Usuária'])
  })

  it('disables the submit button until the password meets every requirement, and shows the hint', async () => {
    const wrapper = mountStep()

    await wrapper.find('input[type="text"]').setValue('Nova Usuária')
    await wrapper.find('input[type="email"]').setValue('nova@example.com')

    await wrapper.find('input[type="password"]').setValue('teste')
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Mínimo de 8 caracteres')
    expect(wrapper.text()).toContain('Ao menos uma letra maiúscula')
    expect(wrapper.text()).toContain('Ao menos um número')

    // Long enough now, but still missing an uppercase letter and a number.
    await wrapper.find('input[type="password"]').setValue('testeteste')
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()

    // Uppercase added, still missing a number.
    await wrapper.find('input[type="password"]').setValue('Testeteste')
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()

    // All three requirements met.
    await wrapper.find('input[type="password"]').setValue('Senha123')
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeUndefined()
  })

  it('emits toggleRegister when the back-to-login link is clicked', async () => {
    const wrapper = mountStep()

    await wrapper.find('[data-testid="toggle-register"]').trigger('click')

    expect(wrapper.emitted('toggleRegister')).toHaveLength(1)
  })
})
