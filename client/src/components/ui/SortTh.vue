<script setup lang="ts">
import { computed } from 'vue'

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
      <b-icon
        :icon="isActive && direction === 'asc' ? 'chevron-up' : 'chevron-down'"
        size="is-small"
        :class="isActive ? 'sort-icon-active' : 'sort-icon-idle'"
      />
    </span>
  </th>
</template>
