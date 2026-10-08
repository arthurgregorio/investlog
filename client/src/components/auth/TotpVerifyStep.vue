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
    <div class="is-flex is-flex-direction-column">
      <b-field label="Código de 6 dígitos">
        <b-input
          v-model="totpCode"
          maxlength="6"
          :has-counter="false"
          placeholder="000000"
          required
        />
      </b-field>
      <b-checkbox v-model="trustDevice">Confiar neste dispositivo por 30 dias</b-checkbox>
    </div>
    <b-button
      type="is-primary"
      expanded
      native-type="submit"
      :loading="submitting"
      class="auth-submit"
    >
      Entrar
    </b-button>
  </form>
</template>
