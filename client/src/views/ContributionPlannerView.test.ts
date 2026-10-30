import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import ContributionPlannerView from './ContributionPlannerView.vue'
import { strategiesApi } from '@/api/strategies'
import {
  contributionPlan,
  defaultStrategy,
  strategy,
  strategyAllocation,
} from '@/test/strategyFixtures'

vi.mock('@/api/strategies', () => ({
  strategiesApi: { findAllocation: vi.fn(), planContribution: vi.fn() },
}))

let activeWrapper: VueWrapper | undefined
let router: Router

async function mountPlanner(strategyId = 'strategy-long') {
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/strategies', name: 'strategies', component: { template: '<div />' } },
      {
        path: '/strategies/:id/plan',
        name: 'strategy-plan',
        component: ContributionPlannerView,
        props: true,
      },
    ],
  })
  await router.push({ name: 'strategy-plan', params: { id: strategyId } })
  const pinia = createTestingPinia({
    initialState: {
      strategies: {
        strategies: [
          strategy(),
          strategy({ id: 'strategy-retirement', name: 'Aposentadoria' }),
          defaultStrategy(),
        ],
      },
    },
  })
  activeWrapper = mount(
    { template: '<RouterView />' },
    { global: { plugins: [pinia, router] }, attachTo: document.body },
  )
  await flushPromises()
  return activeWrapper
}

async function typeAmount(wrapper: VueWrapper, amount: string) {
  await wrapper.find('input[data-testid="plan-amount"]').setValue(amount)
  vi.advanceTimersByTime(300)
  await flushPromises()
}

function rowsText(wrapper: VueWrapper) {
  return wrapper
    .findAll('[data-testid="plan-holding-row"]')
    .filter((row) => row.isVisible())
    .map((row) => row.text())
}

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true })
  vi.mocked(strategiesApi.findAllocation).mockResolvedValue(strategyAllocation())
  vi.mocked(strategiesApi.planContribution).mockResolvedValue(contributionPlan())
})

afterEach(() => {
  activeWrapper?.unmount()
  activeWrapper = undefined
  vi.useRealTimers()
})

describe('ContributionPlannerView', () => {
  it('shows the classes before any amount, with no suggestion yet', async () => {
    const wrapper = await mountPlanner()

    expect(strategiesApi.findAllocation).toHaveBeenCalledWith('strategy-long')
    expect(wrapper.find('.page-desc').text()).toBe('Longo prazo')
    expect(
      wrapper
        .find('select[data-testid="plan-strategy"]')
        .findAll('option')
        .map((option) => option.text()),
    ).toEqual(['Longo prazo', 'Aposentadoria'])
    expect(wrapper.findAll('[data-testid="plan-class-row"]')).toHaveLength(2)
    expect(wrapper.find('[data-testid="plan-summary"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="plan-holdings-empty"]').text()).toBe(
      'Informe o valor do aporte para ver a sugestão.',
    )
    expect(strategiesApi.planContribution).not.toHaveBeenCalled()
  })

  it('splits an amount across classes and holdings and lists the ignored ones', async () => {
    const wrapper = await mountPlanner()

    await typeAmount(wrapper, '300')

    expect(strategiesApi.planContribution).toHaveBeenCalledWith('strategy-long', 300)
    expect(wrapper.find('.page-desc').text()).toBe(
      'Longo prazo · sugestão para um aporte de R$ 300,00',
    )
    const summary = wrapper.find('[data-testid="plan-summary"]').text()
    expect(summary).toContain('R$ 300,00')
    expect(summary).toContain('1 de 2')
    expect(summary).toContain('1 de 4')

    const classRows = wrapper.findAll('[data-testid="plan-class-row"]')
    expect(classRows[0].text()).toContain('61,54%')
    expect(classRows[0].text()).toContain('R$ 300,00')
    expect(classRows[1].text()).toContain('acima da meta')
    expect(classRows[1].text()).toContain('38,46%')
    expect(wrapper.find('[data-testid="plan-total-row"]').text()).toContain('R$ 300,00')

    expect(rowsText(wrapper)).toHaveLength(1)
    expect(rowsText(wrapper)[0]).toContain('VALE3')
    expect(rowsText(wrapper)[0]).toContain('do restante')

    const ignoredTab = wrapper
      .findAll('.tabs li a')
      .find((link) => link.text().includes('Ignorados'))
    await ignoredTab!.trigger('click')
    const ignored = rowsText(wrapper)
    expect(ignored).toHaveLength(3)
    expect(ignored[0]).toContain('acima da meta na classe')
    expect(ignored[1]).toContain('congelado')
    expect(ignored[2]).toContain('classe acima da meta')
  })

  it('says why no class can receive and clears the plan for an empty amount', async () => {
    vi.mocked(strategiesApi.planContribution).mockRejectedValueOnce({
      isAxiosError: true,
      response: {
        status: 400,
        data: { detail: 'Nenhuma classe da estratégia pode receber este aporte' },
      },
    })
    const wrapper = await mountPlanner()

    await typeAmount(wrapper, '50')
    expect(wrapper.find('[data-testid="plan-error"]').text()).toContain('Nenhuma classe')

    await typeAmount(wrapper, '')
    expect(wrapper.find('[data-testid="plan-error"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="plan-summary"]').exists()).toBe(false)
  })

  it('marks a below-target class that nothing in it can receive', async () => {
    vi.mocked(strategiesApi.planContribution).mockResolvedValue(
      contributionPlan({
        leftOut: contributionPlan().leftOut.filter(
          (leftOut) => leftOut.reason !== 'CLASS_ABOVE_TARGET',
        ),
      }),
    )
    const wrapper = await mountPlanner()

    await typeAmount(wrapper, '300')

    expect(wrapper.findAll('[data-testid="plan-class-row"]')[1].text()).toContain(
      'nenhum investimento recebe',
    )
  })

  it('switches strategy through the route and goes back to Estratégias', async () => {
    vi.mocked(strategiesApi.findAllocation).mockResolvedValue(
      strategyAllocation({ strategyId: 'strategy-retirement', strategyName: 'Aposentadoria' }),
    )
    const wrapper = await mountPlanner()

    await typeAmount(wrapper, '300')
    await wrapper.find('select[data-testid="plan-strategy"]').setValue('strategy-retirement')
    await flushPromises()

    expect(router.currentRoute.value.params.id).toBe('strategy-retirement')
    expect(strategiesApi.findAllocation).toHaveBeenLastCalledWith('strategy-retirement')
    expect(strategiesApi.planContribution).toHaveBeenLastCalledWith('strategy-retirement', 300)

    const back = wrapper.findAll('button').find((button) => button.text() === 'Estratégias')
    await back!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('strategies')
    expect(router.currentRoute.value.query.strategy).toBe('strategy-retirement')
  })

  it('falls back to a generic message and to the allocation name', async () => {
    vi.mocked(strategiesApi.findAllocation).mockResolvedValue(
      strategyAllocation({ strategyId: 'strategy-unknown', strategyName: 'Antiga' }),
    )
    vi.mocked(strategiesApi.planContribution).mockRejectedValueOnce(new Error('offline'))
    const wrapper = await mountPlanner('strategy-unknown')

    expect(wrapper.find('.page-desc').text()).toBe('Antiga')
    await typeAmount(wrapper, '100')
    expect(wrapper.find('[data-testid="plan-error"]').text()).toContain(
      'Não foi possível calcular a sugestão.',
    )
  })

  it('leaves out plan lines for holdings the allocation does not know', async () => {
    vi.mocked(strategiesApi.findAllocation).mockResolvedValue(
      strategyAllocation({ classes: [], totalValue: 0 }),
    )
    vi.mocked(strategiesApi.planContribution).mockResolvedValue(
      contributionPlan({ totalValueAfter: 0 }),
    )
    const wrapper = await mountPlanner()

    await typeAmount(wrapper, '300')

    expect(rowsText(wrapper)).toEqual([])
    expect(wrapper.find('[data-testid="plan-holdings-empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="plan-summary"]').text()).toContain('1 de 0')
  })
})
