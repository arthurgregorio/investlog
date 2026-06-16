<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'

defineProps<{ title: string; subtitle?: string; wide?: boolean }>()
const emit = defineEmits<{ close: [] }>()

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
}
onMounted(() => document.addEventListener('keydown', onKey))
onBeforeUnmount(() => document.removeEventListener('keydown', onKey))
</script>

<template>
  <Teleport to="body">
    <div class="modal-scrim" @click="emit('close')">
      <div class="modal" :class="{ 'modal-wide': wide }" role="dialog" @click.stop>
        <div class="modal-head">
          <div>
            <h2 class="modal-title">{{ title }}</h2>
            <p v-if="subtitle" class="modal-sub">{{ subtitle }}</p>
          </div>
          <b-button icon-left="close" aria-label="Fechar" @click="emit('close')" />
        </div>
        <div class="modal-body"><slot /></div>
        <div v-if="$slots.footer" class="modal-foot"><slot name="footer" /></div>
      </div>
    </div>
  </Teleport>
</template>
