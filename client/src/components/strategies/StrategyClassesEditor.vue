<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useToast } from 'buefy'
import NumberInput from '@/components/ui/NumberInput.vue'
import { useStrategiesStore } from '@/stores/strategies'
import { chartColor } from '@/utils/allocation'
import {
  classTargetSummary,
  isHundred,
  sumGapLabel,
  typedSum,
  type PercentageDraft,
} from '@/utils/strategyTargets'
import type { Strategy } from '@/types'

interface ClassDraft {
  key: string
  id?: string
  name: string
  targetPercentage: PercentageDraft
}

const props = defineProps<{ strategy: Strategy }>()
const emit = defineEmits<{ delete: [] }>()
const expandedClassId = defineModel<string | null>('expandedClassId', { required: true })

const toast = useToast()
const strategiesStore = useStrategiesStore()

const name = ref('')
const classes = ref<ClassDraft[]>([])
const saving = ref(false)
let newClassCount = 0

function blankClass(): ClassDraft {
  newClassCount += 1
  return { key: `new-${newClassCount}`, name: '', targetPercentage: '' }
}

function isBlank(draft: ClassDraft): boolean {
  return !draft.id && !draft.name.trim() && draft.targetPercentage === ''
}

function resetDraft() {
  name.value = props.strategy.name
  classes.value = props.strategy.classes.length
    ? props.strategy.classes.map((strategyClass) => ({
        key: strategyClass.id,
        id: strategyClass.id,
        name: strategyClass.name,
        targetPercentage: strategyClass.targetPercentage ?? '',
      }))
    : [blankClass()]
}

watch(() => props.strategy, resetDraft, { immediate: true })

const savedClasses = computed(
  () => new Map(props.strategy.classes.map((strategyClass) => [strategyClass.id, strategyClass])),
)

const sum = computed(() =>
  typedSum(classes.value.map((strategyClass) => strategyClass.targetPercentage)),
)
const sumReached = computed(() => isHundred(sum.value))
const namesFilled = computed(
  () => !!name.value.trim() && classes.value.every((strategyClass) => !!strategyClass.name.trim()),
)

const dirty = computed(() => {
  if (name.value.trim() !== props.strategy.name) return true
  if (props.strategy.classes.length === 0) return !classes.value.every(isBlank)
  if (classes.value.length !== props.strategy.classes.length) return true
  return classes.value.some((draft, index) => {
    const saved = props.strategy.classes[index]
    return (
      draft.id !== saved.id ||
      draft.name.trim() !== saved.name ||
      Number(draft.targetPercentage) !== Number(saved.targetPercentage)
    )
  })
})

const canSave = computed(() => dirty.value && sumReached.value && namesFilled.value)

function addClass() {
  classes.value = [...classes.value, blankClass()]
}

function removeClass(key: string) {
  classes.value = classes.value.filter((strategyClass) => strategyClass.key !== key)
}

function toggle(classId: string) {
  expandedClassId.value = expandedClassId.value === classId ? null : classId
}

function toggleFromRow(event: MouseEvent, classId?: string) {
  if (!classId || (event.target as Element).closest('input, button, .control')) return
  toggle(classId)
}

async function save() {
  if (!canSave.value) return
  saving.value = true
  try {
    if (name.value.trim() !== props.strategy.name) {
      await strategiesStore.rename(props.strategy.id, name.value.trim())
    }
    await strategiesStore.saveClasses(
      props.strategy.id,
      classes.value.map((strategyClass) => ({
        id: strategyClass.id,
        name: strategyClass.name.trim(),
        targetPercentage: Number(strategyClass.targetPercentage),
      })),
    )
    toast.open({ message: 'Estratégia salva.', type: 'is-success' })
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div>
    <div
      class="card-body is-flex is-flex-wrap-wrap is-align-items-flex-end is-justify-content-space-between is-gap-3"
    >
      <div class="is-flex is-flex-wrap-wrap is-align-items-flex-end is-gap-3">
        <b-field label="Nome da estratégia" class="mb-0">
          <b-input v-model="name" data-testid="strategy-name" />
        </b-field>
        <b-button
          type="is-danger"
          outlined
          icon-left="trash-can-outline"
          data-testid="delete-strategy"
          @click="emit('delete')"
        >
          Excluir estratégia
        </b-button>
      </div>
      <b-button icon-left="plus" data-testid="add-class" @click="addClass">
        Adicionar classe
      </b-button>
    </div>

    <div class="card-body pt-0 is-flex is-flex-direction-column is-gap-2">
      <div
        class="is-flex is-flex-wrap-wrap is-align-items-center is-justify-content-space-between is-gap-3"
      >
        <div>
          <div class="chart-title">Classes e metas</div>
          <div class="sub-caption">As metas das classes somam 100% da estratégia.</div>
        </div>
        <b-tag
          :type="sumReached ? 'is-success is-light' : 'is-danger is-light'"
          :icon="sumReached ? 'check' : 'alert-circle-outline'"
          data-testid="classes-sum"
        >
          {{ sumGapLabel(sum) }}
        </b-tag>
      </div>
      <progress
        class="progress is-small mb-0"
        :class="sumReached ? 'is-success' : 'is-danger'"
        max="100"
        :value="Math.min(sum, 100)"
      />
    </div>

    <div class="table-wrap">
      <div class="table-scroll">
        <table class="inv-table">
          <thead>
            <tr>
              <th>Classe</th>
              <th class="c-num has-text-right">Meta</th>
              <th class="c-num has-text-right">Investimentos</th>
              <th>Metas dos investimentos</th>
              <th class="c-act is-wide"><span class="is-sr-only">Ações</span></th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(strategyClass, index) in classes"
              :key="strategyClass.key"
              class="inv-row"
              :class="{
                'is-clickable': !!strategyClass.id,
                'is-current': !!strategyClass.id && expandedClassId === strategyClass.id,
              }"
              data-testid="class-row"
              @click="toggleFromRow($event, strategyClass.id)"
            >
              <td>
                <div class="is-flex is-align-items-center is-gap-2">
                  <span class="wref-dot" :style="{ background: chartColor(index) }" />
                  <b-input
                    v-model="strategyClass.name"
                    class="is-flex-grow-1"
                    size="is-small"
                    placeholder="Nome da classe"
                    aria-label="Nome da classe"
                    data-testid="class-name"
                  />
                </div>
              </td>
              <td class="c-num has-text-right">
                <b-field class="target-field ml-auto mb-0" addons>
                  <NumberInput
                    v-model="strategyClass.targetPercentage"
                    size="is-small"
                    expanded
                    min="0"
                    max="100"
                    custom-class="has-text-right"
                    aria-label="Meta da classe"
                    data-testid="class-target"
                  />
                  <p class="control"><span class="button is-static is-small">%</span></p>
                </b-field>
              </td>
              <td class="c-num has-text-right">
                {{ strategyClass.id ? (savedClasses.get(strategyClass.id)?.holdingCount ?? 0) : 0 }}
              </td>
              <td>
                <span class="is-size-7 has-text-grey">{{
                  strategyClass.id && savedClasses.get(strategyClass.id)
                    ? classTargetSummary(savedClasses.get(strategyClass.id)!)
                    : 'Salve para adicionar investimentos'
                }}</span>
              </td>
              <td class="c-act is-wide">
                <div class="is-flex is-justify-content-flex-end">
                  <b-button
                    type="is-ghost"
                    icon-left="trash-can-outline"
                    :aria-label="`Remover ${strategyClass.name || 'classe'}`"
                    data-testid="remove-class"
                    @click="removeClass(strategyClass.key)"
                  />
                  <b-button
                    type="is-ghost"
                    :icon-left="
                      expandedClassId === strategyClass.id ? 'chevron-up' : 'chevron-down'
                    "
                    :disabled="!strategyClass.id"
                    :aria-label="`Metas dos investimentos de ${strategyClass.name || 'classe'}`"
                    data-testid="expand-class"
                    @click="strategyClass.id && toggle(strategyClass.id)"
                  />
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div
      class="card-body is-flex is-flex-wrap-wrap is-align-items-center is-justify-content-flex-end is-gap-2"
    >
      <p
        v-if="!sumReached"
        class="is-size-7 has-text-danger-on-scheme mb-0 mr-2"
        data-testid="classes-sum-error"
      >
        As metas das classes precisam somar 100% para salvar.
      </p>
      <b-button
        type="is-danger"
        outlined
        :disabled="!dirty || saving"
        data-testid="discard-classes"
        @click="resetDraft"
      >
        Descartar alterações
      </b-button>
      <b-button
        type="is-success"
        icon-left="check"
        :disabled="!canSave"
        :loading="saving"
        data-testid="save-classes"
        @click="save"
      >
        Salvar
      </b-button>
    </div>
  </div>
</template>
