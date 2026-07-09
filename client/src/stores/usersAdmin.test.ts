import { describe, expect, it, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useUsersAdminStore } from './usersAdmin'
import { usersAdminApi } from '@/api/usersAdmin'

vi.mock('@/api/usersAdmin', () => ({
  usersAdminApi: {
    findAll: vi.fn(),
    approve: vi.fn(),
    reject: vi.fn(),
    changeRole: vi.fn(),
    resetTotp: vi.fn(),
    remove: vi.fn(),
  },
}))

const adminUser = {
  id: '11111111-1111-1111-1111-111111111111',
  name: 'Administrador',
  email: 'admin@admin.com',
  role: 'ADMIN' as const,
  status: 'APPROVED' as const,
  authProvider: 'LOCAL' as const,
  totpEnabled: true,
}

const pendingUser = {
  id: '22222222-2222-2222-2222-222222222222',
  name: 'Nova Usuária',
  email: 'nova@example.com',
  role: 'USER' as const,
  status: 'PENDING' as const,
  authProvider: 'LOCAL' as const,
  totpEnabled: false,
}

describe('usersAdmin store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads the user list', async () => {
    vi.mocked(usersAdminApi.findAll).mockResolvedValue([adminUser, pendingUser])

    const store = useUsersAdminStore()
    await store.load()

    expect(store.users).toEqual([adminUser, pendingUser])
  })

  it('approve replaces the user in place', async () => {
    vi.mocked(usersAdminApi.findAll).mockResolvedValue([pendingUser])
    const approved = { ...pendingUser, status: 'APPROVED' as const }
    vi.mocked(usersAdminApi.approve).mockResolvedValue(approved)

    const store = useUsersAdminStore()
    await store.load()
    await store.approve(pendingUser.id)

    expect(usersAdminApi.approve).toHaveBeenCalledWith(pendingUser.id)
    expect(store.users[0].status).toBe('APPROVED')
  })

  it('remove drops the user from the list', async () => {
    vi.mocked(usersAdminApi.findAll).mockResolvedValue([adminUser, pendingUser])
    vi.mocked(usersAdminApi.remove).mockResolvedValue(undefined)

    const store = useUsersAdminStore()
    await store.load()
    await store.remove(pendingUser.id)

    expect(usersAdminApi.remove).toHaveBeenCalledWith(pendingUser.id)
    expect(store.users).toEqual([adminUser])
  })
})
