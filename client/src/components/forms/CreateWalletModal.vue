<script setup lang="ts">
import { computed, ref } from 'vue'
import { ToastProgrammatic as Toast } from 'buefy'
import AppModal from '@/components/ui/AppModal.vue'
import AppIcon from '@/components/AppIcon.vue'
import { usePortfolioStore } from '@/stores/portfolio'
import type { IconName } from '@/components/AppIcon.vue'
import type { WalletKind } from '@/types'

const props = defineProps<{ initialType?: WalletKind }>()
const emit = defineEmits<{ close: []; created: [id: string, type: WalletKind] }>()

const store = usePortfolioStore()

const name = ref('')
const type = ref<WalletKind>(props.initialType ?? 'stocks')
const currency = ref(store.base)
const valid = computed(() => name.value.trim().length > 0)

const KIND_OPTS: { value: WalletKind; label: string; icon: IconName }[] = [
  { value: 'stocks', label: 'Ações', icon: 'trendUp' },
  { value: 'crypto', label: 'Cripto', icon: 'coins' },
  { value: 'funds', label: 'Fundos', icon: 'building' },
]

function submit() {
  if (!valid.value) return
  const id = store.addWallet({ name: name.value, type: type.value, currency: currency.value })
  emit('created', id, type.value)
  emit('close')
  Toast.open({ message: 'Carteira criada!', type: 'is-success' })
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
            v-model="type"
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
          <option v-for="o in store.currencies" :key="o" :value="o">{{ o }}</option>
        </b-select>
      </b-field>
    </div>
    <template #footer>
      <b-button @click="emit('close')">Cancelar</b-button>
      <b-button type="is-primary" icon-left="check" :disabled="!valid" @click="submit">Criar carteira</b-button>
    </template>
  </AppModal>
</template>
