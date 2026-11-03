import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import DefaultStrategyPanel from './DefaultStrategyPanel.vue'
import { strategiesApi } from '@/api/strategies'
import { classHolding, defaultStrategy } from '@/test/strategyFixtures'

vi.mock('@/api/strategies', () => ({ strategiesApi: { findClassHoldings: vi.fn() } }))

describe('DefaultStrategyPanel', () => {
  it('lists the holdings in Não definida and offers to distribute them', async () => {
    vi.mocked(strategiesApi.findClassHoldings).mockResolvedValue([
      classHolding({ targetPercentage: null }),
    ])
    const wrapper = mount(DefaultStrategyPanel, { props: { strategy: defaultStrategy() } })
    await flushPromises()

    expect(strategiesApi.findClassHoldings).toHaveBeenCalledWith('class-undefined')
    expect(wrapper.find('input').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('Sem meta · 2 investimentos')
    expect(wrapper.findAll('[data-testid="undefined-holding"]')).toHaveLength(1)

    await wrapper.find('[data-testid="distribute-holdings"]').trigger('click')
    expect(wrapper.emitted('distribute')).toEqual([['class-undefined']])
  })

  it('says so when nothing is left in Não definida', async () => {
    vi.mocked(strategiesApi.findClassHoldings).mockResolvedValue([])
    const wrapper = mount(DefaultStrategyPanel, { props: { strategy: defaultStrategy() } })
    await flushPromises()

    expect(wrapper.find('[data-testid="undefined-empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="distribute-holdings"]').attributes('disabled')).toBeDefined()
  })

  it('loads nothing for a strategy without classes', async () => {
    vi.mocked(strategiesApi.findClassHoldings).mockClear()
    mount(DefaultStrategyPanel, { props: { strategy: defaultStrategy({ classes: [] }) } })
    await flushPromises()

    expect(strategiesApi.findClassHoldings).not.toHaveBeenCalled()
  })
})
