import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import MoveHoldingList from './MoveHoldingList.vue'
import type { MoveQuantity } from '@/composables/useMoveHoldingsForm'
import type { HoldingRow } from '@/types'

function holdingOf(overrides: Partial<HoldingRow>): HoldingRow {
  return {
    id: 'holding-petr4',
    kind: 'STOCKS',
    name: 'Petrobras',
    ticker: 'PETR4',
    typeLabel: 'Ação ON',
    segmentLabel: null,
    walletId: 'wallet-origin',
    walletName: 'Origem',
    walletCurrency: 'BRL',
    quantity: 30,
    costBasis: 900,
    currentPrice: 35,
    currentValue: 1050,
    gain: 150,
    gainPct: 16.67,
    frozen: false,
    ...overrides,
  }
}

const petrobras = holdingOf({})
const fund = holdingOf({
  id: 'holding-fund',
  kind: 'FUNDS',
  ticker: null,
  name: 'Tesouro Selic',
  quantity: null,
  costBasis: 1000,
})

function mountList(
  props: {
    holdings?: HoldingRow[]
    loading?: boolean
    selected?: Record<string, boolean>
    quantities?: Record<string, MoveQuantity>
    moveAll?: boolean
  } = {},
) {
  return mount(MoveHoldingList, {
    props: {
      holdings: [petrobras, fund],
      loading: false,
      selected: {},
      quantities: {},
      moveAll: false,
      ...props,
    },
  })
}

describe('MoveHoldingList', () => {
  it('lists each holding with its available quantity or cost basis', () => {
    const wrapper = mountList()

    const items = wrapper.findAll('[data-testid="move-item"]')
    expect(items).toHaveLength(2)
    expect(items[0]!.text()).toContain('PETR4')
    expect(items[0]!.text()).toContain('Disponível: 30')
    expect(items[1]!.text()).toContain('Tesouro Selic')
    expect(items[1]!.text()).toContain('R$')
  })

  it('explains an empty wallet once loading is over', () => {
    expect(mountList({ holdings: [] }).text()).toContain(
      'Nenhum investimento ativo nesta carteira.',
    )
    expect(mountList({ holdings: [], loading: true }).text()).not.toContain(
      'Nenhum investimento ativo nesta carteira.',
    )
  })

  it('emits select when a holding is checked', async () => {
    const wrapper = mountList()

    await wrapper.findAll('[data-testid="move-item"] input[type="checkbox"]')[0]!.setValue(true)

    expect(wrapper.emitted('select')).toEqual([['holding-petr4', true]])
  })

  it('shows a quantity input only for a selected stock and emits typed quantities', async () => {
    const wrapper = mountList({ selected: { 'holding-petr4': true, 'holding-fund': true } })

    const quantityInputs = wrapper.findAll('input[data-testid="move-quantity"]')
    expect(quantityInputs).toHaveLength(1)
    expect(wrapper.find('.move-item.is-selected').exists()).toBe(true)

    await quantityInputs[0]!.setValue('12')

    expect(wrapper.emitted('update-quantity')?.slice(-1)[0]).toEqual(['holding-petr4', 12])
  })

  it('warns when the typed quantity exceeds the position', () => {
    const wrapper = mountList({
      selected: { 'holding-petr4': true },
      quantities: { 'holding-petr4': 31 },
    })

    expect(wrapper.text()).toContain('Maior que o disponível')
  })

  it('selects and locks every holding with "Mover tudo", hiding the quantity inputs', () => {
    const wrapper = mountList({ moveAll: true, selected: { 'holding-petr4': true } })

    expect(wrapper.findAll('.move-item.is-selected')).toHaveLength(2)
    expect(wrapper.find('[data-testid="move-quantity"]').exists()).toBe(false)
    const checkboxes = wrapper.findAll('[data-testid="move-item"] input[type="checkbox"]')
    expect(checkboxes.every((checkbox) => checkbox.attributes('disabled') !== undefined)).toBe(true)
  })

  it('toggles "Mover tudo" through v-model and disables it on an empty list', async () => {
    const wrapper = mountList()

    await wrapper.find('[data-testid="move-all"] input').setValue(true)
    expect(wrapper.emitted('update:moveAll')).toEqual([[true]])

    const emptyWrapper = mountList({ holdings: [] })
    expect(emptyWrapper.find('[data-testid="move-all"] input').attributes('disabled')).toBeDefined()
  })
})
