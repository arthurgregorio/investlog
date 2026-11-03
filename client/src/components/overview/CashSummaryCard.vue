<script setup lang="ts">
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import { fmt } from '@/composables/useFormat'

defineProps<{ cash: number; walletCount: number; currency: string }>()

const emit = defineEmits<{ view: [] }>()
</script>

<template>
  <Card class="summary-card" data-testid="cash-summary-card">
    <CardBody>
      <div class="is-flex is-align-items-center is-gap-1">
        <span class="type-ic has-background-grey">
          <b-icon icon="cash" />
        </span>
        <div>
          <div class="type-name">Caixa</div>
          <div class="is-size-7 has-text-grey" data-testid="cash-meta">
            {{
              walletCount
                ? `em ${walletCount} ${walletCount === 1 ? 'carteira' : 'carteiras'}`
                : 'nenhuma carteira com caixa'
            }}
          </div>
        </div>
        <div class="type-value ml-auto">{{ fmt.money(cash, currency, { compact: true }) }}</div>
      </div>
      <div class="mt-auto pt-3">
        <div class="result-row is-align-items-flex-end">
          <span class="is-size-7 has-text-grey">
            {{ cash > 0 ? 'Disponível para comprar' : 'Sem caixa parado' }}
          </span>
          <b-button
            type="is-ghost"
            size="is-small"
            class="overview-link"
            icon-right="chevron-right"
            data-testid="cash-view"
            @click="emit('view')"
            >Ver</b-button
          >
        </div>
      </div>
    </CardBody>
  </Card>
</template>
