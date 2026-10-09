<script setup lang="ts">
import { computed } from 'vue'
import { holdingDisplayName, type DestinationGroup } from '@/composables/useReinvestForm'
import type { HoldingRow } from '@/types'

const props = defineProps<{
  groups: DestinationGroup[]
  currency: string
  disabled: boolean
  unpricedDestination?: HoldingRow
}>()

const destinationId = defineModel<string>({ required: true })

const message = computed(() => {
  if (!props.disabled && props.groups.length === 0) {
    return `Nenhum outro investimento ativo em carteiras de ${props.currency} para receber o reinvestimento.`
  }
  if (props.unpricedDestination) {
    return `${holdingDisplayName(props.unpricedDestination)} não tem preço atual. Defina o preço atual do investimento de destino antes de reinvestir nele.`
  }
  return ''
})
</script>

<template>
  <b-field
    label="Investimento de destino"
    :type="unpricedDestination ? 'is-danger' : ''"
    :message="message"
  >
    <b-select
      v-model="destinationId"
      placeholder="Selecione o investimento"
      expanded
      :disabled="disabled"
      data-testid="reinvest-destination"
    >
      <optgroup v-for="group in groups" :key="group.walletName" :label="group.walletName">
        <option v-for="holding in group.holdings" :key="holding.id" :value="holding.id">
          {{ holdingDisplayName(holding) }}
        </option>
      </optgroup>
    </b-select>
  </b-field>
</template>
