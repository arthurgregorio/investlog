import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import DistributeHoldingsModal from './DistributeHoldingsModal.vue'
import { strategiesApi } from '@/api/strategies'
import { useStrategiesStore } from '@/stores/strategies'
import { classHolding, defaultStrategy, strategy } from '@/test/strategyFixtures'

vi.mock('@/api/strategies', () => ({ strategiesApi: { findClassHoldings: vi.fn() } }))

const HOLDINGS = [
  classHolding({ targetPercentage: null }),
  classHolding({ id: 'holding-btc', kind: 'CRYPTO', ticker: 'BTC', name: 'Bitcoin', frozen: true }),
]

let activeWrapper: VueWrapper | undefined

async function mountModal(initialClassId?: string) {
  const pinia = createTestingPinia({
    initialState: { strategies: { strategies: [strategy(), defaultStrategy()] } },
  })
  activeWrapper = mount(DistributeHoldingsModal, {
    props: { initialClassId },
    global: { plugins: [pinia] },
    attachTo: document.body,
  })
  await flushPromises()
  return activeWrapper
}

function select(wrapper: VueWrapper, testId: string) {
  return wrapper.find(`select[data-testid="${testId}"]`)
}

function optionTexts(wrapper: VueWrapper, testId: string) {
  return select(wrapper, testId)
    .findAll('option')
    .map((option) => option.text())
}

function submitButton(wrapper: VueWrapper) {
  return wrapper.find('[data-testid="distribute-submit"]')
}

beforeEach(() => {
  vi.mocked(strategiesApi.findClassHoldings).mockResolvedValue(HOLDINGS)
})

afterEach(() => {
  activeWrapper?.unmount()
  activeWrapper = undefined
})

describe('DistributeHoldingsModal', () => {
  it('starts from the given class and offers the other classes as destinations', async () => {
    const wrapper = await mountModal('class-undefined')

    expect((select(wrapper, 'origin-class').element as HTMLSelectElement).value).toBe(
      'class-undefined',
    )
    expect(optionTexts(wrapper, 'origin-class')).toEqual([
      'Longo prazo · Ações Brasil (3 investimentos)',
      'Longo prazo · Exterior (1 investimentos)',
      'Sem estratégia · Não definida (2 investimentos)',
    ])
    expect(optionTexts(wrapper, 'destination-strategy')).toEqual(['Longo prazo'])
    expect(optionTexts(wrapper, 'destination-class')).toEqual(['Ações Brasil', 'Exterior'])
    expect(wrapper.findAll('[data-testid="distribute-item"]')).toHaveLength(2)
    expect(submitButton(wrapper).attributes('disabled')).toBeDefined()
  })

  it('moves the checked holdings into the destination class', async () => {
    const wrapper = await mountModal('class-undefined')
    const store = useStrategiesStore()

    await select(wrapper, 'destination-class').setValue('class-abroad')
    await wrapper.findAll('[data-testid="distribute-item"] input')[0].setValue(true)
    expect(submitButton(wrapper).text()).toBe('Mover 1')
    await submitButton(wrapper).trigger('click')
    await flushPromises()

    expect(store.assignHoldings).toHaveBeenCalledWith('class-abroad', [
      { kind: 'STOCKS', id: 'holding-petr4' },
    ])
    expect(wrapper.emitted('moved')).toHaveLength(1)
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('moves everything at once with Mover tudo', async () => {
    const wrapper = await mountModal('class-undefined')
    const store = useStrategiesStore()

    await wrapper.find('[data-testid="move-all"] input').setValue(true)
    await submitButton(wrapper).trigger('click')
    await flushPromises()

    expect(store.assignHoldings).toHaveBeenCalledWith('class-brazil', [
      { kind: 'STOCKS', id: 'holding-petr4' },
      { kind: 'CRYPTO', id: 'holding-btc' },
    ])
  })

  it('reloads the list and keeps the destination apart from a newly chosen origin', async () => {
    const wrapper = await mountModal()

    expect((select(wrapper, 'origin-class').element as HTMLSelectElement).value).toBe(
      'class-brazil',
    )
    expect(optionTexts(wrapper, 'destination-class')).toEqual(['Exterior'])

    vi.mocked(strategiesApi.findClassHoldings).mockResolvedValue([])
    await select(wrapper, 'origin-class').setValue('class-abroad')
    await flushPromises()

    expect(strategiesApi.findClassHoldings).toHaveBeenLastCalledWith('class-abroad')
    expect(optionTexts(wrapper, 'destination-class')).toEqual(['Ações Brasil'])
    expect(wrapper.text()).toContain('Nenhum investimento nesta classe.')

    await select(wrapper, 'destination-strategy').setValue('strategy-default')
    expect(optionTexts(wrapper, 'destination-class')).toEqual(['Não definida'])
  })
})
