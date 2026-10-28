import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import StrategiesView from './StrategiesView.vue'
import { strategiesApi } from '@/api/strategies'
import { useStrategiesStore } from '@/stores/strategies'
import { classHolding, defaultStrategy, strategy } from '@/test/strategyFixtures'
import type { Strategy } from '@/types'

vi.mock('@/api/strategies', () => ({ strategiesApi: { findClassHoldings: vi.fn() } }))

let activeWrapper: VueWrapper | undefined

async function mountView(strategies: Strategy[] = [strategy(), defaultStrategy()]) {
  const pinia = createTestingPinia({ initialState: { strategies: { strategies } } })
  activeWrapper = mount(StrategiesView, { global: { plugins: [pinia] }, attachTo: document.body })
  await flushPromises()
  return activeWrapper
}

function railOption(wrapper: VueWrapper, strategyId: string) {
  return wrapper.find(`[data-testid="strategy-${strategyId}"]`)
}

beforeEach(() => {
  vi.mocked(strategiesApi.findClassHoldings).mockResolvedValue([classHolding()])
})

afterEach(() => {
  activeWrapper?.unmount()
  activeWrapper = undefined
})

describe('StrategiesView', () => {
  it('opens on the first strategy with its Classes e metas tab', async () => {
    const wrapper = await mountView()

    expect(useStrategiesStore().load).toHaveBeenCalled()
    expect(wrapper.find('.page-desc').text()).toBe('Longo prazo · 4 investimentos')
    expect(wrapper.findAll('[data-testid="class-row"]')).toHaveLength(2)
    expect(wrapper.find('.tabs li.is-active').text()).toContain('Classes e metas')
    expect(wrapper.find('[data-testid="default-strategy-notice"]').exists()).toBe(false)
  })

  it('opens the holding targets of an expanded class below the tabs', async () => {
    const wrapper = await mountView()

    await wrapper.findAll('[data-testid="expand-class"]')[0].trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="class-holding-targets"]').exists()).toBe(true)
    expect(strategiesApi.findClassHoldings).toHaveBeenCalledWith('class-brazil')
  })

  it('shows the locked default strategy with Não definida and its distribute modal', async () => {
    const wrapper = await mountView()

    await railOption(wrapper, 'strategy-default').trigger('change')
    await flushPromises()

    expect(wrapper.find('[data-testid="default-strategy-notice"]').exists()).toBe(true)
    expect(wrapper.find('.tabs li.is-disabled').text()).toContain('Alocação')
    expect(wrapper.findAll('[data-testid="undefined-holding"]')).toHaveLength(1)

    await wrapper.find('[data-testid="distribute-holdings"]').trigger('click')
    await flushPromises()
    expect(document.body.textContent).toContain('Distribuir investimentos')
    expect(document.querySelector('select[data-testid="origin-class"]')).not.toBeNull()
  })

  it('creates a strategy and selects it', async () => {
    const wrapper = await mountView()
    const store = useStrategiesStore()
    const created = strategy({
      id: 'strategy-new',
      name: 'Aposentadoria',
      holdingCount: 0,
      classes: [],
    })
    vi.mocked(store.create).mockImplementation(async () => {
      store.strategies = [store.strategies[0], created, store.strategies[1]]
      return created
    })

    await wrapper.find('[data-testid="new-strategy"]').trigger('click')
    const input = document.querySelector<HTMLInputElement>(
      'input[data-testid="new-strategy-name"]',
    )!
    input.value = 'Aposentadoria'
    input.dispatchEvent(new Event('input'))
    await flushPromises()
    document.querySelector<HTMLButtonElement>('[data-testid="create-strategy"]')!.click()
    await flushPromises()

    expect(wrapper.find('.page-desc').text()).toBe('Aposentadoria · 0 investimentos')
  })

  it('confirms before deleting a strategy and falls back to the next one', async () => {
    const wrapper = await mountView()
    const store = useStrategiesStore()
    vi.mocked(store.remove).mockImplementation(async () => {
      store.strategies = [defaultStrategy()]
    })

    await wrapper.find('[data-testid="delete-strategy"]').trigger('click')
    await flushPromises()
    expect(document.body.textContent).toContain('voltam para Sem estratégia · Não definida')

    const confirmButton = [...document.querySelectorAll<HTMLButtonElement>('.modal button')].find(
      (button) => button.textContent?.trim() === 'Excluir estratégia',
    )
    confirmButton!.click()
    await flushPromises()

    expect(store.remove).toHaveBeenCalledWith('strategy-long')
    expect(wrapper.find('.page-desc').text()).toBe('Sem estratégia · 2 investimentos')
  })

  it('shows nothing to select before any strategy loads', async () => {
    const wrapper = await mountView([])

    expect(wrapper.find('.page-desc').exists()).toBe(false)
    expect(wrapper.find('.investments-main').exists()).toBe(false)
  })
})
