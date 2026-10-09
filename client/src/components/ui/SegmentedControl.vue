<script setup lang="ts" generic="Value extends string">
import SegmentedBase from './SegmentedBase.vue'

interface SegmentedOption {
  value: Value
  label: string
  testId?: string
}

defineProps<{ options: SegmentedOption[]; groupLabel: string }>()

const modelValue = defineModel<Value>({ required: true })
</script>

<template>
  <SegmentedBase
    v-model="modelValue"
    :options="options"
    selection-attribute="aria-pressed"
    role="group"
    :aria-label="groupLabel"
  />
</template>
