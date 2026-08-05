import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useConfigurationsStore } from './configurations'
import { configurationsApi } from '@/api/configurations'

vi.mock('@/api/configurations')

describe('useConfigurationsStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('load() indexes the configurations by key', async () => {
    vi.mocked(configurationsApi.findAll).mockResolvedValue([
      { key: 'stock_price_sync_enabled', value: 'true', updatedAt: '2026-08-03T10:00:00Z' },
    ])
    const configurationsStore = useConfigurationsStore()
    await configurationsStore.load()
    expect(configurationsStore.values['stock_price_sync_enabled']).toBe('true')
    expect(configurationsStore.loaded).toBe(true)
  })

  it('load() does not re-fetch once already loaded', async () => {
    vi.mocked(configurationsApi.findAll).mockResolvedValue([])
    const configurationsStore = useConfigurationsStore()
    await configurationsStore.load()
    await configurationsStore.load()
    expect(configurationsApi.findAll).toHaveBeenCalledTimes(1)
  })

  it('updateConfiguration persists via PATCH and updates local state', async () => {
    vi.mocked(configurationsApi.update).mockResolvedValue({
      key: 'stock_price_sync_enabled',
      value: 'false',
      updatedAt: '2026-08-03T10:05:00Z',
    })
    const configurationsStore = useConfigurationsStore()
    await configurationsStore.updateConfiguration('stock_price_sync_enabled', 'false')
    expect(configurationsApi.update).toHaveBeenCalledWith('stock_price_sync_enabled', 'false')
    expect(configurationsStore.values['stock_price_sync_enabled']).toBe('false')
  })
})
