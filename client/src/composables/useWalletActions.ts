import { toValue, type MaybeRefOrGetter } from 'vue'
import { useRouter } from 'vue-router'
import { useDialog, useToast } from 'buefy'
import { walletsApi } from '@/api/wallets'
import { safeHtml, useConfirmDialog } from '@/composables/useConfirmDialog'
import { useWalletDetailStore } from '@/stores/walletDetail'
import { useWalletsStore } from '@/stores/wallets'
import type { WalletDetail } from '@/types'

export function useWalletActions(detail: MaybeRefOrGetter<WalletDetail | null>) {
  const dialog = useDialog()
  const toast = useToast()
  const router = useRouter()
  const { confirm } = useConfirmDialog()
  const walletDetailStore = useWalletDetailStore()
  const walletsStore = useWalletsStore()

  function renameWallet() {
    const wallet = toValue(detail)
    if (!wallet) return
    dialog.prompt({
      title: 'Renomear carteira',
      message: 'Novo nome da carteira',
      inputAttrs: { value: wallet.name, maxlength: 80 },
      confirmText: 'Salvar',
      cancelText: 'Cancelar',
      trapFocus: true,
      onConfirm: async (newName: string) => {
        const trimmedName = newName.trim()
        if (!trimmedName || trimmedName === wallet.name) return
        await walletsApi.update(wallet.id, { name: trimmedName })
        toast.open({ message: 'Carteira renomeada.', type: 'is-success' })
        await Promise.all([walletDetailStore.load(wallet.id), walletsStore.refresh()])
      },
    })
  }

  function confirmRemoveWallet() {
    const wallet = toValue(detail)
    if (!wallet) return
    confirm({
      title: 'Remover carteira',
      message: safeHtml`Remover <strong>${wallet.name}</strong> apagará todos os seus investimentos. Esta ação <strong>não pode ser desfeita</strong>.`,
      confirmText: 'Remover',
      onConfirm: async () => {
        await walletsApi.remove(wallet.id)
        toast.open({ message: 'Carteira removida.', type: 'is-success' })
        await walletsStore.refresh()
        await router.push({ name: 'wallets' })
      },
    })
  }

  return { renameWallet, confirmRemoveWallet }
}
