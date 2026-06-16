<script setup lang="ts">
defineProps<{ title: string; subtitle?: string; wide?: boolean }>()
const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <b-modal :model-value="true" has-modal-card trap-focus :can-cancel="['escape', 'outside']" @close="emit('close')">
    <div class="modal-card" :style="wide ? { width: '600px' } : undefined">
      <header class="modal-card-head">
        <p class="modal-card-title">{{ title }}</p>
        <b-button icon-left="close" aria-label="Fechar" @click="emit('close')" />
      </header>
      <section class="modal-card-body">
        <p v-if="subtitle" class="subtitle is-6 mb-4">{{ subtitle }}</p>
        <slot />
      </section>
      <footer v-if="$slots.footer" class="modal-card-foot is-justify-content-flex-end">
        <slot name="footer" />
      </footer>
    </div>
  </b-modal>
</template>
