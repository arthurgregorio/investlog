<script setup lang="ts">
import { ref } from 'vue'
import NumberInput from '@/components/ui/NumberInput.vue'
import { fmt } from '@/composables/useFormat'
import type { CurrencyRate } from '@/types'

defineProps<{ rate: CurrencyRate; baseCurrency: string }>()

const emit = defineEmits<{ commit: [rate: number] }>()

const draft = ref<number | ''>()

function commit() {
  const value = draft.value
  draft.value = undefined
  if (value === undefined || value === '' || value <= 0) return
  emit('commit', value)
}
</script>

<template>
  <div class="rate-row">
    <div class="is-flex is-align-items-center is-gap-1">
      <span class="cur-chip lg">{{ rate.currencyCode }}</span>
      <span class="rate-symbol">{{ fmt.sym(rate.currencyCode) }}</span>
    </div>
    <span v-if="rate.isBase" class="rate-base">Moeda base · 1,00</span>
    <label v-else class="rate-input">
      <span>1 {{ rate.currencyCode }} =</span>
      <NumberInput
        :model-value="draft ?? rate.rate"
        :prefix="fmt.sym(baseCurrency)"
        @update:model-value="draft = $event"
        @blur="commit"
      />
    </label>
  </div>
</template>

<style scoped>
.rate-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 14px;
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: 9px;
  min-height: 52px;
}

.rate-row + .rate-row {
  margin-top: 10px;
}

.cur-chip.lg {
  font-size: 13px;
  padding: 4px 10px;
  color: var(--text);
}

.rate-symbol,
.rate-base {
  font-size: 13px;
  color: var(--text-muted);
}

.rate-base {
  font-weight: 500;
}

.rate-input {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 13px;
  color: var(--text-2);
  font-weight: 500;
}

.rate-input :deep(.field) {
  width: 150px;
}

/* Buefy renders the addon button and input as inline-flex controls, whose line-box
   strut adds phantom height below them and breaks vertical alignment with the row
   text. Zeroing the control font-size collapses the strut; the button and input keep
   their own font-size for content. */
.rate-input :deep(.field .control) {
  font-size: 0;
}
</style>
