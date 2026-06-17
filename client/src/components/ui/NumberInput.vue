<script setup lang="ts">
defineOptions({ inheritAttrs: false })
withDefaults(
  defineProps<{ modelValue: number | ''; placeholder?: string; prefix?: string; step?: string; min?: string }>(),
  { step: 'any' }
)
const emit = defineEmits<{ 'update:modelValue': [number | ''] }>()
function onInput(v: string) {
  emit('update:modelValue', v === '' ? '' : Number(v))
}
</script>
<template>
  <div v-if="prefix" class="field has-addons">
    <p class="control"><span class="button is-static">{{ prefix }}</span></p>
    <b-input
      type="number"
      :model-value="modelValue === '' ? '' : String(modelValue)"
      :placeholder="placeholder"
      :step="step"
      :min="min"
      inputmode="decimal"
      expanded
      v-bind="$attrs"
      @update:model-value="onInput"
    />
  </div>
  <b-input
    v-else
    type="number"
    :model-value="modelValue === '' ? '' : String(modelValue)"
    :placeholder="placeholder"
    :step="step"
    :min="min"
    inputmode="decimal"
    v-bind="$attrs"
    @update:model-value="onInput"
  />
</template>
