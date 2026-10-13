<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useToast } from 'buefy'
import AppModal from '@/components/ui/AppModal.vue'
import { holdingsApi } from '@/api/holdings'
import { useTypesListStore } from '@/stores/typesList'

const props = defineProps<{
  holdingId: string
  walletId: string
  kind: 'STOCKS' | 'FUNDS'
  initialTypeId: string
  initialSegmentId?: string | null
}>()

const emit = defineEmits<{ updated: []; close: [] }>()

const toast = useToast()
const typesListStore = useTypesListStore()

const isStock = computed(() => props.kind === 'STOCKS')
const typeOptions = computed(() =>
  isStock.value ? typesListStore.stockTypes : typesListStore.fundTypes,
)

const typeId = ref(props.initialTypeId)
const segmentId = ref(props.initialSegmentId ?? '')
const submitting = ref(false)

const typeChanged = computed(() => typeId.value !== props.initialTypeId)
const segmentChanged = computed(
  () => isStock.value && segmentId.value !== (props.initialSegmentId ?? ''),
)

onMounted(() => {
  typesListStore.load()
})

function saveType() {
  return isStock.value
    ? holdingsApi.updateStockHolding(props.walletId, props.holdingId, { stockTypeId: typeId.value })
    : holdingsApi.updateFundHolding(props.walletId, props.holdingId, { fundTypeId: typeId.value })
}

function saveSegment() {
  return holdingsApi.updateStockHoldingSegment(
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
  <AppModal
    title="Editar tags"
    :subtitle="
      isStock ? 'Escolha o tipo e o setor de atuação desta ação.' : 'Escolha o tipo deste fundo.'
    "
    @close="emit('close')"
  >
    <b-field label="Tipo">
      <b-select v-model="typeId" expanded data-testid="edit-tags-type">
        <option v-for="type in typeOptions" :key="type.id" :value="type.id">
          {{ type.name }}
        </option>
      </b-select>
    </b-field>
    <b-field v-if="isStock" label="Segmento">
      <b-select v-model="segmentId" expanded data-testid="edit-tags-segment">
        <option value="">Sem segmento</option>
        <option
          v-for="stockSegment in typesListStore.stockSegments"
          :key="stockSegment.id"
          :value="stockSegment.id"
        >
          {{ stockSegment.name }}
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
