import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authApi } from '@/api/auth'
import type { TrustedDeviceResponse } from '@/types'

export const useTrustedDevicesStore = defineStore('trustedDevices', () => {
  const devices = ref<TrustedDeviceResponse[]>([])

  async function load() {
    devices.value = await authApi.fetchTrustedDevices()
  }

  async function revoke(id: string) {
    await authApi.revokeTrustedDevice(id)
    devices.value = devices.value.filter((device) => device.id !== id)
  }

  return { devices, load, revoke }
})
