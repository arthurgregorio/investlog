import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AllocationLegend, { type AllocationLegendEntry } from './AllocationLegend.vue'

const entries: AllocationLegendEntry[] = [
  {
    key: 'PETR4',
    label: 'PETR4',
    name: 'Petrobras',
    color: 'var(--chart-1)',
    share: '60,00%',
    value: 6000,
    relativeShare: 100,
  },
  { key: 'others', label: 'Outros', color: 'var(--text-muted)', share: '40,00%', value: 4000 },
]

function mountLegend(detailed = false) {
  return mount(AllocationLegend, { props: { entries, currency: 'BRL', detailed } })
}

function legendRows(wrapper: ReturnType<typeof mountLegend>) {
  return wrapper.findAll('[data-testid="allocation-legend-entry"]')
}

describe('AllocationLegend', () => {
  it('renders one row per entry with its colour, label, share and compact value', () => {
    const [first, second] = legendRows(mountLegend())

    expect((first.get('.legend-dot').element as HTMLElement).style.background).toBe(
      'var(--chart-1)',
    )
    expect(first.get('.legend-label').text()).toContain('PETR4')
    expect(first.get('.legend-pct').text()).toBe('60,00%')
    expect(first.get('.legend-value').text()).toBe('R$ 6,0k')
    expect(second.get('.legend-label').text()).toBe('Outros')
  })

  it('shows the entry name only when there is one', () => {
    const [first, second] = legendRows(mountLegend())

    expect(first.get('.legend-label').text()).toContain('Petrobras')
    expect(second.findAll('.legend-label span')).toHaveLength(1)
  })

  it('leaves out the share bars in the compact layout', () => {
    const wrapper = mountLegend()

    expect(wrapper.get('[data-testid="allocation-legend"]').classes()).not.toContain('is-detailed')
    expect(wrapper.find('progress').exists()).toBe(false)
  })

  it('adds a share bar per entry and the full value in the detailed layout', () => {
    const wrapper = mountLegend(true)

    expect(wrapper.get('[data-testid="allocation-legend"]').classes()).toContain('is-detailed')
    expect(wrapper.findAll('progress')).toHaveLength(2)
    expect(wrapper.get('progress').attributes('value')).toBe('100')
    expect(legendRows(wrapper)[0].get('.legend-value').text()).toBe('R$ 6.000,00')
  })
})
