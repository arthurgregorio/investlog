<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AssetTypeTable from '@/components/settings/AssetTypeTable.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import SegmentedTabs from '@/components/ui/SegmentedTabs.vue'
import {
  ASSET_TYPE_KIND_TABS,
  useAssetTypeActions,
  type AssetTypeKind,
} from '@/composables/useAssetTypeActions'
import { useTypesListStore } from '@/stores/typesList'
import { useAuthStore } from '@/stores/auth'

const typesListStore = useTypesListStore()
const auth = useAuthStore()

const activeKind = ref<AssetTypeKind>('stock')
const { copy, types, addType, renameType, confirmRemoveType } = useAssetTypeActions(activeKind)

onMounted(() => {
  typesListStore.load()
})
</script>

<template>
  <div class="page">
    <b-loading :is-full-page="false" :model-value="typesListStore.loading" />

    <PageHeader
      title="Tipos"
      description="Gerencie os tipos de ação, de fundo e os segmentos usados no cadastro."
    />

    <div class="inv-controls">
      <SegmentedTabs v-model="activeKind" :options="ASSET_TYPE_KIND_TABS" list-label="Tipos" />

      <div v-if="auth.isAdmin" class="inv-toolbar">
        <b-button type="is-primary" icon-left="plus" @click="addType">
          {{ copy.newLabel }}
        </b-button>
      </div>
    </div>

    <p class="set-desc">{{ copy.description }}</p>

    <EmptyState
      v-if="typesListStore.loaded && types.length === 0"
      icon="shape-outline"
      :title="copy.emptyTitle"
      :text="copy.emptyText"
    >
      <template v-if="auth.isAdmin" #action>
        <b-button type="is-primary" icon-left="plus" @click="addType">
          {{ copy.newLabel }}
        </b-button>
      </template>
    </EmptyState>

    <AssetTypeTable
      v-else
      :types="types"
      :noun="copy.noun"
      :editable="auth.isAdmin"
      @rename="renameType"
      @remove="confirmRemoveType"
    />
  </div>
</template>
