<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { fmt } from '@/composables/useFormat'

const props = withDefaults(defineProps<{ value: number | null; pct?: number | null; cur?: string }>(), {
  pct: null,
  cur: 'BRL',
})

const dir = computed(() => {
  if (props.value == null) return 'empty'
  if (props.value > 0.0001) return 'up'
  if (props.value < -0.0001) return 'down'
  return 'flat'
})
</script>

<template>
  <span v-if="value == null" class="gl-empty">—</span>
  <span v-else class="gl" :class="`gl-${dir}`">
    <AppIcon
      v-if="dir !== 'flat'"
      name="chevronDown"
      :size="14"
      :stroke="2.6"
      :style="{ transform: dir === 'up' ? 'rotate(180deg)' : 'none' }"
    />
    <span>{{ fmt.moneySigned(value, cur) }}</span>
    <span v-if="pct != null" class="gl-pct">{{ fmt.pctSigned(pct) }}</span>
  </span>
</template>
