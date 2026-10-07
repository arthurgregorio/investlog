<script setup lang="ts">
import { onMounted } from 'vue'
import AppModal from '@/components/ui/AppModal.vue'
import NumberInput from '@/components/ui/NumberInput.vue'
import DateInput from '@/components/ui/DateInput.vue'
import ReinvestDestinationSelect from '@/components/investments/ReinvestDestinationSelect.vue'
import { holdingDisplayName, useReinvestForm } from '@/composables/useReinvestForm'
import { fmt } from '@/composables/useFormat'
import type { HoldingRow } from '@/types'

const props = defineProps<{
  walletId: string
  preselectedHoldingId?: string
}>()

const emit = defineEmits<{ reinvested: []; close: [] }>()

const {
  sources,
  loading,
  sourceId,
  destinationId,
  date,
  quantity,
  unitPrice,
  amount,
  fees,
  taxes,
  submitting,
  error,
  source,
  destination,
  isFundSource,
  currency,
  destinationGroups,
  destinationUnpriced,
  grossAmount,
  netAmount,
  destinationQuantity,
  exceedsRemaining,
  costsExceedGross,
  valid,
  load,
  submit,
} = useReinvestForm(() => {
  emit('reinvested')
  emit('close')
})

function availableLabel(holding: HoldingRow): string {
  return holding.quantity != null
    ? `${fmt.qty(holding.quantity)} disponíveis`
    : fmt.money(holding.currentValue ?? holding.costBasis, holding.walletCurrency)
}

onMounted(() => load(props.walletId, props.preselectedHoldingId))
</script>

<template>
  <AppModal
    title="Reinvestir"
    subtitle="Venda parte ou toda a posição e reinvista o valor líquido em outro investimento da mesma moeda."
    wide
    @close="emit('close')"
  >
    <b-loading :is-full-page="false" :model-value="loading" />
    <b-message v-if="error" type="is-danger" size="is-small" data-testid="reinvest-error">
      {{ error }}
    </b-message>

    <b-field label="Investimento de origem">
      <b-select
        v-model="sourceId"
        placeholder="Selecione o investimento"
        expanded
        data-testid="reinvest-source"
      >
        <option v-for="holding in sources" :key="holding.id" :value="holding.id">
          {{ holdingDisplayName(holding) }} · {{ availableLabel(holding) }}
        </option>
      </b-select>
    </b-field>

    <ReinvestDestinationSelect
      v-model="destinationId"
      :groups="destinationGroups"
      :currency="currency"
      :disabled="!source"
      :unpriced-destination="destinationUnpriced ? destination : undefined"
    />

    <template v-if="source">
      <b-field label="Data">
        <DateInput v-model="date" />
      </b-field>

      <b-field
        v-if="isFundSource"
        label="Valor reinvestido"
        :type="exceedsRemaining ? 'is-danger' : ''"
        :message="exceedsRemaining ? 'Maior que o valor atual do fundo' : ''"
      >
        <NumberInput
          v-model="amount"
          :prefix="fmt.sym(currency)"
          placeholder="0,00"
          min="0"
          data-testid="reinvest-amount"
        />
      </b-field>
      <div v-else class="fixed-grid has-2-cols mb-3">
        <div class="grid">
          <b-field
            class="mb-0"
            label="Quantidade"
            :type="exceedsRemaining ? 'is-danger' : ''"
            :message="exceedsRemaining ? 'Maior que o disponível' : ''"
          >
            <NumberInput
              v-model="quantity"
              :placeholder="`Até ${fmt.qty(source.quantity ?? 0)}`"
              min="0"
              data-testid="reinvest-quantity"
            />
          </b-field>
          <b-field label="Preço unitário de venda">
            <NumberInput
              v-model="unitPrice"
              :prefix="fmt.sym(currency)"
              placeholder="0,00"
              min="0"
              data-testid="reinvest-unit-price"
            />
          </b-field>
        </div>
      </div>

      <div class="fixed-grid has-2-cols mb-3">
        <div class="grid">
          <b-field label="Taxas (opcional)" class="mb-0">
            <NumberInput v-model="fees" :prefix="fmt.sym(currency)" placeholder="0,00" min="0" />
          </b-field>
          <b-field label="Impostos (opcional)">
            <NumberInput v-model="taxes" :prefix="fmt.sym(currency)" placeholder="0,00" min="0" />
          </b-field>
        </div>
      </div>

      <div class="notification is-size-7 mb-0" data-testid="reinvest-summary">
        <div class="is-flex is-justify-content-space-between">
          <span>Valor bruto</span>
          <strong>{{ fmt.money(grossAmount, currency) }}</strong>
        </div>
        <div class="is-flex is-justify-content-space-between mt-1">
          <span>Valor líquido reinvestido</span>
          <strong :class="{ 'has-text-danger': costsExceedGross }">{{
            fmt.money(netAmount, currency)
          }}</strong>
        </div>
        <p v-if="destination && destinationQuantity != null" class="mt-2">
          Compra de ≈ {{ fmt.qty(destinationQuantity) }} {{ holdingDisplayName(destination) }} a
          {{ fmt.money(Number(destination.currentPrice), currency) }}
        </p>
        <p v-if="costsExceedGross" class="has-text-danger mt-2">
          Taxas e impostos não podem consumir todo o valor reinvestido.
        </p>
      </div>
    </template>

    <template #footer>
      <b-button outlined type="is-danger" :disabled="submitting" @click="emit('close')"
        >Cancelar</b-button
      >
      <b-button
        type="is-primary"
        class="has-text-light"
        icon-left="autorenew"
        :disabled="!valid"
        :loading="submitting"
        data-testid="reinvest-submit"
        @click="submit"
      >
        Reinvestir
      </b-button>
    </template>
  </AppModal>
</template>
