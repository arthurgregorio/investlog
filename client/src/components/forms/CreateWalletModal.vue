<script setup lang="ts">
import { computed, ref } from 'vue'
import { useToast } from 'buefy'
import AppModal from '@/components/ui/AppModal.vue'
import { walletsApi } from '@/api/wallets'
import { useWalletsStore } from '@/stores/wallets'
import { useRatesStore } from '@/stores/rates'
import type { WalletKind } from '@/types'

const props = defineProps<{ initialType?: WalletKind }>()
const emit = defineEmits<{ close: []; created: [id: string, kind: WalletKind] }>()

const toast = useToast()
const walletsStore = useWalletsStore()
const ratesStore = useRatesStore()

const name = ref('')
const kind = ref<WalletKind>(props.initialType ?? 'STOCKS')
const currency = ref(ratesStore.baseCurrency || 'BRL')
const submitting = ref(false)
const valid = computed(() => name.value.trim().length > 0)

const KIND_OPTS: { value: WalletKind; label: string; icon: string }[] = [
  { value: 'STOCKS', label: 'Ações', icon: 'trending-up' },
  { value: 'CRYPTO', label: 'Cripto', icon: 'bitcoin' },
  { value: 'FUNDS', label: 'Fundos', icon: 'office-building-outline' },
]

const currencyOptions = computed(() =>
  ratesStore.currencyCodes.length > 0 ? ratesStore.currencyCodes : ['BRL', 'USD', 'EUR'],
)

async function submit() {
  if (!valid.value || submitting.value) return
  submitting.value = true
  try {
    const wallet = await walletsApi.create({
      name: name.value.trim(),
      kind: kind.value,
      currency: currency.value,
    })
    await walletsStore.refresh()
    emit('created', wallet.id, kind.value)
    emit('close')
    toast.open({ message: 'Carteira criada!', type: 'is-success' })
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AppModal
    title="Nova carteira"
    subtitle="Agrupe seus investimentos por objetivo e moeda."
    @close="emit('close')"
  >
    <div class="form-grid">
      <b-field label="Nome da carteira" style="grid-column: 1/-1">
        <b-input v-model="name" placeholder="ex.: Carteira de Dividendos" autofocus />
      </b-field>
      <b-field
        label="Tipo"
        message="Define quais investimentos a carteira aceita."
        style="grid-column: 1/-1"
      >
        <b-field grouped>
          <b-radio-button
            v-for="opt in KIND_OPTS"
            :key="opt.value"
            v-model="kind"
            :native-value="opt.value"
            type="is-primary"
          >
            <b-icon :icon="opt.icon" size="is-small" />
            <span>{{ opt.label }}</span>
          </b-radio-button>
        </b-field>
      </b-field>
      <b-field label="Moeda" message="Convertida para a moeda base na visão consolidada.">
        <b-select v-model="currency">
          <option v-for="code in currencyOptions" :key="code" :value="code">{{ code }}</option>
        </b-select>
      </b-field>
    </div>
    <template #footer>
      <b-button outlined type="is-danger" :disabled="submitting" @click="emit('close')"
        >Cancelar</b-button
      >
      <b-button
        type="is-success"
        class="has-text-light"
        :disabled="!valid"
        :loading="submitting"
        @click="submit"
      >
        Criar carteira
      </b-button>
    </template>
  </AppModal>
</template>
