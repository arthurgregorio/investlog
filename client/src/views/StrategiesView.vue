<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useToast } from 'buefy'
import Card from '@/components/ui/Card.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import StrategyRail from '@/components/strategies/StrategyRail.vue'
import StrategyClassesEditor from '@/components/strategies/StrategyClassesEditor.vue'
import ClassHoldingTargets from '@/components/strategies/ClassHoldingTargets.vue'
import DefaultStrategyPanel from '@/components/strategies/DefaultStrategyPanel.vue'
import CreateStrategyModal from '@/components/strategies/CreateStrategyModal.vue'
import DistributeHoldingsModal from '@/components/strategies/DistributeHoldingsModal.vue'
import { safeHtml, useConfirmDialog } from '@/composables/useConfirmDialog'
import { useStrategiesStore } from '@/stores/strategies'
import type { Strategy } from '@/types'

const CLASSES_TAB = 1

const toast = useToast()
const { confirm } = useConfirmDialog()
const strategiesStore = useStrategiesStore()

const selectedId = ref<string | null>(null)
const expandedClassId = ref<string | null>(null)
const activeTab = ref(CLASSES_TAB)
const creating = ref(false)
const distributeFromClassId = ref<string | null>(null)
const distributeKey = ref(0)

const selected = computed(() =>
  selectedId.value ? strategiesStore.strategyById(selectedId.value) : undefined,
)
const expandedClass = computed(() =>
  selected.value?.classes.find((strategyClass) => strategyClass.id === expandedClassId.value),
)
const headline = computed(() =>
  selected.value ? `${selected.value.name} · ${selected.value.holdingCount} investimentos` : '',
)

function pickInitial() {
  if (selected.value) return
  selectedId.value =
    strategiesStore.strategies.find((strategy) => !strategy.default)?.id ??
    strategiesStore.strategies[0]?.id ??
    null
}

watch(() => strategiesStore.strategies, pickInitial)

watch(selectedId, () => {
  expandedClassId.value = null
  activeTab.value = CLASSES_TAB
})

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

function openDistribute(classId: string) {
  distributeKey.value += 1
  distributeFromClassId.value = classId
}
</script>

<template>
  <div class="page">
    <PageHeader title="Estratégias" :description="headline">
      <b-button icon-left="plus" data-testid="new-strategy" @click="creating = true">
        Nova estratégia
      </b-button>
      <b-button type="is-primary" icon-left="calculator-variant-outline" disabled>
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

        <Card class="table-card mb-0">
          <b-tabs v-model="activeTab" class="wd-tabs" :animated="false">
            <b-tab-item
              :disabled="selected.default"
              :header-attrs="{ title: selected.default ? 'Sem metas para comparar' : undefined }"
            >
              <template #header><span>Alocação</span></template>
              <p class="card-body has-text-grey" data-testid="allocation-placeholder">
                A alocação desta estratégia aparece aqui.
              </p>
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

        <ClassHoldingTargets
          v-if="expandedClass && !selected.default"
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
