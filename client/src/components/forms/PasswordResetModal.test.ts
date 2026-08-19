import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import Buefy from 'buefy'
import PasswordResetModal from './PasswordResetModal.vue'
import { useUsersAdminStore } from '@/stores/usersAdmin'

let activeWrapper: VueWrapper | undefined

function mountModal() {
  activeWrapper = mount(PasswordResetModal, {
    props: { userId: 'user-1', userName: 'Alguém' },
    global: { plugins: [Buefy] },
    attachTo: document.body,
  })
  return activeWrapper
}

describe('PasswordResetModal', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = undefined
  })

  it('shows the server validation message when the new password is rejected', async () => {
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
    await passwordInputs[0].setValue('teste')
    await passwordInputs[1].setValue('teste')
    await wrapper.find('.button.is-success').trigger('click')
    await flushPromises()

    expect(document.body.textContent).toContain('newPassword deve ter entre 8 e 128 caracteres')
  })

  it('does not show a field message for a non-validation failure, leaving the toast as the only feedback', async () => {
    const store = useUsersAdminStore()
    vi.spyOn(store, 'resetPassword').mockRejectedValue({
      isAxiosError: true,
      response: { status: 500, data: { detail: 'Erro interno' } },
    })

    const wrapper = mountModal()
    const passwordInputs = wrapper.findAll('input[type="password"]')
    await passwordInputs[0].setValue('senhaNova123')
    await passwordInputs[1].setValue('senhaNova123')
    await wrapper.find('.button.is-success').trigger('click')
    await flushPromises()

    expect(document.body.querySelectorAll('.help.is-danger')).toHaveLength(0)
    expect(wrapper.emitted('close')).toBeFalsy()
  })
})

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
