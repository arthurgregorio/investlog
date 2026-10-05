import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createMemoryHistory, createRouter } from 'vue-router'
import WalletsView from './WalletsView.vue'
import { useCurrencyStore } from '@/stores/currency'
import { useWalletsStore } from '@/stores/wallets'
import { ModalKey } from '@/composables/useModals'
import type { WalletResponse } from '@/types'

vi.mock('@/api/wallets', () => ({ walletsApi: { findAll: vi.fn() } }))

function walletOf(id: string, name: string): WalletResponse {
  return {
    id,
    name,
    kind: 'STOCKS',
    currency: 'BRL',
    holdingCount: 1,
    totalInvested: 1000,
    currentValue: 1100,
    gain: 100,
    gainPct: 10,
    createdAt: '2026-01-01T00:00:00Z',
  }
}

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

async function mountView(wallets: WalletResponse[]) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/wallets', name: 'wallets', component: WalletsView },
      { path: '/wallets/:id', name: 'wallet-detail', component: { template: '<div />' } },
    ],
  })
  router.push('/wallets')
  await router.isReady()

  const pinia = createTestingPinia()
  const walletsStore = useWalletsStore()
  walletsStore.wallets = wallets
  walletsStore.loaded = true
  vi.mocked(useCurrencyStore().convert).mockImplementation((amount) => amount)

  const wrapper = mount(WalletsView, {
    global: {
      plugins: [pinia, router],
      provide: {
        [ModalKey as symbol]: {
          openAddInvestment: vi.fn(),
          openCreateWallet: vi.fn(),
          openPasswordChange: vi.fn(),
          openTrustedDevices: vi.fn(),
        },
      },
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('WalletsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders one card per wallet and offers no move action', async () => {
    const { wrapper } = await mountView([walletOf('wallet-1', 'Um'), walletOf('wallet-2', 'Dois')])

    expect(wrapper.findAll('.entity-card:not(.wallet-add)')).toHaveLength(2)
    expect(wrapper.text()).toContain('Um')
    expect(wrapper.text()).toContain('Dois')
    expect(wrapper.find('[data-testid="open-move"]').exists()).toBe(false)
  })

  it('opens the wallet detail from the details button', async () => {
    const { wrapper, router } = await mountView([walletOf('wallet-1', 'Um')])

    await wrapper.find('[aria-label="Detalhes da carteira"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('wallet-detail')
    expect(router.currentRoute.value.params.id).toBe('wallet-1')
  })

  it('shows the loading overlay only while the wallets store is loading', async () => {
    const { wrapper } = await mountView([walletOf('wallet-1', 'Um')])
    const walletsStore = useWalletsStore()

    expect(wrapper.find('.loading-overlay').exists()).toBe(false)

    walletsStore.loading = true
    await flushPromises()
    expect(wrapper.findAll('.loading-overlay')).toHaveLength(1)

    walletsStore.loading = false
    await flushPromises()
    expect(wrapper.find('.loading-overlay').exists()).toBe(false)
  })
})
