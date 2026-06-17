<script setup lang="ts">
import { onMounted } from 'vue'
import AppModal from '@/components/ui/AppModal.vue'
import AddInvestmentForm from '@/components/forms/AddInvestmentForm.vue'
import { useAddInvestmentForm } from '@/composables/useAddInvestmentForm'
import { useWalletsStore } from '@/stores/wallets'
import { useTypesListStore } from '@/stores/typesList'
import { useHoldingsListStore } from '@/stores/holdingsList'
import type { WalletKind } from '@/types'

const props = defineProps<{ initialKind?: WalletKind }>()
const emit = defineEmits<{ close: []; 'create-wallet': [WalletKind] }>()

const walletsStore = useWalletsStore()
const typesListStore = useTypesListStore()
const holdingsListStore = useHoldingsListStore()

onMounted(() => {
  Promise.all([walletsStore.load(), typesListStore.load()])
})

const { form, submit } = useAddInvestmentForm(props.initialKind ?? 'stocks', () => {
  holdingsListStore.refresh()
  emit('close')
})
</script>

<template>
  <AppModal
    title="Adicionar investimento"
    subtitle="Registre uma aquisição no seu logbook."
    wide
    @close="emit('close')"
  >
    <AddInvestmentForm :form="form" @create-wallet="(kind) => emit('create-wallet', kind)" />
    <template #footer>
      <b-button :disabled="form.submitting" @click="emit('close')">Cancelar</b-button>
      <b-button
        type="is-primary"
        icon-left="check"
        :disabled="!form.valid"
        :loading="form.submitting"
        @click="submit"
      >
        Adicionar
      </b-button>
    </template>
  </AppModal>
</template>
