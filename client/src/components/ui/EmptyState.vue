<script setup lang="ts">
import AppIcon, { type IconName } from '@/components/AppIcon.vue'

defineProps<{ icon?: IconName; title: string; text?: string }>()
</script>

<template>
  <div class="empty">
    <div v-if="icon" class="empty-icon"><AppIcon :name="icon" :size="26" /></div>
    <div class="empty-title">{{ title }}</div>
    <div v-if="text" class="empty-text">{{ text }}</div>
    <slot name="action" />
  </div>
</template>
