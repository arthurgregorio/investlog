<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useDialog, useToast } from 'buefy'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import PasswordResetModal from '@/components/forms/PasswordResetModal.vue'
import { useUsersAdminStore } from '@/stores/usersAdmin'
import { useAuthStore } from '@/stores/auth'
import type { UserAdminResponse, UserRole } from '@/types'

const toast = useToast()
const dialog = useDialog()
const usersAdminStore = useUsersAdminStore()
const auth = useAuthStore()

const passwordResetTarget = ref<{ id: string; name: string } | null>(null)

onMounted(() => {
  usersAdminStore.load()
})

function isSelf(email: string): boolean {
  return auth.session?.email === email
}

function hasActions(user: UserAdminResponse): boolean {
  return user.status !== 'APPROVED' || !isSelf(user.email)
}

async function approveUser(id: string) {
  await usersAdminStore.approve(id)
  toast.open({ message: 'Usuário aprovado.', type: 'is-success' })
}

async function blockUser(id: string) {
  await usersAdminStore.block(id)
  toast.open({ message: 'Usuário bloqueado.', type: 'is-success' })
}

async function unblockUser(id: string) {
  await usersAdminStore.unblock(id)
  toast.open({ message: 'Usuário desbloqueado.', type: 'is-success' })
}

function confirmRoleChange(id: string, name: string, currentRole: UserRole) {
  const nextRole: UserRole = currentRole === 'ADMIN' ? 'USER' : 'ADMIN'
  dialog.confirm({
    title:
      nextRole === 'ADMIN' ? 'Promover a administrador' : 'Remover privilégios de administrador',
    message: `Alterar o papel de <strong>${name}</strong> para <strong>${nextRole}</strong>?`,
    confirmText: 'Confirmar',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      await usersAdminStore.changeRole(id, nextRole)
      toast.open({ message: 'Papel atualizado.', type: 'is-success' })
    },
  })
}

function confirmTotpReset(id: string, name: string) {
  dialog.confirm({
    title: 'Redefinir autenticação em duas etapas',
    message: `<strong>${name}</strong> precisará configurar a autenticação novamente no próximo login.`,
    type: 'is-danger',
    hasIcon: true,
    confirmText: 'Redefinir',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      await usersAdminStore.resetTotp(id)
      toast.open({ message: 'Autenticação em duas etapas redefinida.', type: 'is-success' })
    },
  })
}

function confirmDeleteUser(id: string, name: string) {
  dialog.confirm({
    title: 'Remover usuário',
    message: `Remover <strong>${name}</strong>? Esta ação <strong>não pode ser desfeita</strong>.`,
    type: 'is-danger',
    hasIcon: true,
    confirmText: 'Remover',
    cancelText: 'Cancelar',
    onConfirm: async () => {
      await usersAdminStore.remove(id)
      toast.open({ message: 'Usuário removido.', type: 'is-success' })
    },
  })
}
</script>

<template>
  <div class="page">
    <b-loading :is-full-page="false" :active="usersAdminStore.loading" />

    <div class="page-head page-head-row">
      <div>
        <h1 class="page-title">Usuários</h1>
        <p class="page-desc">Aprove, bloqueie ou gerencie o acesso de usuários locais.</p>
      </div>
    </div>

    <div class="entity-grid">
      <Card v-for="user in usersAdminStore.users" :key="user.id" class="entity-card">
        <CardBody>
          <div class="entity-head">
            <div class="entity-titles">
              <div class="entity-name">{{ user.name }}</div>
              <div class="entity-tags">
                <b-tag :type="user.role === 'ADMIN' ? 'is-primary' : 'is-dark'">
                  {{ user.role }}
                </b-tag>
                <b-tag
                  :type="
                    user.status === 'APPROVED'
                      ? 'is-success'
                      : user.status === 'BLOCKED'
                        ? 'is-danger'
                        : 'is-warning'
                  "
                >
                  {{ user.status }}
                </b-tag>
                <b-tag v-if="user.totpEnabled" type="is-info">2FA ativo</b-tag>
              </div>
            </div>
          </div>
          <div class="entity-foot">
            <p class="set-desc" style="margin: 0">{{ user.email }}</p>
            <b-dropdown
              v-if="hasActions(user)"
              aria-role="list"
              position="is-bottom-left"
              :triggers="['hover', 'click']"
            >
              <template #trigger>
                <span class="action-trigger"
                  >Ações <b-icon icon="menu-down" size="is-small"
                /></span>
              </template>

              <b-dropdown-item
                v-if="user.status !== 'APPROVED'"
                aria-role="listitem"
                @click="approveUser(user.id)"
              >
                <b-icon icon="check" size="is-small" /> Aprovar
              </b-dropdown-item>

              <template v-if="!isSelf(user.email)">
                <b-dropdown-item
                  v-if="user.status === 'APPROVED'"
                  aria-role="listitem"
                  @click="blockUser(user.id)"
                >
                  <b-icon icon="lock-outline" size="is-small" /> Bloquear
                </b-dropdown-item>
                <b-dropdown-item
                  v-if="user.status === 'BLOCKED'"
                  aria-role="listitem"
                  @click="unblockUser(user.id)"
                >
                  <b-icon icon="lock-open-outline" size="is-small" /> Desbloquear
                </b-dropdown-item>
                <b-dropdown-item
                  aria-role="listitem"
                  @click="confirmRoleChange(user.id, user.name, user.role)"
                >
                  <b-icon icon="account-convert" size="is-small" />
                  {{ user.role === 'ADMIN' ? 'Remover admin' : 'Promover a admin' }}
                </b-dropdown-item>
                <b-dropdown-item aria-role="listitem" @click="confirmTotpReset(user.id, user.name)">
                  <b-icon icon="lock-reset" size="is-small" /> Redefinir 2FA
                </b-dropdown-item>
                <b-dropdown-item
                  v-if="user.authProvider === 'LOCAL'"
                  aria-role="listitem"
                  @click="passwordResetTarget = { id: user.id, name: user.name }"
                >
                  <b-icon icon="key-outline" size="is-small" /> Redefinir senha
                </b-dropdown-item>
                <hr class="dropdown-divider" />
                <b-dropdown-item
                  aria-role="listitem"
                  class="has-text-danger"
                  @click="confirmDeleteUser(user.id, user.name)"
                >
                  <b-icon icon="delete-outline" size="is-small" /> Remover
                </b-dropdown-item>
              </template>
            </b-dropdown>
          </div>
        </CardBody>
      </Card>
    </div>

    <PasswordResetModal
      v-if="passwordResetTarget"
      :user-id="passwordResetTarget.id"
      :user-name="passwordResetTarget.name"
      @close="passwordResetTarget = null"
    />
  </div>
</template>
