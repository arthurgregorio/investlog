import { describe, expect, it } from 'vitest'
import { defineComponent, h, type PropType } from 'vue'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import AllocationDonut from './AllocationDonut.vue'
import type { HoldingRow } from '@/types'

const DonutChartStub = defineComponent({
  props: {
    segments: {
      type: Array as PropType<{ label: string; value: number; color: string }[]>,
      required: true,
    },
  },
  setup(props, { slots }) {
    return () =>
      h('div', { 'data-testid': 'donut-stub' }, [
        ...props.segments.map((segment) =>
          h(
            'span',
            { 'data-testid': 'donut-segment', 'data-color': segment.color },
            `${segment.label}=${segment.value}`,
          ),
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
    segmentLabel: null,
    walletId: 'wallet-1',
    walletName: 'Wallet',
    walletCurrency: 'BRL',
    quantity: 10,
    costBasis: 100,
    currentPrice: 10,
    currentValue: 100,
    gain: 0,
    gainPct: 0,
    frozen: false,
    ...overrides,
  }
}

function mountDonut(rows: HoldingRow[], groupable = false) {
  return mount(AllocationDonut, {
    props: { rows, currency: 'BRL', groupable },
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

  it('colours each entry with its own theme palette colour and a neutral Outros', () => {
    const wrapper = mountDonut([
      rowOf({ id: 'big', ticker: 'BIGG3', currentValue: 600 }),
      rowOf({ id: 'mid', ticker: 'MIDD3', currentValue: 380 }),
      rowOf({ id: 'small', ticker: 'SMLL3', currentValue: 10 }),
    ])

    const swatches = wrapper
      .findAll('.allocation-legend-swatch')
      .map((swatch) => (swatch.element as HTMLElement).style.background)
    expect(swatches).toEqual(['var(--chart-1)', 'var(--chart-2)', 'var(--text-muted)'])
  })

  it('gives the ring the same variable colours so it re-resolves them when the theme changes', () => {
    const wrapper = mountDonut([
      rowOf({ id: 'big', ticker: 'BIGG3', currentValue: 600 }),
      rowOf({ id: 'mid', ticker: 'MIDD3', currentValue: 380 }),
      rowOf({ id: 'small', ticker: 'SMLL3', currentValue: 10 }),
    ])

    const ringColors = wrapper
      .findAll('[data-testid="donut-segment"]')
      .map((segment) => segment.attributes('data-color'))
    expect(ringColors).toEqual(['var(--chart-1)', 'var(--chart-2)', 'var(--text-muted)'])
  })

  it('shows the company name beside the ticker, and the asset count in the ring centre', () => {
    const wrapper = mountDonut([
      rowOf({ id: 'a', ticker: 'AAAA3', name: 'Empresa A', currentValue: 600 }),
      rowOf({ id: 'b', ticker: 'BBBB3', name: 'Empresa B', currentValue: 400 }),
    ])

    const entries = legendTexts(wrapper)
    expect(entries[0]).toContain('AAAA3')
    expect(entries[0]).toContain('Empresa A')
    expect(wrapper.find('[data-testid="donut-stub"]').text()).toContain('2 ativos')
  })

  it('draws one share bar per legend row, scaled to the largest share', () => {
    const wrapper = mountDonut(mixedRows)

    const bars = wrapper.findAll('progress').map((bar) => Number(bar.attributes('value')))
    expect(bars).toEqual([100, expect.closeTo(33.33, 1)])
  })

  it('strips the bottom margin Bulma gives a progress bar so it stays centred on its legend row', () => {
    const wrapper = mountDonut(mixedRows)

    expect(wrapper.findAll('progress').every((bar) => bar.classes().includes('mb-0'))).toBe(true)
  })

  it('states how much the three largest positions concentrate once there are more than three', () => {
    const wrapper = mountDonut([
      rowOf({ id: 'a', ticker: 'AAAA3', currentValue: 400 }),
      rowOf({ id: 'b', ticker: 'BBBB3', currentValue: 300 }),
      rowOf({ id: 'c', ticker: 'CCCC3', currentValue: 200 }),
      rowOf({ id: 'd', ticker: 'DDDD3', currentValue: 100 }),
    ])

    expect(wrapper.find('[data-testid="allocation-concentration-note"]').text()).toBe(
      'As 3 maiores posições concentram 90,00% do total.',
    )
  })

  it('leaves out the concentration note for three holdings or fewer', () => {
    const wrapper = mountDonut(mixedRows)

    expect(wrapper.find('[data-testid="allocation-concentration-note"]').exists()).toBe(false)
  })

  it('explains what Outros groups', () => {
    const wrapper = mountDonut([
      rowOf({ id: 'big', ticker: 'BIGG3', currentValue: 9000 }),
      rowOf({ id: 'small', ticker: 'SMLL3', currentValue: 100 }),
    ])

    expect(wrapper.find('[data-testid="allocation-concentration-note"]').text()).toBe(
      '“Outros” reúne os investimentos abaixo de 3%.',
    )
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

  describe('grouping by segment', () => {
    const segmentedRows = [
      rowOf({ id: 'a', ticker: 'TAEE11', segmentLabel: 'Energia', currentValue: 300 }),
      rowOf({ id: 'b', ticker: 'EGIE3', segmentLabel: 'Energia', currentValue: 200 }),
      rowOf({ id: 'c', ticker: 'WEGE3', segmentLabel: 'Indústria', currentValue: 250 }),
      rowOf({ id: 'd', ticker: 'PETR4', segmentLabel: null, currentValue: 250 }),
    ]

    it('offers no grouping control unless the wallet is groupable', () => {
      const wrapper = mountDonut(segmentedRows)

      expect(wrapper.find('[data-testid="allocation-grouping-segment"]').exists()).toBe(false)
    })

    it('switches to one entry per segment with a neutral Sem segmento and back', async () => {
      const wrapper = mountDonut(segmentedRows, true)
      expect(legendTexts(wrapper)).toHaveLength(4)
      expect(wrapper.text()).toContain('Participação de cada investimento na carteira')

      await wrapper.find('[data-testid="allocation-grouping-segment"]').trigger('click')

      const entries = legendTexts(wrapper)
      expect(entries).toHaveLength(3)
      expect(entries[0]).toContain('Energia')
      expect(entries[0]).toContain('2 investimentos')
      expect(entries[0]).toContain('50,00%')
      expect(wrapper.text()).toContain('Participação de cada segmento na carteira')
      const colors = wrapper
        .findAll('[data-testid="donut-segment"]')
        .map((segment) => [segment.text(), segment.attributes('data-color')])
      expect(colors).toContainEqual(['Sem segmento=250', 'var(--text-muted)'])
      expect(wrapper.find('[data-testid="allocation-concentration-note"]').exists()).toBe(false)

      await wrapper.find('[data-testid="allocation-grouping-holding"]').trigger('click')
      expect(legendTexts(wrapper)).toHaveLength(4)
    })

    it('words the Outros note in segments', async () => {
      const wrapper = mountDonut(
        [
          rowOf({ id: 'a', segmentLabel: 'Energia', currentValue: 980 }),
          rowOf({ id: 'b', segmentLabel: 'Saúde', currentValue: 20 }),
        ],
        true,
      )

      await wrapper.find('[data-testid="allocation-grouping-segment"]').trigger('click')

      expect(wrapper.find('[data-testid="allocation-concentration-note"]').text()).toContain(
        'reúne os segmentos abaixo de 3%',
      )
    })
  })
})
