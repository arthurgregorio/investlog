<script setup lang="ts">
withDefaults(defineProps<{ initials?: string; color?: string }>(), {
  initials: 'RT',
  color: 'var(--primary)',
})
</script>

<template>
  <div class="avatar" :style="{ background: color }">{{ initials }}</div>
</template>
