<script setup lang="ts">
export type WalletHighlightRail = 'up' | 'down' | 'largest'

defineProps<{ label: string; rail: WalletHighlightRail }>()
</script>

<template>
  <div class="wd-highlight-cell is-flex is-align-items-center is-gap-3">
    <span class="wd-rail" :class="`wd-rail-${rail}`" />
    <div class="is-flex-grow-1">
      <div class="is-size-7 has-text-weight-semibold has-text-grey">{{ label }}</div>
      <slot>
        <div class="has-text-grey mt-1">—</div>
      </slot>
    </div>
  </div>
</template>

<style scoped>
.wd-highlight-cell {
  padding: 14px 20px;
  background: var(--surface);
}

.wd-rail {
  width: 3px;
  align-self: stretch;
  flex-shrink: 0;
  border-radius: 2px;
}

.wd-rail-up {
  background: var(--bulma-success-on-scheme);
}

.wd-rail-down {
  background: var(--bulma-danger-on-scheme);
}

.wd-rail-largest {
  background: var(--bulma-info-on-scheme);
}
</style>
