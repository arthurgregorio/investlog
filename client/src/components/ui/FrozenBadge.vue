<script setup lang="ts">
withDefaults(defineProps<{ compact?: boolean; size?: number }>(), {
  compact: false,
  size: 36,
})
</script>

<template>
  <span
    class="frozen-badge"
    :class="{ 'is-compact': compact }"
    :style="compact ? undefined : { width: `${size}px`, height: `${size}px` }"
    role="img"
    title="Congelado"
    aria-label="Congelado"
    data-testid="frozen-tag"
  >
    <b-icon icon="snowflake" />
  </span>
</template>
