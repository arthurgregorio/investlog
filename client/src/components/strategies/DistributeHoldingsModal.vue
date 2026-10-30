<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useToast } from 'buefy'
import AppModal from '@/components/ui/AppModal.vue'
import FrozenBadge from '@/components/ui/FrozenBadge.vue'
import TickerBadge from '@/components/ui/TickerBadge.vue'
import { strategiesApi } from '@/api/strategies'
import { useStrategiesStore } from '@/stores/strategies'
import { badgeColor } from '@/utils/walletTypes'
import type { ClassHolding } from '@/types'

const props = defineProps<{ initialClassId?: string }>()
const emit = defineEmits<{ moved: []; close: [] }>()

const toast = useToast()
const strategiesStore = useStrategiesStore()

const originClassId = ref(props.initialClassId ?? '')
const destinationStrategyId = ref('')
const destinationClassId = ref('')
const holdings = ref<ClassHolding[]>([])
const selected = ref<Record<string, boolean>>({})
const moveAll = ref(false)
const loading = ref(false)
const submitting = ref(false)

const originOptions = computed(() =>
  strategiesStore.strategies.flatMap((strategy) =>
    strategy.classes.map((strategyClass) => ({
      id: strategyClass.id,
      label: `${strategy.name} · ${strategyClass.name} (${strategyClass.holdingCount} investimentos)`,
    })),
  ),
)

const destinationStrategies = computed(() =>
  strategiesStore.strategies.filter((strategy) =>
    strategy.classes.some((strategyClass) => strategyClass.id !== originClassId.value),
  ),
)

const destinationClasses = computed(
  () =>
    strategiesStore
      .strategyById(destinationStrategyId.value)
      ?.classes.filter((strategyClass) => strategyClass.id !== originClassId.value) ?? [],
)

const chosen = computed(() =>
  holdings.value.filter((holding) => moveAll.value || selected.value[holding.id]),
)

const valid = computed(() => !!destinationClassId.value && chosen.value.length > 0)

function pickDestinationClass() {
  if (
    !destinationClasses.value.some((strategyClass) => strategyClass.id === destinationClassId.value)
  ) {
    destinationClassId.value = destinationClasses.value[0]?.id ?? ''
  }
}

async function loadOrigin() {
  selected.value = {}
  moveAll.value = false
  holdings.value = []
  pickDestinationClass()
  if (!originClassId.value) return
  loading.value = true
  try {
    holdings.value = await strategiesApi.findClassHoldings(originClassId.value)
  } finally {
    loading.value = false
  }
}

watch(originClassId, loadOrigin)
watch(destinationStrategyId, pickDestinationClass)

onMounted(async () => {
  await strategiesStore.load()
  if (!originClassId.value) originClassId.value = originOptions.value[0]?.id ?? ''
  destinationStrategyId.value =
    destinationStrategies.value.find((strategy) => !strategy.default)?.id ??
    destinationStrategies.value[0]?.id ??
    ''
  await loadOrigin()
})

function displayName(holding: ClassHolding): string {
  return holding.ticker ?? holding.name
}

async function submit() {
  if (!valid.value || submitting.value) return
  submitting.value = true
  try {
    await strategiesStore.assignHoldings(
      destinationClassId.value,
      chosen.value.map((holding) => ({ kind: holding.kind, id: holding.id })),
    )
    toast.open({ message: 'Investimentos distribuídos.', type: 'is-success' })
    emit('moved')
    emit('close')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AppModal
    title="Distribuir investimentos"
    subtitle="Leve vários investimentos de uma classe para outra, em qualquer estratégia. Posições e histórico de compras não mudam."
    wide
    @close="emit('close')"
  >
    <div class="fixed-grid has-2-cols mb-0">
      <div class="grid is-gap-2">
        <b-field label="Classe de origem" class="cell is-col-span-2">
          <b-select v-model="originClassId" expanded data-testid="origin-class">
            <option v-for="option in originOptions" :key="option.id" :value="option.id">
              {{ option.label }}
            </option>
          </b-select>
        </b-field>
        <b-field label="Estratégia de destino" class="cell is-col-span-2-mobile">
          <b-select v-model="destinationStrategyId" expanded data-testid="destination-strategy">
            <option
              v-for="strategy in destinationStrategies"
              :key="strategy.id"
              :value="strategy.id"
            >
              {{ strategy.name }}
            </option>
          </b-select>
        </b-field>
        <b-field label="Classe de destino" class="cell is-col-span-2-mobile">
          <b-select v-model="destinationClassId" expanded data-testid="destination-class">
            <option
              v-for="strategyClass in destinationClasses"
              :key="strategyClass.id"
              :value="strategyClass.id"
            >
              {{ strategyClass.name }}
            </option>
          </b-select>
        </b-field>
      </div>
    </div>

    <div class="move-list-head">
      <span class="kpi-label">Investimentos</span>
      <b-switch v-model="moveAll" :disabled="holdings.length === 0" data-testid="move-all">
        Mover tudo
      </b-switch>
    </div>
    <div class="move-list">
      <b-loading :is-full-page="false" :model-value="loading" />
      <p v-if="!loading && holdings.length === 0" class="move-hint">
        Nenhum investimento nesta classe.
      </p>
      <div
        v-for="holding in holdings"
        :key="holding.id"
        class="move-item"
        :class="{ 'is-selected': moveAll || selected[holding.id] }"
        data-testid="distribute-item"
      >
        <b-checkbox v-model="selected[holding.id]" :disabled="moveAll" />
        <FrozenBadge v-if="holding.frozen" />
        <TickerBadge
          v-else
          :ticker="displayName(holding)"
          :color="badgeColor(holding.ticker, holding.kind)"
        />
        <div class="move-item-meta">
          <div class="t-ticker">{{ displayName(holding) }}</div>
          <div class="t-name">{{ holding.walletName }}</div>
        </div>
      </div>
    </div>

    <template #footer>
      <b-button outlined type="is-danger" :disabled="submitting" @click="emit('close')">
        Cancelar
      </b-button>
      <b-button
        type="is-success"
        icon-left="swap-horizontal"
        :disabled="!valid"
        :loading="submitting"
        data-testid="distribute-submit"
        @click="submit"
      >
        {{ chosen.length > 0 ? `Mover ${chosen.length}` : 'Mover' }}
      </b-button>
    </template>
  </AppModal>
</template>
