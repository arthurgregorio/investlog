<script setup lang="ts">
import { computed } from 'vue'
import { passwordRequirementStatus } from '@/utils/passwordRules'

const props = defineProps<{ password: string }>()

const status = computed(() => passwordRequirementStatus(props.password))

const requirements = computed(() => [
  { met: status.value.minLength, label: '8+ caracteres' },
  { met: status.value.hasUppercase, label: '1 maiúscula' },
  { met: status.value.hasNumber, label: '1 número' },
])
</script>

<template>
  <ul class="pw-requirements">
    <li v-for="requirement in requirements" :key="requirement.label" :class="{ 'pw-req-met': requirement.met }">
      <span class="pw-req-icon">
        <b-icon :icon="requirement.met ? 'check-bold' : 'circle-small'" size="is-small" />
      </span>
      <span>{{ requirement.label }}</span>
    </li>
  </ul>
</template>

<style scoped>
.pw-requirements {
  list-style: none;
  margin: 6px 0 0;
  padding: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.pw-requirements li {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 26px;
  padding: 0 10px 0 8px;
  border-radius: 20px;
  background: var(--surface-2);
  border: 1px solid var(--border);
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  transition:
    background-color 0.18s ease,
    border-color 0.18s ease,
    color 0.18s ease;
}

.pw-req-icon {
  display: inline-flex;
  transform: scale(0.85);
  transition: transform 0.16s ease;
}

.pw-req-met .pw-req-icon {
  transform: scale(1);
}

.pw-requirements li.pw-req-met {
  background: var(--up);
  border-color: var(--up);
  color: #fff;
}

@media (prefers-reduced-motion: reduce) {
  .pw-requirements li,
  .pw-req-icon {
    transition: none;
  }
}
</style>
