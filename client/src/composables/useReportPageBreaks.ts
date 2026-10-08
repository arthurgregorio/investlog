import { nextTick, onScopeDispose, ref, watch, type Ref, type WatchSource } from 'vue'

const MM_TO_PX = 96 / 25.4
const PAGE_HEIGHT_MM = 297
const PAGE_MARGIN_Y_MM = 14
const PAGE_CONTENT_HEIGHT_PX = (PAGE_HEIGHT_MM - PAGE_MARGIN_Y_MM * 2) * MM_TO_PX
const PAGE_TOP_PADDING_PX = PAGE_MARGIN_Y_MM * MM_TO_PX

export function useReportPageBreaks(
  reportPaperRef: Ref<HTMLElement | null>,
  contentChanged: WatchSource,
) {
  const pageBreakOffsets = ref<number[]>([])
  let paperResizeObserver: ResizeObserver | undefined

  function recomputePageBreaks() {
    const paperElement = reportPaperRef.value
    if (!paperElement) {
      pageBreakOffsets.value = []
      return
    }
    const pageCount = Math.max(
      1,
      Math.ceil((paperElement.scrollHeight - PAGE_TOP_PADDING_PX) / PAGE_CONTENT_HEIGHT_PX),
    )
    pageBreakOffsets.value = Array.from(
      { length: pageCount - 1 },
      (_, index) => PAGE_TOP_PADDING_PX + (index + 1) * PAGE_CONTENT_HEIGHT_PX,
    )
  }

  watch(reportPaperRef, (paperElement) => {
    paperResizeObserver?.disconnect()
    paperResizeObserver = undefined
    if (!paperElement) return
    paperResizeObserver = new ResizeObserver(recomputePageBreaks)
    paperResizeObserver.observe(paperElement)
  })

  watch(contentChanged, () => nextTick(recomputePageBreaks))

  onScopeDispose(() => paperResizeObserver?.disconnect())

  return { pageBreakOffsets, recomputePageBreaks }
}
