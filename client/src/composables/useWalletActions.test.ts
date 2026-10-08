import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useWalletActions } from './useWalletActions'
import { walletsApi } from '@/api/wallets'
import { useWalletDetailStore } from '@/stores/walletDetail'
import { useWalletsStore } from '@/stores/wallets'
import { walletDetailOf } from '@/test/walletDetailFixture'
import type { WalletDetail } from '@/types'

const { dialogPrompt, dialogConfirm, toastOpen, routerPush } = vi.hoisted(() => ({
  dialogPrompt: vi.fn(),
  dialogConfirm: vi.fn(),
  toastOpen: vi.fn(),
  routerPush: vi.fn(),
}))

vi.mock('buefy', async (importOriginal) => ({
  ...(await importOriginal<typeof import('buefy')>()),
  useDialog: () => ({ prompt: dialogPrompt, confirm: dialogConfirm }),
  useToast: () => ({ open: toastOpen }),
}))

vi.mock('vue-router', () => ({ useRouter: () => ({ push: routerPush }) }))

vi.mock('@/api/wallets', () => ({
  walletsApi: { findAll: vi.fn(), update: vi.fn(), remove: vi.fn() },
}))

vi.mock('@/api/walletDetail', () => ({ walletDetailApi: { get: vi.fn() } }))

function setup(detail: WalletDetail | null = walletDetailOf()) {
  const walletDetailStore = useWalletDetailStore()
  const walletsStore = useWalletsStore()
  vi.spyOn(walletDetailStore, 'load').mockResolvedValue()
  vi.spyOn(walletsStore, 'refresh').mockResolvedValue()
  return { walletDetailStore, walletsStore, actions: useWalletActions(ref(detail)) }
}

async function answer(mock: typeof dialogPrompt, value?: string) {
  await mock.mock.lastCall![0].onConfirm(value)
}

describe('useWalletActions', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('prompts for a new name prefilled with the current one', () => {
    const { actions } = setup()

    actions.renameWallet()

    const options = dialogPrompt.mock.lastCall![0]
    expect(options.title).toBe('Renomear carteira')
    expect(options.inputAttrs).toEqual({ value: 'Detail Wallet', maxlength: 80 })
  })

  it('renames to the trimmed name and reloads the wallet and the wallet list', async () => {
    const { actions, walletDetailStore, walletsStore } = setup()

    actions.renameWallet()
    await answer(dialogPrompt, '  Aposentadoria ')

    expect(walletsApi.update).toHaveBeenCalledWith('wallet-1', { name: 'Aposentadoria' })
    expect(toastOpen).toHaveBeenCalledWith({ message: 'Carteira renomeada.', type: 'is-success' })
    expect(walletDetailStore.load).toHaveBeenCalledWith('wallet-1')
    expect(walletsStore.refresh).toHaveBeenCalled()
  })

  it('ignores a blank or unchanged name', async () => {
    const { actions } = setup()

    actions.renameWallet()
    await answer(dialogPrompt, '   ')
    await answer(dialogPrompt, ' Detail Wallet ')

    expect(walletsApi.update).not.toHaveBeenCalled()
  })

  it('confirms the removal as a danger dialog with the name escaped', () => {
    const { actions } = setup(walletDetailOf({ name: '<b>Bold</b>' }))

    actions.confirmRemoveWallet()

    const options = dialogConfirm.mock.lastCall![0]
    expect(options.title).toBe('Remover carteira')
    expect(options.type).toBe('is-danger')
    expect(options.message).toContain('&lt;b&gt;Bold&lt;/b&gt;')
  })

  it('removes the wallet, refreshes the list and goes back to the wallets', async () => {
    const { actions, walletsStore } = setup()

    actions.confirmRemoveWallet()
    await answer(dialogConfirm)

    expect(walletsApi.remove).toHaveBeenCalledWith('wallet-1')
    expect(toastOpen).toHaveBeenCalledWith({ message: 'Carteira removida.', type: 'is-success' })
    expect(walletsStore.refresh).toHaveBeenCalled()
    expect(routerPush).toHaveBeenCalledWith({ name: 'wallets' })
  })

  it('does nothing before the wallet is loaded', () => {
    const { actions } = setup(null)

    actions.renameWallet()
    actions.confirmRemoveWallet()

    expect(dialogPrompt).not.toHaveBeenCalled()
    expect(dialogConfirm).not.toHaveBeenCalled()
  })
})
