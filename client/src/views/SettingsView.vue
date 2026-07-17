<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useToast } from 'buefy'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import NumberInput from '@/components/ui/NumberInput.vue'
import { useTypesListStore } from '@/stores/typesList'
import { useRatesStore } from '@/stores/rates'
import { useAuthStore } from '@/stores/auth'
import { fmt } from '@/composables/useFormat'

const toast = useToast()
const typesListStore = useTypesListStore()
const ratesStore = useRatesStore()
const auth = useAuthStore()

const newStockType = ref('')
const newFundType = ref('')

onMounted(() => {
  Promise.all([typesListStore.load(), ratesStore.load()])
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

const rateDrafts = reactive<Record<string, number | ''>>({})

function rateDisplayValue(currencyCode: string, storedRate: number) {
  return currencyCode in rateDrafts ? rateDrafts[currencyCode] : storedRate
}

function draftRate(currencyCode: string, value: number | '') {
  rateDrafts[currencyCode] = value
}

async function commitRate(currencyCode: string) {
  const value = rateDrafts[currencyCode]
  delete rateDrafts[currencyCode]
  if (value === undefined || value === '' || value <= 0) return
  await ratesStore.upsertRate(currencyCode, Number(value), false)
  toast.open({ message: 'Taxa de conversão atualizada.', type: 'is-success' })
}
</script>

<template>
  <div class="page page-narrow">
    <div class="page-head">
      <h1 class="page-title">Configurações</h1>
      <p class="page-desc">Defina as taxas de conversão e os tipos de ativo usados no cadastro.</p>
    </div>

    <Card>
      <CardBody>
        <b-loading :is-full-page="false" :active="ratesStore.loading" />
        <div class="set-head">
          <h2 class="set-title">Moeda base e conversão</h2>
          <span class="base-chip">
            <b-icon icon="repeat" size="is-small" />Base <b>{{ ratesStore.baseCurrency }}</b>
          </span>
        </div>
        <p class="set-desc">
          A visão consolidada converte cada carteira para {{ ratesStore.baseCurrency }} usando estas
          taxas.
        </p>
        <div class="rate-list">
          <div v-for="rate in ratesStore.rates" :key="rate.currencyCode" class="rate-row">
            <div class="rate-cur">
              <span class="cur-chip lg">{{ rate.currencyCode }}</span>
              <span class="rate-sym">{{ fmt.sym(rate.currencyCode) }}</span>
            </div>
            <span v-if="rate.isBase" class="rate-base">Moeda base · 1,00</span>
            <label v-else class="rate-input">
              <span>1 {{ rate.currencyCode }} =</span>
              <NumberInput
                :model-value="rateDisplayValue(rate.currencyCode, rate.rate)"
                :prefix="fmt.sym(ratesStore.baseCurrency)"
                @update:model-value="(v) => draftRate(rate.currencyCode, v)"
                @blur="commitRate(rate.currencyCode)"
              />
            </label>
          </div>
        </div>
      </CardBody>
    </Card>

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
