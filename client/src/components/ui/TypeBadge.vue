<script setup lang="ts">
import type { WalletKind } from '@/types'

defineProps<{ kind: WalletKind }>()
</script>

<template>
  <span class="wt-badge" :class="`wt-${kind}`"><slot /></span>
</template>
