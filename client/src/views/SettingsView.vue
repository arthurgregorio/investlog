<script setup lang="ts">
import { ref } from 'vue'
import { storeToRefs } from 'pinia'
import AppIcon from '@/components/AppIcon.vue'
import Card from '@/components/ui/Card.vue'
import CardBody from '@/components/ui/CardBody.vue'
import NumberInput from '@/components/ui/NumberInput.vue'
import { usePortfolioStore } from '@/stores/portfolio'
import { useAppearanceStore } from '@/stores/appearance'
import type { AccentKey } from '@/types'

const store = usePortfolioStore()
const { base, config, stockTypes, fundTypes } = storeToRefs(store)
const appearance = useAppearanceStore()
const { accent } = storeToRefs(appearance)

const newType = ref('')
const newFundType = ref('')

const accents: { key: AccentKey; hex: string }[] = [
  { key: 'blue', hex: '#206bc4' },
  { key: 'indigo', hex: '#4263eb' },
  { key: 'teal', hex: '#0ca678' },
  { key: 'green', hex: '#15915b' },
]

function addStockType() {
  store.addStockType(newType.value)
  newType.value = ''
}
function addFundType() {
  store.addFundType(newFundType.value)
  newFundType.value = ''
}
</script>

<template>
  <div class="page page-narrow">
    <div class="page-head">
      <div class="page-eyebrow">Logbook</div>
      <h1 class="page-title">Configurações</h1>
      <p class="page-desc">Defina as taxas de conversão e os tipos de ação usados no cadastro.</p>
    </div>

    <Card>
      <CardBody>
        <div class="set-head">
          <h2 class="set-title">Moeda base e conversão</h2>
          <span class="base-chip"><AppIcon name="repeat" :size="14" />Base <b>{{ base }}</b></span>
        </div>
        <p class="set-desc">A visão consolidada converte cada carteira para {{ base }} usando estas taxas.</p>
        <div class="rate-list">
          <div v-for="c in store.currencies" :key="c" class="rate-row">
            <div class="rate-cur">
              <span class="cur-chip lg">{{ c }}</span>
              <span class="rate-sym">{{ store.currencySymbol[c] }}</span>
            </div>
            <span v-if="c === base" class="rate-base">Moeda base · 1,00</span>
            <label v-else class="rate-input">
              <span>1 {{ c }} =</span>
              <NumberInput
                :model-value="config.rates[c]"
                :prefix="store.currencySymbol[base]"
                @update:model-value="(v) => v !== '' && store.setRate(c, Number(v))"
              />
            </label>
          </div>
        </div>
      </CardBody>
    </Card>

    <Card>
      <CardBody>
        <div class="set-head"><h2 class="set-title">Tipos de ação</h2></div>
        <p class="set-desc">Cadastrados antes de registrar uma ação (escolhidos no formulário).</p>
        <div class="chip-edit">
          <span v-for="t in stockTypes" :key="t" class="edit-chip">
            {{ t }}
            <button :aria-label="`Remover ${t}`" @click="store.removeStockType(t)"><AppIcon name="x" :size="14" /></button>
          </span>
        </div>
        <div class="chip-add">
          <b-input v-model="newType" placeholder="ex.: Stock, REIT…" />
          <b-button icon-left="plus" @click="addStockType">Adicionar tipo</b-button>
        </div>
      </CardBody>
    </Card>

    <Card>
      <CardBody>
        <div class="set-head"><h2 class="set-title">Tipos de fundo</h2></div>
        <p class="set-desc">Cadastrados antes de registrar um fundo (escolhidos no formulário).</p>
        <div class="chip-edit">
          <span v-for="t in fundTypes" :key="t" class="edit-chip">
            {{ t }}
            <button :aria-label="`Remover ${t}`" @click="store.removeFundType(t)"><AppIcon name="x" :size="14" /></button>
          </span>
        </div>
        <div class="chip-add">
          <b-input v-model="newFundType" placeholder="ex.: Previdência, Cambial…" />
          <b-button icon-left="plus" @click="addFundType">Adicionar tipo</b-button>
        </div>
      </CardBody>
    </Card>

    <Card>
      <CardBody>
        <div class="set-head"><h2 class="set-title">Aparência</h2></div>
        <p class="set-desc">Escolha a cor de destaque da interface. Ganhos e perdas continuam verde/vermelho.</p>
        <div class="accent-row">
          <button
            v-for="a in accents"
            :key="a.key"
            class="accent-swatch"
            :class="{ active: accent === a.key }"
            :style="{ background: a.hex }"
            :aria-label="`Cor ${a.key}`"
            @click="appearance.setAccent(a.key)"
          >
            <AppIcon v-if="accent === a.key" name="check" :size="18" class="sw-check" :stroke="2.6" />
          </button>
        </div>
      </CardBody>
    </Card>

    <div class="set-note">
      <AppIcon name="info" :size="16" />As alterações valem para esta sessão e são reiniciadas ao recarregar a página.
    </div>
  </div>
</template>
