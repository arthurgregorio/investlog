<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useDialog, useToast } from 'buefy'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import NumberInput from '@/components/ui/NumberInput.vue'
import { useTypesListStore } from '@/stores/typesList'
import { useRatesStore } from '@/stores/rates'
import { useAppearanceStore } from '@/stores/appearance'
import { useUsersAdminStore } from '@/stores/usersAdmin'
import { useAuthStore } from '@/stores/auth'
import { fmt } from '@/composables/useFormat'
import type { AccentKey, UserRole } from '@/types'

const toast = useToast()
const dialog = useDialog()
const typesListStore = useTypesListStore()
const ratesStore = useRatesStore()
const appearance = useAppearanceStore()
const usersAdminStore = useUsersAdminStore()
const auth = useAuthStore()

const newStockType = ref('')
const newFundType = ref('')

const accents: { key: AccentKey; hex: string }[] = [
  { key: 'blue', hex: '#206bc4' },
  { key: 'indigo', hex: '#4263eb' },
  { key: 'teal', hex: '#0ca678' },
  { key: 'green', hex: '#15915b' },
]

onMounted(() => {
  Promise.all([typesListStore.load(), ratesStore.load(), usersAdminStore.load()])
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

function isSelf(email: string): boolean {
  return auth.session?.email === email
}

async function approveUser(id: string) {
  await usersAdminStore.approve(id)
  toast.open({ message: 'Usuário aprovado.', type: 'is-success' })
}

async function rejectUser(id: string) {
  await usersAdminStore.reject(id)
  toast.open({ message: 'Usuário rejeitado.', type: 'is-success' })
}

function confirmRoleChange(id: string, name: string, currentRole: UserRole) {
  const nextRole: UserRole = currentRole === 'ADMIN' ? 'USER' : 'ADMIN'
  dialog.confirm({
    title: nextRole === 'ADMIN' ? 'Promover a administrador' : 'Remover privilégios de administrador',
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
    message: `Remover <strong>${name}</strong>. Esta ação <strong>não pode ser desfeita</strong>.`,
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
            <button v-if="auth.isAdmin" :aria-label="`Remover ${fundType.name}`" @click="removeFundType(fundType.id)">
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

    <Card>
      <CardBody>
        <div class="set-head"><h2 class="set-title">Aparência</h2></div>
        <p class="set-desc">Escolha a cor de destaque da interface.</p>
        <div class="accent-row">
          <button
            v-for="accentOption in accents"
            :key="accentOption.key"
            class="accent-swatch"
            :class="{ active: appearance.accent === accentOption.key }"
            :style="{ background: accentOption.hex }"
            :aria-label="`Cor ${accentOption.key}`"
            @click="appearance.setAccent(accentOption.key)"
          >
            <b-icon
              v-if="appearance.accent === accentOption.key"
              icon="check"
              size="is-small"
              class="sw-check"
            />
          </button>
        </div>
      </CardBody>
    </Card>

    <Card>
      <CardBody>
        <b-loading :is-full-page="false" :active="usersAdminStore.loading" />
        <div class="set-head"><h2 class="set-title">Usuários locais</h2></div>
        <p class="set-desc">Aprove, rejeite ou gerencie o acesso de usuários locais.</p>
        <div class="wallet-grid">
          <Card v-for="user in usersAdminStore.users" :key="user.id" class="wallet-card">
            <CardBody>
              <div class="wallet-head">
                <div class="wallet-titles">
                  <div class="wallet-name">{{ user.name }}</div>
                  <div class="wallet-tags">
                    <b-tag :type="user.role === 'ADMIN' ? 'is-link' : 'is-light'">{{ user.role }}</b-tag>
                    <b-tag
                      :type="
                        user.status === 'APPROVED' ? 'is-success' : user.status === 'REJECTED' ? 'is-danger' : 'is-warning'
                      "
                    >
                      {{ user.status }}
                    </b-tag>
                    <b-tag v-if="user.totpEnabled" type="is-info">2FA ativo</b-tag>
                  </div>
                </div>
              </div>
              <p class="set-desc">{{ user.email }}</p>
              <div class="wallet-foot" style="flex-wrap: wrap; gap: 6px">
                <b-button
                  v-if="user.status !== 'APPROVED'"
                  size="is-small"
                  type="is-success"
                  outlined
                  @click="approveUser(user.id)"
                >
                  Aprovar
                </b-button>
                <template v-if="!isSelf(user.email)">
                  <b-button
                    v-if="user.status !== 'REJECTED'"
                    size="is-small"
                    type="is-warning"
                    outlined
                    @click="rejectUser(user.id)"
                  >
                    Rejeitar
                  </b-button>
                  <b-button size="is-small" type="is-link" outlined @click="confirmRoleChange(user.id, user.name, user.role)">
                    {{ user.role === 'ADMIN' ? 'Remover admin' : 'Promover a admin' }}
                  </b-button>
                  <b-button size="is-small" type="is-info" outlined @click="confirmTotpReset(user.id, user.name)">
                    Redefinir 2FA
                  </b-button>
                  <b-button size="is-small" type="is-danger" outlined @click="confirmDeleteUser(user.id, user.name)">
                    Remover
                  </b-button>
                </template>
              </div>
            </CardBody>
          </Card>
        </div>
      </CardBody>
    </Card>
  </div>
</template>
