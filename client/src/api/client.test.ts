import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError, type AxiosAdapter } from 'axios'

const toastOpen = vi.hoisted(() => vi.fn())

vi.mock('buefy', async (importOriginal) => ({
  ...(await importOriginal<typeof import('buefy')>()),
  ToastProgrammatic: class {
    open = toastOpen
  },
}))
vi.mock('@/router', () => ({
  router: { currentRoute: { value: { name: 'investments' } }, push: vi.fn() },
}))

import { apiClient } from './client'

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
