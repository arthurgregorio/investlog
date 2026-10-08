<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  page: number
  pageSize: number
  totalElements: number
}>()
const emit = defineEmits<{ 'page-change': [page: number] }>()

const hasSeveralPages = computed(() => props.totalElements > props.pageSize)
</script>

<template>
  <div v-if="hasSeveralPages" class="table-foot">
    <b-pagination
      :model-value="page + 1"
      :total="totalElements"
      :per-page="pageSize"
      order="is-right"
      simple
      @change="(pageNumber: number) => emit('page-change', pageNumber - 1)"
    />
  </div>
</template>

<style scoped>
.table-foot {
  padding: 12px 16px;
  border-top: 1px solid var(--border);
}
</style>
