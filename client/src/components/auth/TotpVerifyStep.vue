<script setup lang="ts">
import { ref } from 'vue'
import TotpCodeInput from './TotpCodeInput.vue'

withDefaults(
  defineProps<{
    submitting: boolean
    invalid?: boolean
  }>(),
  { invalid: false },
)

const emit = defineEmits<{
  submit: [totpCode: string, trustDevice: boolean]
}>()

const totpCode = ref('')
const trustDevice = ref(false)

function submitCode(code: string) {
  emit('submit', code, trustDevice.value)
}
</script>

<template>
  <form class="form-stack" @submit.prevent="submitCode(totpCode)">
    <TotpCodeInput
      v-model="totpCode"
      :invalid="invalid"
      :disabled="submitting"
      @complete="submitCode"
    />
    <b-checkbox v-model="trustDevice">Confiar neste dispositivo por 30 dias</b-checkbox>
    <b-button
      type="is-primary"
      expanded
      native-type="submit"
      :disabled="submitting"
      class="auth-submit"
    >
      {{ submitting ? 'Verificando…' : 'Entrar' }}
    </b-button>
  </form>
</template>
