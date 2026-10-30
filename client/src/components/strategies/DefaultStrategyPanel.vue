<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import ClassHoldingIdentity from '@/components/strategies/ClassHoldingIdentity.vue'
import { strategiesApi } from '@/api/strategies'
import type { ClassHolding, Strategy } from '@/types'

const props = defineProps<{ strategy: Strategy }>()
const emit = defineEmits<{ distribute: [classId: string] }>()

const holdings = ref<ClassHolding[]>([])
const loading = ref(false)

const undefinedClass = computed(() => props.strategy.classes[0])

async function load() {
  if (!undefinedClass.value) return
  loading.value = true
  try {
    holdings.value = await strategiesApi.findClassHoldings(undefinedClass.value.id)
  } finally {
    loading.value = false
  }
}

watch(() => props.strategy, load, { immediate: true })
</script>

<template>
  <div>
    <div class="card-body is-flex is-flex-wrap-wrap is-align-items-flex-end is-gap-3">
      <b-field label="Nome da estratégia" class="mb-0">
        <b-input :model-value="strategy.name" disabled />
      </b-field>
    </div>
    <div
      v-if="undefinedClass"
      class="card-body is-flex is-flex-wrap-wrap is-align-items-center is-justify-content-space-between is-gap-3"
    >
      <div class="is-flex is-align-items-center is-gap-2">
        <span class="wref-dot has-background-grey-light" />
        <div>
          <div class="chart-title">{{ undefinedClass.name }}</div>
          <div class="sub-caption">Sem meta · {{ undefinedClass.holdingCount }} investimentos</div>
        </div>
      </div>
      <b-button
        type="is-primary"
        icon-left="swap-horizontal"
        :disabled="holdings.length === 0"
        data-testid="distribute-holdings"
        @click="emit('distribute', undefinedClass.id)"
      >
        Distribuir investimentos
      </b-button>
    </div>
    <div class="table-wrap is-relative">
      <b-loading :is-full-page="false" :model-value="loading" />
      <p
        v-if="!loading && holdings.length === 0"
        class="card-body has-text-grey"
        data-testid="undefined-empty"
      >
        Todos os investimentos já estão em uma estratégia.
      </p>
      <div v-else class="table-scroll">
        <table class="inv-table">
          <thead>
            <tr>
              <th>Investimento</th>
              <th>Carteira · tipo</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="holding in holdings"
              :key="holding.id"
              class="inv-row"
              :class="{ 'is-frozen': holding.frozen }"
              data-testid="undefined-holding"
            >
              <ClassHoldingIdentity :holding="holding" />
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
