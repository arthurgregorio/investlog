import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import PasswordResetModal from './PasswordResetModal.vue'
import { useUsersAdminStore } from '@/stores/usersAdmin'

let activeWrapper: VueWrapper | undefined

function mountModal() {
  activeWrapper = mount(PasswordResetModal, {
    props: { userId: 'user-1', userName: 'Alguém' },
    attachTo: document.body,
  })
  return activeWrapper
}

describe('PasswordResetModal', () => {
  beforeEach(() => {
    createTestingPinia()
  })

  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = undefined
  })

  it('shows the server validation message when the new password is rejected', async () => {
    // Long enough to pass the client-side gate so the (mocked) request is actually sent — the
    // server stays the final authority even when the client thinks a password is fine.
    const store = useUsersAdminStore()
    vi.spyOn(store, 'resetPassword').mockRejectedValue({
      isAxiosError: true,
      response: {
        status: 400,
        data: { errors: ['newPassword deve ter entre 8 e 128 caracteres'] },
      },
    })

    const wrapper = mountModal()
    const passwordInputs = wrapper.findAll('input[type="password"]')
    await passwordInputs[0].setValue('SenhaValidaAgora1')
    await passwordInputs[1].setValue('SenhaValidaAgora1')
    await wrapper.find('.button.is-success').trigger('click')
    await flushPromises()

    expect(document.body.textContent).toContain('newPassword deve ter entre 8 e 128 caracteres')
  })

  it('disables the submit button while the new password is under 8 characters', async () => {
    const store = useUsersAdminStore()
    const resetSpy = vi.spyOn(store, 'resetPassword')

    const wrapper = mountModal()
    const passwordInputs = wrapper.findAll('input[type="password"]')
    await passwordInputs[0].setValue('teste')
    await passwordInputs[1].setValue('teste')

    expect(wrapper.find('.button.is-success').attributes('disabled')).toBeDefined()
    expect(document.body.textContent).toContain('Mínimo de 8 caracteres')
    expect(document.body.textContent).toContain('Ao menos uma letra maiúscula')
    expect(document.body.textContent).toContain('Ao menos um número')
    expect(resetSpy).not.toHaveBeenCalled()
  })

  it('keeps the submit button disabled until the new password meets every requirement', async () => {
    const wrapper = mountModal()
    const passwordInputs = wrapper.findAll('input[type="password"]')

    // Long enough, but missing an uppercase letter and a number.
    await passwordInputs[0].setValue('senhasenha')
    await passwordInputs[1].setValue('senhasenha')
    expect(wrapper.find('.button.is-success').attributes('disabled')).toBeDefined()

    // Uppercase added, still missing a number.
    await passwordInputs[0].setValue('Senhasenha')
    await passwordInputs[1].setValue('Senhasenha')
    expect(wrapper.find('.button.is-success').attributes('disabled')).toBeDefined()

    // All three requirements met.
    await passwordInputs[0].setValue('Senha123')
    await passwordInputs[1].setValue('Senha123')
    expect(wrapper.find('.button.is-success').attributes('disabled')).toBeUndefined()
  })

  it('does not show a field message for a non-validation failure, leaving the toast as the only feedback', async () => {
    const store = useUsersAdminStore()
    vi.spyOn(store, 'resetPassword').mockRejectedValue({
      isAxiosError: true,
      response: { status: 500, data: { detail: 'Erro interno' } },
    })

    const wrapper = mountModal()
    const passwordInputs = wrapper.findAll('input[type="password"]')
    await passwordInputs[0].setValue('SenhaNova123')
    await passwordInputs[1].setValue('SenhaNova123')
    await wrapper.find('.button.is-success').trigger('click')
    await flushPromises()

    expect(document.body.querySelectorAll('.help.is-danger')).toHaveLength(0)
    expect(wrapper.emitted('close')).toBeFalsy()
  })
})

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
