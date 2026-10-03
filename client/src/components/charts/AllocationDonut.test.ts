import { describe, expect, it } from 'vitest'
import { defineComponent, h, type PropType } from 'vue'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import AllocationDonut from './AllocationDonut.vue'
import type { HoldingRow } from '@/types'

const DonutChartStub = defineComponent({
  props: {
    segments: { type: Array as PropType<{ label: string; value: number }[]>, required: true },
  },
  setup(props, { slots }) {
    return () =>
      h('div', { 'data-testid': 'donut-stub' }, [
        ...props.segments.map((segment) =>
          h('span', { 'data-testid': 'donut-segment' }, `${segment.label}=${segment.value}`),
        ),
        slots.default?.(),
      ])
  },
})

function rowOf(overrides: Partial<HoldingRow>): HoldingRow {
  return {
    id: 'holding-1',
    kind: 'STOCKS',
    name: 'Petrobras',
    ticker: 'PETR4',
    typeLabel: 'Ação ON',
    walletId: 'wallet-1',
    walletName: 'Wallet',
    walletCurrency: 'BRL',
    quantity: 10,
    costBasis: 100,
    currentPrice: 10,
    currentValue: 100,
    gain: 0,
    gainPct: 0,
    ...overrides,
  }
}

function mountDonut(rows: HoldingRow[]) {
  return mount(AllocationDonut, {
    props: { rows, kind: 'STOCKS', currency: 'BRL' },
    global: { plugins: [createTestingPinia()], stubs: { DonutChart: DonutChartStub } },
  })
}

function legendTexts(wrapper: ReturnType<typeof mountDonut>) {
  return wrapper.findAll('[data-testid="allocation-legend-entry"]').map((entry) => entry.text())
}

const mixedRows = [
  rowOf({ id: 'a', ticker: 'AAAA3', costBasis: 700, currentValue: 250 }),
  rowOf({ id: 'b', ticker: 'BBBB3', costBasis: 200, currentValue: 750 }),
]

describe('AllocationDonut', () => {
  it('lists each asset with its percentage and value, ordered by share descending', () => {
    const wrapper = mountDonut(mixedRows)

    const entries = legendTexts(wrapper)
    expect(entries).toHaveLength(2)
    expect(entries[0]).toContain('BBBB3')
    expect(entries[0]).toContain('75,00%')
    expect(entries[0]).toContain('R$ 750,00')
    expect(entries[1]).toContain('AAAA3')
    expect(entries[1]).toContain('25,00%')
    expect(entries[1]).toContain('R$ 250,00')
  })

  it('feeds the donut the same segments the legend lists', () => {
    const wrapper = mountDonut(mixedRows)

    const segments = wrapper
      .findAll('[data-testid="donut-segment"]')
      .map((segment) => segment.text())
    expect(segments).toEqual(['BBBB3=750', 'AAAA3=250'])
  })

  it('defaults to Valor atual and switches to Investido and back', async () => {
    const wrapper = mountDonut(mixedRows)

    expect(
      wrapper.find('[data-testid="allocation-metric-currentValue"]').attributes('aria-pressed'),
    ).toBe('true')
    expect(legendTexts(wrapper)[0]).toContain('BBBB3')

    await wrapper.find('[data-testid="allocation-metric-costBasis"]').trigger('click')
    const invested = legendTexts(wrapper)
    expect(invested[0]).toContain('AAAA3')
    expect(invested[0]).toContain('77,78%')
    expect(invested[0]).toContain('R$ 700,00')
    expect(invested[1]).toContain('22,22%')

    await wrapper.find('[data-testid="allocation-metric-currentValue"]').trigger('click')
    expect(legendTexts(wrapper)[0]).toContain('BBBB3')
    expect(legendTexts(wrapper)[0]).toContain('75,00%')
  })

  it('shows the active metric and its total in the ring centre', async () => {
    const wrapper = mountDonut(mixedRows)

    expect(wrapper.find('[data-testid="allocation-center-label"]').text()).toBe('Valor atual')
    expect(wrapper.find('[data-testid="allocation-center-value"]').text()).toBe('R$ 1,0k')

    await wrapper.find('[data-testid="allocation-metric-costBasis"]').trigger('click')
    expect(wrapper.find('[data-testid="allocation-center-label"]').text()).toBe('Investido')
    expect(wrapper.find('[data-testid="allocation-center-value"]').text()).toBe('R$ 900,00')
  })

  it('colours the legend and ring from the kind accent, darkest first, with a neutral Outros', () => {
    document.documentElement.style.setProperty('--wt-stocks', '#2b6cb0')
    const wrapper = mountDonut([
      rowOf({ id: 'big', ticker: 'BIGG3', currentValue: 600 }),
      rowOf({ id: 'mid', ticker: 'MIDD3', currentValue: 380 }),
      rowOf({ id: 'small', ticker: 'SMLL3', currentValue: 10 }),
    ])

    const swatches = wrapper
      .findAll('.allocation-legend-swatch')
      .map((swatch) => (swatch.element as HTMLElement).style.background)
    expect(swatches).toHaveLength(3)
    expect(swatches[0]).toBe('rgb(43, 108, 176)')
    expect(swatches[1]).not.toBe(swatches[0])
    expect(swatches[2]).toBe('rgb(154, 163, 173)')
    document.documentElement.style.removeProperty('--wt-stocks')
  })

  it('shows an already merged same-ticker holding as one entry', () => {
    const wrapper = mountDonut([
      rowOf({ id: 'merged', ticker: 'PETR4', currentValue: 600, costBasis: 500 }),
      rowOf({ id: 'other', ticker: 'VALE3', currentValue: 400, costBasis: 400 }),
    ])

    const entries = legendTexts(wrapper)
    expect(entries.filter((entry) => entry.includes('PETR4'))).toHaveLength(1)
    expect(entries[0]).toContain('60,00%')
  })

  it('names the fund when the row has no ticker', () => {
    const wrapper = mountDonut([rowOf({ ticker: null, name: 'Fundo Renda Fixa' })])

    expect(legendTexts(wrapper)[0]).toContain('Fundo Renda Fixa')
  })

  it('groups holdings under 3% into a last Outros entry', () => {
    const wrapper = mountDonut([
      rowOf({ id: 'big', ticker: 'BIGG3', currentValue: 9000 }),
      rowOf({ id: 'small-1', ticker: 'SML1', currentValue: 100 }),
      rowOf({ id: 'small-2', ticker: 'SML2', currentValue: 100 }),
    ])

    const entries = legendTexts(wrapper)
    expect(entries).toHaveLength(2)
    expect(entries[1]).toContain('Outros')
    expect(entries[1]).toContain('R$ 200,00')
    expect(wrapper.text()).not.toContain('SML1')
  })

  it('leaves out a holding without a current price and says so only in Valor atual', async () => {
    const wrapper = mountDonut([
      rowOf({ id: 'priced', ticker: 'AAAA3', currentValue: 100, costBasis: 50 }),
      rowOf({ id: 'unpriced', ticker: 'BBBB3', currentValue: null, costBasis: 50 }),
    ])

    expect(wrapper.text()).not.toContain('BBBB3')
    expect(wrapper.find('[data-testid="allocation-exclusion-note"]').text()).toContain(
      '1 investimento sem preço atual ficou de fora',
    )

    await wrapper.find('[data-testid="allocation-metric-costBasis"]').trigger('click')
    expect(wrapper.text()).toContain('BBBB3')
    expect(wrapper.find('[data-testid="allocation-exclusion-note"]').exists()).toBe(false)
  })

  it('pluralises the exclusion note', () => {
    const wrapper = mountDonut([
      rowOf({ id: 'priced', ticker: 'AAAA3', currentValue: 100 }),
      rowOf({ id: 'unpriced-1', ticker: 'BBBB3', currentValue: null }),
      rowOf({ id: 'unpriced-2', ticker: 'CCCC3', currentValue: null }),
    ])

    expect(wrapper.find('[data-testid="allocation-exclusion-note"]').text()).toContain(
      '2 investimentos sem preço atual ficaram de fora',
    )
  })

  it('shows the empty state instead of a chart when the wallet has no holdings', () => {
    const wrapper = mountDonut([])

    expect(wrapper.text()).toContain('Nenhum investimento nesta carteira')
    expect(wrapper.find('[data-testid="donut-stub"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="allocation-metric-currentValue"]').exists()).toBe(false)
  })

  it('explains when no holding has a value for the metric', () => {
    const wrapper = mountDonut([rowOf({ currentValue: null })])

    expect(wrapper.text()).toContain('Sem valores para exibir')
    expect(wrapper.find('[data-testid="donut-stub"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="allocation-exclusion-note"]').exists()).toBe(true)
  })
})
