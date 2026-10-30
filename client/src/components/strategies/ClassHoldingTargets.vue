<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useToast } from 'buefy'
import Card from '@/components/ui/Card.vue'
import NumberInput from '@/components/ui/NumberInput.vue'
import ClassHoldingIdentity from '@/components/strategies/ClassHoldingIdentity.vue'
import { strategiesApi } from '@/api/strategies'
import { useStrategiesStore } from '@/stores/strategies'
import { fmt } from '@/composables/useFormat'
import {
  blankShare,
  holdingTargetsValid,
  typedSum,
  type PercentageDraft,
} from '@/utils/strategyTargets'
import type { ClassHolding, StrategyClass } from '@/types'

const props = defineProps<{ strategyClass: StrategyClass }>()

const toast = useToast()
const strategiesStore = useStrategiesStore()

const holdings = ref<ClassHolding[]>([])
const targets = ref<Record<string, PercentageDraft>>({})
const loading = ref(false)
const saving = ref(false)

function resetDraft() {
  targets.value = Object.fromEntries(
    holdings.value.map((holding) => [holding.id, holding.targetPercentage ?? '']),
  )
}

async function load() {
  loading.value = true
  try {
    holdings.value = await strategiesApi.findClassHoldings(props.strategyClass.id)
    resetDraft()
  } finally {
    loading.value = false
  }
}

watch(() => props.strategyClass.id, load, { immediate: true })

const drafts = computed(() => holdings.value.map((holding) => targets.value[holding.id] ?? ''))
const typed = computed(() => typedSum(drafts.value))
const share = computed(() => blankShare(drafts.value))
const blankCount = computed(() => drafts.value.filter((target) => target === '').length)
const valid = computed(() => holdingTargetsValid(drafts.value))
const dirty = computed(() =>
  holdings.value.some(
    (holding) => (targets.value[holding.id] ?? '') !== (holding.targetPercentage ?? ''),
  ),
)
const classTarget = computed(() => props.strategyClass.targetPercentage ?? 0)

const typedLabel = computed(() => {
  const typedText = `Digitado: ${fmt.pct(typed.value)}`
  if (typed.value > 100) return `${typedText} · passa ${fmt.pct(typed.value - 100)}`
  if (blankCount.value === 0) return typedText
  return `${typedText} · ${blankCount.value} em branco dividem ${fmt.pct(100 - typed.value)}`
})

function effectiveTarget(holding: ClassHolding): number {
  const target = targets.value[holding.id] ?? ''
  return target === '' ? (share.value ?? 0) : Number(target)
}

async function save() {
  if (!valid.value || !dirty.value) return
  saving.value = true
  try {
    holdings.value = await strategiesStore.saveTargets(
      props.strategyClass.id,
      holdings.value.map((holding) => {
        const target = targets.value[holding.id] ?? ''
        return {
          kind: holding.kind,
          holdingId: holding.id,
          targetPercentage: target === '' ? null : Number(target),
        }
      }),
    )
    resetDraft()
    toast.open({ message: 'Metas dos investimentos salvas.', type: 'is-success' })
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Card class="table-card mb-0" data-testid="class-holding-targets">
    <div class="card-body is-flex is-flex-direction-column is-gap-2">
      <div
        class="is-flex is-flex-wrap-wrap is-align-items-center is-justify-content-space-between is-gap-3"
      >
        <div>
          <div class="chart-title">Metas dos investimentos · {{ strategyClass.name }}</div>
          <div class="sub-caption">
            Meta da classe: {{ fmt.pct(classTarget) }} da estratégia ·
            {{ holdings.length - blankCount }} de {{ holdings.length }} com meta digitada
          </div>
        </div>
        <b-tag
          :type="valid ? 'is-success is-light' : 'is-danger is-light'"
          :icon="valid ? 'check' : 'alert-circle-outline'"
          data-testid="holding-targets-sum"
        >
          {{ typedLabel }}
        </b-tag>
      </div>
      <progress
        class="progress is-small mb-0"
        :class="valid ? 'is-success' : 'is-danger'"
        max="100"
        :value="Math.min(typed, 100)"
      />
    </div>

    <div class="table-wrap is-relative">
      <b-loading :is-full-page="false" :model-value="loading" />
      <p
        v-if="!loading && holdings.length === 0"
        class="card-body has-text-grey"
        data-testid="class-holdings-empty"
      >
        Nenhum investimento nesta classe ainda.
      </p>
      <div v-else class="table-scroll">
        <table class="inv-table">
          <thead>
            <tr>
              <th>Investimento</th>
              <th>Carteira · tipo</th>
              <th class="c-num has-text-right">Meta digitada</th>
              <th class="c-num has-text-right">Meta na classe</th>
              <th class="c-num has-text-right">Meta na estratégia</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="holding in holdings"
              :key="holding.id"
              class="inv-row"
              :class="{ 'is-frozen': holding.frozen }"
              data-testid="holding-target-row"
            >
              <ClassHoldingIdentity :holding="holding" />
              <td class="c-num has-text-right">
                <b-field class="is-justify-content-flex-end mb-0" addons>
                  <NumberInput
                    v-model="targets[holding.id]"
                    size="is-small"
                    min="0"
                    max="100"
                    custom-class="has-text-right"
                    :placeholder="fmt.pct(share ?? 0).replace('%', '')"
                    :aria-label="`Meta de ${holding.ticker ?? holding.name}`"
                    data-testid="holding-target"
                  />
                  <p class="control"><span class="button is-static is-small">%</span></p>
                </b-field>
              </td>
              <td class="c-num has-text-right has-text-weight-semibold">
                <template v-if="targets[holding.id] === ''">
                  <span class="has-text-grey">{{ fmt.pct(effectiveTarget(holding)) }}</span>
                  <div class="is-size-7 has-text-grey">do restante</div>
                </template>
                <template v-else>{{ fmt.pct(effectiveTarget(holding)) }}</template>
              </td>
              <td class="c-num has-text-right">
                {{ fmt.pct((effectiveTarget(holding) * classTarget) / 100) }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="card-body is-flex is-flex-direction-column is-gap-3">
      <b-message v-if="!valid" type="is-danger" class="mb-0" data-testid="holding-targets-error">
        {{
          typed > 100
            ? 'As metas digitadas passam de 100% da classe.'
            : 'Quando todos os investimentos têm meta, elas precisam somar 100% da classe.'
        }}
      </b-message>
      <div class="is-flex is-justify-content-flex-end is-gap-2">
        <b-button
          type="is-danger"
          outlined
          :disabled="!dirty || saving"
          data-testid="discard-targets"
          @click="resetDraft"
        >
          Descartar alterações
        </b-button>
        <b-button
          type="is-success"
          icon-left="check"
          :disabled="!dirty || !valid"
          :loading="saving"
          data-testid="save-targets"
          @click="save"
        >
          Salvar metas
        </b-button>
      </div>
    </div>
  </Card>
</template>
