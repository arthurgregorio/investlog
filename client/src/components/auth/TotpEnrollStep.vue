<script setup lang="ts">
import { ref } from 'vue'
import TotpCodeInput from './TotpCodeInput.vue'

withDefaults(
  defineProps<{
    submitting: boolean
    qrCodeDataUri: string
    invalid?: boolean
  }>(),
  { invalid: false },
)

const emit = defineEmits<{
  submit: [totpCode: string]
}>()

const totpCode = ref('')
</script>

<template>
  <form class="form-stack" @submit.prevent="emit('submit', totpCode)">
    <img
      :src="qrCodeDataUri"
      alt="QR code para configurar a autenticação em duas etapas"
      class="auth-totp-qr is-block mx-auto"
    />
    <TotpCodeInput
      v-model="totpCode"
      :invalid="invalid"
      :disabled="submitting"
      @complete="emit('submit', $event)"
    />
    <b-button
      type="is-primary"
      expanded
      native-type="submit"
      :disabled="submitting"
      class="auth-submit"
    >
      {{ submitting ? 'Verificando…' : 'Confirmar' }}
    </b-button>
  </form>
</template>
