<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useToast } from 'buefy'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import { useTypesListStore } from '@/stores/typesList'
import { useAuthStore } from '@/stores/auth'

const toast = useToast()
const typesListStore = useTypesListStore()
const auth = useAuthStore()

const newStockType = ref('')
const newFundType = ref('')

onMounted(() => {
  typesListStore.load()
})

async function addStockType() {
  const name = newStockType.value.trim()
  if (!name) return
  await typesListStore.addStockType(name)
  newStockType.value = ''
  toast.open({ message: 'Tipo de ação adicionado.', type: 'is-success' })
}

async function addFundType() {
  const name = newFundType.value.trim()
  if (!name) return
  await typesListStore.addFundType(name)
  newFundType.value = ''
  toast.open({ message: 'Tipo de fundo adicionado.', type: 'is-success' })
}

async function removeStockType(stockTypeId: string) {
  await typesListStore.removeStockType(stockTypeId)
  toast.open({ message: 'Tipo de ação removido.', type: 'is-success' })
}

async function removeFundType(fundTypeId: string) {
  await typesListStore.removeFundType(fundTypeId)
  toast.open({ message: 'Tipo de fundo removido.', type: 'is-success' })
}
</script>

<template>
  <div class="page page-narrow">
    <div class="page-head">
      <h1 class="page-title">Tipos</h1>
      <p class="page-desc">Gerencie os tipos de ação e de fundo usados no cadastro.</p>
    </div>

    <Card>
      <CardBody>
        <b-loading :is-full-page="false" :active="typesListStore.loading" />
        <div class="set-head"><h2 class="set-title">Tipos de ação</h2></div>
        <p class="set-desc">Cadastrados antes de registrar uma ação (escolhidos no formulário).</p>
        <div class="chip-edit">
          <span
            v-for="stockType in typesListStore.stockTypes"
            :key="stockType.id"
            class="edit-chip"
          >
            {{ stockType.name }}
            <button
              v-if="auth.isAdmin"
              :aria-label="`Remover ${stockType.name}`"
              @click="removeStockType(stockType.id)"
            >
              <b-icon icon="close" size="is-small" />
            </button>
          </span>
        </div>
        <div class="chip-add">
          <b-input
            v-model="newStockType"
            placeholder="ex.: Stock, REIT…"
            @keyup.enter="addStockType"
          />
          <b-button icon-left="plus" @click="addStockType">Adicionar tipo</b-button>
        </div>
      </CardBody>
    </Card>

    <Card>
      <CardBody>
        <div class="set-head"><h2 class="set-title">Tipos de fundo</h2></div>
        <p class="set-desc">Cadastrados antes de registrar um fundo (escolhidos no formulário).</p>
        <div class="chip-edit">
          <span v-for="fundType in typesListStore.fundTypes" :key="fundType.id" class="edit-chip">
            {{ fundType.name }}
            <button
              v-if="auth.isAdmin"
              :aria-label="`Remover ${fundType.name}`"
              @click="removeFundType(fundType.id)"
            >
              <b-icon icon="close" size="is-small" />
            </button>
          </span>
        </div>
        <div class="chip-add">
          <b-input
            v-model="newFundType"
            placeholder="ex.: Previdência, Cambial…"
            @keyup.enter="addFundType"
          />
          <b-button icon-left="plus" @click="addFundType">Adicionar tipo</b-button>
        </div>
      </CardBody>
    </Card>
  </div>
</template>
