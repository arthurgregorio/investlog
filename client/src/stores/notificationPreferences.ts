import { defineStore } from 'pinia'
import { ref } from 'vue'
import { profileApi } from '@/api/profile'
import type { NotificationPreferencesRequest, NotificationPreferencesResponse } from '@/types'

export const useNotificationPreferencesStore = defineStore('notificationPreferences', () => {
  const preferences = ref<NotificationPreferencesResponse | null>(null)
  const loading = ref(false)

  async function load() {
    loading.value = true
    try {
      preferences.value = await profileApi.getNotificationPreferences()
    } finally {
      loading.value = false
    }
  }

  async function save(request: NotificationPreferencesRequest) {
    preferences.value = await profileApi.updateNotificationPreferences(request)
  }

  function reset() {
    preferences.value = null
    loading.value = false
  }

  return { preferences, loading, load, save, reset }
})
