<script setup lang="ts">
defineProps<{ icon?: string; title: string; text?: string }>()
</script>

<template>
  <div class="empty">
    <div v-if="icon" class="empty-icon"><b-icon :icon="icon" size="is-medium" /></div>
    <div class="empty-title">{{ title }}</div>
    <div v-if="text" class="empty-text">{{ text }}</div>
    <slot name="action" />
  </div>
</template>
