<script
  setup
  lang="ts"
  generic="Value extends string, Option extends { value: Value; label: string; testId?: string }"
>
defineProps<{
  options: Option[]
  selectionAttribute: 'aria-pressed' | 'aria-selected'
  optionRole?: string
}>()

const modelValue = defineModel<Value>({ required: true })
</script>

<template>
  <div class="segmented">
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      class="segmented-option"
      :role="optionRole"
      :data-testid="option.testId"
      :[selectionAttribute]="modelValue === option.value"
      :class="{ 'is-active': modelValue === option.value }"
      @click="modelValue = option.value"
    >
      <slot name="option" :option="option">{{ option.label }}</slot>
    </button>
  </div>
</template>

<style scoped>
.segmented {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: 8px;
}

.segmented-option {
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

.segmented-option:hover {
  color: var(--text);
}

.segmented-option.is-active {
  background: var(--primary-soft);
  color: color-mix(in srgb, var(--primary) 65%, var(--text));
  font-weight: 600;
}
</style>
