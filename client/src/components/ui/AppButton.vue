<script setup lang="ts">
import AppIcon, { type IconName } from '@/components/AppIcon.vue'

const props = withDefaults(
  defineProps<{
    variant?: 'primary' | 'soft' | 'ghost'
    size?: 'md' | 'sm'
    icon?: IconName
    type?: 'button' | 'submit'
    disabled?: boolean
    full?: boolean
  }>(),
  { variant: 'soft', size: 'md', type: 'button', disabled: false, full: false },
)

defineEmits<{ click: [MouseEvent] }>()
</script>

<template>
  <button
    :type="type"
    :disabled="disabled"
    class="btn"
    :class="[`btn-${variant}`, `btn-${size}`, { 'btn-full': full }]"
    @click="$emit('click', $event)"
  >
    <AppIcon v-if="icon" :name="icon" :size="props.size === 'sm' ? 16 : 18" :stroke="2.2" />
    <span v-if="$slots.default"><slot /></span>
  </button>
</template>
