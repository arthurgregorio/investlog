<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import AppIcon, { type IconName } from '@/components/AppIcon.vue'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import TypeBadge from '@/components/ui/TypeBadge.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import AppButton from '@/components/ui/AppButton.vue'
import { usePortfolioStore } from '@/stores/portfolio'
import { useModals } from '@/composables/useModals'
import { fmt } from '@/composables/useFormat'
import type { WalletKind } from '@/types'

const store = usePortfolioStore()
const { base, wallets } = storeToRefs(store)
const router = useRouter()
const modals = useModals()

const iconFor = (key: WalletKind): IconName => store.WALLET_TYPES[key].icon as IconName

function gotoType(type: WalletKind) {
  router.push({ name: 'investments', query: { filter: type } })
}
</script>

<template>
  <div class="page">
    <div class="page-head page-head-row">
      <div>
        <div class="page-eyebrow">Logbook</div>
        <h1 class="page-title">Carteiras</h1>
        <p class="page-desc">
          Cada carteira tem um tipo e uma moeda. Os totais são convertidos para {{ base }} na visão geral.
        </p>
      </div>
      <button class="btn btn-primary btn-md" @click="modals.openCreateWallet()">
        <AppIcon name="plus" :size="18" :stroke="2.4" />Nova carteira
      </button>
    </div>

    <EmptyState
      v-if="wallets.length === 0"
      icon="wallet"
      title="Nenhuma carteira ainda"
      text="Crie sua primeira carteira para começar a registrar investimentos."
    >
      <template #action>
        <AppButton variant="primary" icon="plus" @click="modals.openCreateWallet()">Nova carteira</AppButton>
      </template>
    </EmptyState>

    <div v-else class="wallet-grid">
      <Card v-for="w in wallets" :key="w.id" class="wallet-card">
        <div class="wallet-stripe" :style="{ background: store.WALLET_TYPES[w.type].accent }" />
        <CardBody>
          <div class="wallet-head">
            <span class="type-ic sm" :style="{ background: store.WALLET_TYPES[w.type].accent }">
              <AppIcon :name="iconFor(w.type)" :size="18" />
            </span>
            <div class="wallet-titles">
              <div class="wallet-name">{{ w.name }}</div>
              <div class="wallet-tags">
                <TypeBadge :kind="w.type">{{ store.WALLET_TYPES[w.type].label }}</TypeBadge>
                <span class="cur-chip">{{ w.currency }}</span>
              </div>
            </div>
          </div>
          <div class="wallet-invested">
            <div class="wi-value">{{ fmt.money(store.walletInvested(w.id), w.currency) }}</div>
            <div v-if="w.currency !== base" class="wi-base">
              ≈ {{ fmt.money(store.walletInvestedBase(w.id), base, { compact: true }) }}
            </div>
          </div>

          <ul v-if="store.walletHoldings(w.id).length" class="wallet-holdings">
            <li v-for="h in store.walletHoldings(w.id).slice(0, 4)" :key="h.id">
              <span class="wh-name">{{ h.kind === 'fund' ? h.name : h.ticker }}</span>
              <span class="wh-val">{{ fmt.money(store.holdingCost(h), w.currency, { compact: true }) }}</span>
            </li>
            <li v-if="store.walletHoldings(w.id).length > 4" class="wh-more">
              +{{ store.walletHoldings(w.id).length - 4 }} mais
            </li>
          </ul>
          <div v-else class="wallet-empty">Sem investimentos ainda</div>

          <div class="wallet-foot">
            <span class="wallet-count">
              {{ store.walletHoldings(w.id).length }}
              {{ store.walletHoldings(w.id).length === 1 ? 'ativo' : 'ativos' }}
            </span>
            <button class="link-btn" @click="gotoType(w.type)">
              Ver investimentos<AppIcon name="chevronRight" :size="15" />
            </button>
          </div>
        </CardBody>
      </Card>

      <button class="wallet-card wallet-add" @click="modals.openCreateWallet()">
        <AppIcon name="plusCircle" :size="26" /><span>Nova carteira</span>
      </button>
    </div>
  </div>
</template>
