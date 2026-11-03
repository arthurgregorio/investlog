<script setup lang="ts">
defineProps<{ text: string; tip: string }>()
</script>

<template>
  <span class="is-inline-flex is-align-items-center is-gap-1">
    {{ text }}
    <b-tooltip :label="tip" position="is-top" multilined>
      <b-icon icon="information-outline" size="is-small" class="has-text-grey" :aria-label="tip" />
    </b-tooltip>
  </span>
</template>
