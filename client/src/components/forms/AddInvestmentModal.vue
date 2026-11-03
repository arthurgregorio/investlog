<script setup lang="ts">
import { onMounted } from 'vue'
import AppModal from '@/components/ui/AppModal.vue'
import AddInvestmentForm from '@/components/forms/AddInvestmentForm.vue'
import { useAddInvestmentForm } from '@/composables/useAddInvestmentForm'
import { useWalletsStore } from '@/stores/wallets'
import { useTypesListStore } from '@/stores/typesList'
import { useStrategiesStore } from '@/stores/strategies'
import { useHoldingsListStore } from '@/stores/holdingsList'
import type { WalletKind } from '@/types'

const props = defineProps<{ initialKind?: WalletKind; initialWalletId?: string }>()
const emit = defineEmits<{ close: []; 'create-wallet': [WalletKind] }>()

const walletsStore = useWalletsStore()
const typesListStore = useTypesListStore()
const strategiesStore = useStrategiesStore()
const holdingsListStore = useHoldingsListStore()

onMounted(() => {
  Promise.all([walletsStore.load(), typesListStore.load(), strategiesStore.load()])
})

const { form, submit } = useAddInvestmentForm(
  props.initialKind ?? 'STOCKS',
  props.initialWalletId,
  () => {
    holdingsListStore.refresh()
    emit('close')
  },
)
</script>

<template>
  <AppModal
    title="Adicionar investimento"
    subtitle="Registre uma aquisição no seu logbook."
    extra-wide
    @close="emit('close')"
  >
    <AddInvestmentForm :form="form" @create-wallet="(kind) => emit('create-wallet', kind)" />
    <template #footer>
      <b-button outlined type="is-danger" :disabled="form.submitting" @click="emit('close')">
        Cancelar
      </b-button>
      <b-button
        type="is-success"
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
