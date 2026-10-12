<script setup lang="ts">
import { ref, watch } from 'vue'

defineOptions({ inheritAttrs: false })
const props = withDefaults(
  defineProps<{
    modelValue: number | ''
    placeholder?: string
    prefix?: string
    step?: string
    min?: string
  }>(),
  { placeholder: '', prefix: '', step: 'any', min: undefined },
)
const emit = defineEmits<{ 'update:modelValue': [number | ''] }>()

const MONEY_DECIMALS = 2
const PLAIN_DECIMAL = /^-?\d+(\.\d*)?$/

function padMoney(value: string): string {
  if (!props.prefix || !PLAIN_DECIMAL.test(value)) return value
  const [integerPart, decimalPart = ''] = value.split('.')
  return `${integerPart}.${decimalPart.padEnd(MONEY_DECIMALS, '0')}`
}

function displayText(value: number | ''): string {
  return value === '' ? '' : padMoney(String(value))
}

function parse(value: string | number): number | '' {
  return value === '' ? '' : Number(value)
}

// Vue's v-model hands a type="number" input's value over already cast to a number, which drops
// trailing zeros. Holding that number (not a re-stringified copy) keeps v-model from rewriting the
// field, so the text the user typed stays in the input until blur pads it.
const text = ref<string | number>(displayText(props.modelValue))

watch(
  () => props.modelValue,
  (value) => {
    if (value !== parse(text.value)) text.value = displayText(value)
  },
)

function onInput(value: string | number) {
  text.value = value
  emit('update:modelValue', parse(value))
}

function onBlur(event: FocusEvent) {
  text.value = padMoney((event.target as HTMLInputElement).value)
}
</script>
<template>
  <div v-if="prefix" class="field has-addons mb-0">
    <div class="control">
      <span class="button is-static">{{ prefix }}</span>
    </div>
    <b-input
      type="number"
      :model-value="text"
      :placeholder="placeholder"
      :step="step"
      :min="min"
      inputmode="decimal"
      expanded
      v-bind="$attrs"
      @update:model-value="onInput"
      @blur="onBlur"
    />
  </div>
  <b-input
    v-else
    type="number"
    :model-value="text"
    :placeholder="placeholder"
    :step="step"
    :min="min"
    inputmode="decimal"
    v-bind="$attrs"
    @update:model-value="onInput"
  />
</template>
