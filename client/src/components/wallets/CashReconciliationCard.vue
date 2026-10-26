<script setup lang="ts">
import { computed, ref } from 'vue'
import EntityCard from '@/components/ui/EntityCard.vue'
import DateInput from '@/components/ui/DateInput.vue'
import NumberInput from '@/components/ui/NumberInput.vue'
import SegmentedControl from '@/components/ui/SegmentedControl.vue'
import { cashApi } from '@/api/cash'
import { fmt } from '@/composables/useFormat'
import { useRatesStore } from '@/stores/rates'
import { useWalletsStore } from '@/stores/wallets'
import { problemDetailMessage } from '@/utils/apiErrors'
import { crossRate } from '@/utils/crossRate'
import { WALLET_TYPES } from '@/utils/walletTypes'
import type { CashReconciliation, CashSuggestion } from '@/types'

type Destination = 'wallet' | 'bank' | 'keep'

interface ReconciliationLine {
  destination: Destination
  walletId: string | null
  date: Date | null
  amount: number | ''
  rate: number | ''
  bankName: string
}

const DESTINATIONS: { value: Destination; label: string }[] = [
  { value: 'wallet', label: 'Outra carteira' },
  { value: 'bank', label: 'Banco' },
  { value: 'keep', label: 'Continua em caixa' },
]

const BANK_NAME_MAX_LENGTH = 60

const props = defineProps<{ reconciliation: CashReconciliation }>()

const emit = defineEmits<{ registered: [descriptions: string[]] }>()

const walletsStore = useWalletsStore()
const ratesStore = useRatesStore()

const firstSourceDate = computed(() => {
  const dates = props.reconciliation.sources.map((source) => source.date).sort()
  return dates.length > 0 ? parseIsoDate(dates[0]!) : new Date()
})

const lines = ref<ReconciliationLine[]>([newLine()])
const submitting = ref(false)
const error = ref('')

const destinationWallets = computed(() =>
  walletsStore.wallets.filter((wallet) => wallet.id !== props.reconciliation.walletId),
)

function parseIsoDate(isoDate: string): Date {
  return new Date(`${isoDate}T12:00:00`)
}

function newLine(): ReconciliationLine {
  return {
    destination: 'wallet',
    walletId: null,
    date: firstSourceDate.value,
    amount: '',
    rate: '',
    bankName: '',
  }
}

function destinationWalletOf(line: ReconciliationLine) {
  return destinationWallets.value.find((wallet) => wallet.id === line.walletId)
}

function isCrossCurrency(line: ReconciliationLine): boolean {
  const destination = destinationWalletOf(line)
  return !!destination && destination.currency !== props.reconciliation.currency
}

function onDestinationWalletChange(line: ReconciliationLine) {
  const destination = destinationWalletOf(line)!
  line.rate = crossRate(ratesStore.rates, props.reconciliation.currency, destination.currency)
}

function amountOf(line: ReconciliationLine): number {
  return line.destination === 'keep' ? 0 : Number(line.amount || 0)
}

const allocated = computed(() => lines.value.reduce((total, line) => total + amountOf(line), 0))
const remaining = computed(() => props.reconciliation.cash - allocated.value)
const exceedsCash = computed(() => remaining.value < -0.005)

function isLineValid(line: ReconciliationLine): boolean {
  if (line.destination === 'keep') return true
  if (!line.date || amountOf(line) <= 0) return false
  if (line.destination === 'bank') return true
  return !!destinationWalletOf(line) && (!isCrossCurrency(line) || Number(line.rate || 0) > 0)
}

const valid = computed(() => !exceedsCash.value && lines.value.every(isLineValid))

function useSuggestion(suggestion: CashSuggestion) {
  const [first] = lines.value
  first!.destination = 'wallet'
  first!.walletId = suggestion.walletId
  first!.date = parseIsoDate(suggestion.purchaseDate)
  first!.rate = suggestion.rate
  first!.amount = Math.min(
    props.reconciliation.cash,
    Number((suggestion.newMoney / suggestion.rate).toFixed(2)),
  )
}

function addLine() {
  lines.value.push(newLine())
}

function removeLine(index: number) {
  lines.value.splice(index, 1)
}

function fillRemaining(line: ReconciliationLine) {
  line.amount = Number((remaining.value + amountOf(line)).toFixed(2))
}

function isoDateOf(date: Date): string {
  return date.toISOString().slice(0, 10)
}

async function register(line: ReconciliationLine): Promise<string | null> {
  const { walletId, currency } = props.reconciliation
  const amount = amountOf(line)
  if (line.destination === 'keep') return null
  const date = isoDateOf(line.date!)
  if (line.destination === 'bank') {
    const bankName = line.bankName.trim()
    await cashApi.withdrawToBank(walletId, {
      withdrawalDate: date,
      amount,
      ...(bankName ? { bankName } : {}),
    })
    return `${fmt.money(amount, currency)} sacados para ${bankName || 'o banco'}`
  }
  const destination = destinationWalletOf(line)!
  await cashApi.transfer(walletId, {
    destinationWalletId: destination.id,
    transferDate: date,
    amount,
    ...(isCrossCurrency(line) ? { rate: Number(line.rate) } : {}),
  })
  return `${fmt.money(amount, currency)} transferidos para ${destination.name}`
}

async function submit() {
  if (!valid.value) return
  error.value = ''
  submitting.value = true
  const { walletName, currency } = props.reconciliation
  const descriptions: string[] = []
  try {
    for (const line of lines.value) {
      const description = await register(line)
      if (description) descriptions.push(`${walletName}: ${description}`)
    }
    await cashApi.markReconciled(props.reconciliation.walletId)
    if (remaining.value > 0.005) {
      descriptions.push(`${walletName}: ${fmt.money(remaining.value, currency)} continuam em caixa`)
    }
    emit('registered', descriptions)
  } catch (caughtError) {
    error.value = problemDetailMessage(caughtError) ?? 'Não foi possível registrar a conciliação.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <EntityCard
    :name="reconciliation.walletName"
    class="mb-0"
    :data-testid="`reconciliation-card-${reconciliation.walletId}`"
  >
    <template #leading>
      <span class="type-ic sm" :style="{ background: WALLET_TYPES[reconciliation.kind].accent }">
        <b-icon :icon="WALLET_TYPES[reconciliation.kind].icon" size="is-small" />
      </span>
    </template>
    <template #tags>
      <b-tag :class="`tt-${reconciliation.kind.toLowerCase()}`">
        {{ WALLET_TYPES[reconciliation.kind].label }}
      </b-tag>
      <span class="cur-chip">{{ reconciliation.currency }}</span>
    </template>
    <template #actions>
      <div class="has-text-right">
        <div class="has-text-weight-bold" data-testid="reconciliation-cash">
          {{ fmt.money(reconciliation.cash, reconciliation.currency) }}
        </div>
        <div class="sub-caption">Caixa</div>
      </div>
    </template>

    <div class="sub-caption mb-1">Resgates que formaram este caixa</div>
    <div
      v-for="source in reconciliation.sources"
      :key="source.resultId"
      class="is-flex is-justify-content-space-between is-align-items-center is-size-7 py-1"
      data-testid="reconciliation-source"
    >
      <span class="is-flex is-align-items-center is-gap-1">
        <b-icon icon="cash-minus" size="is-small" type="is-grey" />
        <span class="has-text-weight-semibold">{{ source.ticker ?? source.holdingName }}</span>
        <span class="has-text-grey">{{ fmt.date(source.date) }}</span>
      </span>
      <strong>{{ fmt.money(source.amount, reconciliation.currency) }}</strong>
    </div>

    <b-message
      v-if="reconciliation.suggestion"
      type="is-info"
      size="is-small"
      class="mt-3"
      data-testid="reconciliation-suggestion"
    >
      <div class="is-flex is-align-items-center is-justify-content-space-between is-gap-2">
        <span>
          Parece ter ido para
          <strong>{{
            reconciliation.suggestion.ticker ?? reconciliation.suggestion.holdingName
          }}</strong>
          em {{ reconciliation.suggestion.walletName }}, comprado em
          {{ fmt.date(reconciliation.suggestion.purchaseDate) }} com
          {{
            fmt.money(reconciliation.suggestion.newMoney, reconciliation.suggestion.walletCurrency)
          }}
          de dinheiro novo.
        </span>
        <b-button
          type="is-info"
          size="is-small"
          data-testid="reconciliation-use-suggestion"
          @click="useSuggestion(reconciliation.suggestion)"
        >
          Usar sugestão
        </b-button>
      </div>
    </b-message>

    <b-message v-if="error" type="is-danger" size="is-small" data-testid="reconciliation-error">
      {{ error }}
    </b-message>

    <div v-for="(line, index) in lines" :key="index" class="mt-3" data-testid="reconciliation-line">
      <div class="is-flex is-align-items-center is-justify-content-space-between mb-2">
        <SegmentedControl
          v-model="line.destination"
          :options="DESTINATIONS"
          group-label="Para onde foi este caixa"
        />
        <b-button
          v-if="index > 0"
          type="is-danger"
          outlined
          size="is-small"
          icon-left="delete"
          aria-label="Remover destino"
          data-testid="reconciliation-remove-line"
          @click="removeLine(index)"
        />
      </div>

      <p v-if="line.destination === 'keep'" class="is-size-7 has-text-grey">
        O que não for destinado acima fica no caixa desta carteira.
      </p>

      <div v-else class="fixed-grid has-2-cols">
        <div class="grid is-gap-2">
          <b-field v-if="line.destination === 'wallet'" label="Carteira de destino" class="cell">
            <b-select
              v-model="line.walletId"
              placeholder="Selecione a carteira"
              expanded
              data-testid="reconciliation-destination"
              @update:model-value="onDestinationWalletChange(line)"
            >
              <option v-for="wallet in destinationWallets" :key="wallet.id" :value="wallet.id">
                {{ wallet.name }} · {{ wallet.currency }}
              </option>
            </b-select>
          </b-field>
          <b-field v-else label="Banco (opcional)" class="cell">
            <b-input
              v-model="line.bankName"
              :maxlength="BANK_NAME_MAX_LENGTH"
              :has-counter="false"
              placeholder="Ex.: Itaú, Nubank"
              data-testid="reconciliation-bank"
            />
          </b-field>

          <b-field label="Data" class="cell">
            <DateInput v-model="line.date" />
          </b-field>

          <b-field label="Valor" class="cell">
            <div class="field is-grouped">
              <div class="control is-expanded">
                <NumberInput
                  v-model="line.amount"
                  :prefix="fmt.sym(reconciliation.currency)"
                  placeholder="0,00"
                  min="0"
                  data-testid="reconciliation-amount"
                />
              </div>
              <div class="control">
                <b-button
                  type="is-ghost"
                  data-testid="reconciliation-fill-remaining"
                  @click="fillRemaining(line)"
                >
                  Restante
                </b-button>
              </div>
            </div>
          </b-field>

          <b-field v-if="isCrossCurrency(line)" label="Câmbio" class="cell">
            <NumberInput
              v-model="line.rate"
              :prefix="`1 ${reconciliation.currency} = ${fmt.sym(destinationWalletOf(line)?.currency)}`"
              placeholder="0,00"
              min="0"
              data-testid="reconciliation-rate"
            />
          </b-field>
        </div>
      </div>
    </div>

    <template #foot>
      <b-button
        type="is-ghost"
        size="is-small"
        icon-left="call-split"
        data-testid="reconciliation-add-line"
        @click="addLine"
      >
        Dividir em outro destino
      </b-button>
      <div class="is-flex is-align-items-center is-gap-2">
        <span
          class="is-size-7"
          :class="exceedsCash ? 'has-text-danger' : 'has-text-grey'"
          data-testid="reconciliation-remaining"
        >
          {{
            exceedsCash
              ? `Excede o caixa em ${fmt.money(-remaining, reconciliation.currency)}`
              : `Fica em caixa: ${fmt.money(remaining, reconciliation.currency)}`
          }}
        </span>
        <b-button
          type="is-success"
          icon-left="check"
          :disabled="!valid"
          :loading="submitting"
          data-testid="reconciliation-submit"
          @click="submit"
        >
          Registrar
        </b-button>
      </div>
    </template>
  </EntityCard>
</template>
