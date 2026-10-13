import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import EditHoldingTagsModal from './EditHoldingTagsModal.vue'
import { holdingsApi } from '@/api/holdings'
import { useTypesListStore } from '@/stores/typesList'
import type { FundHoldingDetail, StockHoldingDetail } from '@/types'

vi.mock('@/api/holdings', () => ({
  holdingsApi: {
    updateStockHolding: vi.fn(),
    updateFundHolding: vi.fn(),
    updateStockHoldingSegment: vi.fn(),
  },
}))

let activeWrapper: VueWrapper | undefined

interface ModalOptions {
  kind?: 'STOCKS' | 'FUNDS'
  initialTypeId?: string
  initialSegmentId?: string | null
}

function mountModal({
  kind = 'STOCKS',
  initialTypeId = 'stock-type-1',
  initialSegmentId = null,
}: ModalOptions = {}) {
  const pinia = createTestingPinia()
  const typesListStore = useTypesListStore(pinia)
  typesListStore.stockTypes = [
    { id: 'stock-type-1', name: 'Ordinária', usageCount: 1 },
    { id: 'stock-type-2', name: 'FII', usageCount: 1 },
  ]
  typesListStore.fundTypes = [
    { id: 'fund-type-1', name: 'Renda fixa', usageCount: 1 },
    { id: 'fund-type-2', name: 'Multimercado', usageCount: 0 },
  ]
  typesListStore.stockSegments = [
    { id: 'segment-1', name: 'Energia', usageCount: 1 },
    { id: 'segment-2', name: 'Tecnologia', usageCount: 0 },
  ]
  activeWrapper = mount(EditHoldingTagsModal, {
    props: { holdingId: 'holding-1', walletId: 'wallet-1', kind, initialTypeId, initialSegmentId },
    global: { plugins: [pinia], config: { errorHandler: () => undefined } },
    attachTo: document.body,
  })
  return activeWrapper
}

function typeSelect(wrapper: VueWrapper) {
  return wrapper.find('select[data-testid="edit-tags-type"]')
}

function segmentSelect(wrapper: VueWrapper) {
  return wrapper.find('select[data-testid="edit-tags-segment"]')
}

function optionTexts(select: ReturnType<VueWrapper['find']>) {
  return select.findAll('option').map((option) => option.text())
}

function submitButton(wrapper: VueWrapper) {
  return wrapper.find('[data-testid="edit-tags-submit"]')
}

async function save(wrapper: VueWrapper) {
  await submitButton(wrapper).trigger('click')
  await flushPromises()
}

describe('EditHoldingTagsModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(holdingsApi.updateStockHolding).mockResolvedValue({} as StockHoldingDetail)
    vi.mocked(holdingsApi.updateFundHolding).mockResolvedValue({} as FundHoldingDetail)
    vi.mocked(holdingsApi.updateStockHoldingSegment).mockResolvedValue({} as StockHoldingDetail)
  })

  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = undefined
  })

  it('loads the lists and preselects the stock type and segment', () => {
    const wrapper = mountModal({ initialSegmentId: 'segment-1' })

    expect(useTypesListStore().load).toHaveBeenCalledTimes(1)
    expect(optionTexts(typeSelect(wrapper))).toEqual(['Ordinária', 'FII'])
    expect((typeSelect(wrapper).element as HTMLSelectElement).value).toBe('stock-type-1')
    expect(optionTexts(segmentSelect(wrapper))).toEqual(['Sem segmento', 'Energia', 'Tecnologia'])
    expect((segmentSelect(wrapper).element as HTMLSelectElement).value).toBe('segment-1')
  })

  it('keeps Salvar disabled until a tag changes', async () => {
    const wrapper = mountModal()
    expect(submitButton(wrapper).attributes('disabled')).toBeDefined()

    await segmentSelect(wrapper).setValue('segment-2')

    expect(submitButton(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('saves only the stock type when only the type changed', async () => {
    const wrapper = mountModal({ initialSegmentId: 'segment-1' })

    await typeSelect(wrapper).setValue('stock-type-2')
    await save(wrapper)

    expect(holdingsApi.updateStockHolding).toHaveBeenCalledWith('wallet-1', 'holding-1', {
      stockTypeId: 'stock-type-2',
    })
    expect(holdingsApi.updateStockHoldingSegment).not.toHaveBeenCalled()
    expect(document.body.textContent).toContain('Tags atualizadas.')
    expect(wrapper.emitted('updated')).toHaveLength(1)
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('saves type and segment together and clears the segment with Sem segmento', async () => {
    const wrapper = mountModal({ initialSegmentId: 'segment-1' })

    await typeSelect(wrapper).setValue('stock-type-2')
    await segmentSelect(wrapper).setValue('')
    await save(wrapper)

    expect(holdingsApi.updateStockHolding).toHaveBeenCalledTimes(1)
    expect(holdingsApi.updateStockHoldingSegment).toHaveBeenCalledWith(
      'wallet-1',
      'holding-1',
      null,
    )
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('offers only the fund types for a fund and saves the fund type', async () => {
    const wrapper = mountModal({ kind: 'FUNDS', initialTypeId: 'fund-type-1' })

    expect(optionTexts(typeSelect(wrapper))).toEqual(['Renda fixa', 'Multimercado'])
    expect(segmentSelect(wrapper).exists()).toBe(false)

    await typeSelect(wrapper).setValue('fund-type-2')
    await save(wrapper)

    expect(holdingsApi.updateFundHolding).toHaveBeenCalledWith('wallet-1', 'holding-1', {
      fundTypeId: 'fund-type-2',
    })
    expect(holdingsApi.updateStockHolding).not.toHaveBeenCalled()
  })

  it('stays open when every save fails', async () => {
    vi.mocked(holdingsApi.updateStockHoldingSegment).mockRejectedValue(new Error('network'))
    const wrapper = mountModal()

    await segmentSelect(wrapper).setValue('segment-2')
    await save(wrapper)

    expect(wrapper.emitted('updated')).toBeUndefined()
    expect(wrapper.emitted('close')).toBeUndefined()
  })

  it('reports the saved half and stays open when only one save fails', async () => {
    vi.mocked(holdingsApi.updateStockHoldingSegment).mockRejectedValue(new Error('network'))
    const wrapper = mountModal()

    await typeSelect(wrapper).setValue('stock-type-2')
    await segmentSelect(wrapper).setValue('segment-2')
    await save(wrapper)

    expect(wrapper.emitted('updated')).toHaveLength(1)
    expect(wrapper.emitted('close')).toBeUndefined()
  })

  it('closes on Cancelar without saving', async () => {
    const wrapper = mountModal()

    const cancel = wrapper.findAll('button').find((button) => button.text() === 'Cancelar')!
    await cancel.trigger('click')

    expect(holdingsApi.updateStockHolding).not.toHaveBeenCalled()
    expect(wrapper.emitted('close')).toHaveLength(1)
  })
})
