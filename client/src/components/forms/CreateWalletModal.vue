<script setup lang="ts">
import { computed, ref } from 'vue'
import { ToastProgrammatic as Toast } from 'buefy'
import AppModal from '@/components/ui/AppModal.vue'
import AppIcon from '@/components/AppIcon.vue'
import { walletsApi } from '@/api/wallets'
import { useWalletsStore } from '@/stores/wallets'
import { useRatesStore } from '@/stores/rates'
import type { IconName } from '@/components/AppIcon.vue'
import type { WalletKind } from '@/types'

const props = defineProps<{ initialType?: WalletKind }>()
const emit = defineEmits<{ close: []; created: [id: string, kind: WalletKind] }>()

const walletsStore = useWalletsStore()
const ratesStore = useRatesStore()

const name = ref('')
const kind = ref<WalletKind>(props.initialType ?? 'STOCKS')
const currency = ref(ratesStore.baseCurrency || 'BRL')
const submitting = ref(false)
const valid = computed(() => name.value.trim().length > 0)

const KIND_OPTS: { value: WalletKind; label: string; icon: IconName }[] = [
  { value: 'STOCKS', label: 'Ações', icon: 'trendUp' },
  { value: 'CRYPTO', label: 'Cripto', icon: 'coins' },
  { value: 'FUNDS', label: 'Fundos', icon: 'building' },
]

const currencyOptions = computed(() =>
  ratesStore.currencyCodes.length > 0 ? ratesStore.currencyCodes : ['BRL', 'USD', 'EUR'],
)

async function submit() {
  if (!valid.value || submitting.value) return
  submitting.value = true
  try {
    const wallet = await walletsApi.create({ name: name.value.trim(), kind: kind.value, currency: currency.value })
    await walletsStore.refresh()
    emit('created', wallet.id, kind.value)
    emit('close')
    Toast.open({ message: 'Carteira criada!', type: 'is-success' })
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
      <b-field label="Tipo" message="Define quais investimentos a carteira aceita." style="grid-column: 1/-1">
        <b-field grouped>
          <b-radio-button
            v-for="opt in KIND_OPTS"
            :key="opt.value"
            v-model="kind"
            :native-value="opt.value"
            type="is-primary"
          >
            <AppIcon :name="opt.icon" :size="17" />
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
      <b-button :disabled="submitting" @click="emit('close')">Cancelar</b-button>
      <b-button type="is-primary" icon-left="check" :disabled="!valid" :loading="submitting" @click="submit">
        Criar carteira
      </b-button>
    </template>
  </AppModal>
</template>
