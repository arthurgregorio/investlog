import { defineStore } from 'pinia'
import { ref } from 'vue'
import { mailSettingsApi } from '@/api/mailSettings'
import type { MailSettingsRequest, MailSettingsResponse } from '@/types'

export const useMailSettingsStore = defineStore('mailSettings', () => {
  const settings = ref<MailSettingsResponse | null>(null)
  const loading = ref(false)

  async function load() {
    loading.value = true
    try {
      settings.value = await mailSettingsApi.get()
    } finally {
      loading.value = false
    }
  }

  async function save(request: MailSettingsRequest) {
    settings.value = await mailSettingsApi.save(request)
  }

  async function setEnabled(enabled: boolean) {
    settings.value = await mailSettingsApi.setEnabled(enabled)
  }

  function reset() {
    settings.value = null
    loading.value = false
  }

  return { settings, loading, load, save, setEnabled, reset }
})
