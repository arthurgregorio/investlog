import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createRouter, createMemoryHistory } from 'vue-router'
import WalletDetailView from './WalletDetailView.vue'
import { useWalletDetailStore } from '@/stores/walletDetail'
import { useHoldingsListStore } from '@/stores/holdingsList'
import { useAuthStore } from '@/stores/auth'
import type { HoldingRow, WalletDetail } from '@/types'

vi.mock('@/api/walletDetail', () => ({ walletDetailApi: { get: vi.fn() } }))
vi.mock('@/api/wallets', () => ({
  walletsApi: { findAll: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn() },
}))
vi.mock('@/api/holdings', () => ({
  holdingsApi: {
    findAll: vi.fn(),
    getStockHolding: vi.fn(),
    getCryptoHolding: vi.fn(),
    getFundHolding: vi.fn(),
  },
}))

function detailOf(overrides: Partial<WalletDetail> = {}): WalletDetail {
  return {
    id: 'wallet-1',
    name: 'Detail Wallet',
    kind: 'STOCKS',
    currency: 'BRL',
    currentValue: 4750,
    totalInvested: 4500,
    gain: 250,
    gainPct: 5.5556,
    series: [],
    dayChange: null,
    weekChange: null,
    monthChange: null,
    bestPerformer: null,
    worstPerformer: null,
    largestHoldingName: null,
    largestHoldingShare: null,
    activity: {
      lastTransactionDate: null,
      lastTransactionName: null,
      lastTransactionAmount: null,
      transactionCount: 0,
      walletAgeInDays: null,
      investmentCount: 2,
    },
    ...overrides,
  }
}

const mockRow: HoldingRow = {
  id: 'holding-1',
  kind: 'STOCKS',
  name: 'Petrobras',
  ticker: 'PETR4',
  typeLabel: 'Ação ON',
  walletId: 'wallet-1',
  walletName: 'Detail Wallet',
  walletCurrency: 'BRL',
  quantity: 100,
  costBasis: 3500,
  currentPrice: 38.5,
  currentValue: 3850,
  gain: 350,
  gainPct: 10,
}

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

async function mountView(
  detail: WalletDetail | null,
  rows: HoldingRow[] = [mockRow],
  loaded = true,
  isAdmin = true,
) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/wallets', name: 'wallets', component: { template: '<div />' } },
      { path: '/wallets/:id', name: 'wallet-detail', component: WalletDetailView },
      { path: '/investments', name: 'investments', component: { template: '<div />' } },
    ],
  })
  const pinia = createTestingPinia()

  router.push('/wallets/wallet-1')
  await router.isReady()

  const wrapper = mount(WalletDetailView, { global: { plugins: [pinia, router] } })

  const walletDetailStore = useWalletDetailStore()
  const holdingsListStore = useHoldingsListStore()
  const authStore = useAuthStore()
  authStore.session = {
    name: 'Admin',
    email: 'admin@admin.com',
    role: isAdmin ? 'ADMIN' : 'USER',
    status: 'APPROVED',
    authProvider: 'LOCAL',
    demoModeEnabled: false,
  }
  walletDetailStore.detail = detail
  holdingsListStore.rows = rows
  holdingsListStore.loaded = loaded

  await flushPromises()
  return { wrapper, router, walletDetailStore, holdingsListStore }
}

describe('WalletDetailView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads the detail and the wallet-scoped holdings on mount', async () => {
    const { walletDetailStore, holdingsListStore } = await mountView(detailOf())

    expect(walletDetailStore.load).toHaveBeenCalledWith('wallet-1')
    expect(holdingsListStore.loadKind).toHaveBeenCalledWith('all', 0, { walletId: 'wallet-1' })
  })

  it('shows the wallet header figures', async () => {
    const { wrapper } = await mountView(detailOf())

    expect(wrapper.text()).toContain('Detail Wallet')
    expect(wrapper.text()).toContain('BRL')
    expect(wrapper.text()).toContain('4.750,00')
    expect(wrapper.text()).toContain('4.500,00')
  })

  it('shows an empty state instead of a chart when there is no snapshot history', async () => {
    const { wrapper } = await mountView(detailOf({ series: [] }))

    expect(wrapper.text()).toContain('Ainda sem histórico')
  })

  it('renders the chart once a snapshot series exists', async () => {
    const { wrapper } = await mountView(
      detailOf({
        series: [
          {
            snapshotDate: '2026-09-19',
            currentValue: 4750,
            totalInvested: 4500,
            gain: 250,
            gainPct: 5.5,
          },
        ],
        dayChange: 0,
      }),
    )

    expect(wrapper.text()).not.toContain('Ainda sem histórico')
    expect(wrapper.find('canvas').exists()).toBe(true)
  })

  it('shows the largest position share in its card, with no separate warning banner', async () => {
    const { wrapper } = await mountView(
      detailOf({ largestHoldingName: 'Petrobras', largestHoldingShare: 81.05 }),
    )

    expect(wrapper.find('.message.is-warning').exists()).toBe(false)
    expect(wrapper.find('.wd-share-pct').text()).toBe('81,05%')
  })

  it('lists the wallet holdings and expands a row into the detail panel', async () => {
    const { wrapper } = await mountView(detailOf())

    expect(wrapper.text()).toContain('PETR4')

    await wrapper.find('tr.inv-row').trigger('click')
    await flushPromises()

    expect(wrapper.find('tr.detail-row').exists()).toBe(true)
  })

  it('shows an empty state when the wallet has no investments', async () => {
    const { wrapper } = await mountView(detailOf(), [])

    expect(wrapper.text()).toContain('Nenhum investimento nesta carteira')
    expect(wrapper.find('table.inv-table').exists()).toBe(false)
  })

  it('goes back to the wallets list', async () => {
    const { wrapper, router } = await mountView(detailOf())

    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('wallets')
  })

  it('offers a rename button beside the wallet name', async () => {
    const { wrapper } = await mountView(detailOf())

    expect(wrapper.find('button[aria-label="Renomear carteira"]').exists()).toBe(true)
  })

  it('jumps to the holdings list filtered by this wallet', async () => {
    const { wrapper, router } = await mountView(detailOf())

    const link = wrapper.findAll('button').find((button) => button.text() === 'Ver investimentos')
    await link!.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('investments')
    expect(router.currentRoute.value.query).toEqual({ filter: 'STOCKS', walletId: 'wallet-1' })
  })

  it('offers a remove button to an admin', async () => {
    const { wrapper } = await mountView(detailOf(), [mockRow], true, true)

    expect(wrapper.findAll('button').some((button) => button.text() === 'Remover')).toBe(true)
  })

  it('hides the remove button from a non-admin', async () => {
    const { wrapper } = await mountView(detailOf(), [mockRow], true, false)

    expect(wrapper.findAll('button').some((button) => button.text() === 'Remover')).toBe(false)
  })

  it('renders best, worst and largest position as three highlight cards', async () => {
    const { wrapper } = await mountView(
      detailOf({
        bestPerformer: {
          id: 'h1',
          name: 'Itau',
          ticker: 'ITUB4',
          kind: 'STOCKS',
          gain: 3475,
          gainPct: 39.71,
        },
        worstPerformer: {
          id: 'h2',
          name: 'TRX',
          ticker: 'TRXF11',
          kind: 'FUNDS',
          gain: -110,
          gainPct: -11.22,
        },
        largestHoldingName: 'Petrobras',
        largestHoldingShare: 41.1,
      }),
    )

    expect(wrapper.findAll('.wd-highlight')).toHaveLength(3)
    expect(wrapper.text()).toContain('Melhor desempenho')
    expect(wrapper.text()).toContain('Pior desempenho')
    expect(wrapper.text()).toContain('Maior posição')
    expect(wrapper.text()).toContain('ITUB4')
    expect(wrapper.text()).toContain('TRXF11')
    expect(wrapper.find('.wd-share-pct').text()).toBe('41,10%')
  })
})
