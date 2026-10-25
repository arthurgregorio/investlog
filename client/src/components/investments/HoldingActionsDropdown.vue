<script setup lang="ts">
defineProps<{
  isFund: boolean
  isFrozen: boolean
  isAdmin: boolean
}>()

const emit = defineEmits<{
  'update-price': []
  'edit-tags': []
  withdraw: []
  reinvest: []
  move: []
  'toggle-frozen': []
  remove: []
}>()
</script>

<template>
  <b-dropdown aria-role="list" position="is-bottom-left" data-testid="holding-actions">
    <template #trigger>
      <b-button icon-right="menu-down" expanded>Mais ações</b-button>
    </template>

    <p class="actions-group-label" aria-hidden="true">Movimentar</p>
    <b-dropdown-item aria-role="listitem" data-testid="holding-withdraw" @click="emit('withdraw')">
      <b-icon icon="cash-minus" size="is-small" />
      {{ isFund ? 'Resgatar' : 'Vender / resgatar' }}
    </b-dropdown-item>
    <b-dropdown-item aria-role="listitem" data-testid="holding-reinvest" @click="emit('reinvest')">
      <b-icon icon="autorenew" size="is-small" /> Reinvestir
    </b-dropdown-item>
    <b-dropdown-item aria-role="listitem" data-testid="holding-move" @click="emit('move')">
      <b-icon icon="swap-horizontal" size="is-small" /> Mover para outra carteira
    </b-dropdown-item>

    <hr class="dropdown-divider" />
    <p class="actions-group-label" aria-hidden="true">Editar</p>
    <b-dropdown-item aria-role="listitem" @click="emit('update-price')">
      <b-icon icon="pencil" size="is-small" />
      {{ isFund ? 'Atualizar valor atual' : 'Atualizar preço' }}
    </b-dropdown-item>
    <b-dropdown-item
      aria-role="listitem"
      data-testid="holding-edit-tags"
      @click="emit('edit-tags')"
    >
      <b-icon icon="tag-outline" size="is-small" /> Editar tags
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
        <b-icon icon="delete" size="is-small" /> Remover investimento
      </b-dropdown-item>
    </template>
  </b-dropdown>
</template>

<style scoped>
.actions-group-label {
  padding: 6px 16px 2px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--text-muted);
}
</style>
