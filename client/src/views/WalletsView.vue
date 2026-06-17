<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon, { type IconName } from '@/components/AppIcon.vue'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { useWalletsStore } from '@/stores/wallets'
import { useModals } from '@/composables/useModals'
import { fmt } from '@/composables/useFormat'
import { WALLET_TYPES } from '@/utils/walletTypes'
import type { WalletKind } from '@/types'

const walletsStore = useWalletsStore()
const router = useRouter()
const modals = useModals()

onMounted(() => walletsStore.load())

const tagTypeFor: Record<WalletKind, string> = {
  stocks: 'is-link',
  crypto: 'is-warning',
  funds: 'is-success',
}

function gotoType(kind: WalletKind) {
  router.push({ name: 'investments', query: { filter: kind } })
}

const iconFor = (kind: WalletKind): IconName => WALLET_TYPES[kind].icon as IconName
</script>

<template>
  <div class="page">
    <b-loading :is-full-page="false" :active="walletsStore.loading" />

    <div class="page-head page-head-row">
      <div>
        <div class="page-eyebrow">Logbook</div>
        <h1 class="page-title">Carteiras</h1>
        <p class="page-desc">Cada carteira tem um tipo e uma moeda.</p>
      </div>
      <b-button type="is-primary" icon-left="plus" @click="modals.openCreateWallet()">Nova carteira</b-button>
    </div>

    <EmptyState
      v-if="walletsStore.loaded && walletsStore.wallets.length === 0"
      icon="wallet"
      title="Nenhuma carteira ainda"
      text="Crie sua primeira carteira para começar a registrar investimentos."
    >
      <template #action>
        <b-button type="is-primary" icon-left="plus" @click="modals.openCreateWallet()">Nova carteira</b-button>
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
          </div>
          <div class="wallet-invested">
            <div class="wi-value">{{ fmt.money(wallet.totalInvested, wallet.currency) }}</div>
          </div>
          <div class="wallet-foot">
            <span class="wallet-count">
              {{ wallet.holdingCount }} {{ wallet.holdingCount === 1 ? 'ativo' : 'ativos' }}
            </span>
            <b-button type="is-text" icon-right="chevron-right" @click="gotoType(wallet.kind)">
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
