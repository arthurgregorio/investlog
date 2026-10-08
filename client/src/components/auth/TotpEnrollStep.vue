<script setup lang="ts">
import { ref } from 'vue'

defineProps<{
  submitting: boolean
  qrCodeDataUri: string
}>()

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
    <b-field label="Código de 6 dígitos">
      <b-input
        v-model="totpCode"
        maxlength="6"
        :has-counter="false"
        placeholder="000000"
        required
      />
    </b-field>
    <b-button
      type="is-primary"
      expanded
      native-type="submit"
      :loading="submitting"
      class="auth-submit"
    >
      Confirmar
    </b-button>
  </form>
</template>
