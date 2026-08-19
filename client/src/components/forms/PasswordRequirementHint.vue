<script setup lang="ts">
import { computed } from 'vue'
import { passwordRequirementStatus, PASSWORD_MIN_LENGTH } from '@/utils/passwordRules'

const props = defineProps<{ password: string }>()

const status = computed(() => passwordRequirementStatus(props.password))
</script>

<template>
  <ul class="password-requirement-hint">
    <li :class="status.minLength ? 'has-text-success' : 'has-text-grey'">
      <b-icon :icon="status.minLength ? 'check-circle' : 'circle-outline'" size="is-small" />
      <span>Mínimo de {{ PASSWORD_MIN_LENGTH }} caracteres</span>
    </li>
    <li :class="status.hasUppercase ? 'has-text-success' : 'has-text-grey'">
      <b-icon :icon="status.hasUppercase ? 'check-circle' : 'circle-outline'" size="is-small" />
      <span>Ao menos uma letra maiúscula</span>
    </li>
    <li :class="status.hasNumber ? 'has-text-success' : 'has-text-grey'">
      <b-icon :icon="status.hasNumber ? 'check-circle' : 'circle-outline'" size="is-small" />
      <span>Ao menos um número</span>
    </li>
  </ul>
</template>

<style scoped>
.password-requirement-hint {
  list-style: none;
  margin: 0.25rem 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.password-requirement-hint li {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.85rem;
}
</style>
