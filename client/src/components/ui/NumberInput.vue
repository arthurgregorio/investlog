<script setup lang="ts">
defineOptions({ inheritAttrs: false })

withDefaults(
  defineProps<{
    modelValue: number | ''
    placeholder?: string
    prefix?: string
    step?: string
    min?: string
  }>(),
  { step: 'any' },
)
const emit = defineEmits<{ 'update:modelValue': [number | ''] }>()

function onInput(e: Event) {
  const v = (e.target as HTMLInputElement).value
  emit('update:modelValue', v === '' ? '' : Number(v))
}
</script>

<template>
  <div v-if="prefix" class="inp-wrap">
    <span class="inp-prefix">{{ prefix }}</span>
    <input
      class="inp"
      type="number"
      inputmode="decimal"
      :step="step"
      :min="min"
      :value="modelValue"
      :placeholder="placeholder"
      v-bind="$attrs"
      @input="onInput"
    />
  </div>
  <input
    v-else
    class="inp"
    type="number"
    inputmode="decimal"
    :step="step"
    :min="min"
    :value="modelValue"
    :placeholder="placeholder"
    v-bind="$attrs"
    @input="onInput"
  />
</template>
