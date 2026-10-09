import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import ReportHeader from './ReportHeader.vue'

function mountHeader(filterLabels: string[] = []) {
  return mount(ReportHeader, {
    props: {
      generatedAt: new Date(2026, 9, 7, 9, 5),
      filterLabels,
      grandTotals: { costBasis: 2300, currentValue: 2895, gain: 595, gainPct: 25.87 },
    },
    global: { plugins: [createTestingPinia()] },
  })
}

describe('ReportHeader', () => {
  it('shows the brand and the report title', () => {
    const wrapper = mountHeader()

    expect(wrapper.find('.brand-name').text()).toBe('InvestLog')
    expect(wrapper.find('.report-brand-url').text()).toBe('investlog.com.br')
    expect(wrapper.find('h1.report-title').text()).toBe('Relatório de investimentos')
  })

  it('states when the report was generated, with zero-padded time', () => {
    const wrapper = mountHeader()

    expect(wrapper.find('.report-meta').text()).toBe('Gerado em 7 out 2026 às 09:05')
  })

  it('lists the active filters when there are any', () => {
    const wrapper = mountHeader(['Ações', '"itau"'])

    expect(wrapper.find('.report-meta').text()).toContain('Filtros: Ações, "itau"')
  })

  it('renders the grand total figures', () => {
    const grandTotal = mountHeader().find('.report-grand-total')

    expect(grandTotal.findAll('.report-figure-value').map((value) => value.text())).toEqual([
      'R$ 2.300,00',
      'R$ 2.895,00',
    ])
    expect(grandTotal.text()).toContain('+R$ 595,00')
  })
})
