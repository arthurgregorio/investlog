import { computed, ref, type Ref } from 'vue'
import { useDialog, useToast } from 'buefy'
import { safeHtml, useConfirmDialog } from '@/composables/useConfirmDialog'
import { useTypesListStore } from '@/stores/typesList'
import type { AssetType, HoldingType } from '@/types'

export type AssetTypeKind = 'type' | 'segment'

interface AssetTypeKindCopy {
  label: string
  description: string
  emptyTitle: string
  emptyText: string
  newLabel: string
  noun: string
}

export const ASSET_TYPE_KIND_COPY: Record<AssetTypeKind, AssetTypeKindCopy> = {
  type: {
    label: 'Tipos',
    description:
      'Cadastrados antes de registrar um investimento (escolhidos no formulário). Cada tipo vale para as classes marcadas.',
    emptyTitle: 'Nenhum tipo ainda',
    emptyText: 'Crie o primeiro tipo para poder selecioná-lo ao registrar um investimento.',
    newLabel: 'Novo tipo',
    noun: 'tipo',
  },
  segment: {
    label: 'Segmentos',
    description: 'Setores de atuação dos investimentos (opcional no formulário).',
    emptyTitle: 'Nenhum segmento ainda',
    emptyText: 'Crie o primeiro segmento para poder selecioná-lo ao registrar um investimento.',
    newLabel: 'Novo segmento',
    noun: 'segmento',
  },
}

export const ASSET_TYPE_KIND_TABS = (['type', 'segment'] as const).map((kind) => ({
  value: kind,
  label: ASSET_TYPE_KIND_COPY[kind].label,
}))

export interface HoldingTypeModalState {
  holdingType: HoldingType | null
}

function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1)
}

export function useAssetTypeActions(kind: Ref<AssetTypeKind>) {
  const dialog = useDialog()
  const toast = useToast()
  const { confirm } = useConfirmDialog()
  const typesListStore = useTypesListStore()

  const holdingTypeModal = ref<HoldingTypeModalState | null>(null)

  const copy = computed(() => ASSET_TYPE_KIND_COPY[kind.value])
  const types = computed<(AssetType | HoldingType)[]>(() =>
    kind.value === 'type' ? typesListStore.holdingTypes : typesListStore.segments,
  )

  function notify(verb: string) {
    toast.open({ message: `${capitalize(copy.value.noun)} ${verb}.`, type: 'is-success' })
  }

  function addType() {
    if (kind.value === 'type') {
      holdingTypeModal.value = { holdingType: null }
      return
    }
    dialog.prompt({
      title: 'Novo segmento',
      message: 'Nome:',
      inputAttrs: { placeholder: 'Nome do segmento' },
      confirmText: 'Criar',
      cancelText: 'Cancelar',
      onConfirm: async (name: string) => {
        const trimmedName = name.trim()
        if (!trimmedName) return
        await typesListStore.addSegment(trimmedName)
        notify('criado')
      },
    })
  }

  function renameType(type: AssetType | HoldingType) {
    if ('kinds' in type) {
      holdingTypeModal.value = { holdingType: type }
      return
    }
    dialog.prompt({
      title: 'Renomear segmento',
      message: 'Nome:',
      inputAttrs: { value: type.name, placeholder: 'Nome do segmento' },
      confirmText: 'Salvar',
      cancelText: 'Cancelar',
      onConfirm: async (name: string) => {
        const trimmedName = name.trim()
        if (!trimmedName || trimmedName === type.name) return
        await typesListStore.updateSegment(type.id, trimmedName)
        notify('renomeado')
      },
    })
  }

  function confirmRemoveType(type: AssetType | HoldingType) {
    confirm({
      title: `Remover ${copy.value.noun}`,
      message: safeHtml`Remover <strong>${type.name}</strong>? Esta ação <strong>não pode ser desfeita</strong>.`,
      confirmText: 'Remover',
      onConfirm: async () => {
        if ('kinds' in type) {
          await typesListStore.removeHoldingType(type.id)
        } else {
          await typesListStore.removeSegment(type.id)
        }
        notify('removido')
      },
    })
  }

  function closeHoldingTypeModal() {
    holdingTypeModal.value = null
  }

  return {
    copy,
    types,
    holdingTypeModal,
    addType,
    renameType,
    confirmRemoveType,
    closeHoldingTypeModal,
  }
}
