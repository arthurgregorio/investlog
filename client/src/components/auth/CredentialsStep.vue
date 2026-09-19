<script setup lang="ts">
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
      class="auth-submit has-text-light"
    >
      Entrar
    </b-button>
    <button
      type="button"
      class="auth-toggle"
      data-testid="toggle-register"
      @click="emit('toggleRegister')"
    >
      Não tem uma conta? Criar conta
    </button>
    <div v-if="googleAuthEnabled" class="auth-divider">ou</div>
    <a v-if="googleAuthEnabled" href="/private/oauth2/authorization/google" class="auth-google-button">
      Continuar com Google
    </a>
  </form>
</template>
