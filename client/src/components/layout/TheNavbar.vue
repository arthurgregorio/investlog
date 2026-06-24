<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import AppIcon from '@/components/AppIcon.vue'
import Avatar from '@/components/ui/Avatar.vue'
import { useRatesStore } from '@/stores/rates'
import { useCurrencyStore } from '@/stores/currency'
import { useOverviewStore } from '@/stores/overview'
import { useAppearanceStore } from '@/stores/appearance'
import { profileApi } from '@/api/profile'
import type { ProfileResponse } from '@/types'

const ratesStore = useRatesStore()
const currencyStore = useCurrencyStore()
const overviewStore = useOverviewStore()
const appearance = useAppearanceStore()
const { dark } = storeToRefs(appearance)

const profile = ref<ProfileResponse | null>(null)

const currencyOptions = computed(() =>
  ratesStore.currencyCodes.length > 0 ? ratesStore.currencyCodes : ['BRL', 'USD'],
)

onMounted(async () => {
  profile.value = await profileApi.getProfile()
  await Promise.all([ratesStore.load(), currencyStore.load()])
})

async function onCurrencyChange(currencyCode: string) {
  await currencyStore.setDisplayCurrency(currencyCode)
  await overviewStore.refresh()
}

function initials(name: string): string {
  return name
    .split(' ')
    .filter((word) => word.length > 0)
    .slice(0, 2)
    .map((word) => word[0].toUpperCase())
    .join('')
}
</script>

<template>
  <header class="navbar">
    <div class="navbar-inner">
      <RouterLink to="/" class="brand">
        <span class="brand-mark"><AppIcon name="trendUp" :size="20" :stroke="2.4" /></span>
        <span class="brand-name">Invest<b>Log</b></span>
      </RouterLink>
      <div class="navbar-spacer" />
      <b-select
        :model-value="currencyStore.displayCurrency"
        size="is-small"
        class="currency-select"
        @update:model-value="onCurrencyChange"
      >
        <option v-for="currencyCode in currencyOptions" :key="currencyCode" :value="currencyCode">
          {{ currencyCode }}
        </option>
      </b-select>
      <b-button :icon-left="dark ? 'weather-sunny' : 'weather-night'" aria-label="Tema" @click="appearance.toggleDark()" />
      <div class="navbar-user">
        <Avatar :initials="profile ? initials(profile.name) : '?'" />
        <div class="nu-meta">
          <div class="nu-name">{{ profile?.name ?? '...' }}</div>
          <div class="nu-sub">{{ profile?.email ?? '' }}</div>
        </div>
      </div>
    </div>
  </header>
</template>
