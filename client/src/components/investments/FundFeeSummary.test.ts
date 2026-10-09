import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import FundFeeSummary from './FundFeeSummary.vue'

function mountSummary(administrationFeeRate: number | null, performanceFeeRate: number | null) {
  return mount(FundFeeSummary, { props: { administrationFeeRate, performanceFeeRate } })
}

function rate(wrapper: ReturnType<typeof mountSummary>, testId: string) {
  return wrapper.get(`[data-testid="${testId}"]`).text()
}

describe('FundFeeSummary', () => {
  it('labels both rates with their units', () => {
    const wrapper = mountSummary(null, null)

    expect(wrapper.text()).toContain('Taxa de administração (% a.a.)')
    expect(wrapper.text()).toContain('Taxa de performance (%)')
  })

  it('shows both rates as percentages', () => {
    const wrapper = mountSummary(0.5, 20)

    expect(rate(wrapper, 'administration-fee-rate')).toBe('0,50%')
    expect(rate(wrapper, 'performance-fee-rate')).toBe('20,00%')
  })

  it('shows an em dash for a null rate and 0,00% for a zero rate', () => {
    const wrapper = mountSummary(null, 0)

    expect(rate(wrapper, 'administration-fee-rate')).toBe('—')
    expect(rate(wrapper, 'performance-fee-rate')).toBe('0,00%')
  })
})
