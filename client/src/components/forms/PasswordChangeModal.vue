<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useToast } from 'buefy'
import AppModal from '@/components/ui/AppModal.vue'
import { profileApi } from '@/api/profile'

const emit = defineEmits<{ close: [] }>()

const toast = useToast()

const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const currentPasswordError = ref('')

const passwordsMatch = computed(
  () => confirmPassword.value.length === 0 || newPassword.value === confirmPassword.value,
)

const valid = computed(
  () =>
    currentPassword.value.trim().length > 0 &&
    newPassword.value.trim().length > 0 &&
    newPassword.value === confirmPassword.value,
)

watch(currentPassword, () => {
  currentPasswordError.value = ''
})

async function submit() {
  if (!valid.value || submitting.value) return
  currentPasswordError.value = ''
  submitting.value = true
  try {
    await profileApi.changePassword(currentPassword.value, newPassword.value)
    emit('close')
    toast.open({ message: 'Senha alterada!', type: 'is-success' })
  } catch {
    currentPasswordError.value = 'Senha atual incorreta.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AppModal
    title="Trocar senha"
    subtitle="Informe sua senha atual e escolha a nova senha."
    @close="emit('close')"
  >
    <div class="form-grid">
      <b-field
        label="Senha atual"
        style="grid-column: 1/-1"
        :type="currentPasswordError ? 'is-danger' : undefined"
        :message="currentPasswordError || undefined"
      >
        <b-input v-model="currentPassword" type="password" password-reveal autofocus />
      </b-field>
      <b-field label="Nova senha">
        <b-input v-model="newPassword" type="password" password-reveal />
      </b-field>
      <b-field
        label="Confirmar nova senha"
        :type="passwordsMatch ? undefined : 'is-danger'"
        :message="passwordsMatch ? undefined : 'As senhas não coincidem.'"
      >
        <b-input v-model="confirmPassword" type="password" password-reveal />
      </b-field>
    </div>
    <template #footer>
      <b-button outlined type="is-danger" :disabled="submitting" @click="emit('close')"
        >Cancelar</b-button
      >
      <b-button
        type="is-success"
        class="has-text-light"
        :disabled="!valid"
        :loading="submitting"
        @click="submit"
      >
        Salvar
      </b-button>
    </template>
  </AppModal>
</template>
