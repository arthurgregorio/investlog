<script setup lang="ts">
import { computed } from 'vue'
import { useToast } from 'buefy'
import EntityCard from '@/components/ui/EntityCard.vue'
import { safeHtml, useConfirmDialog } from '@/composables/useConfirmDialog'
import { useAuthStore } from '@/stores/auth'
import { useUsersAdminStore } from '@/stores/usersAdmin'
import { statusTagType } from '@/utils/userStatus'
import type { UserAdminResponse, UserRole } from '@/types'

const props = defineProps<{ user: UserAdminResponse }>()

const emit = defineEmits<{ 'reset-password': [user: UserAdminResponse] }>()

const toast = useToast()
const { confirm } = useConfirmDialog()
const usersAdminStore = useUsersAdminStore()
const auth = useAuthStore()

const isSelf = computed(() => auth.session?.email === props.user.email)
const hasActions = computed(() => props.user.status !== 'APPROVED' || !isSelf.value)

async function runAction(action: (id: string) => Promise<unknown>, message: string) {
  await action(props.user.id)
  toast.open({ message, type: 'is-success' })
}

function confirmRoleChange() {
  const nextRole: UserRole = props.user.role === 'ADMIN' ? 'USER' : 'ADMIN'
  confirm({
    title:
      nextRole === 'ADMIN' ? 'Promover a administrador' : 'Remover privilégios de administrador',
    message: safeHtml`Alterar o papel de <strong>${props.user.name}</strong> para <strong>${nextRole}</strong>?`,
    confirmText: 'Confirmar',
    danger: false,
    onConfirm: () =>
      runAction((id) => usersAdminStore.changeRole(id, nextRole), 'Papel atualizado.'),
  })
}

function confirmTotpReset() {
  confirm({
    title: 'Redefinir autenticação em duas etapas',
    message: safeHtml`<strong>${props.user.name}</strong> precisará configurar a autenticação novamente no próximo login.`,
    confirmText: 'Redefinir',
    onConfirm: () =>
      runAction(usersAdminStore.resetTotp, 'Autenticação em duas etapas redefinida.'),
  })
}

function confirmDelete() {
  confirm({
    title: 'Remover usuário',
    message: safeHtml`Remover <strong>${props.user.name}</strong>? Esta ação <strong>não pode ser desfeita</strong>.`,
    confirmText: 'Remover',
    onConfirm: () => runAction(usersAdminStore.remove, 'Usuário removido.'),
  })
}
</script>

<template>
  <EntityCard :name="user.name" class="mb-0">
    <template #tags>
      <b-tag :type="user.role === 'ADMIN' ? 'is-primary' : 'is-dark'">{{ user.role }}</b-tag>
      <b-tag :type="statusTagType[user.status]">{{ user.status }}</b-tag>
      <b-tag v-if="user.totpEnabled" type="is-info">2FA ativo</b-tag>
    </template>

    <template #foot>
      <p class="set-desc m-0">{{ user.email }}</p>
      <b-dropdown
        v-if="hasActions"
        aria-role="list"
        position="is-bottom-left"
        :triggers="['hover', 'click']"
      >
        <template #trigger>
          <span class="action-trigger">Ações <b-icon icon="menu-down" size="is-small" /></span>
        </template>

        <b-dropdown-item
          v-if="user.status === 'PENDING'"
          aria-role="listitem"
          @click="runAction(usersAdminStore.approve, 'Usuário aprovado.')"
        >
          <b-icon icon="check" size="is-small" /> Aprovar
        </b-dropdown-item>

        <template v-if="!isSelf">
          <b-dropdown-item
            v-if="user.status === 'APPROVED'"
            aria-role="listitem"
            @click="runAction(usersAdminStore.block, 'Usuário bloqueado.')"
          >
            <b-icon icon="lock-outline" size="is-small" /> Bloquear
          </b-dropdown-item>
          <b-dropdown-item
            v-if="user.status === 'BLOCKED'"
            aria-role="listitem"
            @click="runAction(usersAdminStore.unblock, 'Usuário desbloqueado.')"
          >
            <b-icon icon="lock-open-outline" size="is-small" /> Desbloquear
          </b-dropdown-item>
          <b-dropdown-item aria-role="listitem" @click="confirmRoleChange">
            <b-icon icon="account-convert" size="is-small" />
            {{ user.role === 'ADMIN' ? 'Remover admin' : 'Promover a admin' }}
          </b-dropdown-item>
          <b-dropdown-item aria-role="listitem" @click="confirmTotpReset">
            <b-icon icon="lock-reset" size="is-small" /> Redefinir 2FA
          </b-dropdown-item>
          <b-dropdown-item
            v-if="user.authProvider === 'LOCAL'"
            aria-role="listitem"
            @click="emit('reset-password', user)"
          >
            <b-icon icon="key-outline" size="is-small" /> Redefinir senha
          </b-dropdown-item>
          <hr class="dropdown-divider" />
          <b-dropdown-item aria-role="listitem" class="has-text-danger" @click="confirmDelete">
            <b-icon icon="delete-outline" size="is-small" /> Remover
          </b-dropdown-item>
        </template>
      </b-dropdown>
    </template>
  </EntityCard>
</template>

<style scoped>
.action-trigger {
  display: inline-flex;
  align-items: center;
  gap: 1px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--primary);
  cursor: pointer;
}

.action-trigger:hover {
  color: var(--primary-d);
}
</style>
