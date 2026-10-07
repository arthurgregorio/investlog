<script setup lang="ts" generic="Value extends string">
import SegmentedBase from './SegmentedBase.vue'

interface SegmentedTab {
  value: Value
  label: string
  icon?: string
  testId?: string
}

defineProps<{ options: SegmentedTab[]; listLabel: string }>()

const modelValue = defineModel<Value>({ required: true })
</script>

<template>
  <SegmentedBase
    v-model="modelValue"
    class="segmented-tabs"
    :options="options"
    selection-attribute="aria-selected"
    option-role="tab"
    role="tablist"
    :aria-label="listLabel"
  >
    <template #option="{ option }">
      <b-icon v-if="option.icon" :icon="option.icon" size="is-small" />{{ option.label }}
    </template>
  </SegmentedBase>
</template>

<style scoped>
.segmented-tabs {
  flex-shrink: 0;
}

.segmented-tabs :deep(.segmented-option) {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 32px;
  font-size: 13px;
  font-weight: 600;
}
</style>
