<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { BButton } from 'buefy'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import GainChip from '@/components/ui/GainChip.vue'
import { useWalletsStore } from '@/stores/wallets'
import { useCurrencyStore } from '@/stores/currency'
import { useRatesStore } from '@/stores/rates'
import { useModals } from '@/composables/useModals'
import { fmt } from '@/composables/useFormat'
import { WALLET_TYPES } from '@/utils/walletTypes'
import type { WalletKind } from '@/types'

const walletsStore = useWalletsStore()
const currencyStore = useCurrencyStore()
const ratesStore = useRatesStore()
const router = useRouter()
const modals = useModals()

onMounted(() => {
  walletsStore.load()
  currencyStore.load()
  ratesStore.load()
})

function openWallet(walletId: string) {
  router.push({ name: 'wallet-detail', params: { id: walletId } })
}

function gotoType(kind: WalletKind, walletId: string) {
  router.push({ name: 'investments', query: { filter: kind, walletId } })
}

const iconFor = (kind: WalletKind): string => WALLET_TYPES[kind].icon
</script>

<template>
  <div class="page">
    <b-loading :is-full-page="false" :model-value="walletsStore.loading" />

    <div class="page-head page-head-row">
      <div>
        <h1 class="page-title">Carteiras</h1>
        <p class="page-desc">Carteiras podem ter tipos e moedas distintas</p>
      </div>
    </div>

    <EmptyState
      v-if="walletsStore.loaded && walletsStore.wallets.length === 0"
      icon="wallet-outline"
      title="Nenhuma carteira ainda"
      text="Crie sua primeira carteira para começar a registrar investimentos."
    >
      <template #action>
        <b-button type="is-primary" icon-left="plus" @click="modals.openCreateWallet()"
          >Nova carteira</b-button
        >
      </template>
    </EmptyState>

    <div v-else class="entity-grid">
      <Card v-for="wallet in walletsStore.wallets" :key="wallet.id" class="entity-card mb-0">
        <div class="wallet-stripe" :style="{ background: WALLET_TYPES[wallet.kind].accent }" />
        <CardBody>
          <div class="entity-head">
            <span class="type-ic sm" :style="{ background: WALLET_TYPES[wallet.kind].accent }">
              <b-icon :icon="iconFor(wallet.kind)" size="is-small" />
            </span>
            <div class="entity-titles">
              <div class="entity-name">{{ wallet.name }}</div>
              <div class="is-flex is-align-items-center is-gap-1 mt-1">
                <b-tag :class="`tt-${wallet.kind.toLowerCase()}`">{{
                  WALLET_TYPES[wallet.kind].label
                }}</b-tag>
                <span class="cur-chip">{{ wallet.currency }}</span>
              </div>
            </div>
            <div class="is-flex is-gap-1 ml-auto">
              <b-tooltip label="Detalhes" position="is-left">
                <b-button
                  outlined
                  type="is-primary"
                  size="is-small"
                  icon-left="finance"
                  aria-label="Detalhes da carteira"
                  @click.stop="openWallet(wallet.id)"
                />
              </b-tooltip>
            </div>
          </div>
          <div class="wallet-invested">
            <div class="wi-value">
              {{
                fmt.money(
                  currencyStore.convert(wallet.totalInvested, wallet.currency),
                  currencyStore.displayCurrency,
                )
              }}
            </div>
            <div class="sub-caption">Investido</div>
          </div>
          <div class="result-row">
            <div class="result-item">
              <div class="result-value">
                {{
                  wallet.currentValue == null
                    ? '—'
                    : fmt.money(
                        currencyStore.convert(wallet.currentValue, wallet.currency),
                        currencyStore.displayCurrency,
                      )
                }}
              </div>
              <div class="result-label">Valor atual</div>
            </div>
            <div class="result-item">
              <GainChip
                :value="
                  wallet.gain == null ? null : currencyStore.convert(wallet.gain, wallet.currency)
                "
                :pct="wallet.gainPct"
                :cur="currencyStore.displayCurrency"
              />
              <div class="result-label">Resultado</div>
            </div>
          </div>
          <div class="entity-foot">
            <span class="wallet-count">
              {{ wallet.holdingCount }} {{ wallet.holdingCount === 1 ? 'ativo' : 'ativos' }}
            </span>
            <b-button type="is-ghost" size="is-small" @click="gotoType(wallet.kind, wallet.id)">
              Ver investimentos
            </b-button>
          </div>
        </CardBody>
      </Card>

      <button class="entity-card wallet-add" @click="modals.openCreateWallet()">
        <b-icon icon="plus-circle-outline" size="is-medium" /><span>Nova carteira</span>
      </button>
    </div>
  </div>
</template>
