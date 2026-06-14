<script setup lang="ts">
import { storeToRefs } from 'pinia'
import AppIcon from '@/components/AppIcon.vue'
import Avatar from '@/components/ui/Avatar.vue'
import { usePortfolioStore } from '@/stores/portfolio'
import { useAppearanceStore } from '@/stores/appearance'
import { useModals } from '@/composables/useModals'

const portfolio = usePortfolioStore()
const { base } = storeToRefs(portfolio)
const appearance = useAppearanceStore()
const { dark } = storeToRefs(appearance)
const modals = useModals()
</script>

<template>
  <header class="navbar">
    <div class="navbar-inner">
      <div class="brand">
        <span class="brand-mark"><AppIcon name="trendUp" :size="20" :stroke="2.4" /></span>
        <span class="brand-name">Invest<b>Log</b></span>
      </div>
      <div class="navbar-spacer" />
      <span class="base-chip"><AppIcon name="repeat" :size="14" />Base&nbsp;<b>{{ base }}</b></span>
      <button class="btn btn-primary btn-md" @click="modals.openAddInvestment()">
        <AppIcon name="plus" :size="18" :stroke="2.4" /><span>Adicionar investimento</span>
      </button>
      <button class="icon-btn" aria-label="Tema" @click="appearance.toggleDark()">
        <AppIcon :name="dark ? 'sun' : 'moon'" :size="20" />
      </button>
      <div class="navbar-user">
        <Avatar initials="RT" />
        <div class="nu-meta">
          <div class="nu-name">Rafael T.</div>
          <div class="nu-sub">Investidor</div>
        </div>
      </div>
    </div>
  </header>
</template>
