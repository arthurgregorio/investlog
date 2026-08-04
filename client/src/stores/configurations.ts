import { defineStore } from 'pinia'
import { ref } from 'vue'
import { configurationsApi } from '@/api/configurations'

export const useConfigurationsStore = defineStore('configurations', () => {
  const values = ref<Record<string, string>>({})
  const loaded = ref(false)
  const loading = ref(false)

  async function load() {
    if (loaded.value) return
    loading.value = true
    try {
      const configurations = await configurationsApi.findAll()
      values.value = Object.fromEntries(
        configurations.map((configuration) => [configuration.key, configuration.value]),
      )
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  async function updateConfiguration(key: string, value: string) {
    const updated = await configurationsApi.update(key, value)
    values.value = { ...values.value, [updated.key]: updated.value }
  }

  return { values, loaded, loading, load, updateConfiguration }
})
