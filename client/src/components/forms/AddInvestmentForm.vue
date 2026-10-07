<script setup lang="ts">
import { computed } from 'vue'
import NumberInput from '@/components/ui/NumberInput.vue'
import DateInput from '@/components/ui/DateInput.vue'
import { useTypesListStore } from '@/stores/typesList'
import { fmt } from '@/composables/useFormat'
import type { AddInvestmentForm } from '@/composables/useAddInvestmentForm'
import type { WalletKind } from '@/types'

const props = defineProps<{ form: AddInvestmentForm }>()
const emit = defineEmits<{ 'create-wallet': [WalletKind] }>()

const typesListStore = useTypesListStore()

const KIND_OPTS: { value: WalletKind; label: string; icon: string }[] = [
  { value: 'STOCKS', label: 'Ações', icon: 'trending-up' },
  { value: 'CRYPTO', label: 'Cripto', icon: 'bitcoin' },
  { value: 'FUNDS', label: 'Fundos', icon: 'office-building-outline' },
]

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

    <div v-else class="fixed-grid has-2-cols">
      <div class="grid is-gap-2">
        <b-field label="Carteira" class="cell is-col-span-2">
          <b-select v-model="form.walletId">
            <option v-for="opt in walletOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </option>
          </b-select>
        </b-field>

        <b-field v-if="form.kind === 'STOCKS'" label="Tipo" class="cell is-col-span-2-mobile">
          <b-select v-model="form.stockTypeId">
            <option
              v-for="stockType in typesListStore.stockTypes"
              :key="stockType.id"
              :value="stockType.id"
            >
              {{ stockType.name }}
            </option>
          </b-select>
        </b-field>

        <b-field
          v-if="form.kind === 'STOCKS'"
          label="Segmento (opcional)"
          class="cell is-col-span-2-mobile"
        >
          <b-select v-model="form.stockSegmentId" data-testid="stock-segment-select">
            <option value="">Sem segmento</option>
            <option
              v-for="stockSegment in typesListStore.stockSegments"
              :key="stockSegment.id"
              :value="stockSegment.id"
            >
              {{ stockSegment.name }}
            </option>
          </b-select>
        </b-field>

        <template v-if="form.kind !== 'FUNDS'">
          <b-field
            :label="form.kind === 'CRYPTO' ? 'Sigla / código' : 'Ticker'"
            class="cell is-col-span-2-mobile"
          >
            <b-input v-model="ticker" :placeholder="form.kind === 'CRYPTO' ? 'BTC' : 'PETR4'" />
          </b-field>
          <b-field
            label="Nome (opcional)"
            class="cell"
            :class="form.kind === 'CRYPTO' ? 'is-col-span-2' : 'is-col-span-2-mobile'"
          >
            <b-input
              v-model="form.name"
              :placeholder="form.kind === 'CRYPTO' ? 'Bitcoin' : 'Petrobras'"
            />
          </b-field>
          <b-field label="Data da aquisição" class="cell is-col-span-2-mobile">
            <DateInput v-model="form.date" />
          </b-field>
          <b-field label="Quantidade" class="cell is-col-span-2-mobile">
            <NumberInput v-model="form.quantity" placeholder="0" min="0" />
          </b-field>
          <b-field label="Preço na aquisição" class="cell is-col-span-2-mobile">
            <NumberInput v-model="form.price" placeholder="0,00" :prefix="sym" min="0" />
          </b-field>
          <b-field
            label="Preço atual (opcional)"
            class="cell is-col-span-2-mobile"
            message="Preencha para acompanhar lucro/prejuízo."
          >
            <NumberInput v-model="form.currentPrice" placeholder="0,00" :prefix="sym" min="0" />
          </b-field>
        </template>

        <template v-else>
          <b-field label="Tipo de fundo" class="cell is-col-span-2-mobile">
            <b-select v-model="form.fundTypeId">
              <option
                v-for="fundType in typesListStore.fundTypes"
                :key="fundType.id"
                :value="fundType.id"
              >
                {{ fundType.name }}
              </option>
            </b-select>
          </b-field>
          <b-field label="Nome do fundo" class="cell is-col-span-2-mobile">
            <b-input v-model="form.name" placeholder="ex.: Tesouro Selic 2029" />
          </b-field>
          <b-field label="Taxa de administração (% a.a.)" class="cell is-col-span-2-mobile">
            <NumberInput v-model="form.administrationFeeRate" placeholder="0,00" min="0" />
          </b-field>
          <b-field label="Taxa de performance (%)" class="cell is-col-span-2-mobile">
            <NumberInput v-model="form.performanceFeeRate" placeholder="0,00" min="0" />
          </b-field>
          <b-field label="Data do aporte" class="cell is-col-span-2-mobile">
            <DateInput v-model="form.date" />
          </b-field>
          <b-field label="Valor aportado" class="cell is-col-span-2-mobile">
            <NumberInput v-model="form.amount" placeholder="0,00" :prefix="sym" min="0" />
          </b-field>
          <b-field
            label="Valor atual (opcional)"
            message="Saldo atual do fundo, para calcular o rendimento."
            class="cell is-col-span-2"
          >
            <NumberInput v-model="form.currentValue" placeholder="0,00" :prefix="sym" min="0" />
          </b-field>
        </template>
      </div>
    </div>
  </div>
</template>
