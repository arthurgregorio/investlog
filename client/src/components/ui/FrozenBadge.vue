<script setup lang="ts">
defineProps<{ compact?: boolean }>()
</script>

<template>
  <b-icon
    icon="snowflake"
    type="is-info-on-scheme"
    :size="compact ? 'is-small' : undefined"
    :custom-size="compact ? undefined : 'mdi-18px'"
    :class="{ 'ticker-badge': !compact }"
    :style="compact ? undefined : { width: '36px', height: '36px' }"
    role="img"
    title="Congelado"
    aria-label="Congelado"
    data-testid="frozen-tag"
  />
</template>
