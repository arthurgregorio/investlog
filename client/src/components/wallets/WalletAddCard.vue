<script setup lang="ts">
const emit = defineEmits<{ create: [] }>()
</script>

<template>
  <button type="button" class="wallet-add" @click="emit('create')">
    <b-icon icon="plus-circle-outline" size="is-medium" /><span>Nova carteira</span>
  </button>
</template>

<style scoped>
.wallet-add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 180px;
  border: 1.5px dashed var(--border);
  border-radius: var(--radius);
  background: var(--surface-2);
  color: var(--text-2);
  font-size: 13.5px;
  font-weight: 600;
  transition: 0.14s;
}

.wallet-add:hover {
  border-color: var(--primary);
  color: var(--primary-d);
  background: var(--primary-soft);
}
</style>
