<script setup lang="ts">
import { computed } from 'vue'
import FormField from '@/components/ui/FormField.vue'
import TextInput from '@/components/ui/TextInput.vue'
import NumberInput from '@/components/ui/NumberInput.vue'
import DateInput from '@/components/ui/DateInput.vue'
import SelectInput from '@/components/ui/SelectInput.vue'
import SegChoice from '@/components/ui/SegChoice.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppIcon from '@/components/AppIcon.vue'
import { usePortfolioStore } from '@/stores/portfolio'
import type { AddInvestmentForm } from '@/composables/useAddInvestmentForm'
import type { IconName } from '@/components/AppIcon.vue'
import type { WalletKind } from '@/types'

const props = defineProps<{ form: AddInvestmentForm }>()
const emit = defineEmits<{ 'create-wallet': [WalletKind] }>()

const store = usePortfolioStore()

const KIND_OPTS: { value: WalletKind; label: string; icon: IconName }[] = [
  { value: 'stocks', label: 'Ações', icon: 'trendUp' },
  { value: 'crypto', label: 'Cripto', icon: 'coins' },
  { value: 'funds', label: 'Fundos', icon: 'building' },
]

const sym = computed(() => store.currencySymbol[props.form.cur])
const walletOptions = computed(() =>
  props.form.walletsOfKind.map((w) => ({ value: w.id, label: `${w.name} · ${w.currency}` })),
)
const kindLabelPt = computed(() =>
  props.form.kind === 'stocks' ? 'ações' : props.form.kind === 'crypto' ? 'cripto' : 'fundos',
)
</script>

<template>
  <div class="form-stack">
    <FormField label="Tipo de investimento" span>
      <SegChoice v-model="form.kind" :options="KIND_OPTS" />
    </FormField>

    <div v-if="form.walletsOfKind.length === 0" class="form-notice">
      <AppIcon name="info" :size="18" />
      <span>Nenhuma carteira de <b>{{ kindLabelPt }}</b> ainda.</span>
      <AppButton size="sm" variant="primary" icon="plus" @click="emit('create-wallet', form.kind)">
        Criar carteira
      </AppButton>
    </div>

    <div v-else class="form-grid">
      <FormField label="Carteira" span>
        <SelectInput v-model="form.walletId" :options="walletOptions" />
      </FormField>

      <FormField v-if="form.kind === 'stocks'" label="Tipo">
        <SelectInput v-model="form.stockType" :options="store.stockTypes" />
      </FormField>

      <template v-if="form.kind !== 'funds'">
        <FormField :label="form.kind === 'crypto' ? 'Sigla / código' : 'Ticker'">
          <TextInput v-model="form.ticker" :placeholder="form.kind === 'crypto' ? 'BTC' : 'PETR4'" />
        </FormField>
        <FormField label="Nome (opcional)" :span="form.kind === 'crypto'">
          <TextInput v-model="form.name" :placeholder="form.kind === 'crypto' ? 'Bitcoin' : 'Petrobras'" />
        </FormField>
        <FormField label="Data da aquisição">
          <DateInput v-model="form.date" />
        </FormField>
        <FormField label="Quantidade">
          <NumberInput v-model="form.qty" placeholder="0" min="0" />
        </FormField>
        <FormField label="Preço na aquisição">
          <NumberInput v-model="form.price" placeholder="0,00" :prefix="sym" min="0" />
        </FormField>
        <FormField label="Preço atual (opcional)" hint="Preencha para acompanhar lucro/prejuízo.">
          <NumberInput v-model="form.currentPrice" placeholder="0,00" :prefix="sym" min="0" />
        </FormField>
      </template>

      <template v-else>
        <FormField label="Tipo de fundo">
          <SelectInput v-model="form.fundType" :options="store.fundTypes" />
        </FormField>
        <FormField label="Nome do fundo">
          <TextInput v-model="form.name" placeholder="ex.: Tesouro Selic 2029" />
        </FormField>
        <FormField label="Data do aporte">
          <DateInput v-model="form.date" />
        </FormField>
        <FormField label="Valor aportado">
          <NumberInput v-model="form.amount" placeholder="0,00" :prefix="sym" min="0" />
        </FormField>
        <FormField label="Valor atual (opcional)" span hint="Saldo atual do fundo, para calcular o rendimento.">
          <NumberInput v-model="form.currentValue" placeholder="0,00" :prefix="sym" min="0" />
        </FormField>
      </template>
    </div>
  </div>
</template>
