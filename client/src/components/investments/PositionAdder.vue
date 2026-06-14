<script setup lang="ts">
import { computed, ref } from 'vue'
import NumberInput from '@/components/ui/NumberInput.vue'
import DateInput from '@/components/ui/DateInput.vue'
import AppButton from '@/components/ui/AppButton.vue'
import { usePortfolioStore } from '@/stores/portfolio'
import type { Holding } from '@/types'

const props = defineProps<{ holding: Holding }>()
const emit = defineEmits<{ close: [] }>()

const store = usePortfolioStore()
const isFund = computed(() => props.holding.kind === 'fund')
const cur = computed(() => store.walletOf(props.holding.walletId)?.currency ?? store.base)
const sym = computed(() => store.currencySymbol[cur.value])

const date = ref(new Date().toISOString().slice(0, 10))
const qty = ref<number | ''>('')
const price = ref<number | ''>('')
const amount = ref<number | ''>('')

const valid = computed(() =>
  isFund.value
    ? Number(amount.value) > 0 && !!date.value
    : Number(qty.value) > 0 && Number(price.value) > 0 && !!date.value,
)

function submit() {
  if (!valid.value) return
  if (isFund.value) {
    store.addContribution(props.holding.id, { date: date.value, amount: Number(amount.value) })
  } else {
    store.addLot(props.holding.id, { date: date.value, qty: Number(qty.value), price: Number(price.value) })
  }
  emit('close')
}
</script>

<template>
  <div class="adder">
    <div class="adder-fields">
      <label class="ad-f"><span>Data</span><DateInput v-model="date" /></label>
      <label v-if="isFund" class="ad-f">
        <span>Valor aportado</span>
        <NumberInput v-model="amount" :prefix="sym" placeholder="0,00" />
      </label>
      <template v-else>
        <label class="ad-f"><span>Quantidade</span><NumberInput v-model="qty" placeholder="0" /></label>
        <label class="ad-f"><span>Preço</span><NumberInput v-model="price" :prefix="sym" placeholder="0,00" /></label>
      </template>
    </div>
    <div class="adder-actions">
      <AppButton size="sm" variant="ghost" @click="emit('close')">Cancelar</AppButton>
      <AppButton size="sm" variant="primary" icon="check" :disabled="!valid" @click="submit">
        {{ isFund ? 'Adicionar aporte' : 'Adicionar compra' }}
      </AppButton>
    </div>
  </div>
</template>
