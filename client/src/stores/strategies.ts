import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  strategiesApi,
  type HoldingReferencePayload,
  type HoldingTargetPayload,
  type StrategyClassPayload,
} from '@/api/strategies'
import type { ClassHolding, Strategy } from '@/types'

export const useStrategiesStore = defineStore('strategies', () => {
  const strategies = ref<Strategy[]>([])
  const loaded = ref(false)
  const loading = ref(false)

  const defaultStrategy = computed(() => strategies.value.find((strategy) => strategy.default))

  const strategyById = computed(
    () => (id: string) => strategies.value.find((strategy) => strategy.id === id),
  )

  async function load() {
    if (loaded.value) return
    loading.value = true
    try {
      strategies.value = await strategiesApi.findAll()
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  async function refresh() {
    loaded.value = false
    await load()
  }

  function replace(updated: Strategy) {
    strategies.value = strategies.value.map((strategy) =>
      strategy.id === updated.id ? updated : strategy,
    )
  }

  async function create(name: string): Promise<Strategy> {
    const created = await strategiesApi.create(name)
    const defaults = strategies.value.filter((strategy) => strategy.default)
    const others = strategies.value.filter((strategy) => !strategy.default)
    strategies.value = [...others, created, ...defaults]
    return created
  }

  async function rename(id: string, name: string) {
    replace(await strategiesApi.rename(id, name))
  }

  async function remove(id: string) {
    await strategiesApi.remove(id)
    await refresh()
  }

  async function saveClasses(id: string, classes: StrategyClassPayload[]) {
    replace(await strategiesApi.replaceClasses(id, classes))
  }

  async function assignHoldings(classId: string, holdings: HoldingReferencePayload[]) {
    await strategiesApi.assignHoldings(classId, holdings)
    await refresh()
  }

  async function saveTargets(
    classId: string,
    targets: HoldingTargetPayload[],
  ): Promise<ClassHolding[]> {
    const holdings = await strategiesApi.updateTargets(classId, targets)
    await refresh()
    return holdings
  }

  function reset() {
    strategies.value = []
    loaded.value = false
    loading.value = false
  }

  return {
    strategies,
    loaded,
    loading,
    defaultStrategy,
    strategyById,
    load,
    refresh,
    create,
    rename,
    remove,
    saveClasses,
    assignHoldings,
    saveTargets,
    reset,
  }
})
