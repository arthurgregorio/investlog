<script setup lang="ts">
import Card from '@/components/ui/Card.vue'
import type { AssetType } from '@/types'

defineProps<{ types: AssetType[]; noun: string; editable: boolean }>()

const emit = defineEmits<{ rename: [type: AssetType]; remove: [type: AssetType] }>()
</script>

<template>
  <Card class="table-card">
    <div class="table-wrap">
      <div class="table-scroll">
        <table class="inv-table">
          <thead>
            <tr>
              <th>Nome</th>
              <th class="c-num has-text-right">Investimentos</th>
              <th v-if="editable" class="c-act is-wide">Ações</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="type in types" :key="type.id">
              <td class="has-text-weight-bold">{{ type.name }}</td>
              <td class="c-num has-text-right">{{ type.usageCount }}</td>
              <td v-if="editable" class="c-act">
                <div class="is-flex is-gap-1 is-justify-content-center">
                  <b-button
                    outlined
                    type="is-primary"
                    size="is-small"
                    icon-left="pencil"
                    @click="emit('rename', type)"
                  />
                  <b-tooltip
                    v-if="type.usageCount > 0"
                    :label="`Não é possível remover: ${noun} em uso`"
                    position="is-left"
                  >
                    <b-button
                      outlined
                      type="is-danger"
                      size="is-small"
                      icon-left="delete"
                      disabled
                    />
                  </b-tooltip>
                  <b-button
                    v-else
                    outlined
                    type="is-danger"
                    size="is-small"
                    icon-left="delete"
                    @click="emit('remove', type)"
                  />
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </Card>
</template>
