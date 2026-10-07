<script setup lang="ts">
import Card from './Card.vue'
import CardBody from './CardBody.vue'

defineProps<{ label: string; value: string; valueClass?: string; valueTestId?: string }>()
</script>

<template>
  <Card class="kpi-card">
    <CardBody>
      <div class="kpi-label">{{ label }}</div>
      <div class="kpi-value" :class="valueClass" :data-testid="valueTestId">{{ value }}</div>
      <div class="kpi-foot">
        <slot name="foot" />
      </div>
    </CardBody>
  </Card>
</template>

<style scoped>
.kpi-label {
  font-size: 12.5px;
  color: var(--text-2);
  font-weight: 500;
}

.kpi-value {
  font-size: 24px;
  font-weight: 800;
  letter-spacing: -0.025em;
  margin: 9px 0 7px;
}

.kpi-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 20px;
}

.kpi-foot :slotted(.kpi-sub) {
  font-size: 12px;
  color: var(--text-muted);
}
</style>
