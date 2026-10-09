<script setup lang="ts">
import Card from './Card.vue'
import CardBody from './CardBody.vue'

defineProps<{ name: string }>()
</script>

<template>
  <Card class="entity-card">
    <slot name="decoration" />
    <CardBody>
      <div class="entity-head">
        <slot name="leading" />
        <div class="entity-titles">
          <div class="entity-name">{{ name }}</div>
          <div v-if="$slots.tags" class="entity-tags">
            <slot name="tags" />
          </div>
        </div>
        <div v-if="$slots.actions" class="entity-actions">
          <slot name="actions" />
        </div>
      </div>
      <slot />
      <div v-if="$slots.foot" class="entity-foot">
        <slot name="foot" />
      </div>
    </CardBody>
  </Card>
</template>

<style scoped>
.entity-card {
  position: relative;
  display: flex;
  flex-direction: column;
}

.entity-card .card-body {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.entity-head {
  display: flex;
  align-items: center;
  gap: 11px;
  margin-bottom: 14px;
}

.entity-titles {
  min-width: 0;
}

.entity-name {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.01em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.entity-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
}

.entity-actions {
  display: flex;
  gap: 6px;
  margin-left: auto;
}

.entity-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
}

.entity-foot :slotted(.button) {
  padding-right: 0;
}
</style>
