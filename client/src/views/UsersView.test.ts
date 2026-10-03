import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import UsersView from './UsersView.vue'
import { useAuthStore } from '@/stores/auth'
import { useUsersAdminStore } from '@/stores/usersAdmin'
import type { UserAdminResponse } from '@/types'

vi.mock('@/api/usersAdmin', () => ({ usersAdminApi: {} }))

const PasswordResetModalStub = {
  props: ['userId', 'userName'],
  emits: ['close'],
  template:
    '<div class="password-reset-stub">{{ userId }}|{{ userName }}<button class="stub-close" @click="$emit(\'close\')">fechar</button></div>',
}

function userOf(overrides: Partial<UserAdminResponse>): UserAdminResponse {
  return {
    id: 'user-1',
    name: 'Maria',
    email: 'maria@example.com',
    role: 'USER',
    status: 'APPROVED',
    authProvider: 'LOCAL',
    totpEnabled: false,
    ...overrides,
  }
}

const self = userOf({ id: 'self', name: 'Admin', email: 'admin@admin.com', role: 'ADMIN' })

let activeWrapper: VueWrapper | undefined

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

async function mountView(users: UserAdminResponse[]) {
  const wrapper = mount(UsersView, {
    global: {
      plugins: [createTestingPinia()],
      stubs: { PasswordResetModal: PasswordResetModalStub },
    },
    attachTo: document.body,
  })
  activeWrapper = wrapper
  const usersAdminStore = useUsersAdminStore()
  usersAdminStore.users = users
  useAuthStore().session = {
    name: 'Admin',
    email: 'admin@admin.com',
    role: 'ADMIN',
    status: 'APPROVED',
    authProvider: 'LOCAL',
    demoModeEnabled: false,
  }
  await flushPromises()
  return { wrapper, usersAdminStore }
}

function bodyButton(label: string) {
  return Array.from(document.body.querySelectorAll('button')).find(
    (button) => button.textContent?.trim() === label,
  ) as HTMLButtonElement | undefined
}

async function confirmDialog(label: string) {
  await flushPromises()
  bodyButton(label)?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
  await flushPromises()
}

function cardOf(wrapper: VueWrapper, name: string) {
  return wrapper.findAll('.entity-card').find((card) => card.find('.entity-name').text() === name)!
}

function actionLabels(wrapper: VueWrapper, name: string) {
  return cardOf(wrapper, name)
    .findAll('.dropdown-item')
    .map((item) => item.text())
}

async function chooseAction(wrapper: VueWrapper, name: string, label: string) {
  const item = cardOf(wrapper, name)
    .findAll('.dropdown-item')
    .find((candidate) => candidate.text() === label)!
  await item.trigger('click')
  await flushPromises()
}

describe('UsersView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = undefined
  })

  it('loads the users store on mount', async () => {
    const { usersAdminStore } = await mountView([])

    expect(usersAdminStore.load).toHaveBeenCalledTimes(1)
  })

  it('renders one card per user with email, role, status and the 2FA tag', async () => {
    const { wrapper } = await mountView([
      self,
      userOf({ id: 'user-2', name: 'Joao', email: 'joao@example.com', totpEnabled: true }),
      userOf({ id: 'user-3', name: 'Ana', status: 'BLOCKED' }),
      userOf({ id: 'user-4', name: 'Carlos', status: 'PENDING' }),
    ])

    expect(wrapper.findAll('.entity-card')).toHaveLength(4)
    expect(cardOf(wrapper, 'Joao').text()).toContain('joao@example.com')
    expect(cardOf(wrapper, 'Joao').text()).toContain('USER')
    expect(cardOf(wrapper, 'Joao').text()).toContain('APPROVED')
    expect(cardOf(wrapper, 'Joao').text()).toContain('2FA ativo')
    expect(cardOf(wrapper, 'Admin').text()).toContain('ADMIN')
    expect(cardOf(wrapper, 'Admin').text()).not.toContain('2FA ativo')
    expect(cardOf(wrapper, 'Ana').find('.tag.is-danger').text()).toBe('BLOCKED')
    expect(cardOf(wrapper, 'Carlos').find('.tag.is-warning').text()).toBe('PENDING')
    expect(cardOf(wrapper, 'Joao').find('.tag.is-success').text()).toBe('APPROVED')
  })

  it('renders no cards when there are no users', async () => {
    const { wrapper } = await mountView([])

    expect(wrapper.findAll('.entity-card')).toHaveLength(0)
  })

  it('offers the acting admin no actions on their own approved row', async () => {
    const { wrapper } = await mountView([self])

    expect(cardOf(wrapper, 'Admin').find('.dropdown').exists()).toBe(false)
  })

  it('offers only Aprovar on a pending row that belongs to the acting admin', async () => {
    const { wrapper } = await mountView([{ ...self, status: 'PENDING' }])

    expect(actionLabels(wrapper, 'Admin')).toEqual(['Aprovar'])
  })

  it('lists approve but neither block nor unblock for a pending user', async () => {
    const { wrapper } = await mountView([self, userOf({ name: 'Carlos', status: 'PENDING' })])

    expect(actionLabels(wrapper, 'Carlos')).toEqual([
      'Aprovar',
      'Promover a admin',
      'Redefinir 2FA',
      'Redefinir senha',
      'Remover',
    ])
  })

  it('offers Bloquear on approved rows and Desbloquear only on blocked rows', async () => {
    const { wrapper } = await mountView([
      self,
      userOf({ id: 'user-2', name: 'Joao', status: 'APPROVED' }),
      userOf({ id: 'user-3', name: 'Ana', status: 'BLOCKED' }),
    ])

    expect(actionLabels(wrapper, 'Joao')).toContain('Bloquear')
    expect(actionLabels(wrapper, 'Joao')).not.toContain('Desbloquear')
    expect(actionLabels(wrapper, 'Joao')).not.toContain('Aprovar')
    expect(actionLabels(wrapper, 'Ana')).toContain('Desbloquear')
    expect(actionLabels(wrapper, 'Ana')).not.toContain('Bloquear')
  })

  it('offers the password reset only for local accounts', async () => {
    const { wrapper } = await mountView([
      self,
      userOf({ id: 'user-2', name: 'Joao', authProvider: 'LOCAL' }),
      userOf({ id: 'user-3', name: 'Gabriela', authProvider: 'GOOGLE' }),
    ])

    expect(actionLabels(wrapper, 'Joao')).toContain('Redefinir senha')
    expect(actionLabels(wrapper, 'Gabriela')).not.toContain('Redefinir senha')
  })

  it('labels the role action by the role it would assign', async () => {
    const { wrapper } = await mountView([
      self,
      userOf({ id: 'user-2', name: 'Joao', role: 'USER' }),
      userOf({ id: 'user-3', name: 'Beatriz', role: 'ADMIN' }),
    ])

    expect(actionLabels(wrapper, 'Joao')).toContain('Promover a admin')
    expect(actionLabels(wrapper, 'Beatriz')).toContain('Remover admin')
  })

  it('approves a pending user and confirms with a toast', async () => {
    const { wrapper, usersAdminStore } = await mountView([
      self,
      userOf({ id: 'user-4', name: 'Carlos', status: 'PENDING' }),
    ])

    await chooseAction(wrapper, 'Carlos', 'Aprovar')

    expect(usersAdminStore.approve).toHaveBeenCalledWith('user-4')
    expect(document.body.textContent).toContain('Usuário aprovado.')
  })

  it('blocks an approved user', async () => {
    const { wrapper, usersAdminStore } = await mountView([
      self,
      userOf({ id: 'user-2', name: 'Joao' }),
    ])

    await chooseAction(wrapper, 'Joao', 'Bloquear')

    expect(usersAdminStore.block).toHaveBeenCalledWith('user-2')
    expect(document.body.textContent).toContain('Usuário bloqueado.')
  })

  it('unblocks a blocked user', async () => {
    const { wrapper, usersAdminStore } = await mountView([
      self,
      userOf({ id: 'user-3', name: 'Ana', status: 'BLOCKED' }),
    ])

    await chooseAction(wrapper, 'Ana', 'Desbloquear')

    expect(usersAdminStore.unblock).toHaveBeenCalledWith('user-3')
    expect(document.body.textContent).toContain('Usuário desbloqueado.')
  })

  it('promotes a user to admin only after confirmation', async () => {
    const { wrapper, usersAdminStore } = await mountView([
      self,
      userOf({ id: 'user-2', name: 'Joao', role: 'USER' }),
    ])

    await chooseAction(wrapper, 'Joao', 'Promover a admin')

    expect(usersAdminStore.changeRole).not.toHaveBeenCalled()
    expect(document.body.textContent).toContain('Promover a administrador')

    await confirmDialog('Confirmar')

    expect(usersAdminStore.changeRole).toHaveBeenCalledWith('user-2', 'ADMIN')
    expect(document.body.textContent).toContain('Papel atualizado.')
  })

  it('demotes an admin to user after confirmation', async () => {
    const { wrapper, usersAdminStore } = await mountView([
      self,
      userOf({ id: 'user-3', name: 'Beatriz', role: 'ADMIN' }),
    ])

    await chooseAction(wrapper, 'Beatriz', 'Remover admin')

    expect(document.body.textContent).toContain('Remover privilégios de administrador')

    await confirmDialog('Confirmar')

    expect(usersAdminStore.changeRole).toHaveBeenCalledWith('user-3', 'USER')
  })

  it('resets the two-factor authentication after confirmation', async () => {
    const { wrapper, usersAdminStore } = await mountView([
      self,
      userOf({ id: 'user-2', name: 'Joao', totpEnabled: true }),
    ])

    await chooseAction(wrapper, 'Joao', 'Redefinir 2FA')
    await confirmDialog('Redefinir')

    expect(usersAdminStore.resetTotp).toHaveBeenCalledWith('user-2')
    expect(document.body.textContent).toContain('Autenticação em duas etapas redefinida.')
  })

  it('deletes a user after confirmation', async () => {
    const { wrapper, usersAdminStore } = await mountView([
      self,
      userOf({ id: 'user-2', name: 'Joao' }),
    ])

    await chooseAction(wrapper, 'Joao', 'Remover')
    await confirmDialog('Remover')

    expect(usersAdminStore.remove).toHaveBeenCalledWith('user-2')
    expect(document.body.textContent).toContain('Usuário removido.')
  })

  it('does not call the store when a confirmation is cancelled', async () => {
    const { wrapper, usersAdminStore } = await mountView([
      self,
      userOf({ id: 'user-2', name: 'Joao' }),
    ])

    await chooseAction(wrapper, 'Joao', 'Remover')
    await confirmDialog('Cancelar')

    expect(usersAdminStore.remove).not.toHaveBeenCalled()
  })

  it('opens the password reset modal for the chosen user and closes it again', async () => {
    const { wrapper } = await mountView([self, userOf({ id: 'user-2', name: 'Joao' })])

    expect(wrapper.find('.password-reset-stub').exists()).toBe(false)

    await chooseAction(wrapper, 'Joao', 'Redefinir senha')

    expect(wrapper.find('.password-reset-stub').text()).toContain('user-2|Joao')

    await wrapper.find('.stub-close').trigger('click')

    expect(wrapper.find('.password-reset-stub').exists()).toBe(false)
  })
})
