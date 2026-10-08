import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import ReportTotalsLine from './ReportTotalsLine.vue'
import { useCurrencyStore } from '@/stores/currency'
import type { ReportTotals } from '@/utils/reportGrouping'

const totals: ReportTotals = { costBasis: 700, currentValue: 750, gain: 50, gainPct: 7.14 }

function mountLine(variant: 'grand' | 'kind' | 'subgroup', displayCurrency = 'BRL') {
  const pinia = createTestingPinia()
  useCurrencyStore().displayCurrency = displayCurrency
  return mount(ReportTotalsLine, {
    props: { totals, variant },
    global: { plugins: [pinia] },
  })
}

describe('ReportTotalsLine', () => {
  it('labels the invested, current and result figures in order', () => {
    const wrapper = mountLine('kind')

    expect(wrapper.findAll('.report-figure-label').map((label) => label.text())).toEqual([
      'Investido',
      'Atual',
      'Resultado',
    ])
    expect(wrapper.findAll('.report-figure-value').map((value) => value.text())).toEqual([
      'R$ 700,00',
      'R$ 750,00',
    ])
    expect(wrapper.find('.report-figure:last-child').text()).toContain('+R$ 50,00')
  })

  it('formats the figures in the display currency', () => {
    const wrapper = mountLine('kind', 'USD')

    expect(wrapper.findAll('.report-figure-value').map((value) => value.text())).toEqual([
      'US$ 700,00',
      'US$ 750,00',
    ])
  })

  it('marks the grand total line so the header can size it down', () => {
    const wrapper = mountLine('grand')

    expect(wrapper.classes()).toEqual(['report-subtotal-line', 'report-grand-total'])
  })

  it('marks the sub-group line with its own modifier', () => {
    const wrapper = mountLine('subgroup')

    expect(wrapper.classes()).toEqual(['report-subtotal-line', 'is-subgroup'])
  })

  it('leaves the kind line unmodified', () => {
    const wrapper = mountLine('kind')

    expect(wrapper.classes()).toEqual(['report-subtotal-line'])
  })
})
