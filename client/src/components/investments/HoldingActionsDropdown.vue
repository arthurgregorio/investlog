<script setup lang="ts">
const props = defineProps<{
  isFund: boolean
  isStock: boolean
  isFrozen: boolean
  isAdmin: boolean
}>()

const emit = defineEmits<{
  'add-position': []
  'update-price': []
  'set-segment': []
  withdraw: []
  reinvest: []
  move: []
  'toggle-frozen': []
  remove: []
}>()

function addPosition() {
  if (!props.isFrozen) emit('add-position')
}
</script>

<template>
  <b-dropdown
    aria-role="list"
    position="is-bottom-left"
    append-to-body
    data-testid="holding-actions"
  >
    <template #trigger>
      <b-button size="is-small" icon-right="menu-down">Ações</b-button>
    </template>

    <b-dropdown-item
      aria-role="listitem"
      :disabled="isFrozen"
      data-testid="holding-add-position"
      @click="addPosition"
    >
      <b-icon icon="plus" size="is-small" />
      {{ isFund ? 'Registrar novo aporte' : 'Registrar nova compra' }}
    </b-dropdown-item>
    <b-dropdown-item aria-role="listitem" @click="emit('update-price')">
      <b-icon icon="pencil" size="is-small" />
      {{ isFund ? 'Atualizar valor atual' : 'Atualizar preço' }}
    </b-dropdown-item>
    <b-dropdown-item
      v-if="isStock"
      aria-role="listitem"
      data-testid="holding-set-segment"
      @click="emit('set-segment')"
    >
      <b-icon icon="tag-outline" size="is-small" /> Definir segmento
    </b-dropdown-item>
    <b-dropdown-item aria-role="listitem" @click="emit('withdraw')">
      <b-icon icon="cash-minus" size="is-small" /> Resgatar
    </b-dropdown-item>
    <b-dropdown-item aria-role="listitem" data-testid="holding-reinvest" @click="emit('reinvest')">
      <b-icon icon="autorenew" size="is-small" /> Reinvestir
    </b-dropdown-item>
    <b-dropdown-item aria-role="listitem" data-testid="holding-move" @click="emit('move')">
      <b-icon icon="swap-horizontal" size="is-small" /> Mover
    </b-dropdown-item>
    <b-dropdown-item
      aria-role="listitem"
      data-testid="holding-freeze"
      @click="emit('toggle-frozen')"
    >
      <b-icon :icon="isFrozen ? 'snowflake-off' : 'snowflake'" size="is-small" />
      {{ isFrozen ? 'Descongelar' : 'Congelar' }}
    </b-dropdown-item>
    <template v-if="isAdmin">
      <hr class="dropdown-divider" />
      <b-dropdown-item
        aria-role="listitem"
        class="has-text-danger"
        data-testid="holding-remove"
        @click="emit('remove')"
      >
        <b-icon icon="delete" size="is-small" /> Remover
      </b-dropdown-item>
    </template>
  </b-dropdown>
</template>
