<script setup lang="ts">
import { onMounted, ref } from 'vue'
import PasswordResetModal from '@/components/forms/PasswordResetModal.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import UserCard from '@/components/users/UserCard.vue'
import { useUsersAdminStore } from '@/stores/usersAdmin'
import type { UserAdminResponse } from '@/types'

const usersAdminStore = useUsersAdminStore()

const passwordResetTarget = ref<UserAdminResponse | null>(null)

onMounted(() => {
  usersAdminStore.load()
})
</script>

<template>
  <div class="page">
    <b-loading :is-full-page="false" :model-value="usersAdminStore.loading" />

    <PageHeader
      title="Usuários"
      description="Aprove, bloqueie ou gerencie o acesso de usuários locais."
    />

    <div class="entity-grid">
      <UserCard
        v-for="user in usersAdminStore.users"
        :key="user.id"
        :user="user"
        @reset-password="passwordResetTarget = $event"
      />
    </div>

    <PasswordResetModal
      v-if="passwordResetTarget"
      :user-id="passwordResetTarget.id"
      :user-name="passwordResetTarget.name"
      @close="passwordResetTarget = null"
    />
  </div>
</template>
