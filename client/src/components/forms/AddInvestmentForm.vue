<script setup lang="ts">
import { computed } from 'vue'
import NumberInput from '@/components/ui/NumberInput.vue'
import DateInput from '@/components/ui/DateInput.vue'
import FieldLabel from '@/components/ui/FieldLabel.vue'
import { useTypesListStore } from '@/stores/typesList'
import { useStrategiesStore } from '@/stores/strategies'
import { fmt } from '@/composables/useFormat'
import type { AddInvestmentForm } from '@/composables/useAddInvestmentForm'
import type { WalletKind } from '@/types'

const props = defineProps<{ form: AddInvestmentForm }>()
const emit = defineEmits<{ 'create-wallet': [WalletKind] }>()

const typesListStore = useTypesListStore()
const strategiesStore = useStrategiesStore()

const KIND_OPTS: { value: WalletKind; label: string; icon: string }[] = [
  { value: 'STOCKS', label: 'Ações', icon: 'trending-up' },
  { value: 'CRYPTO', label: 'Cripto', icon: 'bitcoin' },
  { value: 'FUNDS', label: 'Fundos', icon: 'office-building-outline' },
]

const THIRD = 'cell is-col-span-2 is-col-span-6-mobile'
const HALF = 'cell is-col-span-3 is-col-span-6-mobile'

const walletCurrency = computed(() => {
  const wallet = props.form.walletsOfKind.find((wallet) => wallet.id === props.form.walletId)
  return wallet?.currency ?? 'BRL'
})
const sym = computed(() => fmt.sym(walletCurrency.value))

const ticker = computed({
  get: () => props.form.ticker,
  set: (value: string) => {
    props.form.ticker = value.toUpperCase().replace(/[^A-Z0-9]/g, '')
  },
})

const walletOptions = computed(() =>
  props.form.walletsOfKind.map((wallet) => ({
    value: wallet.id,
    label: `${wallet.name} · ${wallet.currency}`,
  })),
)

const kindTypes = computed(() => typesListStore.typesForKind(props.form.kind))

const strategyClasses = computed(
  () => strategiesStore.strategyById(props.form.strategyId)?.classes ?? [],
)

const kindLabelPt = computed(() =>
  props.form.kind === 'STOCKS' ? 'ações' : props.form.kind === 'CRYPTO' ? 'cripto' : 'fundos',
)
</script>

<template>
  <div class="form-stack">
    <b-field label="Tipo de investimento">
      <b-field grouped>
        <b-radio-button
          v-for="opt in KIND_OPTS"
          :key="opt.value"
          v-model="form.kind"
          :native-value="opt.value"
          type="is-primary"
        >
          <b-icon :icon="opt.icon" size="is-small" />
          <span>{{ opt.label }}</span>
        </b-radio-button>
      </b-field>
    </b-field>

    <div v-if="form.walletsOfKind.length === 0" class="form-notice">
      <b-icon icon="information-outline" />
      <span
        >Nenhuma carteira de <b>{{ kindLabelPt }}</b> ainda.</span
      >
      <b-button
        size="is-small"
        type="is-primary"
        icon-left="plus"
        @click="emit('create-wallet', form.kind)"
      >
        Criar carteira
      </b-button>
    </div>

    <div v-else class="fixed-grid has-6-cols">
      <div class="grid is-gap-2">
        <b-field label="Carteira" :class="THIRD">
          <b-select v-model="form.walletId" expanded>
            <option v-for="opt in walletOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </option>
          </b-select>
        </b-field>

        <b-field :label="form.kind === 'CRYPTO' ? 'Tipo (opcional)' : 'Tipo'" :class="THIRD">
          <b-select v-model="form.typeId" expanded data-testid="type-select">
            <option v-if="form.kind === 'CRYPTO'" value="">Sem tipo</option>
            <option v-for="holdingType in kindTypes" :key="holdingType.id" :value="holdingType.id">
              {{ holdingType.name }}
            </option>
          </b-select>
        </b-field>

        <b-field label="Segmento (opcional)" :class="THIRD">
          <b-select v-model="form.segmentId" expanded data-testid="segment-select">
            <option value="">Sem segmento</option>
            <option
              v-for="segment in typesListStore.segments"
              :key="segment.id"
              :value="segment.id"
            >
              {{ segment.name }}
            </option>
          </b-select>
        </b-field>

        <template v-if="form.kind !== 'FUNDS'">
          <b-field :label="form.kind === 'CRYPTO' ? 'Sigla / código' : 'Ticker'" :class="THIRD">
            <b-input v-model="ticker" :placeholder="form.kind === 'CRYPTO' ? 'BTC' : 'PETR4'" />
          </b-field>
          <b-field label="Nome (opcional)" :class="THIRD">
            <b-input
              v-model="form.name"
              :placeholder="form.kind === 'CRYPTO' ? 'Bitcoin' : 'Petrobras'"
            />
          </b-field>
          <b-field label="Data da aquisição" :class="THIRD">
            <DateInput v-model="form.date" />
          </b-field>
          <b-field label="Quantidade" :class="THIRD">
            <NumberInput v-model="form.quantity" placeholder="0" min="0" />
          </b-field>
          <b-field label="Preço na aquisição" :class="THIRD">
            <NumberInput v-model="form.price" placeholder="0,00" :prefix="sym" min="0" />
          </b-field>
          <b-field :class="THIRD">
            <template #label>
              <FieldLabel
                text="Preço atual (opcional)"
                tip="Preencha para acompanhar lucro/prejuízo."
              />
            </template>
            <NumberInput v-model="form.currentPrice" placeholder="0,00" :prefix="sym" min="0" />
          </b-field>
        </template>

        <template v-else>
          <b-field label="Nome do fundo" :class="THIRD">
            <b-input v-model="form.name" placeholder="ex.: Tesouro Selic 2029" />
          </b-field>
          <b-field label="Taxa de administração (% a.a.)" :class="THIRD">
            <NumberInput v-model="form.administrationFeeRate" placeholder="0,00" min="0" />
          </b-field>
          <b-field label="Taxa de performance (%)" :class="THIRD">
            <NumberInput v-model="form.performanceFeeRate" placeholder="0,00" min="0" />
          </b-field>
          <b-field label="Data do aporte" :class="THIRD">
            <DateInput v-model="form.date" />
          </b-field>
          <b-field label="Valor aportado" :class="THIRD">
            <NumberInput v-model="form.amount" placeholder="0,00" :prefix="sym" min="0" />
          </b-field>
          <b-field :class="THIRD">
            <template #label>
              <FieldLabel
                text="Valor atual (opcional)"
                tip="Saldo atual do fundo, para calcular o rendimento."
              />
            </template>
            <NumberInput v-model="form.currentValue" placeholder="0,00" :prefix="sym" min="0" />
          </b-field>
        </template>

        <b-field :class="HALF">
          <template #label>
            <FieldLabel
              text="Estratégia"
              tip="Sem escolha, o investimento entra em Sem estratégia · Não definida."
            />
          </template>
          <b-select v-model="form.strategyId" expanded data-testid="strategy-select">
            <option
              v-for="strategy in strategiesStore.strategies"
              :key="strategy.id"
              :value="strategy.id"
            >
              {{ strategy.name }}
            </option>
          </b-select>
        </b-field>
        <b-field label="Classe" :class="HALF">
          <b-select v-model="form.strategyClassId" expanded data-testid="strategy-class-select">
            <option
              v-for="strategyClass in strategyClasses"
              :key="strategyClass.id"
              :value="strategyClass.id"
            >
              {{ strategyClass.name }}
            </option>
          </b-select>
        </b-field>
      </div>
    </div>
  </div>
</template>
