<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useToast } from 'buefy'
import AppModal from '@/components/ui/AppModal.vue'
import { holdingsApi } from '@/api/holdings'
import { useTypesListStore } from '@/stores/typesList'
import { useStrategiesStore } from '@/stores/strategies'
import type { WalletKind } from '@/types'

const props = defineProps<{
  holdingId: string
  walletId: string
  kind: WalletKind
  initialTypeId: string | null
  initialSegmentId?: string | null
  initialStrategyClassId?: string | null
}>()

const emit = defineEmits<{ updated: []; close: [] }>()

const toast = useToast()
const typesListStore = useTypesListStore()
const strategiesStore = useStrategiesStore()

const SUBTITLES: Record<WalletKind, string> = {
  STOCKS: 'Escolha o tipo, o setor de atuação e a estratégia desta ação.',
  CRYPTO: 'Escolha o tipo, o segmento e a estratégia deste ativo.',
  FUNDS: 'Escolha o tipo, o segmento e a estratégia deste fundo.',
}

const typeOptions = computed(() => typesListStore.typesForKind(props.kind))

const typeId = ref(props.initialTypeId ?? '')
const segmentId = ref(props.initialSegmentId ?? '')
const strategyId = ref('')
const strategyClassId = ref(props.initialStrategyClassId ?? '')
const submitting = ref(false)

const strategyClasses = computed(
  () => strategiesStore.strategyById(strategyId.value)?.classes ?? [],
)

const typeChanged = computed(
  () => typeId.value !== '' && typeId.value !== (props.initialTypeId ?? ''),
)
const segmentChanged = computed(() => segmentId.value !== (props.initialSegmentId ?? ''))
const baselineClassId = computed(
  () => props.initialStrategyClassId ?? strategiesStore.defaultStrategy?.classes[0]?.id ?? '',
)
const classChanged = computed(
  () => strategyClassId.value !== '' && strategyClassId.value !== baselineClassId.value,
)

function strategyOfClass(classId: string) {
  return strategiesStore.strategies.find((strategy) =>
    strategy.classes.some((strategyClass) => strategyClass.id === classId),
  )
}

watch(strategyId, () => {
  if (!strategyClasses.value.some((strategyClass) => strategyClass.id === strategyClassId.value)) {
    strategyClassId.value = strategyClasses.value[0]?.id ?? ''
  }
})

onMounted(async () => {
  typesListStore.load()
  await strategiesStore.load()
  strategyId.value =
    strategyOfClass(strategyClassId.value)?.id ?? strategiesStore.defaultStrategy?.id ?? ''
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

function saveClass() {
  return holdingsApi.updateHoldingClass(
    props.kind,
    props.walletId,
    props.holdingId,
    strategyClassId.value,
  )
}

async function submit() {
  submitting.value = true
  const saves: Promise<unknown>[] = []
  if (typeChanged.value) saves.push(saveType())
  if (segmentChanged.value) saves.push(saveSegment())
  if (classChanged.value) saves.push(saveClass())
  const results = await Promise.allSettled(saves)
  submitting.value = false
  if (results.some((result) => result.status === 'fulfilled')) emit('updated')
  if (classChanged.value) strategiesStore.refresh()
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
    <div class="fixed-grid has-2-cols mb-0">
      <div class="grid is-gap-2">
        <b-field label="Estratégia" class="cell is-col-span-2-mobile">
          <b-select v-model="strategyId" expanded data-testid="edit-tags-strategy">
            <option
              v-for="strategy in strategiesStore.strategies"
              :key="strategy.id"
              :value="strategy.id"
            >
              {{ strategy.name }}
            </option>
          </b-select>
        </b-field>
        <b-field label="Classe" class="cell is-col-span-2-mobile">
          <b-select v-model="strategyClassId" expanded data-testid="edit-tags-class">
            <option
              v-for="strategyClass in strategyClasses"
              :key="strategyClass.id"
              :value="strategyClass.id"
            >
              {{ strategyClass.name }}
            </option>
          </b-select>
        </b-field>
      </div>
    </div>
    <template #footer>
      <b-button outlined type="is-danger" :disabled="submitting" @click="emit('close')"
        >Cancelar</b-button
      >
      <b-button
        type="is-success"
        icon-left="check"
        :loading="submitting"
        :disabled="!typeChanged && !segmentChanged && !classChanged"
        data-testid="edit-tags-submit"
        @click="submit"
      >
        Salvar
      </b-button>
    </template>
  </AppModal>
</template>
