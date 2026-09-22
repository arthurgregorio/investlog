<script setup lang="ts">
import { computed, ref } from 'vue'
import PasswordRequirementHint from '@/components/forms/PasswordRequirementHint.vue'
import { meetsPasswordRequirements } from '@/utils/passwordRules'

defineProps<{
  submitting: boolean
}>()

const emit = defineEmits<{
  submit: [name: string]
  toggleRegister: []
}>()

const email = defineModel<string>('email', { required: true })
const password = defineModel<string>('password', { required: true })

const name = ref('')

const registrationValid = computed(
  () =>
    name.value.trim().length > 0 &&
    email.value.trim().length > 0 &&
    meetsPasswordRequirements(password.value),
)
</script>

<template>
  <form class="form-stack" @submit.prevent="emit('submit', name)">
    <b-field label="Nome">
      <b-input v-model="name" type="text" placeholder="Seu nome" required />
    </b-field>
    <b-field label="E-mail">
      <b-input v-model="email" type="email" placeholder="voce@email.com" required />
    </b-field>
    <div class="field-with-hint">
      <b-field label="Senha">
        <b-input v-model="password" type="password" placeholder="••••••••" required />
      </b-field>
      <PasswordRequirementHint :password="password" />
    </div>
    <b-button
      type="is-primary"
      expanded
      native-type="submit"
      :loading="submitting"
      :disabled="!registrationValid"
      class="auth-submit has-text-light"
    >
      Criar conta
    </b-button>
    <button
      type="button"
      class="auth-toggle"
      data-testid="toggle-register"
      @click="emit('toggleRegister')"
    >
      Já tem uma conta? Entrar
    </button>
  </form>
</template>
