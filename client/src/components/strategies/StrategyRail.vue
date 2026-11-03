<script setup lang="ts">
import { chartColor } from '@/utils/allocation'
import type { Strategy } from '@/types'

defineProps<{ strategies: Strategy[]; selectedId: string | null }>()
const emit = defineEmits<{ select: [strategyId: string] }>()

function dotColor(strategy: Strategy, index: number): string {
  return strategy.default ? 'var(--text-muted)' : chartColor(index)
}
</script>

<template>
  <aside class="card filter-rail">
    <div class="is-flex is-justify-content-space-between is-align-items-center px-4 py-3">
      <h2 class="is-flex is-align-items-center is-gap-1 has-text-weight-bold">Estratégias</h2>
    </div>
    <fieldset class="rail-group">
      <legend class="is-sr-only">Estratégia</legend>
      <p class="rail-title" aria-hidden="true">Estratégia</p>
      <label
        v-for="(strategy, index) in strategies"
        :key="strategy.id"
        class="rail-option"
        :class="{ 'is-selected': selectedId === strategy.id }"
      >
        <input
          type="radio"
          name="strategy"
          :value="strategy.id"
          :checked="selectedId === strategy.id"
          :data-testid="`strategy-${strategy.id}`"
          @change="emit('select', strategy.id)"
        />
        <span class="rail-dot" :style="{ background: dotColor(strategy, index) }" />
        <span class="rail-label">{{ strategy.name }}</span>
        <span class="rail-side">{{ strategy.holdingCount }}</span>
      </label>
    </fieldset>
  </aside>
</template>
