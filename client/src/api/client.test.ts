import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError, type AxiosAdapter } from 'axios'

const toastOpen = vi.hoisted(() => vi.fn())
const auth = vi.hoisted(() => ({
  session: null as { email: string } | null,
  expireSession: vi.fn(),
}))
const routerPush = vi.hoisted(() => vi.fn())

vi.mock('buefy', async (importOriginal) => ({
  ...(await importOriginal<typeof import('buefy')>()),
  ToastProgrammatic: class {
    open = toastOpen
  },
}))
vi.mock('@/router', () => ({
  router: { currentRoute: { value: { name: 'investments' } }, push: routerPush },
}))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => auth }))

import { apiClient } from './client'
import { lastServerActivityAt } from './serverActivity'

function rejectWith(status: number, data: unknown): AxiosAdapter {
  return (config) =>
    Promise.reject(
      new AxiosError('Request failed', String(status), config, null, {
        status,
        statusText: '',
        headers: {},
        config,
        data,
      }),
    )
}

describe('apiClient error interceptor', () => {
  beforeEach(() => {
    toastOpen.mockClear()
  })

  it.each([
    [
      'a stock lot',
      '/wallets/w-1/stock-holdings/h-1/lots',
      'Esta posição está congelada e não aceita novos lotes',
    ],
    [
      'a crypto lot',
      '/wallets/w-1/crypto-holdings/h-1/lots',
      'Esta posição está congelada e não aceita novos lotes',
    ],
    [
      'a fund contribution',
      '/wallets/w-1/fund-holdings/h-1/contributions',
      'Esta posição está congelada e não aceita novos aportes',
    ],
    [
      'a reinvestment',
      '/reinvestments',
      'Tesouro Selic está congelado e não pode receber um reinvestimento',
    ],
  ])(
    'toasts the reason the server gives when %s hits a frozen holding',
    async (_label, url, detail) => {
      apiClient.defaults.adapter = rejectWith(409, { status: 409, detail })

      await expect(apiClient.post(url, {})).rejects.toBeInstanceOf(AxiosError)

      expect(toastOpen).toHaveBeenCalledWith({ message: detail, type: 'is-danger', duration: 4000 })
    },
  )

  it('falls back to a generic message when the server gives no reason', async () => {
    apiClient.defaults.adapter = rejectWith(409, {})

    await expect(apiClient.post('/reinvestments', {})).rejects.toBeInstanceOf(AxiosError)

    expect(toastOpen).toHaveBeenCalledWith({
      message: 'Erro ao comunicar com o servidor.',
      type: 'is-danger',
      duration: 4000,
    })
  })
})

describe('apiClient session handling', () => {
  beforeEach(() => {
    vi.useFakeTimers({ now: new Date('2026-10-12T10:00:00Z') })
    toastOpen.mockClear()
    routerPush.mockClear()
    auth.expireSession.mockClear()
    auth.session = null
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('records server activity on a successful response', async () => {
    apiClient.defaults.adapter = (config) =>
      Promise.resolve({ data: {}, status: 200, statusText: 'OK', headers: {}, config })
    vi.setSystemTime(new Date('2026-10-12T10:05:00Z'))

    await apiClient.get('/wallets')

    expect(lastServerActivityAt()).toBe(new Date('2026-10-12T10:05:00Z').getTime())
  })

  it('records server activity on an error response', async () => {
    apiClient.defaults.adapter = rejectWith(409, {})
    vi.setSystemTime(new Date('2026-10-12T10:07:00Z'))

    await expect(apiClient.post('/reinvestments', {})).rejects.toBeInstanceOf(AxiosError)

    expect(lastServerActivityAt()).toBe(new Date('2026-10-12T10:07:00Z').getTime())
  })

  it('does not count a request that never reached the server as activity', async () => {
    apiClient.defaults.adapter = (config) =>
      Promise.reject(new AxiosError('Network Error', 'ERR_NETWORK', config))
    const before = lastServerActivityAt()
    vi.setSystemTime(new Date('2026-10-12T11:00:00Z'))

    await expect(apiClient.get('/wallets')).rejects.toBeInstanceOf(AxiosError)

    expect(lastServerActivityAt()).toBe(before)
  })

  it('expires a live session on a 401, without a toast', async () => {
    auth.session = { email: 'admin@admin.com' }
    apiClient.defaults.adapter = rejectWith(401, {})

    await expect(apiClient.get('/wallets')).rejects.toBeInstanceOf(AxiosError)

    expect(auth.expireSession).toHaveBeenCalledWith('expired')
    expect(routerPush).not.toHaveBeenCalled()
    expect(toastOpen).not.toHaveBeenCalled()
  })

  it('sends a visitor without a session to the login page on a 401', async () => {
    apiClient.defaults.adapter = rejectWith(401, {})

    await expect(apiClient.get('/wallets')).rejects.toBeInstanceOf(AxiosError)

    expect(auth.expireSession).not.toHaveBeenCalled()
    expect(routerPush).toHaveBeenCalledWith({ name: 'login' })
  })
})
