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
    await passwordInputs[1].setValue('teste')
    await passwordInputs[2].setValue('teste')
    await wrapper.find('.button.is-success').trigger('click')
    await flushPromises()

    expect(document.body.textContent).toContain('newPassword deve ter entre 8 e 128 caracteres')
    expect(document.body.textContent).not.toContain('Senha atual incorreta')
  })

  it('shows the wrong-current-password message when the current password is rejected', async () => {
    vi.mocked(profileApi.changePassword).mockRejectedValue({
      isAxiosError: true,
      response: { status: 401, data: { detail: 'E-mail ou senha inválidos', error: 'invalid_credentials' } },
    })

    const wrapper = mountModal()
    const passwordInputs = wrapper.findAll('input[type="password"]')
    await passwordInputs[0].setValue('senhaErrada')
    await passwordInputs[1].setValue('senhaNova123')
    await passwordInputs[2].setValue('senhaNova123')
    await wrapper.find('.button.is-success').trigger('click')
    await flushPromises()

    expect(document.body.textContent).toContain('Senha atual incorreta')
  })
})

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
