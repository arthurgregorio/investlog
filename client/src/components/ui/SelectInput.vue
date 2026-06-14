<script setup lang="ts" generic="T extends string">
import { computed } from 'vue'
import AppIcon from '@/components/AppIcon.vue'

type Option = T | { value: T; label: string }

const props = defineProps<{ modelValue: T; options: Option[] }>()
const emit = defineEmits<{ 'update:modelValue': [T] }>()

const opts = computed(() =>
  props.options.map((o) => (typeof o === 'string' ? { value: o as T, label: o } : o)),
)
</script>

<template>
  <div class="sel-wrap">
    <select
      class="inp sel"
      :value="modelValue"
      @change="emit('update:modelValue', ($event.target as HTMLSelectElement).value as T)"
    >
      <option v-for="o in opts" :key="o.value" :value="o.value">{{ o.label }}</option>
    </select>
    <span class="sel-chev"><AppIcon name="chevronDown" :size="16" /></span>
  </div>
</template>
