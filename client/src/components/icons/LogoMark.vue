<script setup lang="ts">
defineProps<{ size?: number }>()
</script>

<template>
  <svg :width="size ?? 24" :height="size ?? 24" viewBox="0 0 24 24" fill="currentColor">
    <rect x="3.6" y="17.7" width="16.8" height="2.2" rx="1.1" opacity=".5" />
    <rect x="5.4" y="12.4" width="3.1" height="5.6" rx="1.2" />
    <rect x="10.45" y="8.2" width="3.1" height="9.8" rx="1.2" />
    <rect x="15.5" y="3.6" width="3.1" height="14.4" rx="1.2" />
    <circle cx="17.05" cy="3.6" r="2.05" />
  </svg>
</template>
