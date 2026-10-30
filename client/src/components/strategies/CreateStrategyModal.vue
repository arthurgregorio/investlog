<script setup lang="ts">
import { computed, ref } from 'vue'
import { useToast } from 'buefy'
import AppModal from '@/components/ui/AppModal.vue'
import { useStrategiesStore } from '@/stores/strategies'
import type { Strategy } from '@/types'

const emit = defineEmits<{ created: [Strategy]; close: [] }>()

const toast = useToast()
const strategiesStore = useStrategiesStore()

const name = ref('')
const submitting = ref(false)
const valid = computed(() => !!name.value.trim())

async function submit() {
  if (!valid.value || submitting.value) return
  submitting.value = true
  try {
    const created = await strategiesStore.create(name.value.trim())
    toast.open({ message: 'Estratégia criada.', type: 'is-success' })
    emit('created', created)
    emit('close')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AppModal
    title="Nova estratégia"
    subtitle="Dê um nome à estratégia. As classes e as metas vêm em seguida."
    @close="emit('close')"
  >
    <b-field label="Nome">
      <b-input
        v-model="name"
        placeholder="ex.: Aposentadoria"
        maxlength="60"
        autofocus
        data-testid="new-strategy-name"
        @keyup.enter="submit"
      />
    </b-field>
    <template #footer>
      <b-button outlined type="is-danger" :disabled="submitting" @click="emit('close')">
        Cancelar
      </b-button>
      <b-button
        type="is-success"
        icon-left="check"
        :disabled="!valid"
        :loading="submitting"
        data-testid="create-strategy"
        @click="submit"
      >
        Criar estratégia
      </b-button>
    </template>
  </AppModal>
</template>
