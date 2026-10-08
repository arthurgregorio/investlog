<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useToast } from 'buefy'
import AppModal from '@/components/ui/AppModal.vue'
import { holdingsApi } from '@/api/holdings'
import { useTypesListStore } from '@/stores/typesList'

const props = defineProps<{
  holdingId: string
  walletId: string
  initialSegmentId: string | null
}>()

const emit = defineEmits<{ updated: []; close: [] }>()

const toast = useToast()
const typesListStore = useTypesListStore()

const segmentId = ref(props.initialSegmentId ?? '')
const submitting = ref(false)

onMounted(() => {
  typesListStore.load()
})

async function submit() {
  submitting.value = true
  try {
    await holdingsApi.updateStockHoldingSegment(
      props.walletId,
      props.holdingId,
      segmentId.value || null,
    )
    toast.open({ message: 'Segmento atualizado.', type: 'is-success' })
    emit('updated')
    emit('close')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AppModal
    title="Definir segmento"
    subtitle="Escolha o setor de atuação desta ação."
    @close="emit('close')"
  >
    <b-field label="Segmento">
      <b-select v-model="segmentId" expanded data-testid="set-segment-select">
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
        data-testid="set-segment-submit"
        @click="submit"
      >
        Salvar
      </b-button>
    </template>
  </AppModal>
</template>
