import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import StrategiesView from './StrategiesView.vue'
import { strategiesApi } from '@/api/strategies'
import { useStrategiesStore } from '@/stores/strategies'
import {
  classHolding,
  defaultStrategy,
  strategy,
  strategyAllocation,
} from '@/test/strategyFixtures'
import type { Strategy } from '@/types'

vi.mock('@/api/strategies', () => ({
  strategiesApi: { findClassHoldings: vi.fn(), findAllocation: vi.fn() },
}))

let activeWrapper: VueWrapper | undefined
let router: Router

async function mountView(
  strategies: Strategy[] = [strategy(), defaultStrategy()],
  query: Record<string, string> = {},
) {
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/strategies', name: 'strategies', component: StrategiesView },
      { path: '/strategies/:id/plan', name: 'strategy-plan', component: { template: '<div />' } },
    ],
  })
  await router.push({ name: 'strategies', query })
  const pinia = createTestingPinia({ initialState: { strategies: { strategies } } })
  activeWrapper = mount(StrategiesView, {
    global: { plugins: [pinia, router] },
    attachTo: document.body,
  })
  await flushPromises()
  return activeWrapper
}

function railOption(wrapper: VueWrapper, strategyId: string) {
  return wrapper.find(`[data-testid="strategy-${strategyId}"]`)
}

async function openClassesTab(wrapper: VueWrapper) {
  const tab = wrapper.findAll('.tabs li a').find((link) => link.text().includes('Classes e metas'))
  await tab!.trigger('click')
  await flushPromises()
}

beforeEach(() => {
  vi.mocked(strategiesApi.findClassHoldings).mockResolvedValue([classHolding()])
  vi.mocked(strategiesApi.findAllocation).mockResolvedValue(strategyAllocation())
})

afterEach(() => {
  activeWrapper?.unmount()
  activeWrapper = undefined
})

describe('StrategiesView', () => {
  it('opens on the first strategy with its allocation, KPIs and Não definida notice', async () => {
    const wrapper = await mountView()

    expect(useStrategiesStore().load).toHaveBeenCalled()
    expect(strategiesApi.findAllocation).toHaveBeenCalledWith('strategy-long')
    expect(wrapper.find('.page-desc').text()).toBe('Longo prazo · 4 investimentos')
    expect(wrapper.find('.tabs li.is-active').text()).toContain('Alocação')
    expect(wrapper.findAll('[data-testid="allocation-class-row"]')).toHaveLength(2)
    expect(wrapper.find('[data-testid="unassigned-notice"]').text()).toContain(
      '2 investimentos ainda estão em Sem estratégia · Não definida',
    )
    expect(wrapper.find('[data-testid="kpi-largest-drift"]').text()).toBe('−10,0 pp')
  })

  it('opens a class detail below the tabs from the allocation table', async () => {
    const wrapper = await mountView()

    await wrapper.findAll('[data-testid="expand-allocation-class"]')[0].trigger('click')

    expect(wrapper.find('[data-testid="class-allocation-detail"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid="allocation-holding-row"]')).toHaveLength(3)
  })

  it('opens the Não definida distribution from the allocation notice', async () => {
    const wrapper = await mountView()

    await wrapper.find('[data-testid="notice-distribute"]').trigger('click')
    await flushPromises()

    expect(
      document.querySelector<HTMLSelectElement>('select[data-testid="origin-class"]')?.value,
    ).toBe('class-undefined')

    const cancel = [...document.querySelectorAll<HTMLButtonElement>('.modal button')].find(
      (button) => button.textContent?.trim() === 'Cancelar',
    )
    cancel!.click()
    await flushPromises()
    expect(document.querySelector('select[data-testid="origin-class"]')).toBeNull()
  })

  it('opens the holding targets of an expanded class on the Classes e metas tab', async () => {
    const wrapper = await mountView()

    await openClassesTab(wrapper)
    await wrapper.findAll('[data-testid="expand-class"]')[0].trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="class-holding-targets"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="unassigned-notice"]').exists()).toBe(false)
    expect(strategiesApi.findClassHoldings).toHaveBeenCalledWith('class-brazil')
  })

  it('goes to the planner of the selected strategy', async () => {
    const wrapper = await mountView()

    await wrapper.find('[data-testid="plan-contribution"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('strategy-plan')
    expect(router.currentRoute.value.params.id).toBe('strategy-long')
  })

  it('selects the strategy named in the query', async () => {
    vi.mocked(strategiesApi.findAllocation).mockResolvedValue(
      strategyAllocation({ strategyId: 'strategy-retirement' }),
    )
    const wrapper = await mountView(
      [
        strategy(),
        strategy({ id: 'strategy-retirement', name: 'Aposentadoria' }),
        defaultStrategy(),
      ],
      { strategy: 'strategy-retirement' },
    )

    expect(wrapper.find('.page-desc').text()).toBe('Aposentadoria · 4 investimentos')
    expect(wrapper.findAll('[data-testid="allocation-class-row"]')).toHaveLength(2)
  })

  it('shows the locked default strategy with Não definida and no allocation', async () => {
    const wrapper = await mountView()

    await railOption(wrapper, 'strategy-default').trigger('change')
    await flushPromises()

    expect(wrapper.find('[data-testid="default-strategy-notice"]').exists()).toBe(true)
    expect(wrapper.find('.tabs li.is-disabled').text()).toContain('Alocação')
    expect(wrapper.find('.tabs li.is-active').text()).toContain('Classes e metas')
    expect(wrapper.find('[data-testid="plan-contribution"]').attributes('disabled')).toBeDefined()
    expect(wrapper.findAll('[data-testid="undefined-holding"]')).toHaveLength(1)

    await wrapper.find('[data-testid="distribute-holdings"]').trigger('click')
    await flushPromises()
    expect(document.querySelector('select[data-testid="origin-class"]')).not.toBeNull()
  })

  it('ignores an allocation that arrives after the selection moved on', async () => {
    vi.mocked(strategiesApi.findAllocation).mockResolvedValue(
      strategyAllocation({ strategyId: 'strategy-other' }),
    )
    const wrapper = await mountView()

    expect(wrapper.find('[data-testid="allocation-class-row"]').exists()).toBe(false)
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

    await openClassesTab(wrapper)
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
