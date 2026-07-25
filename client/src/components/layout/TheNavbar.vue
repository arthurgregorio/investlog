<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import Avatar from '@/components/ui/Avatar.vue'
import LogoMark from '@/components/icons/LogoMark.vue'
import PasswordChangeModal from '@/components/forms/PasswordChangeModal.vue'
import { useRatesStore } from '@/stores/rates'
import { useCurrencyStore } from '@/stores/currency'
import { useOverviewStore } from '@/stores/overview'
import { useAppearanceStore } from '@/stores/appearance'
import { useAuthStore } from '@/stores/auth'
import { profileApi } from '@/api/profile'
import type { AccentKey, ProfileResponse } from '@/types'

const ratesStore = useRatesStore()
const currencyStore = useCurrencyStore()
const overviewStore = useOverviewStore()
const appearance = useAppearanceStore()
const auth = useAuthStore()
const { dark } = storeToRefs(appearance)

const showPasswordChangeModal = ref(false)

const accents: { key: AccentKey; hex: string }[] = [
  { key: 'blue', hex: '#206bc4' },
  { key: 'indigo', hex: '#4263eb' },
  { key: 'teal', hex: '#0ca678' },
  { key: 'yellow', hex: '#b8860b' },
]

const profile = ref<ProfileResponse | null>(null)

const currencyOptions = computed(() =>
  ratesStore.currencyCodes.length > 0 ? ratesStore.currencyCodes : ['BRL', 'USD'],
)

onMounted(async () => {
  profile.value = await profileApi.getProfile()
  currencyStore.hydrate(profile.value.preferredCurrency)
  await ratesStore.load()
})

async function onCurrencyChange(currencyCode: string) {
  await currencyStore.setDisplayCurrency(currencyCode)
  await overviewStore.refresh()
}

function cycleCurrency() {
  const options = currencyOptions.value
  const currentIndex = options.indexOf(currencyStore.displayCurrency)
  const nextCurrency = options[(currentIndex + 1) % options.length]
  onCurrencyChange(nextCurrency)
}

function initials(name: string): string {
  return name
    .split(' ')
    .filter((word) => word.length > 0)
    .slice(0, 2)
    .map((word) => word[0].toUpperCase())
    .join('')
}

async function logout() {
  await auth.logout()
}
</script>

<template>
  <header class="navbar">
    <div class="navbar-inner">
      <RouterLink to="/" class="brand">
        <span class="brand-mark"><LogoMark :size="19" /></span>
        <span class="brand-name">Invest<b>Log</b></span>
      </RouterLink>
      <div class="navbar-spacer" />
      <button
        type="button"
        class="base-chip currency-toggle"
        :aria-label="`Moeda de exibição: ${currencyStore.displayCurrency}. Clique para alternar.`"
        @click="cycleCurrency"
      >
        <b-icon icon="repeat" size="is-small" />
        <b>{{ currencyStore.displayCurrency }}</b>
      </button>
      <b-dropdown position="is-bottom-left" aria-role="menu" append-to-body>
        <template #trigger>
          <div class="navbar-user" role="button" aria-label="Menu do usuário">
            <Avatar :initials="profile ? initials(profile.name) : '?'" />
            <div class="nu-meta">
              <div class="nu-name">{{ profile?.name ?? '...' }}</div>
              <div class="nu-sub">{{ profile?.email ?? '' }}</div>
            </div>
            <b-icon icon="menu-down" size="is-small" />
          </div>
        </template>

        <b-dropdown-item custom aria-role="menuitem" class="user-menu-section">
          <div class="user-menu-label">Aparência</div>
          <button type="button" class="user-menu-row" @click="appearance.toggleDark()">
            <b-icon :icon="dark ? 'weather-sunny' : 'weather-night'" size="is-small" />
            {{ dark ? 'Modo claro' : 'Modo escuro' }}
          </button>
          <div class="accent-row">
            <button
              v-for="accentOption in accents"
              :key="accentOption.key"
              type="button"
              class="accent-swatch"
              :class="{ active: appearance.accent === accentOption.key }"
              :style="{ background: accentOption.hex }"
              :aria-label="`Cor ${accentOption.key}`"
              @click="appearance.setAccent(accentOption.key)"
            >
              <b-icon
                v-if="appearance.accent === accentOption.key"
                icon="check"
                size="is-small"
                class="sw-check"
              />
            </button>
          </div>
        </b-dropdown-item>

        <template v-if="auth.session?.authProvider === 'LOCAL'">
          <hr class="dropdown-divider" />

          <b-dropdown-item aria-role="menuitem" @click="showPasswordChangeModal = true">
            <b-icon icon="lock-reset" size="is-small" /> Alterar senha
          </b-dropdown-item>
        </template>

        <hr class="dropdown-divider" />

        <b-dropdown-item aria-role="menuitem" @click="logout">
          <b-icon icon="logout" size="is-small" /> Sair
        </b-dropdown-item>
      </b-dropdown>
    </div>

    <PasswordChangeModal v-if="showPasswordChangeModal" @close="showPasswordChangeModal = false" />
  </header>
</template>
