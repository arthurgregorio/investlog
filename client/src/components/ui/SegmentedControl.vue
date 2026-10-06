<script setup lang="ts" generic="Value extends string">
interface SegmentedOption {
  value: Value
  label: string
  testId?: string
}

defineProps<{ options: SegmentedOption[]; groupLabel: string }>()

const modelValue = defineModel<Value>({ required: true })
</script>

<template>
  <div class="segmented-control" role="group" :aria-label="groupLabel">
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      :data-testid="option.testId"
      :aria-pressed="modelValue === option.value"
      :class="{ 'is-active': modelValue === option.value }"
      @click="modelValue = option.value"
    >
      {{ option.label }}
    </button>
  </div>
</template>

<style scoped>
.segmented-control {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: 8px;
}

button {
  height: 28px;
  padding: 0 14px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-2);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: 0.12s;
}

button:hover {
  color: var(--text);
}

button.is-active {
  background: var(--primary-soft);
  color: color-mix(in srgb, var(--primary) 65%, var(--text));
  font-weight: 600;
}
</style>
