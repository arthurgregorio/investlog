import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import SetSegmentModal from './SetSegmentModal.vue'
import { holdingsApi } from '@/api/holdings'
import { useTypesListStore } from '@/stores/typesList'
import type { StockHoldingDetail } from '@/types'

vi.mock('@/api/holdings', () => ({
  holdingsApi: { updateStockHoldingSegment: vi.fn() },
}))

let activeWrapper: VueWrapper | undefined

function mountModal(initialSegmentId: string | null) {
  const pinia = createTestingPinia()
  useTypesListStore(pinia).stockSegments = [
    { id: 'segment-1', name: 'Energia', usageCount: 1 },
    { id: 'segment-2', name: 'Tecnologia', usageCount: 0 },
  ]
  activeWrapper = mount(SetSegmentModal, {
    props: { holdingId: 'holding-1', walletId: 'wallet-1', initialSegmentId },
    global: { plugins: [pinia], config: { errorHandler: () => undefined } },
    attachTo: document.body,
  })
  return activeWrapper
}

function segmentSelect(wrapper: VueWrapper) {
  return wrapper.find('select[data-testid="set-segment-select"]')
}

async function save(wrapper: VueWrapper) {
  await wrapper.find('[data-testid="set-segment-submit"]').trigger('click')
  await flushPromises()
}

describe('SetSegmentModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(holdingsApi.updateStockHoldingSegment).mockResolvedValue({} as StockHoldingDetail)
  })

  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = undefined
  })

  it('loads the segment list and offers Sem segmento first', () => {
    const wrapper = mountModal(null)

    expect(useTypesListStore().load).toHaveBeenCalledTimes(1)
    expect(
      segmentSelect(wrapper)
        .findAll('option')
        .map((option) => option.text()),
    ).toEqual(['Sem segmento', 'Energia', 'Tecnologia'])
    expect((segmentSelect(wrapper).element as HTMLSelectElement).value).toBe('')
  })

  it('preselects the current segment and saves a new one', async () => {
    const wrapper = mountModal('segment-1')
    expect((segmentSelect(wrapper).element as HTMLSelectElement).value).toBe('segment-1')

    await segmentSelect(wrapper).setValue('segment-2')
    await save(wrapper)

    expect(holdingsApi.updateStockHoldingSegment).toHaveBeenCalledWith(
      'wallet-1',
      'holding-1',
      'segment-2',
    )
    expect(document.body.textContent).toContain('Segmento atualizado.')
    expect(wrapper.emitted('updated')).toHaveLength(1)
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('clears the segment with Sem segmento', async () => {
    const wrapper = mountModal('segment-1')

    await segmentSelect(wrapper).setValue('')
    await save(wrapper)

    expect(holdingsApi.updateStockHoldingSegment).toHaveBeenCalledWith(
      'wallet-1',
      'holding-1',
      null,
    )
  })

  it('stays open when the save fails', async () => {
    vi.mocked(holdingsApi.updateStockHoldingSegment).mockRejectedValue(new Error('network'))
    const wrapper = mountModal(null)

    await save(wrapper)

    expect(wrapper.emitted('close')).toBeUndefined()
  })

  it('closes on Cancelar without saving', async () => {
    const wrapper = mountModal(null)

    const cancel = wrapper.findAll('button').find((button) => button.text() === 'Cancelar')!
    await cancel.trigger('click')

    expect(holdingsApi.updateStockHoldingSegment).not.toHaveBeenCalled()
    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})
