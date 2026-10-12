import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { resetUserStores } from './resetUserStores'
import { useAppearanceStore } from './appearance'
import { useConfigurationsStore } from './configurations'
import { useCurrencyStore } from './currency'
import { useHoldingsListStore } from './holdingsList'
import { useOverviewStore } from './overview'
import { useRatesStore } from './rates'
import { useReinvestmentsStore } from './reinvestments'
import { useResultsStore } from './results'
import { useTrustedDevicesStore } from './trustedDevices'
import { useTypesListStore } from './typesList'
import { useUsersAdminStore } from './usersAdmin'
import { useWalletDetailStore } from './walletDetail'
import { useWalletMovesStore } from './walletMoves'
import { useWalletsStore } from './wallets'

vi.mock('@/router', () => ({ router: {} }))

interface StoreCase {
  name: string
  useStore: () => object
  seed: Record<string, unknown>
  initial: Record<string, unknown>
}

const storeCases: StoreCase[] = [
  {
    name: 'wallets',
    useStore: useWalletsStore,
    seed: { wallets: [{ id: 'wallet-1' }], loaded: true, loading: true },
    initial: { wallets: [], loaded: false, loading: false },
  },
  {
    name: 'holdingsList',
    useStore: useHoldingsListStore,
    seed: {
      rows: [{ id: 'holding-1' }],
      currentKind: 'STOCKS',
      page: 3,
      pageSize: 10,
      totalElements: 41,
      totalPages: 5,
      loading: true,
      loaded: true,
    },
    initial: {
      rows: [],
      currentKind: 'all',
      page: 0,
      pageSize: 20,
      totalElements: 0,
      totalPages: 0,
      loading: false,
      loaded: false,
    },
  },
  {
    name: 'overview',
    useStore: useOverviewStore,
    seed: { summary: { totalGain: 10 }, series: [{ month: '2026-01' }], loaded: true },
    initial: { summary: null, series: [], loaded: false, loading: false },
  },
  {
    name: 'walletDetail',
    useStore: useWalletDetailStore,
    seed: { detail: { id: 'wallet-1' }, loaded: true },
    initial: { detail: null, loaded: false },
  },
  {
    name: 'walletMoves',
    useStore: useWalletMovesStore,
    seed: {
      rows: [{ id: 'move-1' }],
      walletId: 'wallet-1',
      page: 2,
      pageSize: 50,
      totalElements: 30,
      totalPages: 3,
      loaded: true,
    },
    initial: {
      rows: [],
      walletId: null,
      page: 0,
      pageSize: 10,
      totalElements: 0,
      totalPages: 0,
      loading: false,
      loaded: false,
    },
  },
  {
    name: 'results',
    useStore: useResultsStore,
    seed: {
      rows: [{ id: 'result-1' }],
      page: 1,
      pageSize: 5,
      totalElements: 7,
      totalPages: 2,
      loaded: true,
      summary: { totalProfit: 100 },
      summaryLoading: true,
    },
    initial: {
      rows: [],
      page: 0,
      pageSize: 20,
      totalElements: 0,
      totalPages: 0,
      loading: false,
      loaded: false,
      summary: null,
      summaryLoading: false,
    },
  },
  {
    name: 'reinvestments',
    useStore: useReinvestmentsStore,
    seed: {
      rows: [{ id: 'reinvestment-1' }],
      page: 1,
      pageSize: 10,
      totalElements: 12,
      totalPages: 2,
      loaded: true,
    },
    initial: {
      rows: [],
      page: 0,
      pageSize: 20,
      totalElements: 0,
      totalPages: 0,
      loading: false,
      loaded: false,
    },
  },
  {
    name: 'typesList',
    useStore: useTypesListStore,
    seed: {
      stockTypes: [{ id: 'type-1' }],
      fundTypes: [{ id: 'type-2' }],
      stockSegments: [{ id: 'segment-1' }],
      loaded: true,
    },
    initial: { stockTypes: [], fundTypes: [], stockSegments: [], loaded: false, loading: false },
  },
  {
    name: 'rates',
    useStore: useRatesStore,
    seed: { rates: [{ currencyCode: 'USD', rate: 5, isBase: false }], loaded: true },
    initial: { rates: [], loaded: false, loading: false },
  },
  {
    name: 'currency',
    useStore: useCurrencyStore,
    seed: { displayCurrency: 'USD', loaded: true },
    initial: { displayCurrency: 'BRL', loaded: false, loading: false },
  },
  {
    name: 'usersAdmin',
    useStore: useUsersAdminStore,
    seed: { users: [{ id: 'user-1' }], loaded: true },
    initial: { users: [], loaded: false, loading: false },
  },
  {
    name: 'configurations',
    useStore: useConfigurationsStore,
    seed: { values: { 'stock-price-sync': 'true' }, loaded: true },
    initial: { values: {}, loaded: false, loading: false },
  },
  {
    name: 'trustedDevices',
    useStore: useTrustedDevicesStore,
    seed: { devices: [{ id: 'device-1' }], loading: true },
    initial: { devices: [], loading: false },
  },
]

describe('resetUserStores', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it.each(storeCases)(
    'puts the $name store back to its initial state',
    ({ useStore, seed, initial }) => {
      const store = useStore()
      Object.assign(store, seed)

      resetUserStores()

      expect(store).toMatchObject(initial)
    },
  )

  it('keeps the appearance preferences, which are not user data', () => {
    const appearance = useAppearanceStore()
    appearance.dark = true
    appearance.accent = 'indigo'

    resetUserStores()

    expect(appearance.dark).toBe(true)
    expect(appearance.accent).toBe('indigo')
  })
})
