<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from '@/components/AppIcon.vue'

const props = withDefaults(
  defineProps<{
    sortKey: string
    activeKey: string | null
    direction: 'asc' | 'desc'
    align?: 'left' | 'right'
  }>(),
  { align: 'right' },
)
const emit = defineEmits<{ toggle: [string] }>()

const isActive = computed(() => props.activeKey === props.sortKey)
</script>

<template>
  <th :class="align === 'right' ? 'c-num sort-th' : 'sort-th'" @click="emit('toggle', sortKey)">
    <span class="sort-th-inner">
      <slot />
      <AppIcon
        :name="isActive && direction === 'asc' ? 'chevronUp' : 'chevronDown'"
        :size="13"
        :class="isActive ? 'sort-icon-active' : 'sort-icon-idle'"
      />
    </span>
  </th>
</template>
