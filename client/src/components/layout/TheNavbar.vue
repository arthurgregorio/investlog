<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import AppIcon from '@/components/AppIcon.vue'
import Avatar from '@/components/ui/Avatar.vue'
import { useRatesStore } from '@/stores/rates'
import { useAppearanceStore } from '@/stores/appearance'
import { profileApi } from '@/api/profile'
import type { ProfileResponse } from '@/types'

const ratesStore = useRatesStore()
const { baseCurrency } = storeToRefs(ratesStore)
const appearance = useAppearanceStore()
const { dark } = storeToRefs(appearance)

const profile = ref<ProfileResponse | null>(null)

onMounted(async () => {
  profile.value = await profileApi.getProfile()
})

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
      <span class="base-chip"><AppIcon name="repeat" :size="14" />Base&nbsp;<b>{{ baseCurrency }}</b></span>
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
