<script setup lang="ts">
import { onMounted } from 'vue'
import AppModal from '@/components/ui/AppModal.vue'
import MoveHoldingList from '@/components/investments/MoveHoldingList.vue'
import { useMoveHoldingsForm } from '@/composables/useMoveHoldingsForm'
import { useWalletsStore } from '@/stores/wallets'

const props = defineProps<{
  originWalletId?: string
  preselectedHoldingId?: string
}>()

const emit = defineEmits<{ moved: []; close: [] }>()

const walletsStore = useWalletsStore()

const {
  selectedOriginId,
  destinationId,
  holdings,
  loadingHoldings,
  moveAll,
  selected,
  quantities,
  submitting,
  error,
  origin,
  destinations,
  valid,
  select,
  setQuantity,
  load,
  submit,
} = useMoveHoldingsForm(
  { originWalletId: props.originWalletId, preselectedHoldingId: props.preselectedHoldingId },
  () => {
    emit('moved')
    emit('close')
  },
)

onMounted(load)
</script>

<template>
  <AppModal
    title="Mover investimentos"
    subtitle="Leve investimentos para outra carteira do mesmo tipo e moeda, mantendo o histórico de compras."
    wide
    @close="emit('close')"
  >
    <p v-if="error" class="auth-error" data-testid="move-error">{{ error }}</p>

    <div class="form-grid">
      <b-field v-if="!originWalletId" label="Carteira de origem" style="grid-column: 1/-1">
        <b-select
          v-model="selectedOriginId"
          placeholder="Selecione a carteira"
          expanded
          data-testid="move-origin"
        >
          <option v-for="wallet in walletsStore.wallets" :key="wallet.id" :value="wallet.id">
            {{ wallet.name }} · {{ wallet.currency }}
          </option>
        </b-select>
      </b-field>

      <b-field label="Carteira de destino" style="grid-column: 1/-1">
        <b-select
          v-model="destinationId"
          placeholder="Selecione a carteira"
          expanded
          :disabled="!origin"
          data-testid="move-destination"
        >
          <option v-for="wallet in destinations" :key="wallet.id" :value="wallet.id">
            {{ wallet.name }} · {{ wallet.currency }}
          </option>
        </b-select>
      </b-field>
    </div>

    <p v-if="origin && destinations.length === 0" class="has-text-grey is-size-7 my-2">
      Nenhuma outra carteira do mesmo tipo e moeda para receber os investimentos.
    </p>

    <MoveHoldingList
      v-if="origin"
      v-model:move-all="moveAll"
      :holdings="holdings"
      :loading="loadingHoldings"
      :selected="selected"
      :quantities="quantities"
      @select="select"
      @update-quantity="setQuantity"
    />

    <template #footer>
      <b-button outlined type="is-danger" :disabled="submitting" @click="emit('close')"
        >Cancelar</b-button
      >
      <b-button
        type="is-primary"
        class="has-text-light"
        icon-left="swap-horizontal"
        :disabled="!valid"
        :loading="submitting"
        data-testid="move-submit"
        @click="submit"
      >
        Mover
      </b-button>
    </template>
  </AppModal>
</template>
