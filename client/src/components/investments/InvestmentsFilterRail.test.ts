import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import InvestmentsFilterRail from './InvestmentsFilterRail.vue'
import type { InvestmentKindFilter } from '@/composables/useInvestmentFilters'
import type { AssetType, WalletResponse } from '@/types'

function walletOf(id: string, name: string, kind: WalletResponse['kind'], holdingCount: number) {
  return {
    id,
    name,
    kind,
    currency: 'BRL',
    holdingCount,
    totalInvested: 0,
    currentValue: 0,
    gain: 0,
    gainPct: 0,
    cash: 0,
    cashReconciled: true,
    createdAt: '2026-01-01T00:00:00Z',
  } satisfies WalletResponse
}

const stockTypes: AssetType[] = [
  { id: 'type-1', name: 'FII', usageCount: 1 },
  { id: 'type-2', name: 'ETF', usageCount: 1 },
]

interface RailProps {
  activeFilter: InvestmentKindFilter
  walletIds: string[]
  typeLabels: string[]
  hideFrozen: boolean
  activeFilterCount: number
  classCounts: Partial<Record<InvestmentKindFilter, number>>
  walletOptions: WalletResponse[]
  typeOptions: AssetType[]
}

function mountRail(props: Partial<RailProps> = {}) {
  return mount(InvestmentsFilterRail, {
    props: {
      activeFilter: 'STOCKS',
      walletIds: ['wallet-1'],
      typeLabels: ['ETF'],
      hideFrozen: false,
      activeFilterCount: 3,
      classCounts: { all: 16, STOCKS: 9, CRYPTO: 3, FUNDS: 4 },
      walletOptions: [
        walletOf('wallet-1', 'Ações BR', 'STOCKS', 8),
        walletOf('wallet-2', 'Ações EUA', 'STOCKS', 1),
      ],
      typeOptions: stockTypes,
      ...props,
    },
  })
}

describe('InvestmentsFilterRail', () => {
  it('heads the rail with the number of active filters and a clear button', async () => {
    const wrapper = mountRail()

    expect(wrapper.get('[data-testid="active-filter-count"]').text()).toBe('3')
    await wrapper.get('[data-testid="clear-filters"]').trigger('click')

    expect(wrapper.emitted('clear')).toHaveLength(1)
  })

  it('groups each filter in a labelled fieldset', () => {
    expect(
      mountRail()
        .findAll('legend')
        .map((legend) => legend.text()),
    ).toEqual(['Classe', 'Carteiras', 'Tipo de ativo', 'Situação'])
  })

  it('marks the active class and emits the chosen one', async () => {
    const wrapper = mountRail()

    expect(wrapper.get<HTMLInputElement>('[data-testid="class-STOCKS"]').element.checked).toBe(true)
    expect(
      wrapper.get('[data-testid="class-STOCKS"]').element.closest('label')!.classList,
    ).toContain('is-selected')
    await wrapper.get('[data-testid="class-FUNDS"]').trigger('change')

    expect(wrapper.emitted('select-class')).toEqual([['FUNDS']])
  })

  it('counts the classes only once the counts are known', () => {
    expect(
      mountRail()
        .findAll('fieldset')[0]
        .findAll('.rail-side')
        .map((count) => count.text()),
    ).toEqual(['16', '9', '3', '4'])
    expect(
      mountRail({ classCounts: {} }).findAll('fieldset')[0].findAll('.rail-side'),
    ).toHaveLength(0)
  })

  it('ticks the selected wallets, shows their holding counts and emits a toggle', async () => {
    const wrapper = mountRail()

    expect(wrapper.get<HTMLInputElement>('[data-testid="wallet-wallet-1"]').element.checked).toBe(
      true,
    )
    expect(wrapper.get<HTMLInputElement>('[data-testid="wallet-wallet-2"]').element.checked).toBe(
      false,
    )
    expect(
      wrapper
        .findAll('fieldset')[1]
        .findAll('.rail-side')
        .map((count) => count.text()),
    ).toEqual(['8', '1'])
    await wrapper.get('[data-testid="wallet-wallet-2"]').trigger('change')

    expect(wrapper.emitted('toggle-wallet')).toEqual([['wallet-2']])
  })

  it('ticks the selected types and emits a toggle', async () => {
    const wrapper = mountRail()

    expect(wrapper.get<HTMLInputElement>('[data-testid="type-ETF"]').element.checked).toBe(true)
    expect(wrapper.get<HTMLInputElement>('[data-testid="type-FII"]').element.checked).toBe(false)
    await wrapper.get('[data-testid="type-FII"]').trigger('change')

    expect(wrapper.emitted('toggle-type')).toEqual([['FII']])
  })

  it.each([
    ['all', 'Escolha Ações ou Fundos para filtrar por tipo.'],
    ['CRYPTO', 'Cripto não tem tipos de ativo.'],
  ] as const)('explains the missing types under %s', (activeFilter, hint) => {
    const wrapper = mountRail({ activeFilter, typeOptions: [] })

    expect(wrapper.get('[data-testid="type-hint"]').text()).toBe(hint)
  })

  it('emits the hide-frozen box state', async () => {
    const wrapper = mountRail({ hideFrozen: true })
    const checkbox = wrapper.get<HTMLInputElement>('[data-testid="hide-frozen"]')

    expect(checkbox.element.checked).toBe(true)
    await checkbox.setValue(false)

    expect(wrapper.emitted('change-hide-frozen')).toEqual([[false]])
  })
})
