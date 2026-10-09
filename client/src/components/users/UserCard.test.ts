import { afterEach, describe, expect, it } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import UserCard from './UserCard.vue'
import { useUsersAdminStore } from '@/stores/usersAdmin'
import type { UserAdminResponse } from '@/types'

const session = {
  name: 'Admin',
  email: 'admin@admin.com',
  role: 'ADMIN' as const,
  status: 'APPROVED' as const,
  authProvider: 'LOCAL' as const,
  demoModeEnabled: false,
}

function userOf(overrides: Partial<UserAdminResponse> = {}): UserAdminResponse {
  return {
    id: 'user-2',
    name: 'Joao',
    email: 'joao@example.com',
    role: 'USER',
    status: 'APPROVED',
    authProvider: 'LOCAL',
    totpEnabled: false,
    ...overrides,
  }
}

let activeWrapper: VueWrapper | undefined

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

function mountCard(user: UserAdminResponse) {
  const wrapper = mount(UserCard, {
    props: { user },
    global: { plugins: [createTestingPinia({ initialState: { auth: { session } } })] },
    attachTo: document.body,
  })
  activeWrapper = wrapper
  return wrapper
}

async function chooseAction(wrapper: VueWrapper, label: string) {
  const item = wrapper.findAll('.dropdown-item').find((candidate) => candidate.text() === label)!
  await item.trigger('click')
  await flushPromises()
}

function dialogButton(label: string) {
  return Array.from(document.body.querySelectorAll<HTMLButtonElement>('.dialog button')).find(
    (button) => button.textContent?.trim() === label,
  )
}

describe('UserCard', () => {
  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = undefined
  })

  it('shows the name, email, role, status colour and the 2FA tag', () => {
    const wrapper = mountCard(userOf({ status: 'BLOCKED', role: 'ADMIN', totpEnabled: true }))

    expect(wrapper.get('.entity-name').text()).toBe('Joao')
    expect(wrapper.get('.entity-foot').text()).toContain('joao@example.com')
    expect(wrapper.get('.tag.is-primary').text()).toBe('ADMIN')
    expect(wrapper.get('.tag.is-danger').text()).toBe('BLOCKED')
    expect(wrapper.get('.tag.is-info').text()).toBe('2FA ativo')
  })

  it('has no actions on the acting admin own approved card', () => {
    const wrapper = mountCard(userOf({ email: session.email }))

    expect(wrapper.find('.dropdown').exists()).toBe(false)
  })

  it('approves a pending user straight away', async () => {
    const wrapper = mountCard(userOf({ status: 'PENDING' }))

    await chooseAction(wrapper, 'Aprovar')

    expect(useUsersAdminStore().approve).toHaveBeenCalledWith('user-2')
    expect(document.body.textContent).toContain('Usuário aprovado.')
  })

  it('asks for the password reset of a local account through an event', async () => {
    const user = userOf()
    const wrapper = mountCard(user)

    await chooseAction(wrapper, 'Redefinir senha')

    expect(wrapper.emitted('reset-password')).toEqual([[user]])
  })

  it('confirms a role change with a neutral dialog before changing the role', async () => {
    const wrapper = mountCard(userOf())

    await chooseAction(wrapper, 'Promover a admin')

    expect(document.body.querySelector('.dialog .modal-card-title')?.textContent).toContain(
      'Promover a administrador',
    )
    expect(document.body.querySelector('.dialog .media-left')).toBeNull()
    expect(useUsersAdminStore().changeRole).not.toHaveBeenCalled()

    dialogButton('Confirmar')!.click()
    await flushPromises()

    expect(useUsersAdminStore().changeRole).toHaveBeenCalledWith('user-2', 'ADMIN')
  })

  it('confirms removing a user with a danger dialog', async () => {
    const wrapper = mountCard(userOf())

    await chooseAction(wrapper, 'Remover')

    expect(document.body.querySelector('.dialog .media-left')).not.toBeNull()

    dialogButton('Remover')!.click()
    await flushPromises()

    expect(useUsersAdminStore().remove).toHaveBeenCalledWith('user-2')
  })
})
