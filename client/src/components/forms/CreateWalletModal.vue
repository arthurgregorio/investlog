<script setup lang="ts">
import { computed, ref } from 'vue'
import AppModal from '@/components/ui/AppModal.vue'
import AppButton from '@/components/ui/AppButton.vue'
import FormField from '@/components/ui/FormField.vue'
import TextInput from '@/components/ui/TextInput.vue'
import SelectInput from '@/components/ui/SelectInput.vue'
import SegChoice from '@/components/ui/SegChoice.vue'
import type { IconName } from '@/components/AppIcon.vue'
import { usePortfolioStore } from '@/stores/portfolio'
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
}
</script>

<template>
  <AppModal
    title="Nova carteira"
    subtitle="Agrupe seus investimentos por objetivo e moeda."
    @close="emit('close')"
  >
    <div class="form-grid">
      <FormField label="Nome da carteira" span>
        <TextInput v-model="name" placeholder="ex.: Carteira de Dividendos" autofocus />
      </FormField>
      <FormField label="Tipo" span hint="Define quais investimentos a carteira aceita.">
        <SegChoice v-model="type" :options="KIND_OPTS" />
      </FormField>
      <FormField label="Moeda" hint="Convertida para a moeda base na visão consolidada.">
        <SelectInput v-model="currency" :options="store.currencies" />
      </FormField>
    </div>
    <template #footer>
      <AppButton variant="ghost" @click="emit('close')">Cancelar</AppButton>
      <AppButton variant="primary" icon="check" :disabled="!valid" @click="submit">Criar carteira</AppButton>
    </template>
  </AppModal>
</template>
