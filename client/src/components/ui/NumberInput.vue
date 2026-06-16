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
  <b-field grouped v-if="prefix">
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
  </b-field>
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
