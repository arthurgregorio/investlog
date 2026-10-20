<script setup lang="ts">
import { parseTypedDate } from '@/utils/typedDate'

defineProps<{ modelValue: Date | null }>()
const emit = defineEmits<{ 'update:modelValue': [Date | null] }>()
</script>
<template>
  <b-datepicker
    :model-value="modelValue"
    locale="pt-BR"
    trap-focus
    append-to-body
    editable
    :date-parser="parseTypedDate"
    @update:model-value="emit('update:modelValue', $event)"
  />
</template>
