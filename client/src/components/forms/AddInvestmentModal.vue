<script setup lang="ts">
import AppModal from '@/components/ui/AppModal.vue'
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
      <b-button type="is-text" @click="emit('close')">Cancelar</b-button>
      <b-button type="is-primary" icon-left="check" :disabled="!form.valid" @click="submit">Adicionar</b-button>
    </template>
  </AppModal>
</template>
