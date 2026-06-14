<script setup lang="ts">
import AppModal from '@/components/ui/AppModal.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AddInvestmentForm from '@/components/forms/AddInvestmentForm.vue'
import { useAddInvestmentForm } from '@/composables/useAddInvestmentForm'
import type { WalletKind } from '@/types'

const props = defineProps<{ initialKind?: WalletKind }>()
const emit = defineEmits<{ close: []; 'create-wallet': [WalletKind] }>()

const { form, submit } = useAddInvestmentForm(props.initialKind ?? 'stocks', () => emit('close'))
</script>

<template>
  <AppModal
    title="Adicionar investimento"
    subtitle="Registre uma aquisição no seu logbook."
    wide
    @close="emit('close')"
  >
    <AddInvestmentForm :form="form" @create-wallet="(type) => emit('create-wallet', type)" />
    <template #footer>
      <AppButton variant="ghost" @click="emit('close')">Cancelar</AppButton>
      <AppButton variant="primary" icon="check" :disabled="!form.valid" @click="submit">Adicionar</AppButton>
    </template>
  </AppModal>
</template>
