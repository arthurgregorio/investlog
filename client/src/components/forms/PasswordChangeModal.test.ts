import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import Buefy from 'buefy'
import PasswordChangeModal from './PasswordChangeModal.vue'
import { profileApi } from '@/api/profile'

vi.mock('@/api/profile', () => ({
  profileApi: {
    changePassword: vi.fn(),
  },
}))

let activeWrapper: VueWrapper | undefined

function mountModal() {
  activeWrapper = mount(PasswordChangeModal, {
    global: { plugins: [Buefy] },
    attachTo: document.body,
  })
  return activeWrapper
}

describe('PasswordChangeModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = undefined
  })

  it('shows the server validation message under the new-password field, not the current-password field', async () => {
    // A password long enough to pass the client-side gate, so the (mocked) request is actually
    // sent — this covers the server staying the final authority even when the client thinks a
    // password is fine (e.g. a rule the client doesn't mirror, like the max-length cap).
    vi.mocked(profileApi.changePassword).mockRejectedValue({
      isAxiosError: true,
      response: {
        status: 400,
        data: { errors: ['newPassword deve ter entre 8 e 128 caracteres'] },
      },
    })

    const wrapper = mountModal()
    const passwordInputs = wrapper.findAll('input[type="password"]')
    await passwordInputs[0].setValue('senhaAtual123')
    await passwordInputs[1].setValue('SenhaValidaAgora1')
    await passwordInputs[2].setValue('SenhaValidaAgora1')
    await wrapper.find('.button.is-success').trigger('click')
    await flushPromises()

    expect(document.body.textContent).toContain('newPassword deve ter entre 8 e 128 caracteres')
    expect(document.body.textContent).not.toContain('Senha atual incorreta')
  })

  it('disables the submit button and shows the requirement hint as unmet while the new password is under 8 characters', async () => {
    const wrapper = mountModal()
    const passwordInputs = wrapper.findAll('input[type="password"]')
    await passwordInputs[0].setValue('senhaAtual123')
    await passwordInputs[1].setValue('teste')
    await passwordInputs[2].setValue('teste')

    expect(wrapper.find('.button.is-success').attributes('disabled')).toBeDefined()
    expect(document.body.textContent).toContain('Mínimo de 8 caracteres')
    expect(document.body.textContent).toContain('Ao menos uma letra maiúscula')
    expect(document.body.textContent).toContain('Ao menos um número')
    expect(profileApi.changePassword).not.toHaveBeenCalled()
  })

  it('keeps the submit button disabled until the new password meets every requirement', async () => {
    const wrapper = mountModal()
    const passwordInputs = wrapper.findAll('input[type="password"]')
    await passwordInputs[0].setValue('senhaAtual123')

    // Long enough, but missing an uppercase letter and a number.
    await passwordInputs[1].setValue('senhasenha')
    await passwordInputs[2].setValue('senhasenha')
    expect(wrapper.find('.button.is-success').attributes('disabled')).toBeDefined()

    // Uppercase added, still missing a number.
    await passwordInputs[1].setValue('Senhasenha')
    await passwordInputs[2].setValue('Senhasenha')
    expect(wrapper.find('.button.is-success').attributes('disabled')).toBeDefined()

    // All three requirements met.
    await passwordInputs[1].setValue('Senha123')
    await passwordInputs[2].setValue('Senha123')
    expect(wrapper.find('.button.is-success').attributes('disabled')).toBeUndefined()
  })

  it('shows the wrong-current-password message when the current password is rejected', async () => {
    vi.mocked(profileApi.changePassword).mockRejectedValue({
      isAxiosError: true,
      response: { status: 401, data: { detail: 'E-mail ou senha inválidos', error: 'invalid_credentials' } },
    })

    const wrapper = mountModal()
    const passwordInputs = wrapper.findAll('input[type="password"]')
    await passwordInputs[0].setValue('senhaErrada')
    await passwordInputs[1].setValue('SenhaNova123')
    await passwordInputs[2].setValue('SenhaNova123')
    await wrapper.find('.button.is-success').trigger('click')
    await flushPromises()

    expect(document.body.textContent).toContain('Senha atual incorreta')
  })
})

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
