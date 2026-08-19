<script setup lang="ts">
import { computed } from 'vue'
import { meetsPasswordRequirements, PASSWORD_MIN_LENGTH } from '@/utils/passwordRules'

const props = defineProps<{ password: string }>()

const met = computed(() => meetsPasswordRequirements(props.password))
</script>

<template>
  <p class="password-requirement-hint" :class="met ? 'has-text-success' : 'has-text-grey'">
    <b-icon :icon="met ? 'check-circle' : 'circle-outline'" size="is-small" />
    <span>Mínimo de {{ PASSWORD_MIN_LENGTH }} caracteres</span>
  </p>
</template>

<style scoped>
.password-requirement-hint {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.85rem;
  margin-top: 0.25rem;
}
</style>
