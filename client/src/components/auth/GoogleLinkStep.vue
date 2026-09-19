<script setup lang="ts">
import { ref } from 'vue'

defineProps<{
  submitting: boolean
}>()

const emit = defineEmits<{
  submit: [linkPassword: string]
}>()

const linkPassword = ref('')
</script>

<template>
  <form class="form-stack" @submit.prevent="emit('submit', linkPassword)">
    <b-field label="Senha">
      <b-input v-model="linkPassword" type="password" placeholder="••••••••" required />
    </b-field>
    <b-button
      type="is-primary"
      expanded
      native-type="submit"
      :loading="submitting"
      class="auth-submit has-text-light"
    >
      Vincular conta
    </b-button>
  </form>
</template>
