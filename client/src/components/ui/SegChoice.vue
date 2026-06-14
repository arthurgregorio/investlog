<script setup lang="ts" generic="T extends string">
import { computed } from 'vue'
import AppIcon, { type IconName } from '@/components/AppIcon.vue'

type Option = T | { value: T; label: string; icon?: IconName }

const props = defineProps<{ modelValue: T; options: Option[] }>()
const emit = defineEmits<{ 'update:modelValue': [T] }>()

const opts = computed(() =>
  props.options.map((o) => (typeof o === 'string' ? { value: o as T, label: o } : o)),
)
</script>

<template>
  <div class="seg-choice">
    <button
      v-for="opt in opts"
      :key="opt.value"
      type="button"
      class="seg-opt"
      :class="{ active: opt.value === modelValue }"
      @click="emit('update:modelValue', opt.value)"
    >
      <AppIcon v-if="'icon' in opt && opt.icon" :name="opt.icon" :size="17" />
      <span>{{ opt.label }}</span>
    </button>
  </div>
</template>
