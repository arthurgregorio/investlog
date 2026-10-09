import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, ref } from 'vue'
import { useReportPageBreaks } from './useReportPageBreaks'

let observerCallbacks: (() => void)[] = []
const observeSpy = vi.fn()
const disconnectSpy = vi.fn()

class ResizeObserverStub {
  constructor(callback: () => void) {
    observerCallbacks.push(callback)
  }
  observe(element: Element) {
    observeSpy(element)
  }
  disconnect() {
    disconnectSpy()
  }
}

function paperOfHeight(height: number) {
  const element = document.createElement('div')
  Object.defineProperty(element, 'scrollHeight', { value: height, configurable: true })
  return element
}

function setUp() {
  const reportPaperRef = ref<HTMLElement | null>(null)
  const content = ref(0)
  const scope = effectScope()
  const pageBreaks = scope.run(() => useReportPageBreaks(reportPaperRef, content))!
  return { scope, content, reportPaperRef, ...pageBreaks }
}

describe('useReportPageBreaks', () => {
  beforeEach(() => {
    observerCallbacks = []
    observeSpy.mockClear()
    disconnectSpy.mockClear()
    vi.stubGlobal('ResizeObserver', ResizeObserverStub)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('has no page breaks before the paper exists', () => {
    const { pageBreakOffsets, recomputePageBreaks } = setUp()

    recomputePageBreaks()

    expect(pageBreakOffsets.value).toEqual([])
  })

  it('places one break per extra A4 page, offset by the top margin', () => {
    const { reportPaperRef, pageBreakOffsets, recomputePageBreaks } = setUp()
    reportPaperRef.value = paperOfHeight(2500)

    recomputePageBreaks()

    expect(pageBreakOffsets.value.map(Math.round)).toEqual([1070, 2086])
  })

  it('places no break when the content fits one page', () => {
    const { reportPaperRef, pageBreakOffsets, recomputePageBreaks } = setUp()
    reportPaperRef.value = paperOfHeight(600)

    recomputePageBreaks()

    expect(pageBreakOffsets.value).toEqual([])
  })

  it('observes the paper and recomputes when it resizes', async () => {
    const { reportPaperRef, pageBreakOffsets } = setUp()
    const paper = paperOfHeight(600)
    reportPaperRef.value = paper
    await nextTick()

    expect(observeSpy).toHaveBeenCalledWith(paper)
    Object.defineProperty(paper, 'scrollHeight', { value: 2500 })
    observerCallbacks.forEach((callback) => callback())

    expect(pageBreakOffsets.value).toHaveLength(2)
  })

  it('recomputes after the content changes', async () => {
    const { reportPaperRef, pageBreakOffsets, content } = setUp()
    reportPaperRef.value = paperOfHeight(2500)

    content.value = 1
    await nextTick()
    await nextTick()

    expect(pageBreakOffsets.value).toHaveLength(2)
  })

  it('swaps the observer when the paper is replaced or removed', async () => {
    const { reportPaperRef, pageBreakOffsets } = setUp()
    reportPaperRef.value = paperOfHeight(600)
    await nextTick()

    reportPaperRef.value = null
    await nextTick()

    expect(disconnectSpy).toHaveBeenCalledTimes(1)
    expect(observerCallbacks).toHaveLength(1)
    observerCallbacks[0]()
    expect(pageBreakOffsets.value).toEqual([])
  })

  it('stops observing when its scope is disposed', async () => {
    const { reportPaperRef, scope } = setUp()
    reportPaperRef.value = paperOfHeight(600)
    await nextTick()

    scope.stop()

    expect(disconnectSpy).toHaveBeenCalledTimes(1)
  })
})
