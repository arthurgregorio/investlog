import { computed, type Ref } from 'vue'
import { useDialog, useToast } from 'buefy'
import { safeHtml, useConfirmDialog } from '@/composables/useConfirmDialog'
import { useTypesListStore } from '@/stores/typesList'
import type { AssetType } from '@/types'

export type AssetTypeKind = 'stock' | 'fund' | 'segment'

interface AssetTypeKindCopy {
  label: string
  description: string
  addTitle: string
  emptyTitle: string
  emptyText: string
  newLabel: string
  noun: string
}

interface AssetTypeOperations {
  list: () => AssetType[]
  add: (name: string) => Promise<AssetType>
  update: (id: string, name: string) => Promise<AssetType>
  remove: (id: string) => Promise<void>
}

export const ASSET_TYPE_KIND_COPY: Record<AssetTypeKind, AssetTypeKindCopy> = {
  stock: {
    label: 'Tipos de ação',
    description: 'Cadastrados antes de registrar uma ação (escolhidos no formulário).',
    addTitle: 'Novo tipo de ação',
    emptyTitle: 'Nenhum tipo ainda',
    emptyText: 'Crie o primeiro tipo para poder selecioná-lo ao registrar uma ação.',
    newLabel: 'Novo tipo',
    noun: 'tipo',
  },
  fund: {
    label: 'Tipos de fundo',
    description: 'Cadastrados antes de registrar um fundo (escolhidos no formulário).',
    addTitle: 'Novo tipo de fundo',
    emptyTitle: 'Nenhum tipo ainda',
    emptyText: 'Crie o primeiro tipo para poder selecioná-lo ao registrar um fundo.',
    newLabel: 'Novo tipo',
    noun: 'tipo',
  },
  segment: {
    label: 'Segmentos',
    description: 'Setores de atuação das ações (opcional no formulário).',
    addTitle: 'Novo segmento',
    emptyTitle: 'Nenhum segmento ainda',
    emptyText: 'Crie o primeiro segmento para poder selecioná-lo ao registrar uma ação.',
    newLabel: 'Novo segmento',
    noun: 'segmento',
  },
}

export const ASSET_TYPE_KIND_TABS = (['stock', 'fund', 'segment'] as const).map((kind) => ({
  value: kind,
  label: ASSET_TYPE_KIND_COPY[kind].label,
}))

function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1)
}

export function useAssetTypeActions(kind: Ref<AssetTypeKind>) {
  const dialog = useDialog()
  const toast = useToast()
  const { confirm } = useConfirmDialog()
  const typesListStore = useTypesListStore()

  const operations: Record<AssetTypeKind, AssetTypeOperations> = {
    stock: {
      list: () => typesListStore.stockTypes,
      add: (name) => typesListStore.addStockType(name),
      update: (id, name) => typesListStore.updateStockType(id, name),
      remove: (id) => typesListStore.removeStockType(id),
    },
    fund: {
      list: () => typesListStore.fundTypes,
      add: (name) => typesListStore.addFundType(name),
      update: (id, name) => typesListStore.updateFundType(id, name),
      remove: (id) => typesListStore.removeFundType(id),
    },
    segment: {
      list: () => typesListStore.stockSegments,
      add: (name) => typesListStore.addStockSegment(name),
      update: (id, name) => typesListStore.updateStockSegment(id, name),
      remove: (id) => typesListStore.removeStockSegment(id),
    },
  }

  const copy = computed(() => ASSET_TYPE_KIND_COPY[kind.value])
  const types = computed(() => operations[kind.value].list())

  function notify(verb: string) {
    toast.open({ message: `${capitalize(copy.value.noun)} ${verb}.`, type: 'is-success' })
  }

  function addType() {
    const { noun, addTitle } = copy.value
    const { add } = operations[kind.value]
    dialog.prompt({
      title: addTitle,
      message: 'Nome:',
      inputAttrs: { placeholder: `Nome do ${noun}` },
      confirmText: 'Criar',
      cancelText: 'Cancelar',
      onConfirm: async (name: string) => {
        const trimmedName = name.trim()
        if (!trimmedName) return
        await add(trimmedName)
        notify('criado')
      },
    })
  }

  function renameType(type: AssetType) {
    const { noun } = copy.value
    const { update } = operations[kind.value]
    dialog.prompt({
      title: `Renomear ${noun}`,
      message: 'Nome:',
      inputAttrs: { value: type.name, placeholder: `Nome do ${noun}` },
      confirmText: 'Salvar',
      cancelText: 'Cancelar',
      onConfirm: async (name: string) => {
        const trimmedName = name.trim()
        if (!trimmedName || trimmedName === type.name) return
        await update(type.id, trimmedName)
        notify('renomeado')
      },
    })
  }

  function confirmRemoveType(type: AssetType) {
    const { remove } = operations[kind.value]
    confirm({
      title: `Remover ${copy.value.noun}`,
      message: safeHtml`Remover <strong>${type.name}</strong>? Esta ação <strong>não pode ser desfeita</strong>.`,
      confirmText: 'Remover',
      onConfirm: async () => {
        await remove(type.id)
        notify('removido')
      },
    })
  }

  return { copy, types, addType, renameType, confirmRemoveType }
}
