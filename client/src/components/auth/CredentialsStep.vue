<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import GoogleMark from '@/components/icons/GoogleMark.vue'

defineProps<{
  submitting: boolean
  googleAuthEnabled: boolean
}>()

const emit = defineEmits<{
  submit: []
  toggleRegister: []
}>()

const email = defineModel<string>('email', { required: true })
const password = defineModel<string>('password', { required: true })

const redirectingToGoogle = ref(false)

function resetAfterBackForwardRestore(event: PageTransitionEvent) {
  if (event.persisted) {
    redirectingToGoogle.value = false
  }
}

onMounted(() => window.addEventListener('pageshow', resetAfterBackForwardRestore))
onBeforeUnmount(() => window.removeEventListener('pageshow', resetAfterBackForwardRestore))
</script>

<template>
  <form class="form-stack" @submit.prevent="emit('submit')">
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
    <b-button
      type="is-ghost"
      size="is-small"
      class="has-text-primary has-text-weight-semibold"
      data-testid="toggle-register"
      @click="emit('toggleRegister')"
    >
      Não tem uma conta? Criar conta
    </b-button>
    <div v-if="googleAuthEnabled" class="auth-divider">ou</div>
    <b-button
      v-if="googleAuthEnabled"
      tag="a"
      href="/private/oauth2/authorization/google"
      expanded
      class="auth-submit google-signin"
      data-testid="google-login"
      @click="redirectingToGoogle = true"
    >
      <span class="google-signin-content is-flex is-align-items-center">
        <svg
          v-if="redirectingToGoogle"
          class="google-signin-spinner"
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          aria-hidden="true"
          data-testid="google-spinner"
        >
          <circle class="google-signin-spinner-track" cx="12" cy="12" r="9" stroke-width="3" />
          <path d="M21 12a9 9 0 0 0-9-9" stroke="#4285F4" stroke-width="3" stroke-linecap="round" />
        </svg>
        <GoogleMark v-else />
        <span>{{ redirectingToGoogle ? 'Redirecionando…' : 'Continuar com o Google' }}</span>
      </span>
    </b-button>
  </form>
</template>
