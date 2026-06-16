<script setup lang="ts">
import { storeToRefs } from 'pinia'
import AppIcon from '@/components/AppIcon.vue'
import Avatar from '@/components/ui/Avatar.vue'
import { usePortfolioStore } from '@/stores/portfolio'
import { useAppearanceStore } from '@/stores/appearance'

const portfolio = usePortfolioStore()
const { base } = storeToRefs(portfolio)
const appearance = useAppearanceStore()
const { dark } = storeToRefs(appearance)
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
      <b-button :icon-left="dark ? 'weather-sunny' : 'weather-night'" aria-label="Tema" @click="appearance.toggleDark()" />
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
