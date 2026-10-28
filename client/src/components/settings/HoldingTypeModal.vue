<script setup lang="ts">
import { computed, ref } from 'vue'
import { useToast } from 'buefy'
import AppModal from '@/components/ui/AppModal.vue'
import { useTypesListStore } from '@/stores/typesList'
import { WALLET_TYPES } from '@/utils/walletTypes'
import type { HoldingType, WalletKind } from '@/types'

const props = defineProps<{ holdingType: HoldingType | null }>()
const emit = defineEmits<{ close: [] }>()

const KINDS: WalletKind[] = ['STOCKS', 'CRYPTO', 'FUNDS']

const toast = useToast()
const typesListStore = useTypesListStore()

const name = ref(props.holdingType?.name ?? '')
const selectedKinds = ref<WalletKind[]>([...(props.holdingType?.kinds ?? [])])
const submitting = ref(false)

const isEdit = computed(() => props.holdingType !== null)
const trimmedName = computed(() => name.value.trim())
const valid = computed(() => trimmedName.value.length > 0 && selectedKinds.value.length > 0)

async function submit() {
  if (!valid.value || submitting.value) return
  submitting.value = true
  const kinds = KINDS.filter((kind) => selectedKinds.value.includes(kind))
  try {
    if (props.holdingType) {
      await typesListStore.updateHoldingType(props.holdingType.id, trimmedName.value, kinds)
    } else {
      await typesListStore.addHoldingType(trimmedName.value, kinds)
    }
    toast.open({ message: isEdit.value ? 'Tipo atualizado.' : 'Tipo criado.', type: 'is-success' })
    emit('close')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AppModal
    :title="isEdit ? 'Editar tipo' : 'Novo tipo'"
    subtitle="Escolha em quais classes de investimento este tipo pode ser usado."
    @close="emit('close')"
  >
    <b-field label="Nome">
      <b-input v-model="name" placeholder="Nome do tipo" data-testid="holding-type-name" />
    </b-field>
    <b-field label="Aplica-se a">
      <div class="is-flex is-flex-wrap-wrap is-gap-2">
        <b-checkbox
          v-for="kind in KINDS"
          :key="kind"
          v-model="selectedKinds"
          :native-value="kind"
          :data-testid="`holding-type-kind-${kind}`"
        >
          {{ WALLET_TYPES[kind].label }}
        </b-checkbox>
      </div>
    </b-field>
    <template #footer>
      <b-button outlined type="is-danger" :disabled="submitting" @click="emit('close')"
        >Cancelar</b-button
      >
      <b-button
        type="is-success"
        :disabled="!valid"
        :loading="submitting"
        data-testid="holding-type-submit"
        @click="submit"
      >
        {{ isEdit ? 'Salvar' : 'Criar' }}
      </b-button>
    </template>
  </AppModal>
</template>
