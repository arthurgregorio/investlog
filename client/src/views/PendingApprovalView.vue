<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import LogoMark from '@/components/icons/LogoMark.vue'

const auth = useAuthStore()
const router = useRouter()

const message = computed(() => {
  if (!auth.session) {
    return 'Cadastro enviado! Aguarde a aprovação de um administrador para acessar sua conta.'
  }

  if (auth.session.status === 'REJECTED') {
    return `Olá, ${auth.session.name}. Seu cadastro não foi aprovado por um administrador.`
  }

  return `Olá, ${auth.session.name}! Sua conta ainda está aguardando aprovação de um administrador.`
})

async function logout() {
  await auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="auth-root">
    <main class="auth-main">
      <div class="auth-card">
        <div class="auth-card-brand">
          <span class="brand-mark"><LogoMark :size="20" /></span>
          <span class="brand-name">Invest<b>Log</b></span>
        </div>
        <div class="auth-head has-text-centered">
          <h1 class="auth-title">Aguardando aprovação</h1>
          <p class="auth-sub">{{ message }}</p>
        </div>
        <b-button v-if="auth.session" type="is-ghost" expanded @click="logout">Sair</b-button>
        <RouterLink v-else :to="{ name: 'login' }">
          <b-button type="is-ghost" expanded>Voltar para o login</b-button>
        </RouterLink>
      </div>
    </main>
  </div>
</template>
