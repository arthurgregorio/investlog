import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useMailSettingsStore } from './mailSettings'
import { mailSettingsApi } from '@/api/mailSettings'
import type { MailSettingsRequest, MailSettingsResponse } from '@/types'

vi.mock('@/api/mailSettings')

const savedSettings: MailSettingsResponse = {
  enabled: false,
  configured: true,
  host: 'smtp.gmail.com',
  port: 587,
  security: 'STARTTLS',
  username: 'investlog@gmail.com',
  passwordSet: true,
  fromAddress: 'investlog@gmail.com',
  fromName: 'InvestLog',
  publicUrl: 'https://investlog.example.com',
  updatedAt: '2026-11-08T10:00:00Z',
}

const request: MailSettingsRequest = {
  host: 'smtp.gmail.com',
  port: 587,
  security: 'STARTTLS',
  username: 'investlog@gmail.com',
  password: null,
  fromAddress: 'investlog@gmail.com',
  fromName: 'InvestLog',
  publicUrl: 'https://investlog.example.com',
}

describe('useMailSettingsStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('load() fetches the saved settings', async () => {
    vi.mocked(mailSettingsApi.get).mockResolvedValue(savedSettings)
    const mailSettingsStore = useMailSettingsStore()

    await mailSettingsStore.load()

    expect(mailSettingsStore.settings).toEqual(savedSettings)
    expect(mailSettingsStore.loading).toBe(false)
  })

  it('load() clears the loading flag when the request fails', async () => {
    vi.mocked(mailSettingsApi.get).mockRejectedValue(new Error('network'))
    const mailSettingsStore = useMailSettingsStore()

    await expect(mailSettingsStore.load()).rejects.toThrow('network')

    expect(mailSettingsStore.settings).toBeNull()
    expect(mailSettingsStore.loading).toBe(false)
  })

  it('save() stores what the server answers', async () => {
    vi.mocked(mailSettingsApi.save).mockResolvedValue(savedSettings)
    const mailSettingsStore = useMailSettingsStore()

    await mailSettingsStore.save(request)

    expect(mailSettingsApi.save).toHaveBeenCalledWith(request)
    expect(mailSettingsStore.settings).toEqual(savedSettings)
  })

  it('save() keeps the previous settings when the server refuses them', async () => {
    vi.mocked(mailSettingsApi.save).mockRejectedValue(new Error('400'))
    const mailSettingsStore = useMailSettingsStore()
    mailSettingsStore.settings = savedSettings

    await expect(mailSettingsStore.save({ ...request, host: '' })).rejects.toThrow('400')

    expect(mailSettingsStore.settings).toEqual(savedSettings)
  })

  it('setEnabled() stores the switched settings only after the server confirms', async () => {
    vi.mocked(mailSettingsApi.setEnabled).mockResolvedValue({ ...savedSettings, enabled: true })
    const mailSettingsStore = useMailSettingsStore()
    mailSettingsStore.settings = savedSettings

    const pending = mailSettingsStore.setEnabled(true)
    expect(mailSettingsStore.settings?.enabled).toBe(false)
    await pending

    expect(mailSettingsApi.setEnabled).toHaveBeenCalledWith(true)
    expect(mailSettingsStore.settings?.enabled).toBe(true)
  })

  it('reset() forgets the settings', () => {
    const mailSettingsStore = useMailSettingsStore()
    mailSettingsStore.settings = savedSettings
    mailSettingsStore.loading = true

    mailSettingsStore.reset()

    expect(mailSettingsStore.settings).toBeNull()
    expect(mailSettingsStore.loading).toBe(false)
  })
})
