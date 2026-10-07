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
      class="auth-submit"
      data-testid="google-login"
    >
      Continuar com Google
    </b-button>
  </form>
</template>
