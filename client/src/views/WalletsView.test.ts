import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createMemoryHistory, createRouter } from 'vue-router'
import WalletsView from './WalletsView.vue'
import { holdingsApi } from '@/api/holdings'
import { useWalletsStore } from '@/stores/wallets'
import { ModalKey } from '@/composables/useModals'
import type { WalletResponse } from '@/types'

vi.mock('@/api/holdings', () => ({ holdingsApi: { findAll: vi.fn() } }))
vi.mock('@/api/wallets', () => ({ walletsApi: { findAll: vi.fn() } }))
vi.mock('@/api/walletMoves', () => ({ walletMovesApi: { findAll: vi.fn(), move: vi.fn() } }))

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
    routes: [{ path: '/wallets', name: 'wallets', component: WalletsView }],
  })
  router.push('/wallets')
  await router.isReady()

  const pinia = createTestingPinia()
  const walletsStore = useWalletsStore()
  walletsStore.wallets = wallets
  walletsStore.loaded = true

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
  return { wrapper }
}

describe('WalletsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('opens the move modal with no wallet pre-selected', async () => {
    const { wrapper } = await mountView([walletOf('wallet-1', 'Um'), walletOf('wallet-2', 'Dois')])

    await wrapper.find('[data-testid="open-move"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Mover investimentos')
    expect(wrapper.find('[data-testid="move-origin"]').exists()).toBe(true)
    expect(holdingsApi.findAll).not.toHaveBeenCalled()
  })

  it('hides the move action while there is nowhere to move to', async () => {
    const { wrapper } = await mountView([walletOf('wallet-1', 'Um')])

    expect(wrapper.find('[data-testid="open-move"]').exists()).toBe(false)
  })
})
