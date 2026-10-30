import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import EditHoldingTagsModal from './EditHoldingTagsModal.vue'
import { holdingsApi } from '@/api/holdings'
import { useTypesListStore } from '@/stores/typesList'
import { useStrategiesStore } from '@/stores/strategies'
import { defaultStrategy, strategy } from '@/test/strategyFixtures'
import type {
  CryptoHoldingDetail,
  FundHoldingDetail,
  StockHoldingDetail,
  WalletKind,
} from '@/types'

vi.mock('@/api/holdings', () => ({
  holdingsApi: {
    updateStockHolding: vi.fn(),
    updateFundHolding: vi.fn(),
    updateCryptoHolding: vi.fn(),
    updateHoldingSegment: vi.fn(),
    updateHoldingClass: vi.fn(),
  },
}))

let activeWrapper: VueWrapper | undefined

interface ModalOptions {
  kind?: WalletKind
  initialTypeId?: string | null
  initialSegmentId?: string | null
  initialStrategyClassId?: string | null
}

function mountModal({
  kind = 'STOCKS',
  initialTypeId = 'stock-type-1',
  initialSegmentId = null,
  initialStrategyClassId = 'class-undefined',
}: ModalOptions = {}) {
  const pinia = createTestingPinia()
  useStrategiesStore(pinia).strategies = [strategy(), defaultStrategy()]
  const typesListStore = useTypesListStore(pinia)
  typesListStore.holdingTypes = [
    { id: 'stock-type-1', name: 'Ordinária', kinds: ['STOCKS'], usageCount: 1 },
    { id: 'stock-type-2', name: 'FII', kinds: ['STOCKS'], usageCount: 1 },
    { id: 'fund-type-1', name: 'Renda fixa', kinds: ['FUNDS'], usageCount: 1 },
    { id: 'fund-type-2', name: 'Multimercado', kinds: ['FUNDS'], usageCount: 0 },
    { id: 'crypto-type-1', name: 'Moeda', kinds: ['CRYPTO'], usageCount: 1 },
    { id: 'crypto-type-2', name: 'Token', kinds: ['CRYPTO'], usageCount: 0 },
  ]
  typesListStore.segments = [
    { id: 'segment-1', name: 'Energia', usageCount: 1 },
    { id: 'segment-2', name: 'Tecnologia', usageCount: 0 },
  ]
  activeWrapper = mount(EditHoldingTagsModal, {
    props: {
      holdingId: 'holding-1',
      walletId: 'wallet-1',
      kind,
      initialTypeId,
      initialSegmentId,
      initialStrategyClassId,
    },
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
    vi.mocked(holdingsApi.updateCryptoHolding).mockResolvedValue({} as CryptoHoldingDetail)
    vi.mocked(holdingsApi.updateHoldingSegment).mockResolvedValue({} as StockHoldingDetail)
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
      typeId: 'stock-type-2',
    })
    expect(holdingsApi.updateHoldingSegment).not.toHaveBeenCalled()
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
    expect(holdingsApi.updateHoldingSegment).toHaveBeenCalledWith(
      'STOCKS',
      'wallet-1',
      'holding-1',
      null,
    )
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('offers only the fund types for a fund and saves the type and the segment', async () => {
    const wrapper = mountModal({ kind: 'FUNDS', initialTypeId: 'fund-type-1' })

    expect(optionTexts(typeSelect(wrapper))).toEqual(['Renda fixa', 'Multimercado'])
    expect(optionTexts(segmentSelect(wrapper))).toEqual(['Sem segmento', 'Energia', 'Tecnologia'])

    await typeSelect(wrapper).setValue('fund-type-2')
    await segmentSelect(wrapper).setValue('segment-2')
    await save(wrapper)

    expect(holdingsApi.updateFundHolding).toHaveBeenCalledWith('wallet-1', 'holding-1', {
      typeId: 'fund-type-2',
    })
    expect(holdingsApi.updateHoldingSegment).toHaveBeenCalledWith(
      'FUNDS',
      'wallet-1',
      'holding-1',
      'segment-2',
    )
    expect(holdingsApi.updateStockHolding).not.toHaveBeenCalled()
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('offers only the crypto types for a crypto holding and saves the type and the segment', async () => {
    const wrapper = mountModal({ kind: 'CRYPTO', initialTypeId: 'crypto-type-1' })

    expect(optionTexts(typeSelect(wrapper))).toEqual(['Moeda', 'Token'])

    await typeSelect(wrapper).setValue('crypto-type-2')
    await segmentSelect(wrapper).setValue('segment-1')
    await save(wrapper)

    expect(holdingsApi.updateCryptoHolding).toHaveBeenCalledWith('wallet-1', 'holding-1', {
      typeId: 'crypto-type-2',
    })
    expect(holdingsApi.updateHoldingSegment).toHaveBeenCalledWith(
      'CRYPTO',
      'wallet-1',
      'holding-1',
      'segment-1',
    )
    expect(holdingsApi.updateStockHolding).not.toHaveBeenCalled()
    expect(holdingsApi.updateFundHolding).not.toHaveBeenCalled()
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  describe('a crypto holding without a type', () => {
    it('shows Sem tipo selected and saves nothing for the type until one is chosen', async () => {
      const wrapper = mountModal({ kind: 'CRYPTO', initialTypeId: null })

      expect(optionTexts(typeSelect(wrapper))).toEqual(['Sem tipo', 'Moeda', 'Token'])
      expect((typeSelect(wrapper).element as HTMLSelectElement).value).toBe('')
      expect(submitButton(wrapper).attributes('disabled')).toBeDefined()

      await segmentSelect(wrapper).setValue('segment-1')
      await save(wrapper)

      expect(holdingsApi.updateCryptoHolding).not.toHaveBeenCalled()
      expect(holdingsApi.updateHoldingSegment).toHaveBeenCalledTimes(1)
    })

    it('sets the type when one is chosen', async () => {
      const wrapper = mountModal({ kind: 'CRYPTO', initialTypeId: null })

      await typeSelect(wrapper).setValue('crypto-type-1')
      await save(wrapper)

      expect(holdingsApi.updateCryptoHolding).toHaveBeenCalledWith('wallet-1', 'holding-1', {
        typeId: 'crypto-type-1',
      })
    })
  })

  it('stays open when every save fails', async () => {
    vi.mocked(holdingsApi.updateHoldingSegment).mockRejectedValue(new Error('network'))
    const wrapper = mountModal()

    await segmentSelect(wrapper).setValue('segment-2')
    await save(wrapper)

    expect(wrapper.emitted('updated')).toBeUndefined()
    expect(wrapper.emitted('close')).toBeUndefined()
  })

  it('reports the saved half and stays open when only one save fails', async () => {
    vi.mocked(holdingsApi.updateHoldingSegment).mockRejectedValue(new Error('network'))
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

  describe('strategy and class', () => {
    function strategySelect(wrapper: VueWrapper) {
      return wrapper.find('select[data-testid="edit-tags-strategy"]')
    }

    function classSelect(wrapper: VueWrapper) {
      return wrapper.find('select[data-testid="edit-tags-class"]')
    }

    it('preselects the strategy and class the holding is in', async () => {
      const wrapper = mountModal({ initialStrategyClassId: 'class-abroad' })
      await flushPromises()

      expect((strategySelect(wrapper).element as HTMLSelectElement).value).toBe('strategy-long')
      expect(optionTexts(classSelect(wrapper))).toEqual(['Ações Brasil', 'Exterior'])
      expect((classSelect(wrapper).element as HTMLSelectElement).value).toBe('class-abroad')
      expect(submitButton(wrapper).attributes('disabled')).toBeDefined()
    })

    it('saves a new class through the class endpoint and reloads the strategies', async () => {
      vi.mocked(holdingsApi.updateHoldingClass).mockResolvedValue({} as StockHoldingDetail)
      const wrapper = mountModal()
      await flushPromises()

      await strategySelect(wrapper).setValue('strategy-long')
      expect((classSelect(wrapper).element as HTMLSelectElement).value).toBe('class-brazil')
      await classSelect(wrapper).setValue('class-abroad')
      await save(wrapper)

      expect(holdingsApi.updateHoldingClass).toHaveBeenCalledWith(
        'STOCKS',
        'wallet-1',
        'holding-1',
        'class-abroad',
      )
      expect(holdingsApi.updateStockHolding).not.toHaveBeenCalled()
      expect(holdingsApi.updateHoldingSegment).not.toHaveBeenCalled()
      expect(useStrategiesStore().refresh).toHaveBeenCalled()
      expect(wrapper.emitted('updated')).toHaveLength(1)
      expect(wrapper.emitted('close')).toHaveLength(1)
    })

    it('falls back to the default strategy when the holding has no class yet', async () => {
      const wrapper = mountModal({ initialStrategyClassId: null })
      await flushPromises()

      expect((strategySelect(wrapper).element as HTMLSelectElement).value).toBe('strategy-default')
      expect((classSelect(wrapper).element as HTMLSelectElement).value).toBe('class-undefined')
      expect(submitButton(wrapper).attributes('disabled')).toBeDefined()
    })
  })
})
