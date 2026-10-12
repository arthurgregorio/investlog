import { useConfigurationsStore } from '@/stores/configurations'
import { useCurrencyStore } from '@/stores/currency'
import { useHoldingsListStore } from '@/stores/holdingsList'
import { useOverviewStore } from '@/stores/overview'
import { useRatesStore } from '@/stores/rates'
import { useReinvestmentsStore } from '@/stores/reinvestments'
import { useResultsStore } from '@/stores/results'
import { useTrustedDevicesStore } from '@/stores/trustedDevices'
import { useTypesListStore } from '@/stores/typesList'
import { useUsersAdminStore } from '@/stores/usersAdmin'
import { useWalletDetailStore } from '@/stores/walletDetail'
import { useWalletMovesStore } from '@/stores/walletMoves'
import { useWalletsStore } from '@/stores/wallets'

export function resetUserStores() {
  useWalletsStore().reset()
  useHoldingsListStore().reset()
  useOverviewStore().reset()
  useWalletDetailStore().reset()
  useWalletMovesStore().reset()
  useResultsStore().reset()
  useReinvestmentsStore().reset()
  useTypesListStore().reset()
  useRatesStore().reset()
  useCurrencyStore().reset()
  useUsersAdminStore().reset()
  useConfigurationsStore().reset()
  useTrustedDevicesStore().reset()
}
