<script setup lang="ts">
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
    @update:model-value="emit('update:modelValue', $event)"
  />
</template>
