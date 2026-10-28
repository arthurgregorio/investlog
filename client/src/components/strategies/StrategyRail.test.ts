import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import StrategyRail from './StrategyRail.vue'
import { defaultStrategy, strategy } from '@/test/strategyFixtures'

describe('StrategyRail', () => {
  it('lists every strategy with its holding count and marks the selected one', async () => {
    const wrapper = mount(StrategyRail, {
      props: { strategies: [strategy(), defaultStrategy()], selectedId: 'strategy-long' },
    })

    const options = wrapper.findAll('.rail-option')
    expect(options.map((option) => option.find('.rail-label').text())).toEqual([
      'Longo prazo',
      'Sem estratégia',
    ])
    expect(options.map((option) => option.find('.rail-side').text())).toEqual(['4', '2'])
    expect(options[0].classes()).toContain('is-selected')
    expect(options[1].find('.rail-dot').attributes('style')).toContain('var(--text-muted)')

    await wrapper.find('[data-testid="strategy-strategy-default"]').trigger('change')
    expect(wrapper.emitted('select')).toEqual([['strategy-default']])
  })
})
