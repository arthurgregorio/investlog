<script setup lang="ts">
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'

withDefaults(defineProps<{ title: string; description: string; loading?: boolean }>(), {
  loading: false,
})
</script>

<template>
  <Card>
    <CardBody>
      <b-loading :is-full-page="false" :model-value="loading" />
      <div class="is-flex is-align-items-center is-justify-content-space-between is-gap-1.5">
        <h2 class="settings-title">{{ title }}</h2>
        <slot name="aside" />
      </div>
      <p class="set-desc">{{ description }}</p>
      <slot />
    </CardBody>
  </Card>
</template>

<style scoped>
.settings-title {
  font-size: 16px;
  font-weight: 700;
  margin: 0;
}
</style>
