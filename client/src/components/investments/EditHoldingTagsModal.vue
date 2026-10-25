<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useToast } from 'buefy'
import AppModal from '@/components/ui/AppModal.vue'
import { holdingsApi } from '@/api/holdings'
import { useTypesListStore } from '@/stores/typesList'
import type { WalletKind } from '@/types'

const props = defineProps<{
  holdingId: string
  walletId: string
  kind: WalletKind
  initialTypeId: string | null
  initialSegmentId?: string | null
}>()

const emit = defineEmits<{ updated: []; close: [] }>()

const toast = useToast()
const typesListStore = useTypesListStore()

const SUBTITLES: Record<WalletKind, string> = {
  STOCKS: 'Escolha o tipo e o setor de atuação desta ação.',
  CRYPTO: 'Escolha o tipo e o segmento deste ativo.',
  FUNDS: 'Escolha o tipo e o segmento deste fundo.',
}

const typeOptions = computed(() => typesListStore.typesForKind(props.kind))

const typeId = ref(props.initialTypeId ?? '')
const segmentId = ref(props.initialSegmentId ?? '')
const submitting = ref(false)

const typeChanged = computed(
  () => typeId.value !== '' && typeId.value !== (props.initialTypeId ?? ''),
)
const segmentChanged = computed(() => segmentId.value !== (props.initialSegmentId ?? ''))

onMounted(() => {
  typesListStore.load()
})

function saveType() {
  const payload = { typeId: typeId.value }
  if (props.kind === 'STOCKS') {
    return holdingsApi.updateStockHolding(props.walletId, props.holdingId, payload)
  }
  if (props.kind === 'CRYPTO') {
    return holdingsApi.updateCryptoHolding(props.walletId, props.holdingId, payload)
  }
  return holdingsApi.updateFundHolding(props.walletId, props.holdingId, payload)
}

function saveSegment() {
  return holdingsApi.updateHoldingSegment(
    props.kind,
    props.walletId,
    props.holdingId,
    segmentId.value || null,
  )
}

async function submit() {
  submitting.value = true
  const saves: Promise<unknown>[] = []
  if (typeChanged.value) saves.push(saveType())
  if (segmentChanged.value) saves.push(saveSegment())
  const results = await Promise.allSettled(saves)
  submitting.value = false
  if (results.some((result) => result.status === 'fulfilled')) emit('updated')
  if (results.every((result) => result.status === 'fulfilled')) {
    toast.open({ message: 'Tags atualizadas.', type: 'is-success' })
    emit('close')
  }
}
</script>

<template>
  <AppModal title="Editar tags" :subtitle="SUBTITLES[kind]" @close="emit('close')">
    <b-field label="Tipo">
      <b-select v-model="typeId" expanded data-testid="edit-tags-type">
        <option v-if="!initialTypeId" value="">Sem tipo</option>
        <option v-for="type in typeOptions" :key="type.id" :value="type.id">
          {{ type.name }}
        </option>
      </b-select>
    </b-field>
    <b-field label="Segmento">
      <b-select v-model="segmentId" expanded data-testid="edit-tags-segment">
        <option value="">Sem segmento</option>
        <option v-for="segment in typesListStore.segments" :key="segment.id" :value="segment.id">
          {{ segment.name }}
        </option>
      </b-select>
    </b-field>
    <template #footer>
      <b-button outlined type="is-danger" :disabled="submitting" @click="emit('close')"
        >Cancelar</b-button
      >
      <b-button
        type="is-success"
        icon-left="check"
        :loading="submitting"
        :disabled="!typeChanged && !segmentChanged"
        data-testid="edit-tags-submit"
        @click="submit"
      >
        Salvar
      </b-button>
    </template>
  </AppModal>
</template>
