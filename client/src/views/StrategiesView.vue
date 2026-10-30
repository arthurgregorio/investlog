<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from 'buefy'
import Card from '@/components/ui/Card.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import StrategyRail from '@/components/strategies/StrategyRail.vue'
import StrategyClassesEditor from '@/components/strategies/StrategyClassesEditor.vue'
import ClassHoldingTargets from '@/components/strategies/ClassHoldingTargets.vue'
import DefaultStrategyPanel from '@/components/strategies/DefaultStrategyPanel.vue'
import CreateStrategyModal from '@/components/strategies/CreateStrategyModal.vue'
import DistributeHoldingsModal from '@/components/strategies/DistributeHoldingsModal.vue'
import AllocationSummary from '@/components/strategies/AllocationSummary.vue'
import ClassAllocationTable from '@/components/strategies/ClassAllocationTable.vue'
import ClassAllocationDetail from '@/components/strategies/ClassAllocationDetail.vue'
import { strategiesApi } from '@/api/strategies'
import { safeHtml, useConfirmDialog } from '@/composables/useConfirmDialog'
import { useStrategiesStore } from '@/stores/strategies'
import type { Strategy, StrategyAllocation } from '@/types'

const ALLOCATION_TAB = 0
const CLASSES_TAB = 1

const route = useRoute()
const router = useRouter()
const toast = useToast()
const { confirm } = useConfirmDialog()
const strategiesStore = useStrategiesStore()

const selectedId = ref<string | null>(null)
const expandedClassId = ref<string | null>(null)
const expandedAllocationClassId = ref<string | null>(null)
const activeTab = ref(ALLOCATION_TAB)
const allocation = ref<StrategyAllocation | null>(null)
const creating = ref(false)
const distributeFromClassId = ref<string | null>(null)
const distributeKey = ref(0)

const selected = computed(() =>
  selectedId.value ? strategiesStore.strategyById(selectedId.value) : undefined,
)
const expandedClass = computed(() =>
  selected.value?.classes.find((strategyClass) => strategyClass.id === expandedClassId.value),
)
const expandedAllocationClass = computed(() =>
  allocation.value?.classes.find(
    (strategyClass) => strategyClass.id === expandedAllocationClassId.value,
  ),
)
const showAllocation = computed(
  () => !!selected.value && !selected.value.default && activeTab.value === ALLOCATION_TAB,
)
const headline = computed(() =>
  selected.value ? `${selected.value.name} · ${selected.value.holdingCount} investimentos` : '',
)

function pickInitial() {
  if (selected.value) return
  const requested = route.query.strategy
  selectedId.value =
    (typeof requested === 'string' ? strategiesStore.strategyById(requested)?.id : undefined) ??
    strategiesStore.strategies.find((strategy) => !strategy.default)?.id ??
    strategiesStore.strategies[0]?.id ??
    null
}

async function loadAllocation(strategy: Strategy | undefined) {
  if (!strategy || strategy.default) {
    allocation.value = null
    return
  }
  const loaded = await strategiesApi.findAllocation(strategy.id)
  if (selected.value?.id === loaded.strategyId) allocation.value = loaded
}

watch(() => strategiesStore.strategies, pickInitial)

watch(selectedId, () => {
  expandedClassId.value = null
  expandedAllocationClassId.value = null
  allocation.value = null
  activeTab.value = selected.value?.default ? CLASSES_TAB : ALLOCATION_TAB
})

watch(selected, loadAllocation)

onMounted(async () => {
  await strategiesStore.load()
  pickInitial()
})

function onCreated(strategy: Strategy) {
  selectedId.value = strategy.id
}

function confirmDelete(strategy: Strategy) {
  confirm({
    title: 'Excluir estratégia',
    message: safeHtml`<b>${strategy.name}</b> será excluída. Os ${strategy.holdingCount} investimentos dela voltam para <b>Sem estratégia · Não definida</b>, sem metas.`,
    confirmText: 'Excluir estratégia',
    onConfirm: async () => {
      await strategiesStore.remove(strategy.id)
      selectedId.value = null
      pickInitial()
      toast.open({ message: 'Estratégia excluída.', type: 'is-success' })
    },
  })
}

function openDistribute(classId: string | undefined) {
  if (!classId) return
  distributeKey.value += 1
  distributeFromClassId.value = classId
}

function openPlanner() {
  if (selected.value) router.push({ name: 'strategy-plan', params: { id: selected.value.id } })
}
</script>

<template>
  <div class="page">
    <PageHeader title="Estratégias" :description="headline">
      <b-button icon-left="plus" data-testid="new-strategy" @click="creating = true">
        Nova estratégia
      </b-button>
      <b-button
        type="is-primary"
        icon-left="calculator-variant-outline"
        :disabled="!selected || selected.default"
        data-testid="plan-contribution"
        @click="openPlanner"
      >
        Planejar aporte
      </b-button>
    </PageHeader>

    <div class="investments-body">
      <StrategyRail
        :strategies="strategiesStore.strategies"
        :selected-id="selectedId"
        @select="(strategyId) => (selectedId = strategyId)"
      />

      <div v-if="selected" class="investments-main is-flex is-flex-direction-column is-gap-4">
        <b-message
          v-if="selected.default"
          type="is-info"
          icon="lock-outline"
          has-icon
          class="mb-0"
          data-testid="default-strategy-notice"
        >
          <strong>Estratégia padrão.</strong> Não pode ser renomeada nem excluída, e Não definida
          não tem meta. Todo investimento novo entra aqui quando nenhuma outra estratégia é
          escolhida, e é para cá que voltam os investimentos de uma classe ou estratégia excluída.
        </b-message>

        <AllocationSummary
          v-if="showAllocation && allocation"
          :allocation="allocation"
          @distribute="openDistribute(strategiesStore.defaultStrategy?.classes[0]?.id)"
        />

        <Card class="table-card mb-0">
          <b-tabs v-model="activeTab" class="wd-tabs" :animated="false">
            <b-tab-item
              :disabled="selected.default"
              :header-attrs="{ title: selected.default ? 'Sem metas para comparar' : undefined }"
            >
              <template #header><span>Alocação</span></template>
              <ClassAllocationTable
                v-if="allocation"
                v-model:expanded-class-id="expandedAllocationClassId"
                :allocation="allocation"
              />
              <b-loading v-else :is-full-page="false" :model-value="!selected.default" />
            </b-tab-item>
            <b-tab-item>
              <template #header>
                <span>Classes e metas</span>
                <span class="tag is-rounded ml-2">{{ selected.classes.length }}</span>
              </template>
              <DefaultStrategyPanel
                v-if="selected.default"
                :strategy="selected"
                @distribute="openDistribute"
              />
              <StrategyClassesEditor
                v-else
                v-model:expanded-class-id="expandedClassId"
                :strategy="selected"
                @delete="confirmDelete(selected)"
              />
            </b-tab-item>
          </b-tabs>
        </Card>

        <ClassAllocationDetail
          v-if="showAllocation && expandedAllocationClass && allocation"
          :strategy-class="expandedAllocationClass"
          :currency="allocation.displayCurrency"
        />
        <ClassHoldingTargets
          v-if="activeTab === CLASSES_TAB && expandedClass && !selected.default"
          :strategy-class="expandedClass"
        />
      </div>
    </div>

    <CreateStrategyModal v-if="creating" @created="onCreated" @close="creating = false" />
    <DistributeHoldingsModal
      v-if="distributeFromClassId"
      :key="distributeKey"
      :initial-class-id="distributeFromClassId"
      @close="distributeFromClassId = null"
    />
  </div>
</template>
