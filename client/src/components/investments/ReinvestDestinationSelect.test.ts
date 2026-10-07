import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ReinvestDestinationSelect from './ReinvestDestinationSelect.vue'
import type { DestinationGroup } from '@/composables/useReinvestForm'
import type { HoldingRow } from '@/types'

function holdingOf(overrides: Partial<HoldingRow>): HoldingRow {
  return {
    id: 'holding-vale3',
    kind: 'STOCKS',
    name: 'Vale',
    ticker: 'VALE3',
    typeLabel: 'Ação ON',
    segmentLabel: null,
    walletId: 'wallet-stocks',
    walletName: 'Ações',
    walletCurrency: 'BRL',
    quantity: 100,
    costBasis: 3500,
    currentPrice: 60,
    currentValue: 6000,
    gain: 2500,
    gainPct: 71,
    frozen: false,
    ...overrides,
  }
}

const vale = holdingOf({})
const treasury = holdingOf({
  id: 'holding-treasury',
  kind: 'FUNDS',
  ticker: null,
  name: 'Tesouro Selic',
})
const unpricedBitcoin = holdingOf({
  id: 'holding-btc',
  kind: 'CRYPTO',
  ticker: 'BTC',
  currentPrice: null,
})

const groups: DestinationGroup[] = [
  { walletName: 'Ações', holdings: [vale] },
  { walletName: 'Fundos', holdings: [treasury] },
]

function mountSelect(
  props: Partial<InstanceType<typeof ReinvestDestinationSelect>['$props']> = {},
) {
  return mount(ReinvestDestinationSelect, {
    props: { modelValue: '', groups, currency: 'BRL', disabled: false, ...props },
  })
}

describe('ReinvestDestinationSelect', () => {
  it('groups the destinations by wallet and names them by ticker or name', () => {
    const wrapper = mountSelect()

    const optgroups = wrapper.findAll('optgroup')
    expect(optgroups.map((group) => group.attributes('label'))).toEqual(['Ações', 'Fundos'])
    expect(wrapper.findAll('option:not([disabled])').map((option) => option.text())).toEqual([
      'VALE3',
      'Tesouro Selic',
    ])
  })

  it('emits the chosen destination', async () => {
    const wrapper = mountSelect()

    await wrapper.find('select[data-testid="reinvest-destination"]').setValue('holding-treasury')

    expect(wrapper.emitted('update:modelValue')?.slice(-1)[0]).toEqual(['holding-treasury'])
  })

  it('is disabled and silent before a source is chosen', () => {
    const wrapper = mountSelect({ disabled: true, groups: [] })

    expect(wrapper.find('select').attributes('disabled')).toBeDefined()
    expect(wrapper.find('.help').exists()).toBe(false)
  })

  it('explains when no destination shares the source currency', () => {
    const wrapper = mountSelect({ groups: [], currency: 'USD' })

    expect(wrapper.find('.help').text()).toBe(
      'Nenhum outro investimento ativo em carteiras de USD para receber o reinvestimento.',
    )
  })

  it('flags an unpriced destination', () => {
    const wrapper = mountSelect({ unpricedDestination: unpricedBitcoin })

    expect(wrapper.find('.help').text()).toBe(
      'BTC não tem preço atual. Defina o preço atual do investimento de destino antes de reinvestir nele.',
    )
    expect(wrapper.find('.help').classes()).toContain('is-danger')
  })
})
