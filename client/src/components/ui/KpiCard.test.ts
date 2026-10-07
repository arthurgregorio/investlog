import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import KpiCard from './KpiCard.vue'

describe('KpiCard', () => {
  it('renders the label and the value inside a KPI card', () => {
    const wrapper = mount(KpiCard, {
      props: { label: 'Total investido', value: 'R$ 1,2k', valueTestId: 'kpi-invested' },
    })

    expect(wrapper.classes()).toContain('kpi-card')
    expect(wrapper.get('.kpi-label').text()).toBe('Total investido')
    expect(wrapper.get('[data-testid="kpi-invested"]').text()).toBe('R$ 1,2k')
  })

  it('applies the value class to the value only', () => {
    const wrapper = mount(KpiCard, {
      props: { label: 'Resultado', value: '+R$ 10,00', valueClass: 'gl-up' },
    })

    expect(wrapper.get('.kpi-value').classes()).toContain('gl-up')
    expect(wrapper.get('.kpi-label').classes()).not.toContain('gl-up')
  })

  it('renders the foot slot inside the KPI foot', () => {
    const wrapper = mount(KpiCard, {
      props: { label: 'Taxas pagas', value: 'R$ 0,00' },
      slots: { foot: '<span class="kpi-sub">corretagem e custos</span>' },
    })

    expect(wrapper.get('.kpi-foot .kpi-sub').text()).toBe('corretagem e custos')
  })
})
