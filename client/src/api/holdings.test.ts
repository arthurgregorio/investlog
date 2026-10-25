import { beforeEach, describe, expect, it, vi } from 'vitest'
import { holdingsApi } from './holdings'
import axios from 'axios'
import { apiClient } from './client'

vi.mock('./client', () => ({ apiClient: { patch: vi.fn(), get: vi.fn() } }))

describe('holdingsApi frozen updates', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(apiClient.patch).mockResolvedValue({ data: { frozen: true } })
  })

  it.each([
    ['updateStockHolding', 'stock-holdings'],
    ['updateCryptoHolding', 'crypto-holdings'],
    ['updateFundHolding', 'fund-holdings'],
  ] as const)('%s patches only the frozen flag on its own endpoint', async (method, path) => {
    const result = await holdingsApi[method]('wallet-1', 'holding-1', { frozen: true })

    expect(apiClient.patch).toHaveBeenCalledWith(`/wallets/wallet-1/${path}/holding-1`, {
      frozen: true,
    })
    expect(result).toEqual({ frozen: true })
  })
})

describe('holdingsApi list filters', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(apiClient.get).mockResolvedValue({ data: [] })
  })

  it.each([
    ['findAll', '/holdings'],
    ['findAllForReport', '/holdings/report'],
  ] as const)('%s sends wallets and types as repeated keys', async (method, path) => {
    await holdingsApi[method]({ walletId: ['wallet-1', 'wallet-2'], typeLabel: ['FII'] })

    const [url, config] = vi.mocked(apiClient.get).mock.calls[0]
    expect(url).toBe(path)
    expect(
      axios.getUri({ url, params: config!.params, paramsSerializer: config!.paramsSerializer }),
    ).toBe(`${path}?walletId=wallet-1&walletId=wallet-2&typeLabel=FII`)
  })
})

describe('holdingsApi.downloadReportPdf', () => {
  const params = { walletId: ['wallet-1', 'wallet-2'], typeLabel: ['Ações, FIIs'], kind: 'STOCKS' }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('requests a blob with repeated keys and the abort signal', async () => {
    const signal = new AbortController().signal
    vi.mocked(apiClient.get).mockResolvedValue({ status: 200, data: new Blob(), headers: {} })

    await holdingsApi.downloadReportPdf(params, signal)

    const [url, config] = vi.mocked(apiClient.get).mock.calls[0]
    expect(url).toBe('/holdings/report/pdf')
    expect(config).toMatchObject({ responseType: 'blob', signal })
    expect(
      axios.getUri({ url, params: config!.params, paramsSerializer: config!.paramsSerializer }),
    ).toBe(
      '/holdings/report/pdf?walletId=wallet-1&walletId=wallet-2&typeLabel=A%C3%A7%C3%B5es,+FIIs&kind=STOCKS',
    )
  })

  it('resolves to the blob and the file name from a quoted Content-Disposition', async () => {
    const blob = new Blob(['%PDF'])
    vi.mocked(apiClient.get).mockResolvedValue({
      status: 200,
      data: blob,
      headers: {
        'content-disposition': 'attachment; filename="relatorio-investimentos-2026-10-22.pdf"',
      },
    })

    await expect(holdingsApi.downloadReportPdf(params)).resolves.toEqual({
      blob,
      fileName: 'relatorio-investimentos-2026-10-22.pdf',
    })
  })

  it('reads an unquoted file name too', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      status: 200,
      data: new Blob(),
      headers: { 'content-disposition': 'attachment; filename=relatorio.pdf; size=10' },
    })

    const result = await holdingsApi.downloadReportPdf(params)

    expect(result?.fileName).toBe('relatorio.pdf')
  })

  it('falls back to a default file name without a Content-Disposition header', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ status: 200, data: new Blob(), headers: {} })

    const result = await holdingsApi.downloadReportPdf(params)

    expect(result?.fileName).toBe('relatorio-investimentos.pdf')
  })

  it('falls back to the default when the header has no file name', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      status: 200,
      data: new Blob(),
      headers: { 'content-disposition': 'attachment' },
    })

    const result = await holdingsApi.downloadReportPdf(params)

    expect(result?.fileName).toBe('relatorio-investimentos.pdf')
  })

  it('resolves to null on a 204', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ status: 204, data: new Blob(), headers: {} })

    await expect(holdingsApi.downloadReportPdf(params)).resolves.toBeNull()
  })
})
