<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{ ticker?: string; color?: string; size?: number }>(), {
  ticker: '',
  size: 36,
})

const txt = computed(() => (props.ticker.length <= 4 ? props.ticker : props.ticker.slice(0, 4)))
const fontSize = computed(() => (props.ticker.length > 3 ? 10 : 12))
</script>

<template>
  <div
    class="ticker-badge"
    :style="{ width: `${size}px`, height: `${size}px`, background: color, fontSize: `${fontSize}px` }"
  >
    {{ txt }}
  </div>
</template>
