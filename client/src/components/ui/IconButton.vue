<script setup lang="ts">
import AppIcon, { type IconName } from '@/components/AppIcon.vue'

withDefaults(defineProps<{ icon: IconName; label?: string; size?: number }>(), { size: 18 })

defineEmits<{ click: [MouseEvent] }>()
</script>

<template>
  <button type="button" class="icon-btn" :aria-label="label" :title="label" @click="$emit('click', $event)">
    <AppIcon :name="icon" :size="size" />
  </button>
</template>
