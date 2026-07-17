import { defineStore } from 'pinia'
import { ref } from 'vue'
import { usersAdminApi } from '@/api/usersAdmin'
import type { UserAdminResponse, UserRole } from '@/types'

export const useUsersAdminStore = defineStore('usersAdmin', () => {
  const users = ref<UserAdminResponse[]>([])
  const loaded = ref(false)
  const loading = ref(false)

  async function load() {
    if (loaded.value) return
    loading.value = true
    try {
      users.value = await usersAdminApi.findAll()
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  async function refresh() {
    loaded.value = false
    await load()
  }

  function replaceUser(updated: UserAdminResponse) {
    users.value = users.value.map((user) => (user.id === updated.id ? updated : user))
  }

  async function approve(id: string): Promise<void> {
    replaceUser(await usersAdminApi.approve(id))
  }

  async function block(id: string): Promise<void> {
    replaceUser(await usersAdminApi.block(id))
  }

  async function unblock(id: string): Promise<void> {
    replaceUser(await usersAdminApi.unblock(id))
  }

  async function changeRole(id: string, role: UserRole): Promise<void> {
    replaceUser(await usersAdminApi.changeRole(id, role))
  }

  async function resetTotp(id: string): Promise<void> {
    replaceUser(await usersAdminApi.resetTotp(id))
  }

  async function remove(id: string): Promise<void> {
    await usersAdminApi.remove(id)
    users.value = users.value.filter((user) => user.id !== id)
  }

  return { users, loaded, loading, load, refresh, approve, block, unblock, changeRole, resetTotp, remove }
})
