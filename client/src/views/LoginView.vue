<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import LogoMark from '@/components/icons/LogoMark.vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const email = ref('')
const password = ref('')
const error = ref('')
const submitting = ref(false)

async function submit() {
  error.value = ''
  submitting.value = true
  try {
    await auth.login(email.value, password.value)
    await router.push({ name: 'overview' })
  } catch {
    error.value = 'E-mail ou senha inválidos.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="auth-root">
    <aside class="auth-aside">
      <div class="auth-aside-top">
        <div class="auth-logo">
          <span class="auth-logo-mark"><LogoMark :size="26" /></span>
          <span class="auth-logo-name">Invest<b>Log</b></span>
        </div>
      </div>
      <div class="auth-aside-mid">
        <h2 class="auth-headline">Seu diário de<br />investimentos,<br />sempre em dia.</h2>
        <p class="auth-tagline">
          Registre aportes, acompanhe carteiras em várias moedas e veja seu patrimônio
          evoluir — tudo em um só lugar.
        </p>
      </div>
      <ul class="auth-points">
        <li><span class="ap-dot" />Carteiras multi-moeda consolidadas</li>
        <li><span class="ap-dot" />Resultado por ativo, tipo e período</li>
        <li><span class="ap-dot" />Histórico completo de cada aporte</li>
      </ul>
      <div class="auth-deco" aria-hidden="true">
        <span style="height: 34%" /><span style="height: 52%" />
        <span style="height: 46%" /><span style="height: 68%" />
        <span style="height: 60%" /><span style="height: 84%" />
        <span style="height: 76%" /><span style="height: 100%" />
      </div>
    </aside>

    <main class="auth-main">
      <form class="auth-card" @submit.prevent="submit">
        <div class="auth-card-brand">
          <span class="brand-mark"><LogoMark :size="20" /></span>
          <span class="brand-name">Invest<b>Log</b></span>
        </div>

        <div class="auth-head">
          <h1 class="auth-title">Bem-vindo de volta</h1>
          <p class="auth-sub">Entre para acompanhar seus investimentos.</p>
        </div>

        <p v-if="error" class="auth-error">{{ error }}</p>

        <div class="form-stack">
          <b-field label="E-mail">
            <b-input v-model="email" type="email" placeholder="voce@email.com" required />
          </b-field>
          <b-field label="Senha">
            <b-input v-model="password" type="password" placeholder="••••••••" required />
          </b-field>

          <b-button
            type="is-primary"
            expanded
            native-type="submit"
            :loading="submitting"
            class="auth-submit"
          >
            Entrar
          </b-button>
        </div>
      </form>
    </main>
  </div>
</template>
