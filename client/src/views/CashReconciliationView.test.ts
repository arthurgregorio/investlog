import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createRouter, createMemoryHistory } from 'vue-router'
import CashReconciliationView from './CashReconciliationView.vue'
import CashReconciliationCard from '@/components/wallets/CashReconciliationCard.vue'
import { cashApi } from '@/api/cash'
import { useRatesStore } from '@/stores/rates'
import { useWalletsStore } from '@/stores/wallets'
import type { CashReconciliation } from '@/types'

vi.mock('@/api/cash', () => ({ cashApi: { findReconciliation: vi.fn() } }))

function reconciliationOf(walletId: string, walletName: string): CashReconciliation {
  return {
    walletId,
    walletName,
    kind: 'STOCKS',
    currency: 'BRL',
    cash: 500,
    sources: [
      {
        resultId: `${walletId}-result`,
        date: '2026-10-01',
        holdingName: 'Vale',
        ticker: 'VALE3',
        amount: 500,
      },
    ],
    suggestion: null,
  }
}

async function mountView(pending: CashReconciliation[][]) {
  pending.forEach((reconciliations) =>
    vi.mocked(cashApi.findReconciliation).mockResolvedValueOnce(reconciliations),
  )
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/overview', name: 'overview', component: { template: '<div />' } },
      { path: '/wallets', name: 'wallets', component: { template: '<div />' } },
      {
        path: '/wallets/conciliar',
        name: 'cash-reconciliation',
        component: CashReconciliationView,
      },
    ],
  })
  await router.push('/wallets/conciliar')
  const wrapper = mount(CashReconciliationView, {
    global: { plugins: [createTestingPinia(), router] },
  })
  await flushPromises()
  return { wrapper, router }
}

afterEach(() => {
  vi.clearAllMocks()
})

describe('CashReconciliationView', () => {
  it('loads the wallets, the rates and one card per wallet left to reconcile', async () => {
    const { wrapper } = await mountView([
      [reconciliationOf('wallet-1', 'Ações EUA'), reconciliationOf('wallet-2', 'Fundos')],
    ])

    expect(useWalletsStore().load).toHaveBeenCalled()
    expect(useRatesStore().load).toHaveBeenCalled()
    expect(wrapper.findAllComponents(CashReconciliationCard)).toHaveLength(2)
    expect(wrapper.get('[data-testid="reconciliation-progress"]').text()).toContain(
      '0 de 2 carteiras conciliadas',
    )
    expect(wrapper.find('[data-testid="reconciliation-done"]').exists()).toBe(false)
  })

  it('advances the progress and reloads after a wallet is registered', async () => {
    const { wrapper } = await mountView([
      [reconciliationOf('wallet-1', 'Ações EUA'), reconciliationOf('wallet-2', 'Fundos')],
      [reconciliationOf('wallet-2', 'Fundos')],
    ])

    wrapper.findComponent(CashReconciliationCard).vm.$emit('registered', ['Ações EUA: feito'])
    await flushPromises()

    expect(useWalletsStore().refresh).toHaveBeenCalled()
    expect(wrapper.findAllComponents(CashReconciliationCard)).toHaveLength(1)
    expect(wrapper.get('[data-testid="reconciliation-progress"]').text()).toContain(
      '1 de 2 carteiras conciliadas',
    )
  })

  it('ends in the success state listing what was registered, with a link to the overview', async () => {
    const { wrapper } = await mountView([[reconciliationOf('wallet-1', 'Ações EUA')], []])

    wrapper
      .findComponent(CashReconciliationCard)
      .vm.$emit('registered', ['Ações EUA: R$ 500,00 sacados para o banco'])
    await flushPromises()

    const done = wrapper.get('[data-testid="reconciliation-done"]')
    expect(done.text()).toContain('Todo o caixa está conciliado.')
    expect(wrapper.get('[data-testid="reconciliation-registered"]').text()).toBe(
      'Ações EUA: R$ 500,00 sacados para o banco',
    )
    expect(done.get('a').attributes('href')).toBe('/overview')
  })

  it('shows the success state without a progress card when nothing was pending', async () => {
    const { wrapper } = await mountView([[]])

    expect(wrapper.find('[data-testid="reconciliation-progress"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="reconciliation-registered"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="reconciliation-done"]').exists()).toBe(true)
  })

  it('goes back to the wallets', async () => {
    const { wrapper, router } = await mountView([[]])

    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Carteiras')!
      .trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('wallets')
  })
})
