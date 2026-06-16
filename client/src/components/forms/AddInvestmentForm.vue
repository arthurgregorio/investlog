<script setup lang="ts">
import { computed } from 'vue'
import NumberInput from '@/components/ui/NumberInput.vue'
import DateInput from '@/components/ui/DateInput.vue'
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
    <b-field label="Tipo de investimento" style="grid-column: 1/-1">
      <b-field grouped>
        <b-radio-button
          v-for="opt in KIND_OPTS"
          :key="opt.value"
          v-model="form.kind"
          :native-value="opt.value"
          type="is-primary"
        >
          <AppIcon :name="opt.icon" :size="17" />
          <span>{{ opt.label }}</span>
        </b-radio-button>
      </b-field>
    </b-field>

    <div v-if="form.walletsOfKind.length === 0" class="form-notice">
      <AppIcon name="info" :size="18" />
      <span>Nenhuma carteira de <b>{{ kindLabelPt }}</b> ainda.</span>
      <b-button size="is-small" type="is-primary" icon-left="plus" @click="emit('create-wallet', form.kind)">
        Criar carteira
      </b-button>
    </div>

    <div v-else class="form-grid">
      <b-field label="Carteira" style="grid-column: 1/-1">
        <b-select v-model="form.walletId">
          <option v-for="o in walletOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
        </b-select>
      </b-field>

      <b-field v-if="form.kind === 'stocks'" label="Tipo">
        <b-select v-model="form.stockType">
          <option v-for="o in store.stockTypes" :key="o" :value="o">{{ o }}</option>
        </b-select>
      </b-field>

      <template v-if="form.kind !== 'funds'">
        <b-field :label="form.kind === 'crypto' ? 'Sigla / código' : 'Ticker'">
          <b-input v-model="form.ticker" :placeholder="form.kind === 'crypto' ? 'BTC' : 'PETR4'" />
        </b-field>
        <b-field label="Nome (opcional)" :style="form.kind === 'crypto' ? 'grid-column: 1/-1' : undefined">
          <b-input v-model="form.name" :placeholder="form.kind === 'crypto' ? 'Bitcoin' : 'Petrobras'" />
        </b-field>
        <b-field label="Data da aquisição">
          <DateInput v-model="form.date" />
        </b-field>
        <b-field label="Quantidade">
          <NumberInput v-model="form.qty" placeholder="0" min="0" />
        </b-field>
        <b-field label="Preço na aquisição">
          <NumberInput v-model="form.price" placeholder="0,00" :prefix="sym" min="0" />
        </b-field>
        <b-field label="Preço atual (opcional)" message="Preencha para acompanhar lucro/prejuízo.">
          <NumberInput v-model="form.currentPrice" placeholder="0,00" :prefix="sym" min="0" />
        </b-field>
      </template>

      <template v-else>
        <b-field label="Tipo de fundo">
          <b-select v-model="form.fundType">
            <option v-for="o in store.fundTypes" :key="o" :value="o">{{ o }}</option>
          </b-select>
        </b-field>
        <b-field label="Nome do fundo">
          <b-input v-model="form.name" placeholder="ex.: Tesouro Selic 2029" />
        </b-field>
        <b-field label="Data do aporte">
          <DateInput v-model="form.date" />
        </b-field>
        <b-field label="Valor aportado">
          <NumberInput v-model="form.amount" placeholder="0,00" :prefix="sym" min="0" />
        </b-field>
        <b-field label="Valor atual (opcional)" message="Saldo atual do fundo, para calcular o rendimento." style="grid-column: 1/-1">
          <NumberInput v-model="form.currentValue" placeholder="0,00" :prefix="sym" min="0" />
        </b-field>
      </template>
    </div>
  </div>
</template>
