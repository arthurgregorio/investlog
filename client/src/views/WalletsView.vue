<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {BButton, useDialog, useToast} from 'buefy'
import AppIcon, { type IconName } from '@/components/AppIcon.vue'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { walletsApi } from '@/api/wallets'
import { useWalletsStore } from '@/stores/wallets'
import { useModals } from '@/composables/useModals'
import { fmt } from '@/composables/useFormat'
import { WALLET_TYPES } from '@/utils/walletTypes'
import type { WalletKind } from '@/types'

const dialog = useDialog()
const toast = useToast()
const walletsStore = useWalletsStore()
const router = useRouter()
const modals = useModals()

onMounted(() => walletsStore.load())

const tagTypeFor: Record<WalletKind, string> = {
  STOCKS: 'is-link',
  CRYPTO: 'is-warning',
  FUNDS: 'is-success',
}

function gotoType(kind: WalletKind) {
  router.push({ name: 'investments', query: { filter: kind } })
}

const iconFor = (kind: WalletKind): IconName => WALLET_TYPES[kind].icon as IconName

function confirmDeleteWallet(walletId: string, walletName: string) {
  dialog.confirm({
    title: 'Remover carteira',
    message: `Remover <strong>${walletName}</strong> apagará todos os seus investimentos. Esta ação <strong>não pode ser desfeita</strong>.`,
    type: 'is-danger',
    hasIcon: true,
    confirmText: 'Remover',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      await walletsApi.remove(walletId)
      toast.open({ message: 'Carteira removida.', type: 'is-success' })
      await walletsStore.refresh()
    },
  })
}

function renameWallet(walletId: string, currentName: string) {
  dialog.prompt({
    title: 'Renomear carteira',
    message: 'Novo nome:',
    inputAttrs: { value: currentName, placeholder: 'Nome da carteira' },
    confirmText: 'Salvar',
    cancelText: 'Cancelar',
    onConfirm: async (newName: string) => {
      const trimmedName = newName.trim()
      if (!trimmedName || trimmedName === currentName) return
      await walletsApi.update(walletId, { name: trimmedName })
      toast.open({ message: 'Carteira renomeada.', type: 'is-success' })
      await walletsStore.refresh()
    },
  })
}
</script>

<template>
  <div class="page">
    <b-loading :is-full-page="false" :active="walletsStore.loading" />

    <div class="page-head page-head-row">
      <div>
        <h1 class="page-title">Carteiras</h1>
        <p class="page-desc">Carteiras podem ter tipos e moedas distintas</p>
      </div>
    </div>

    <EmptyState
      v-if="walletsStore.loaded && walletsStore.wallets.length === 0"
      icon="wallet"
      title="Nenhuma carteira ainda"
      text="Crie sua primeira carteira para começar a registrar investimentos."
    >
      <template #action>
        <b-button type="is-primary" class="has-text-light" icon-left="plus" @click="modals.openCreateWallet()">Nova carteira</b-button>
      </template>
    </EmptyState>

    <div v-else class="wallet-grid">
      <Card v-for="wallet in walletsStore.wallets" :key="wallet.id" class="wallet-card">
        <div class="wallet-stripe" :style="{ background: WALLET_TYPES[wallet.kind].accent }" />
        <CardBody>
          <div class="wallet-head">
            <span class="type-ic sm" :style="{ background: WALLET_TYPES[wallet.kind].accent }">
              <AppIcon :name="iconFor(wallet.kind)" :size="18" />
            </span>
            <div class="wallet-titles">
              <div class="wallet-name">{{ wallet.name }}</div>
              <div class="wallet-tags">
                <b-tag :type="tagTypeFor[wallet.kind]">{{ WALLET_TYPES[wallet.kind].label }}</b-tag>
                <span class="cur-chip">{{ wallet.currency }}</span>
              </div>
            </div>
            <div style="display: flex; gap: 6px; margin-left: auto">
              <b-button
                  outlined
                  type="is-primary"
                size="is-small"
                icon-left="pencil"
                @click.stop="renameWallet(wallet.id, wallet.name)"
              />
              <b-button
                  outlined
                  type="is-danger"
                size="is-small"
                icon-left="delete"
                @click.stop="confirmDeleteWallet(wallet.id, wallet.name)"
              />
            </div>
          </div>
          <div class="wallet-invested">
            <div class="wi-value">{{ fmt.money(wallet.totalInvested, wallet.currency) }}</div>
          </div>
          <div class="wallet-foot">
            <span class="wallet-count">
              {{ wallet.holdingCount }} {{ wallet.holdingCount === 1 ? 'ativo' : 'ativos' }}
            </span>
            <b-button type="is-ghost" size="is-small" @click="gotoType(wallet.kind)">
              Ver investimentos
            </b-button>
          </div>
        </CardBody>
      </Card>

      <button class="wallet-card wallet-add" @click="modals.openCreateWallet()">
        <AppIcon name="plusCircle" :size="26" /><span>Nova carteira</span>
      </button>
    </div>
  </div>
</template>
