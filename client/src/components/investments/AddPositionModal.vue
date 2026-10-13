<script setup lang="ts">
import { computed, ref } from 'vue'
import { useToast } from 'buefy'
import AppModal from '@/components/ui/AppModal.vue'
import NumberInput from '@/components/ui/NumberInput.vue'
import DateInput from '@/components/ui/DateInput.vue'
import { holdingsApi } from '@/api/holdings'
import { fmt } from '@/composables/useFormat'
import { problemDetailMessage } from '@/utils/apiErrors'
import type { WalletKind } from '@/types'

export interface PositionEntry {
  id: string
  date: string
  quantity: number | null
  price: number | null
  amount: number
}

const props = defineProps<{
  holdingId: string
  walletId: string
  kind: WalletKind
  walletCurrency: string
  entry?: PositionEntry
  holdingLabel?: string
}>()

const emit = defineEmits<{ added: []; updated: []; close: [] }>()

const toast = useToast()
const isFund = computed(() => props.kind === 'FUNDS')
const isEdit = computed(() => props.entry != null)
const sym = computed(() => fmt.sym(props.walletCurrency))

const date = ref<Date | null>(props.entry ? parseDate(props.entry.date) : new Date())
const quantity = ref<number | ''>(props.entry?.quantity ?? '')
const price = ref<number | ''>(props.entry?.price ?? '')
const amount = ref<number | ''>(props.entry && isFund.value ? props.entry.amount : '')
const submitting = ref(false)
const error = ref<string | null>(null)

const valid = computed(() =>
  isFund.value
    ? Number(amount.value) > 0 && !!date.value
    : Number(quantity.value) > 0 && Number(price.value) > 0 && !!date.value,
)

const total = computed(() =>
  isFund.value ? Number(amount.value) : Number(quantity.value) * Number(price.value),
)

const changed = computed(() => {
  const entry = props.entry
  if (!entry || !date.value) return false
  if (isoDate(date.value) !== entry.date) return true
  if (isFund.value) return Number(amount.value) !== entry.amount
  return Number(quantity.value) !== entry.quantity || Number(price.value) !== entry.price
})

const title = computed(() => {
  if (isEdit.value) return isFund.value ? 'Editar aporte' : 'Editar compra'
  return isFund.value ? 'Registrar aporte' : 'Registrar compra'
})

const subtitle = computed(() => {
  if (props.entry) {
    const registeredOn = `registrada em ${fmt.date(props.entry.date)}`
    return props.holdingLabel ? `${props.holdingLabel} · ${registeredOn}` : registeredOn
  }
  return isFund.value
    ? 'Registre um novo aporte neste fundo.'
    : 'Registre uma nova compra para este ativo.'
})

const submitLabel = computed(() => {
  if (isEdit.value) return 'Salvar alterações'
  return isFund.value ? 'Registrar aporte' : 'Registrar compra'
})

const canSubmit = computed(() => valid.value && (!isEdit.value || changed.value))

function totalOf(entry: PositionEntry): number {
  return isFund.value ? entry.amount : entry.quantity! * entry.price!
}

function parseDate(iso: string): Date {
  return new Date(iso + 'T00:00:00')
}

function isoDate(value: Date): string {
  return value.toISOString().slice(0, 10)
}

async function save(dateStr: string) {
  if (props.entry) {
    if (isFund.value) {
      await holdingsApi.updateFundContribution(props.walletId, props.holdingId, props.entry.id, {
        contributionDate: dateStr,
        amount: Number(amount.value),
      })
      toast.open({ message: 'Aporte atualizado!', type: 'is-success' })
    } else {
      const update =
        props.kind === 'STOCKS' ? holdingsApi.updateStockLot : holdingsApi.updateCryptoLot
      await update(props.walletId, props.holdingId, props.entry.id, {
        lotDate: dateStr,
        quantity: Number(quantity.value),
        price: Number(price.value),
      })
      toast.open({ message: 'Compra atualizada!', type: 'is-success' })
    }
    emit('updated')
    return
  }
  if (isFund.value) {
    await holdingsApi.addContribution(props.walletId, props.holdingId, {
      contributionDate: dateStr,
      amount: Number(amount.value),
    })
    toast.open({ message: 'Aporte registrado!', type: 'is-success' })
  } else if (props.kind === 'STOCKS') {
    await holdingsApi.addStockLot(props.walletId, props.holdingId, {
      lotDate: dateStr,
      quantity: Number(quantity.value),
      price: Number(price.value),
    })
    toast.open({ message: 'Compra registrada!', type: 'is-success' })
  } else {
    await holdingsApi.addCryptoLot(props.walletId, props.holdingId, {
      lotDate: dateStr,
      quantity: Number(quantity.value),
      price: Number(price.value),
    })
    toast.open({ message: 'Compra registrada!', type: 'is-success' })
  }
  emit('added')
}

async function submit() {
  if (!canSubmit.value) return
  submitting.value = true
  error.value = null
  try {
    await save(isoDate(date.value!))
    emit('close')
  } catch (caughtError) {
    error.value = problemDetailMessage(caughtError) ?? null
    if (!error.value) throw caughtError
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AppModal :title="title" :subtitle="subtitle" @close="emit('close')">
    <div class="fixed-grid has-2-cols">
      <div class="grid is-gap-2">
        <b-field label="Data" class="cell is-col-span-2">
          <DateInput v-model="date" />
        </b-field>
        <b-field v-if="isFund" label="Valor aportado" class="cell is-col-span-2">
          <NumberInput v-model="amount" :prefix="sym" placeholder="0,00" />
        </b-field>
        <template v-else>
          <b-field label="Quantidade" class="cell is-col-span-2-mobile">
            <NumberInput v-model="quantity" placeholder="0" />
          </b-field>
          <b-field label="Preço" class="cell is-col-span-2-mobile">
            <NumberInput v-model="price" :prefix="sym" placeholder="0,00" />
          </b-field>
        </template>
      </div>
    </div>
    <div v-if="isEdit" class="position-total" data-testid="position-total">
      <div class="is-flex is-justify-content-space-between is-align-items-center">
        <span class="has-text-grey has-text-weight-semibold is-size-7">{{
          isFund ? 'Total aportado' : 'Total da compra'
        }}</span>
        <span class="has-text-weight-bold">{{ fmt.money(total, walletCurrency) }}</span>
      </div>
      <p v-if="changed" class="has-text-grey is-size-7 mt-1" data-testid="position-impact">
        Antes: {{ fmt.money(totalOf(entry!), walletCurrency) }}.
        {{
          isFund
            ? 'O valor investido e o resultado serão recalculados.'
            : 'Preço médio e resultado serão recalculados.'
        }}
      </p>
    </div>
    <b-message
      v-if="error"
      type="is-danger"
      size="is-small"
      class="mt-3"
      data-testid="position-error"
    >
      {{ error }}
    </b-message>
    <template #footer>
      <b-button outlined type="is-danger" :disabled="submitting" @click="emit('close')"
        >Cancelar</b-button
      >
      <b-button
        type="is-success"
        icon-left="check"
        :disabled="!canSubmit"
        :loading="submitting"
        @click="submit"
      >
        {{ submitLabel }}
      </b-button>
    </template>
  </AppModal>
</template>

<style scoped>
.position-total {
  margin-top: 16px;
  padding: 12px 14px;
  border: 1px solid var(--border-2);
  border-radius: 8px;
  background: var(--surface-2);
}
</style>
