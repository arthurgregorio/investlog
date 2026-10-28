<script setup lang="ts">
import { fmt } from '@/composables/useFormat'

defineProps<{ cash: number; currency: string }>()
const emit = defineEmits<{ transfer: []; 'withdraw-to-bank': [] }>()
</script>

<template>
  <tr data-testid="cash-row">
    <td>
      <div class="is-flex is-align-items-center is-gap-1.5">
        <b-icon icon="cash" custom-size="mdi-24px" class="ticker-badge cash-badge" />
        <div>
          <div class="t-ticker">Caixa</div>
          <div class="t-name">Disponível para compras</div>
        </div>
      </div>
    </td>
    <td>
      <span class="type-tag">Caixa</span>
    </td>
    <td class="c-num has-text-right">—</td>
    <td class="c-num has-text-right"><span class="gl-empty">—</span></td>
    <td class="c-num has-text-right"><span class="gl-empty">—</span></td>
    <td class="c-num has-text-right has-text-weight-bold" data-testid="cash-row-value">
      {{ fmt.money(cash, currency) }}
    </td>
    <td class="c-num has-text-right"><span class="gl-empty">—</span></td>
    <td class="c-act is-wide">
      <div class="is-flex is-justify-content-flex-end">
        <b-tooltip label="Transferir caixa" position="is-left">
          <b-button
            type="is-ghost"
            icon-left="bank-transfer"
            aria-label="Transferir caixa"
            data-testid="cash-row-transfer"
            @click="emit('transfer')"
          />
        </b-tooltip>
        <b-tooltip label="Sacar para o banco" position="is-left">
          <b-button
            type="is-ghost"
            icon-left="bank-outline"
            aria-label="Sacar para o banco"
            data-testid="cash-row-withdraw"
            @click="emit('withdraw-to-bank')"
          />
        </b-tooltip>
      </div>
    </td>
  </tr>
</template>
