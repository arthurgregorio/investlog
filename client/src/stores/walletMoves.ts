import { defineStore } from 'pinia'
import { walletMovesApi, type WalletMovePayload } from '@/api/walletMoves'

export const useWalletMovesStore = defineStore('walletMoves', () => {
  async function move(originWalletId: string, payload: WalletMovePayload) {
    await walletMovesApi.move(originWalletId, payload)
  }

  return { move }
})
