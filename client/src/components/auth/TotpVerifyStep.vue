<script setup lang="ts">
import { ref } from 'vue'

defineProps<{
  submitting: boolean
}>()

const emit = defineEmits<{
  submit: [totpCode: string, trustDevice: boolean]
}>()

const totpCode = ref('')
const trustDevice = ref(false)
</script>

<template>
  <form class="form-stack" @submit.prevent="emit('submit', totpCode, trustDevice)">
    <b-field label="Código de 6 dígitos">
      <b-input v-model="totpCode" maxlength="6" placeholder="000000" required />
    </b-field>
    <b-checkbox v-model="trustDevice">Confiar neste dispositivo por 30 dias</b-checkbox>
    <b-button
      type="is-primary"
      expanded
      native-type="submit"
      :loading="submitting"
      class="auth-submit has-text-light"
    >
      Entrar
    </b-button>
  </form>
</template>
