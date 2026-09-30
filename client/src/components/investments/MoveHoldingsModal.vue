<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useToast } from 'buefy'
import AppModal from '@/components/ui/AppModal.vue'
import NumberInput from '@/components/ui/NumberInput.vue'
import TickerBadge from '@/components/ui/TickerBadge.vue'
import { holdingsApi } from '@/api/holdings'
import type { WalletMoveItemPayload } from '@/api/walletMoves'
import { useWalletsStore } from '@/stores/wallets'
import { useWalletMovesStore } from '@/stores/walletMoves'
import { fmt } from '@/composables/useFormat'
import { problemDetailMessage } from '@/utils/apiErrors'
import { badgeColor } from '@/utils/walletTypes'
import type { HoldingRow } from '@/types'

const MOVABLE_HOLDINGS_PAGE_SIZE = 500

const props = defineProps<{
  originWalletId?: string
  preselectedHoldingId?: string
}>()

const emit = defineEmits<{ moved: []; close: [] }>()

const toast = useToast()
const walletsStore = useWalletsStore()
const walletMovesStore = useWalletMovesStore()

const selectedOriginId = ref(props.originWalletId ?? '')
const destinationId = ref('')
const holdings = ref<HoldingRow[]>([])
const loadingHoldings = ref(false)
const moveAll = ref(false)
const selected = ref<Record<string, boolean>>({})
const quantities = ref<Record<string, number | ''>>({})
const submitting = ref(false)
const error = ref('')

const origin = computed(() => walletsStore.walletById(selectedOriginId.value))

const destinations = computed(() => {
  const currentOrigin = origin.value
  if (!currentOrigin) return []
  return walletsStore.wallets.filter(
    (wallet) =>
      wallet.id !== currentOrigin.id &&
      wallet.kind === currentOrigin.kind &&
      wallet.currency === currentOrigin.currency,
  )
})

function displayName(holding: HoldingRow): string {
  return holding.ticker ?? holding.name
}

function takesQuantity(holding: HoldingRow): boolean {
  return holding.kind !== 'FUNDS'
}

function exceedsRemaining(holding: HoldingRow): boolean {
  const quantity = quantities.value[holding.id]
  return quantity !== '' && quantity != null && holding.quantity != null && quantity > holding.quantity
}

const items = computed<WalletMoveItemPayload[]>(() => {
  if (moveAll.value) return holdings.value.map((holding) => ({ holdingId: holding.id }))
  return holdings.value
    .filter((holding) => selected.value[holding.id])
    .map((holding) => {
      const quantity = quantities.value[holding.id]
      return takesQuantity(holding) && quantity !== '' && quantity != null
        ? { holdingId: holding.id, quantity }
        : { holdingId: holding.id }
    })
})

const hasInvalidQuantity = computed(
  () =>
    !moveAll.value &&
    holdings.value.some(
      (holding) =>
        selected.value[holding.id] &&
        (exceedsRemaining(holding) || Number(quantities.value[holding.id]) < 0),
    ),
)

const valid = computed(
  () =>
    !!origin.value && !!destinationId.value && items.value.length > 0 && !hasInvalidQuantity.value,
)

async function loadHoldings(walletId: string) {
  holdings.value = []
  selected.value = {}
  quantities.value = {}
  moveAll.value = false
  if (!walletId) return
  loadingHoldings.value = true
  try {
    const page = await holdingsApi.findAll({ walletId, size: MOVABLE_HOLDINGS_PAGE_SIZE })
    holdings.value = page.content
    if (props.preselectedHoldingId && page.content.some((holding) => holding.id === props.preselectedHoldingId)) {
      selected.value = { [props.preselectedHoldingId]: true }
    }
  } finally {
    loadingHoldings.value = false
  }
}

watch(selectedOriginId, (walletId) => {
  if (!destinations.value.some((wallet) => wallet.id === destinationId.value)) {
    destinationId.value = ''
  }
  loadHoldings(walletId)
})

onMounted(() => {
  walletsStore.load()
  loadHoldings(selectedOriginId.value)
})

async function submit() {
  if (!valid.value) return
  error.value = ''
  submitting.value = true
  try {
    await walletMovesStore.move(selectedOriginId.value, {
      destinationWalletId: destinationId.value,
      items: items.value,
    })
    toast.open({ message: 'Investimentos movidos!', type: 'is-success' })
    emit('moved')
    emit('close')
  } catch (caughtError) {
    error.value = problemDetailMessage(caughtError) ?? 'Não foi possível mover os investimentos.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AppModal
    title="Mover investimentos"
    subtitle="Leve investimentos para outra carteira do mesmo tipo e moeda, mantendo o histórico de compras."
    wide
    @close="emit('close')"
  >
    <p v-if="error" class="auth-error" data-testid="move-error">{{ error }}</p>

    <div class="form-grid">
      <b-field v-if="!originWalletId" label="Carteira de origem" style="grid-column: 1/-1">
        <b-select
          v-model="selectedOriginId"
          placeholder="Selecione a carteira"
          expanded
          data-testid="move-origin"
        >
          <option v-for="wallet in walletsStore.wallets" :key="wallet.id" :value="wallet.id">
            {{ wallet.name }} · {{ wallet.currency }}
          </option>
        </b-select>
      </b-field>

      <b-field label="Carteira de destino" style="grid-column: 1/-1">
        <b-select
          v-model="destinationId"
          placeholder="Selecione a carteira"
          expanded
          :disabled="!origin"
          data-testid="move-destination"
        >
          <option v-for="wallet in destinations" :key="wallet.id" :value="wallet.id">
            {{ wallet.name }} · {{ wallet.currency }}
          </option>
        </b-select>
      </b-field>
    </div>

    <p v-if="origin && destinations.length === 0" class="move-hint">
      Nenhuma outra carteira do mesmo tipo e moeda para receber os investimentos.
    </p>

    <template v-if="origin">
      <div class="move-list-head">
        <span class="kpi-label">Investimentos</span>
        <b-switch v-model="moveAll" :disabled="holdings.length === 0" data-testid="move-all">
          Mover tudo
        </b-switch>
      </div>

      <div class="move-list">
        <b-loading :is-full-page="false" :active="loadingHoldings" />
        <p v-if="!loadingHoldings && holdings.length === 0" class="move-hint">
          Nenhum investimento ativo nesta carteira.
        </p>
        <div
          v-for="holding in holdings"
          :key="holding.id"
          class="move-item"
          :class="{ 'is-selected': moveAll || selected[holding.id] }"
          data-testid="move-item"
        >
          <b-checkbox
            :model-value="moveAll || !!selected[holding.id]"
            :disabled="moveAll"
            @update:model-value="(checked: boolean) => (selected[holding.id] = checked)"
          />
          <TickerBadge :ticker="displayName(holding)" :color="badgeColor(holding.ticker, holding.kind)" />
          <div class="move-item-meta">
            <div class="t-ticker">{{ displayName(holding) }}</div>
            <div class="t-name">
              <template v-if="holding.quantity != null">
                Disponível: {{ fmt.qty(holding.quantity) }}
              </template>
              <template v-else>{{ fmt.money(holding.costBasis, holding.walletCurrency) }}</template>
            </div>
          </div>
          <b-field
            v-if="takesQuantity(holding) && selected[holding.id] && !moveAll"
            class="move-item-quantity"
            :type="exceedsRemaining(holding) ? 'is-danger' : ''"
            :message="exceedsRemaining(holding) ? 'Maior que o disponível' : ''"
          >
            <NumberInput
              :model-value="quantities[holding.id] ?? ''"
              :placeholder="`Tudo (${fmt.qty(holding.quantity ?? 0)})`"
              min="0"
              size="is-small"
              data-testid="move-quantity"
              @update:model-value="(value: number | '') => (quantities[holding.id] = value)"
            />
          </b-field>
        </div>
      </div>
    </template>

    <template #footer>
      <b-button outlined type="is-danger" :disabled="submitting" @click="emit('close')"
        >Cancelar</b-button
      >
      <b-button
        type="is-primary"
        class="has-text-light"
        icon-left="swap-horizontal"
        :disabled="!valid"
        :loading="submitting"
        data-testid="move-submit"
        @click="submit"
      >
        Mover
      </b-button>
    </template>
  </AppModal>
</template>
