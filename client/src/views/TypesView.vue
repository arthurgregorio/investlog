<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useDialog, useToast } from 'buefy'
import Card from '@/components/ui/Card.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { useTypesListStore } from '@/stores/typesList'
import { useAuthStore } from '@/stores/auth'
import type { AssetType } from '@/types'

type TypeKind = 'stock' | 'fund'

const KIND_CONFIG: Record<
  TypeKind,
  { label: string; description: string; addTitle: string; emptyText: string }
> = {
  stock: {
    label: 'Tipos de ação',
    description: 'Cadastrados antes de registrar uma ação (escolhidos no formulário).',
    addTitle: 'Novo tipo de ação',
    emptyText: 'Crie o primeiro tipo para poder selecioná-lo ao registrar uma ação.',
  },
  fund: {
    label: 'Tipos de fundo',
    description: 'Cadastrados antes de registrar um fundo (escolhidos no formulário).',
    addTitle: 'Novo tipo de fundo',
    emptyText: 'Crie o primeiro tipo para poder selecioná-lo ao registrar um fundo.',
  },
}

const dialog = useDialog()
const toast = useToast()
const typesListStore = useTypesListStore()
const auth = useAuthStore()

const activeKind = ref<TypeKind>('stock')

onMounted(() => {
  typesListStore.load()
})

const activeTypes = computed(() =>
  activeKind.value === 'stock' ? typesListStore.stockTypes : typesListStore.fundTypes,
)

function addType() {
  const kind = activeKind.value
  dialog.prompt({
    title: KIND_CONFIG[kind].addTitle,
    message: 'Nome:',
    inputAttrs: { placeholder: 'Nome do tipo' },
    confirmText: 'Criar',
    cancelText: 'Cancelar',
    onConfirm: async (name: string) => {
      const trimmedName = name.trim()
      if (!trimmedName) return
      if (kind === 'stock') {
        await typesListStore.addStockType(trimmedName)
      } else {
        await typesListStore.addFundType(trimmedName)
      }
      toast.open({ message: 'Tipo criado.', type: 'is-success' })
    },
  })
}

function renameType(type: AssetType) {
  const kind = activeKind.value
  dialog.prompt({
    title: 'Renomear tipo',
    message: 'Nome:',
    inputAttrs: { value: type.name, placeholder: 'Nome do tipo' },
    confirmText: 'Salvar',
    cancelText: 'Cancelar',
    onConfirm: async (name: string) => {
      const trimmedName = name.trim()
      if (!trimmedName || trimmedName === type.name) return
      if (kind === 'stock') {
        await typesListStore.updateStockType(type.id, trimmedName)
      } else {
        await typesListStore.updateFundType(type.id, trimmedName)
      }
      toast.open({ message: 'Tipo renomeado.', type: 'is-success' })
    },
  })
}

function confirmRemoveType(type: AssetType) {
  const kind = activeKind.value
  dialog.confirm({
    title: 'Remover tipo',
    message: `Remover <strong>${type.name}</strong>? Esta ação <strong>não pode ser desfeita</strong>.`,
    type: 'is-danger',
    hasIcon: true,
    confirmText: 'Remover',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      if (kind === 'stock') {
        await typesListStore.removeStockType(type.id)
      } else {
        await typesListStore.removeFundType(type.id)
      }
      toast.open({ message: 'Tipo removido.', type: 'is-success' })
    },
  })
}
</script>

<template>
  <div class="page">
    <b-loading :is-full-page="false" :active="typesListStore.loading" />

    <div class="page-head-row">
      <div>
        <h1 class="page-title">Tipos</h1>
        <p class="page-desc">Gerencie os tipos de ação e de fundo usados no cadastro.</p>
      </div>
    </div>

    <div class="inv-controls">
      <div class="seg-tabs">
        <button
          class="seg-tab"
          :class="{ active: activeKind === 'stock' }"
          @click="activeKind = 'stock'"
        >
          Tipos de ação
        </button>
        <button
          class="seg-tab"
          :class="{ active: activeKind === 'fund' }"
          @click="activeKind = 'fund'"
        >
          Tipos de fundo
        </button>
      </div>

      <div v-if="auth.isAdmin" class="inv-toolbar">
        <b-button type="is-primary" class="has-text-light" icon-left="plus" @click="addType">
          Novo tipo
        </b-button>
      </div>
    </div>

    <p class="set-desc">{{ KIND_CONFIG[activeKind].description }}</p>

    <EmptyState
      v-if="typesListStore.loaded && activeTypes.length === 0"
      icon="shape-outline"
      title="Nenhum tipo ainda"
      :text="KIND_CONFIG[activeKind].emptyText"
    >
      <template v-if="auth.isAdmin" #action>
        <b-button type="is-primary" class="has-text-light" icon-left="plus" @click="addType">
          Novo tipo
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
                      label="Não é possível remover: tipo em uso"
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
