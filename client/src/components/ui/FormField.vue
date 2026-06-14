<script setup lang="ts">
defineProps<{ label: string; hint?: string; span?: boolean }>()
</script>

<template>
  <label class="field" :style="span ? { gridColumn: '1 / -1' } : undefined">
    <span class="field-label">{{ label }}</span>
    <slot />
    <span v-if="hint" class="field-hint">{{ hint }}</span>
  </label>
</template>
