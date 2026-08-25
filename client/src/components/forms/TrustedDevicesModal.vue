<script setup lang="ts">
import { onMounted } from 'vue'
import { useDialog, useToast } from 'buefy'
import AppModal from '@/components/ui/AppModal.vue'
import { useTrustedDevicesStore } from '@/stores/trustedDevices'

const emit = defineEmits<{ close: [] }>()

const toast = useToast()
const dialog = useDialog()
const trustedDevicesStore = useTrustedDevicesStore()

onMounted(() => {
  trustedDevicesStore.load()
})

function formatDateTime(iso: string): string {
  const date = new Date(iso)
  return date.toLocaleString('pt-BR', { dateStyle: 'medium', timeStyle: 'short' })
}

function confirmRevoke(id: string, label: string) {
  dialog.confirm({
    title: 'Revogar dispositivo confiável',
    message: `Revogar <strong>${label}</strong>? Será necessário confirmar o código de autenticação no próximo login nesse dispositivo.`,
    type: 'is-danger',
    hasIcon: true,
    confirmText: 'Revogar',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      await trustedDevicesStore.revoke(id)
      toast.open({ message: 'Dispositivo revogado.', type: 'is-success' })
    },
  })
}
</script>

<template>
  <AppModal
    title="Dispositivos confiáveis"
    subtitle="Dispositivos que não pedem código de autenticação por 30 dias."
    @close="emit('close')"
  >
    <p v-if="trustedDevicesStore.devices.length === 0" class="has-text-grey">
      Nenhum dispositivo confiável no momento.
    </p>
    <div v-else>
      <div
        v-for="device in trustedDevicesStore.devices"
        :key="device.id"
        class="trusted-device-row"
      >
        <div>
          <div class="trusted-device-label">{{ device.label }}</div>
          <div class="trusted-device-meta">
            Último uso em {{ formatDateTime(device.lastUsedAt) }} · Expira em
            {{ formatDateTime(device.expiresAt) }}
          </div>
        </div>
        <b-button
          type="is-danger"
          outlined
          icon-left="delete-outline"
          aria-label="Revogar dispositivo"
          @click="confirmRevoke(device.id, device.label)"
        />
      </div>
    </div>
    <template #footer>
      <b-button type="is-primary" class="has-text-light" @click="emit('close')">Fechar</b-button>
    </template>
  </AppModal>
</template>
