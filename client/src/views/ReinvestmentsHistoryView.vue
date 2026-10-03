<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Card from '@/components/ui/Card.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ReinvestmentsTable from '@/components/investments/ReinvestmentsTable.vue'
import { useReinvestmentsStore } from '@/stores/reinvestments'

const HISTORY_PAGE_SIZE = 20

const reinvestmentsStore = useReinvestmentsStore()
const router = useRouter()

onMounted(() => {
  reinvestmentsStore.load(0, HISTORY_PAGE_SIZE)
})

function onPageChange(page: number) {
  reinvestmentsStore.load(page - 1, HISTORY_PAGE_SIZE)
}
</script>

<template>
  <div class="page">
    <div class="page-head page-head-row">
      <div>
        <h1 class="page-title">Histórico de reinvestimentos</h1>
        <p class="page-desc">Todo valor que saiu de um investimento e entrou em outro</p>
      </div>
      <div class="head-actions">
        <b-button icon-left="arrow-left" @click="router.push({ name: 'overview-results' })"
          >Resultados</b-button
        >
      </div>
    </div>

    <Card class="table-card">
      <div v-if="reinvestmentsStore.rows.length > 0" class="table-wrap">
        <b-loading :is-full-page="false" :active="reinvestmentsStore.loading" />
        <ReinvestmentsTable :rows="reinvestmentsStore.rows" />
        <div v-if="reinvestmentsStore.totalPages > 1" class="table-foot">
          <b-pagination
            :model-value="reinvestmentsStore.page + 1"
            :total="reinvestmentsStore.totalElements"
            :per-page="reinvestmentsStore.pageSize"
            order="is-right"
            simple
            @change="onPageChange"
          />
        </div>
      </div>
      <div v-else-if="reinvestmentsStore.loaded" class="p-4">
        <EmptyState
          icon="swap-horizontal"
          title="Nenhum reinvestimento"
          text="Reinvestimentos de um investimento em outro aparecem aqui."
        />
      </div>
    </Card>
  </div>
</template>
