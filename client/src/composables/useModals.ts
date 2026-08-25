/* App-shell modal controls, provided by App.vue and injected by feature views.
   Keeps add-investment / create-wallet modal state out of the route tree. */
import { inject, type InjectionKey } from 'vue'
import type { WalletKind } from '@/types'

export interface ModalControls {
  openAddInvestment: (kind?: WalletKind) => void
  openCreateWallet: (type?: WalletKind) => void
  openPasswordChange: () => void
  openTrustedDevices: () => void
}

export const ModalKey: InjectionKey<ModalControls> = Symbol('investlog.modals')

export function useModals(): ModalControls {
  const ctx = inject(ModalKey)
  if (!ctx) throw new Error('useModals() must be used within the App modal provider')
  return ctx
}
