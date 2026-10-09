import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import InvestmentsToolbar from './InvestmentsToolbar.vue'
import type { AssetType, WalletResponse } from '@/types'

const stockWallet: WalletResponse = {
  id: 'wallet-1',
  name: 'Carteira B3',
  kind: 'STOCKS',
  currency: 'BRL',
  holdingCount: 1,
  totalInvested: 1000,
  currentValue: 1100,
  gain: 100,
  gainPct: 10,
  createdAt: '2026-01-01T00:00:00Z',
}

const stockTypes: AssetType[] = [
  { id: 'type-1', name: 'Ação PN', usageCount: 1 },
  { id: 'type-2', name: 'FII', usageCount: 2 },
]

function mountToolbar(props: Partial<InstanceType<typeof InvestmentsToolbar>['$props']> = {}) {
  return mount(InvestmentsToolbar, {
    props: {
      activeFilter: 'all',
      search: '',
      walletOptions: [stockWallet],
      typeLabelOptions: [],
      ...props,
    },
  })
}

describe('InvestmentsToolbar', () => {
  it('renders the four kind tabs with the active one selected', () => {
    const wrapper = mountToolbar({ activeFilter: 'CRYPTO' })

    const tabs = wrapper.findAll('[role="tab"]')
    expect(tabs.map((tab) => tab.text())).toEqual(['Todos', 'Ações', 'Cripto', 'Fundos'])
    expect(wrapper.get('[role="tab"][aria-selected="true"]').text()).toBe('Cripto')
  })

  it('emits the clicked kind', async () => {
    const wrapper = mountToolbar()

    await wrapper.findAll('[role="tab"]')[3].trigger('click')

    expect(wrapper.emitted('select-tab')).toEqual([['FUNDS']])
  })

  it('lists the wallets labelled with their kind and emits the chosen one', async () => {
    const wrapper = mountToolbar({ walletId: 'wallet-1' })
    const walletSelect = wrapper.get('select')

    expect(walletSelect.findAll('option').map((option) => option.text())).toEqual([
      'Todas as carteiras',
      '[Ações] Carteira B3',
    ])
    expect((walletSelect.element as HTMLSelectElement).value).toBe('wallet-1')

    await walletSelect.setValue('')

    expect(wrapper.emitted('change-wallet')).toEqual([['']])
  })

  it('hides the wallet and type selects when they have no options', () => {
    const wrapper = mountToolbar({ walletOptions: [] })

    expect(wrapper.find('select').exists()).toBe(false)
  })

  it('emits the chosen type label', async () => {
    const wrapper = mountToolbar({ walletOptions: [], typeLabelOptions: stockTypes })
    const typeSelect = wrapper.get('select')

    expect(typeSelect.findAll('option').map((option) => option.text())).toEqual([
      'Todos os tipos',
      'Ação PN',
      'FII',
    ])

    await typeSelect.setValue('FII')

    expect(wrapper.emitted('change-type')).toEqual([['FII']])
  })

  it('shows the current search and emits every change', async () => {
    const wrapper = mountToolbar({ search: 'pe' })
    const searchInput = wrapper.get<HTMLInputElement>('.search-input input')

    expect(searchInput.element.value).toBe('pe')

    await searchInput.setValue('petr')

    expect(wrapper.emitted('change-search')).toEqual([['petr']])
  })

  it('emits add-investment and export from its buttons', async () => {
    const wrapper = mountToolbar()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Adicionar investimento')!
      .trigger('click')
    await wrapper.get('button[aria-label="Exportar relatório"]').trigger('click')

    expect(wrapper.emitted('add-investment')).toHaveLength(1)
    expect(wrapper.emitted('export')).toHaveLength(1)
  })
})
