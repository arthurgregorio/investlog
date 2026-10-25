import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { CanceledError } from 'axios'
import ExportReportModal from './ExportReportModal.vue'
import { holdingsApi, type ReportPdf } from '@/api/holdings'

vi.mock('@/api/holdings', () => ({ holdingsApi: { downloadReportPdf: vi.fn() } }))

const params = { kind: 'STOCKS', walletId: ['wallet-1'], hideFrozen: true }
const report: ReportPdf = {
  blob: new Blob(['%PDF'], { type: 'application/pdf' }),
  fileName: 'relatorio-investimentos-2026-10-22.pdf',
}

let activeWrapper: VueWrapper | undefined
let downloadedFileNames: string[]

function mountModal() {
  activeWrapper = mount(ExportReportModal, { props: { params }, attachTo: document.body })
  return activeWrapper
}

function button(testId: string) {
  return document.body.querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`)
}

function modalText() {
  return document.body.querySelector('.modal-card')?.textContent ?? ''
}

describe('ExportReportModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    downloadedFileNames = []
    URL.createObjectURL = vi.fn(() => 'blob:report')
    URL.revokeObjectURL = vi.fn()
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      downloadedFileNames.push(this.download)
    })
  })

  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = undefined
    vi.restoreAllMocks()
  })

  describe('generating', () => {
    it('requests the PDF with the given params as soon as it opens', async () => {
      vi.mocked(holdingsApi.downloadReportPdf).mockReturnValue(new Promise(() => {}))

      mountModal()
      await flushPromises()

      expect(holdingsApi.downloadReportPdf).toHaveBeenCalledExactlyOnceWith(
        params,
        expect.any(AbortSignal),
      )
    })

    it('shows the spinner, the explanation and the filters notice', async () => {
      vi.mocked(holdingsApi.downloadReportPdf).mockReturnValue(new Promise(() => {}))

      mountModal()
      await flushPromises()

      expect(button('export-cancel')).not.toBeNull()
      expect(document.body.querySelector('[data-testid="export-spinner"]')).not.toBeNull()
      expect(modalText()).toContain('Gerando o PDF…')
      expect(modalText()).toContain('Consolidando as posições por classe e tipo.')
      expect(modalText()).toContain(
        'O relatório considera os filtros aplicados na lista de investimentos.',
      )
    })

    it('cannot be dismissed by the header close button', async () => {
      vi.mocked(holdingsApi.downloadReportPdf).mockReturnValue(new Promise(() => {}))

      const wrapper = mountModal()
      await flushPromises()

      const closeButton = document.body.querySelector<HTMLButtonElement>(
        'button[aria-label="Fechar"]',
      )!
      expect(closeButton.disabled).toBe(true)
      closeButton.click()
      document.body.querySelector<HTMLElement>('.modal-background')?.click()
      document.dispatchEvent(new KeyboardEvent('keyup', { key: 'Escape', keyCode: 27 }))
      await flushPromises()

      expect(wrapper.emitted('close')).toBeUndefined()
    })

    it('aborts the request and closes on Cancelar, without an error state', async () => {
      let signal!: AbortSignal
      vi.mocked(holdingsApi.downloadReportPdf).mockImplementation((_params, abortSignal) => {
        signal = abortSignal!
        return new Promise((_resolve, reject) => {
          abortSignal!.addEventListener('abort', () => reject(new CanceledError()))
        })
      })

      const wrapper = mountModal()
      await flushPromises()
      button('export-cancel')!.click()
      await flushPromises()

      expect(signal.aborted).toBe(true)
      expect(wrapper.emitted('close')).toHaveLength(1)
      expect(modalText()).not.toContain('Não foi possível gerar o relatório')
      expect(downloadedFileNames).toEqual([])
    })
  })

  describe('done', () => {
    beforeEach(() => {
      vi.mocked(holdingsApi.downloadReportPdf).mockResolvedValue(report)
    })

    it('saves the PDF straight away and shows its file name', async () => {
      mountModal()
      await flushPromises()

      expect(URL.createObjectURL).toHaveBeenCalledExactlyOnceWith(report.blob)
      expect(downloadedFileNames).toEqual([report.fileName])
      expect(URL.revokeObjectURL).toHaveBeenCalledExactlyOnceWith('blob:report')
      expect(modalText()).toContain('Relatório gerado')
      expect(modalText()).toContain('O download começou automaticamente.')
      expect(document.body.querySelector('[data-testid="export-file"]')?.textContent).toContain(
        report.fileName,
      )
      expect(modalText()).toContain(
        'O relatório considera os filtros aplicados na lista de investimentos.',
      )
    })

    it('downloads the blob in memory again without a new request', async () => {
      mountModal()
      await flushPromises()

      button('export-download-again')!.click()
      await flushPromises()

      expect(holdingsApi.downloadReportPdf).toHaveBeenCalledTimes(1)
      expect(URL.createObjectURL).toHaveBeenCalledTimes(2)
      expect(vi.mocked(URL.createObjectURL).mock.calls[1][0]).toBe(report.blob)
      expect(downloadedFileNames).toEqual([report.fileName, report.fileName])
    })

    it('closes on Fechar and can be dismissed again', async () => {
      const wrapper = mountModal()
      await flushPromises()

      expect(
        document.body.querySelector<HTMLButtonElement>('button[aria-label="Fechar"]')!.disabled,
      ).toBe(false)
      button('export-close')!.click()

      expect(wrapper.emitted('close')).toHaveLength(1)
    })
  })

  describe('empty', () => {
    it('tells that no investment matches and offers only Fechar', async () => {
      vi.mocked(holdingsApi.downloadReportPdf).mockResolvedValue(null)

      const wrapper = mountModal()
      await flushPromises()

      expect(modalText()).toContain('Nenhum investimento para os filtros atuais')
      expect(downloadedFileNames).toEqual([])
      expect(button('export-download-again')).toBeNull()
      expect(button('export-retry')).toBeNull()
      button('export-close')!.click()
      expect(wrapper.emitted('close')).toHaveLength(1)
    })
  })

  describe('error', () => {
    it('shows the failure and retries the request on Tentar novamente', async () => {
      vi.mocked(holdingsApi.downloadReportPdf)
        .mockRejectedValueOnce(new Error('Network Error'))
        .mockResolvedValueOnce(report)

      mountModal()
      await flushPromises()

      expect(modalText()).toContain('Não foi possível gerar o relatório')
      expect(downloadedFileNames).toEqual([])
      expect(button('export-download-again')).toBeNull()

      button('export-retry')!.click()
      await flushPromises()

      expect(holdingsApi.downloadReportPdf).toHaveBeenCalledTimes(2)
      expect(modalText()).toContain('Relatório gerado')
      expect(downloadedFileNames).toEqual([report.fileName])
    })

    it('closes on Fechar', async () => {
      vi.mocked(holdingsApi.downloadReportPdf).mockRejectedValue(new Error('failed'))

      const wrapper = mountModal()
      await flushPromises()
      button('export-close')!.click()

      expect(wrapper.emitted('close')).toHaveLength(1)
    })
  })
})
