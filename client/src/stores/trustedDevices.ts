import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authApi } from '@/api/auth'
import type { TrustedDeviceResponse } from '@/types'

export const useTrustedDevicesStore = defineStore('trustedDevices', () => {
  const devices = ref<TrustedDeviceResponse[]>([])
  const loading = ref(false)

  // No `loaded` guard, unlike sibling stores — the list must always refetch on open
  // since another device/session can change it at any time.
  async function load() {
    loading.value = true
    try {
      devices.value = await authApi.fetchTrustedDevices()
    } finally {
      loading.value = false
    }
  }

  async function revoke(id: string) {
    await authApi.revokeTrustedDevice(id)
    devices.value = devices.value.filter((device) => device.id !== id)
  }

  return { devices, loading, load, revoke }
})
