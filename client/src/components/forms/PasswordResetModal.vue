<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useToast } from 'buefy'
import AppModal from '@/components/ui/AppModal.vue'
import { useUsersAdminStore } from '@/stores/usersAdmin'
import { fieldValidationMessage } from '@/utils/apiErrors'

const props = defineProps<{ userId: string; userName: string }>()
const emit = defineEmits<{ close: [] }>()

const toast = useToast()
const usersAdminStore = useUsersAdminStore()

const newPassword = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const newPasswordError = ref('')

const passwordsMatch = computed(
  () => confirmPassword.value.length === 0 || newPassword.value === confirmPassword.value,
)

const valid = computed(
  () => newPassword.value.trim().length > 0 && newPassword.value === confirmPassword.value,
)

watch(newPassword, () => {
  newPasswordError.value = ''
})

async function submit() {
  if (!valid.value || submitting.value) return
  newPasswordError.value = ''
  submitting.value = true
  try {
    await usersAdminStore.resetPassword(props.userId, newPassword.value)
    emit('close')
    toast.open({ message: 'Senha redefinida.', type: 'is-success' })
  } catch (caughtError) {
    newPasswordError.value =
      fieldValidationMessage(caughtError) ?? 'Não foi possível redefinir a senha.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AppModal
    :title="`Redefinir senha de ${userName}`"
    subtitle="Defina uma nova senha para este usuário."
    @close="emit('close')"
  >
    <div class="form-grid">
      <b-field
        label="Nova senha"
        style="grid-column: 1/-1"
        :type="newPasswordError ? 'is-danger' : undefined"
        :message="newPasswordError || undefined"
      >
        <b-input v-model="newPassword" type="password" password-reveal autofocus />
      </b-field>
      <b-field
        label="Confirmar nova senha"
        style="grid-column: 1/-1"
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
        Redefinir
      </b-button>
    </template>
  </AppModal>
</template>
