<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useDialog, useToast } from 'buefy'
import Card from '@/components/ui/Card.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { useTypesListStore } from '@/stores/typesList'
import { escapeHtml } from '@/utils/escapeHtml'
import { useAuthStore } from '@/stores/auth'
import type { AssetType } from '@/types'

type TypeKind = 'stock' | 'fund' | 'segment'

const dialog = useDialog()
const toast = useToast()
const typesListStore = useTypesListStore()
const auth = useAuthStore()

const KIND_CONFIG: Record<
  TypeKind,
  {
    label: string
    description: string
    addTitle: string
    emptyTitle: string
    emptyText: string
    newLabel: string
    noun: string
    list: () => AssetType[]
    add: (name: string) => Promise<AssetType>
    update: (id: string, name: string) => Promise<AssetType>
    remove: (id: string) => Promise<void>
  }
> = {
  stock: {
    label: 'Tipos de ação',
    description: 'Cadastrados antes de registrar uma ação (escolhidos no formulário).',
    addTitle: 'Novo tipo de ação',
    emptyTitle: 'Nenhum tipo ainda',
    emptyText: 'Crie o primeiro tipo para poder selecioná-lo ao registrar uma ação.',
    newLabel: 'Novo tipo',
    noun: 'tipo',
    list: () => typesListStore.stockTypes,
    add: (name) => typesListStore.addStockType(name),
    update: (id, name) => typesListStore.updateStockType(id, name),
    remove: (id) => typesListStore.removeStockType(id),
  },
  fund: {
    label: 'Tipos de fundo',
    description: 'Cadastrados antes de registrar um fundo (escolhidos no formulário).',
    addTitle: 'Novo tipo de fundo',
    emptyTitle: 'Nenhum tipo ainda',
    emptyText: 'Crie o primeiro tipo para poder selecioná-lo ao registrar um fundo.',
    newLabel: 'Novo tipo',
    noun: 'tipo',
    list: () => typesListStore.fundTypes,
    add: (name) => typesListStore.addFundType(name),
    update: (id, name) => typesListStore.updateFundType(id, name),
    remove: (id) => typesListStore.removeFundType(id),
  },
  segment: {
    label: 'Segmentos',
    description: 'Setores de atuação das ações (opcional no formulário).',
    addTitle: 'Novo segmento',
    emptyTitle: 'Nenhum segmento ainda',
    emptyText: 'Crie o primeiro segmento para poder selecioná-lo ao registrar uma ação.',
    newLabel: 'Novo segmento',
    noun: 'segmento',
    list: () => typesListStore.stockSegments,
    add: (name) => typesListStore.addStockSegment(name),
    update: (id, name) => typesListStore.updateStockSegment(id, name),
    remove: (id) => typesListStore.removeStockSegment(id),
  },
}

const KINDS: TypeKind[] = ['stock', 'fund', 'segment']

const activeKind = ref<TypeKind>('stock')

onMounted(() => {
  typesListStore.load()
})

function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1)
}

const activeConfig = computed(() => KIND_CONFIG[activeKind.value])
const activeTypes = computed(() => activeConfig.value.list())

function addType() {
  const config = activeConfig.value
  dialog.prompt({
    title: config.addTitle,
    message: 'Nome:',
    inputAttrs: { placeholder: `Nome do ${config.noun}` },
    confirmText: 'Criar',
    cancelText: 'Cancelar',
    onConfirm: async (name: string) => {
      const trimmedName = name.trim()
      if (!trimmedName) return
      await config.add(trimmedName)
      toast.open({ message: `${capitalize(config.noun)} criado.`, type: 'is-success' })
    },
  })
}

function renameType(type: AssetType) {
  const config = activeConfig.value
  dialog.prompt({
    title: `Renomear ${config.noun}`,
    message: 'Nome:',
    inputAttrs: { value: type.name, placeholder: `Nome do ${config.noun}` },
    confirmText: 'Salvar',
    cancelText: 'Cancelar',
    onConfirm: async (name: string) => {
      const trimmedName = name.trim()
      if (!trimmedName || trimmedName === type.name) return
      await config.update(type.id, trimmedName)
      toast.open({ message: `${capitalize(config.noun)} renomeado.`, type: 'is-success' })
    },
  })
}

function confirmRemoveType(type: AssetType) {
  const config = activeConfig.value
  dialog.confirm({
    title: `Remover ${config.noun}`,
    message: `Remover <strong>${escapeHtml(type.name)}</strong>? Esta ação <strong>não pode ser desfeita</strong>.`,
    type: 'is-danger',
    hasIcon: true,
    confirmText: 'Remover',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      await config.remove(type.id)
      toast.open({ message: `${capitalize(config.noun)} removido.`, type: 'is-success' })
    },
  })
}
</script>

<template>
  <div class="page">
    <b-loading :is-full-page="false" :model-value="typesListStore.loading" />

    <div class="page-head-row">
      <div>
        <h1 class="page-title">Tipos</h1>
        <p class="page-desc">
          Gerencie os tipos de ação, de fundo e os segmentos usados no cadastro.
        </p>
      </div>
    </div>

    <div class="inv-controls">
      <div class="seg-tabs">
        <button
          v-for="kind in KINDS"
          :key="kind"
          class="seg-tab"
          :class="{ active: activeKind === kind }"
          @click="activeKind = kind"
        >
          {{ KIND_CONFIG[kind].label }}
        </button>
      </div>

      <div v-if="auth.isAdmin" class="inv-toolbar">
        <b-button type="is-primary" class="has-text-light" icon-left="plus" @click="addType">
          {{ activeConfig.newLabel }}
        </b-button>
      </div>
    </div>

    <p class="set-desc">{{ activeConfig.description }}</p>

    <EmptyState
      v-if="typesListStore.loaded && activeTypes.length === 0"
      icon="shape-outline"
      :title="activeConfig.emptyTitle"
      :text="activeConfig.emptyText"
    >
      <template v-if="auth.isAdmin" #action>
        <b-button type="is-primary" class="has-text-light" icon-left="plus" @click="addType">
          {{ activeConfig.newLabel }}
        </b-button>
      </template>
    </EmptyState>

    <Card v-else class="table-card">
      <div class="table-wrap">
        <div class="table-scroll">
          <table class="inv-table">
            <thead>
              <tr>
                <th>Nome</th>
                <th class="c-num">Investimentos</th>
                <th v-if="auth.isAdmin" class="c-act" style="width: 90px">Ações</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="type in activeTypes" :key="type.id">
                <td class="cell-strong">{{ type.name }}</td>
                <td class="c-num">{{ type.usageCount }}</td>
                <td v-if="auth.isAdmin" class="c-act">
                  <div style="display: flex; gap: 6px; justify-content: center">
                    <b-button
                      outlined
                      type="is-primary"
                      size="is-small"
                      icon-left="pencil"
                      @click="renameType(type)"
                    />
                    <b-tooltip
                      v-if="type.usageCount > 0"
                      :label="`Não é possível remover: ${activeConfig.noun} em uso`"
                      position="is-left"
                    >
                      <b-button outlined type="is-danger" size="is-small" icon-left="delete" disabled />
                    </b-tooltip>
                    <b-button
                      v-else
                      outlined
                      type="is-danger"
                      size="is-small"
                      icon-left="delete"
                      @click="confirmRemoveType(type)"
                    />
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </Card>
  </div>
</template>
