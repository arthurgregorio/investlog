import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import ClassHoldingTargets from './ClassHoldingTargets.vue'
import { strategiesApi } from '@/api/strategies'
import { useStrategiesStore } from '@/stores/strategies'
import { classHolding, strategyClass } from '@/test/strategyFixtures'

vi.mock('@/api/strategies', () => ({ strategiesApi: { findClassHoldings: vi.fn() } }))

const HOLDINGS = [
  classHolding(),
  classHolding({
    id: 'holding-vale3',
    ticker: 'VALE3',
    name: 'Vale ON',
    targetPercentage: null,
    effectiveTargetPercentage: 35,
  }),
  classHolding({
    id: 'holding-fund',
    kind: 'FUNDS',
    ticker: null,
    name: 'BB Renda Fixa',
    segmentLabel: null,
    typeLabel: 'Fundo de Renda Fixa',
    frozen: true,
    targetPercentage: null,
    effectiveTargetPercentage: 35,
  }),
]

let activeWrapper: VueWrapper | undefined

async function mountTargets() {
  const pinia = createTestingPinia()
  activeWrapper = mount(ClassHoldingTargets, {
    props: { strategyClass: strategyClass() },
    global: { plugins: [pinia] },
    attachTo: document.body,
  })
  await flushPromises()
  return activeWrapper
}

function targetInputs(wrapper: VueWrapper) {
  return wrapper.findAll('input[data-testid="holding-target"]')
}

function rows(wrapper: VueWrapper) {
  return wrapper.findAll('[data-testid="holding-target-row"]')
}

function saveButton(wrapper: VueWrapper) {
  return wrapper.find('[data-testid="save-targets"]')
}

beforeEach(() => {
  vi.mocked(strategiesApi.findClassHoldings).mockResolvedValue(HOLDINGS)
})

afterEach(() => {
  activeWrapper?.unmount()
  activeWrapper = undefined
})

describe('ClassHoldingTargets', () => {
  it('shows typed targets, the blank share as placeholder and each target in the strategy', async () => {
    const wrapper = await mountTargets()

    expect(strategiesApi.findClassHoldings).toHaveBeenCalledWith('class-brazil')
    expect(wrapper.text()).toContain('Metas dos investimentos · Ações Brasil')
    expect(wrapper.text()).toContain(
      'Meta da classe: 60,00% da estratégia · 1 de 3 com meta digitada',
    )
    expect(wrapper.find('[data-testid="holding-targets-sum"]').text()).toBe(
      'Digitado: 30,00% · 2 em branco dividem 70,00%',
    )
    expect((targetInputs(wrapper)[0].element as HTMLInputElement).value).toBe('30')
    expect(targetInputs(wrapper)[1].attributes('placeholder')).toBe('35,00')
    expect(rows(wrapper)[0].text()).toContain('18,00%')
    expect(rows(wrapper)[1].text()).toContain('do restante')
    expect(rows(wrapper)[1].text()).toContain('21,00%')
    expect(rows(wrapper)[2].classes()).toContain('is-frozen')
    expect(rows(wrapper)[2].text()).toContain('Fundo de investimento')
    expect(rows(wrapper)[2].text()).toContain('Fundo de Renda Fixa')
    expect(saveButton(wrapper).attributes('disabled')).toBeDefined()
  })

  it('blocks saving when the typed targets pass 100%', async () => {
    const wrapper = await mountTargets()

    await targetInputs(wrapper)[1].setValue('80')

    expect(wrapper.find('[data-testid="holding-targets-sum"]').text()).toBe(
      'Digitado: 110,00% · passa 10,00%',
    )
    expect(wrapper.find('[data-testid="holding-targets-error"]').text()).toBe(
      'As metas digitadas passam de 100% da classe.',
    )
    expect(saveButton(wrapper).attributes('disabled')).toBeDefined()
  })

  it('needs a fully typed class to add up to 100%', async () => {
    const wrapper = await mountTargets()

    await targetInputs(wrapper)[1].setValue('30')
    await targetInputs(wrapper)[2].setValue('30')

    expect(wrapper.find('[data-testid="holding-targets-sum"]').text()).toBe('Digitado: 90,00%')
    expect(wrapper.find('[data-testid="holding-targets-error"]').text()).toContain(
      'precisam somar 100% da classe',
    )

    await targetInputs(wrapper)[2].setValue('40')
    expect(saveButton(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('saves every holding with its typed target or none, and discards a draft', async () => {
    const wrapper = await mountTargets()
    const store = useStrategiesStore()
    vi.mocked(store.saveTargets).mockResolvedValue(HOLDINGS)

    await targetInputs(wrapper)[0].setValue('')
    await wrapper.find('[data-testid="discard-targets"]').trigger('click')
    expect((targetInputs(wrapper)[0].element as HTMLInputElement).value).toBe('30')

    await targetInputs(wrapper)[1].setValue('20')
    await saveButton(wrapper).trigger('click')
    await flushPromises()

    expect(store.saveTargets).toHaveBeenCalledWith('class-brazil', [
      { kind: 'STOCKS', holdingId: 'holding-petr4', targetPercentage: 30 },
      { kind: 'STOCKS', holdingId: 'holding-vale3', targetPercentage: 20 },
      { kind: 'FUNDS', holdingId: 'holding-fund', targetPercentage: null },
    ])
    expect(saveButton(wrapper).attributes('disabled')).toBeDefined()
  })

  it('says so when the class has no holdings and reloads for another class', async () => {
    vi.mocked(strategiesApi.findClassHoldings).mockResolvedValue([])
    const wrapper = await mountTargets()

    expect(wrapper.find('[data-testid="class-holdings-empty"]').exists()).toBe(true)

    await wrapper.setProps({
      strategyClass: strategyClass({ id: 'class-abroad', targetPercentage: null }),
    })
    await flushPromises()
    expect(strategiesApi.findClassHoldings).toHaveBeenLastCalledWith('class-abroad')
    expect(wrapper.text()).toContain('Meta da classe: 0,00% da estratégia')
  })
})
