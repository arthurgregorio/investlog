<script setup lang="ts">
defineProps<{ title: string; description?: string }>()
</script>

<template>
  <div class="page-head page-head-row">
    <div>
      <h1 class="page-title">{{ title }}</h1>
      <p v-if="description" class="page-desc">{{ description }}</p>
    </div>
    <div v-if="$slots.default" class="head-actions">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.page-head-row {
  flex-flow: row wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.page-desc {
  font-size: 13.5px;
  color: var(--text-2);
  max-width: 560px;
  margin: 2px 0 0;
  line-height: 1.5;
}

.head-actions {
  display: flex;
  gap: 8px;
}
</style>
