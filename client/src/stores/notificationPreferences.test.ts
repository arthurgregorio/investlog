import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useNotificationPreferencesStore } from './notificationPreferences'
import { profileApi } from '@/api/profile'
import type { NotificationPreferencesResponse } from '@/types'

vi.mock('@/api/profile', () => ({
  profileApi: {
    getNotificationPreferences: vi.fn(),
    updateNotificationPreferences: vi.fn(),
  },
}))

const defaults: NotificationPreferencesResponse = {
  periodicReportFrequency: 'DISABLED',
  monthEndReportEnabled: true,
  frozenAlertEnabled: true,
  frozenAlertMinimumGainPercentage: 1,
  emailConfigured: true,
}

describe('notificationPreferences store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads the preferences from the API', async () => {
    vi.mocked(profileApi.getNotificationPreferences).mockResolvedValue(defaults)

    const store = useNotificationPreferencesStore()
    await store.load()

    expect(store.preferences).toEqual(defaults)
  })

  it('refetches on every load', async () => {
    vi.mocked(profileApi.getNotificationPreferences).mockResolvedValue(defaults)

    const store = useNotificationPreferencesStore()
    await store.load()
    await store.load()

    expect(profileApi.getNotificationPreferences).toHaveBeenCalledTimes(2)
  })

  it('sets loading while fetching and clears it once resolved', async () => {
    let resolveFetch: (preferences: NotificationPreferencesResponse) => void
    vi.mocked(profileApi.getNotificationPreferences).mockReturnValue(
      new Promise((resolve) => {
        resolveFetch = resolve
      }),
    )

    const store = useNotificationPreferencesStore()
    const loadPromise = store.load()

    expect(store.loading).toBe(true)

    resolveFetch!(defaults)
    await loadPromise

    expect(store.loading).toBe(false)
  })

  it('clears loading when the fetch fails', async () => {
    vi.mocked(profileApi.getNotificationPreferences).mockRejectedValue(new Error('boom'))

    const store = useNotificationPreferencesStore()

    await expect(store.load()).rejects.toThrow('boom')
    expect(store.loading).toBe(false)
    expect(store.preferences).toBeNull()
  })

  it('saves the whole request and keeps the response the server returns', async () => {
    const saved = { ...defaults, periodicReportFrequency: 'WEEKLY' as const }
    vi.mocked(profileApi.getNotificationPreferences).mockResolvedValue(defaults)
    vi.mocked(profileApi.updateNotificationPreferences).mockResolvedValue(saved)
    const store = useNotificationPreferencesStore()
    await store.load()

    await store.save({
      periodicReportFrequency: 'WEEKLY',
      monthEndReportEnabled: true,
      frozenAlertEnabled: true,
      frozenAlertMinimumGainPercentage: 1,
    })

    expect(profileApi.updateNotificationPreferences).toHaveBeenCalledWith({
      periodicReportFrequency: 'WEEKLY',
      monthEndReportEnabled: true,
      frozenAlertEnabled: true,
      frozenAlertMinimumGainPercentage: 1,
    })
    expect(store.preferences).toEqual(saved)
  })

  it('keeps the previous preferences when saving fails', async () => {
    vi.mocked(profileApi.getNotificationPreferences).mockResolvedValue(defaults)
    vi.mocked(profileApi.updateNotificationPreferences).mockRejectedValue(new Error('boom'))
    const store = useNotificationPreferencesStore()
    await store.load()

    await expect(
      store.save({
        periodicReportFrequency: 'DAILY',
        monthEndReportEnabled: false,
        frozenAlertEnabled: false,
        frozenAlertMinimumGainPercentage: 5,
      }),
    ).rejects.toThrow('boom')

    expect(store.preferences).toEqual(defaults)
  })

  it('resets to no preferences', async () => {
    vi.mocked(profileApi.getNotificationPreferences).mockResolvedValue(defaults)
    const store = useNotificationPreferencesStore()
    await store.load()

    store.reset()

    expect(store.preferences).toBeNull()
    expect(store.loading).toBe(false)
  })
})
