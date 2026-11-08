<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import SettingsSection from '@/components/settings/SettingsSection.vue'
import FieldLabel from '@/components/ui/FieldLabel.vue'
import NumberInput from '@/components/ui/NumberInput.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import SegmentedControl from '@/components/ui/SegmentedControl.vue'
import { useNotificationPreferencesStore } from '@/stores/notificationPreferences'
import { problemDetailMessage } from '@/utils/apiErrors'
import type {
  NotificationPreferencesRequest,
  NotificationPreferencesResponse,
  PeriodicReportFrequency,
} from '@/types'

const FREQUENCY_OPTIONS: { value: PeriodicReportFrequency; label: string }[] = [
  { value: 'DAILY', label: 'Diário' },
  { value: 'WEEKLY', label: 'Semanal' },
  { value: 'BIWEEKLY', label: 'Quinzenal' },
  { value: 'DISABLED', label: 'Desativado' },
]

const FREQUENCY_SCHEDULES: Record<PeriodicReportFrequency, string> = {
  DAILY:
    'Enviado às 07:00 com o resultado do dia útil anterior, comparado com o dia útil antes dele.',
  WEEKLY: 'Enviado toda segunda-feira às 07:00, comparando a semana que terminou com a anterior.',
  BIWEEKLY: 'Enviado nos dias 1º e 16 às 07:00, comparando a quinzena que terminou com a anterior.',
  DISABLED: 'Você não recebe relatórios periódicos.',
}

const MINIMUM_GAIN_TIP =
  'Só avisa sobre congelados com ganho acima deste percentual. Use 0 para ser avisado com qualquer ganho positivo.'

const notificationPreferencesStore = useNotificationPreferencesStore()
const { preferences, loading } = storeToRefs(notificationPreferencesStore)

const switchesRevision = ref(0)
const minimumGainDraft = ref<number | ''>()
const minimumGainError = ref<string>()

onMounted(() => {
  notificationPreferencesStore.load()
})

function saveChanges(changes: Partial<NotificationPreferencesRequest>) {
  const current = preferences.value as NotificationPreferencesResponse
  return notificationPreferencesStore.save({
    periodicReportFrequency: current.periodicReportFrequency,
    monthEndReportEnabled: current.monthEndReportEnabled,
    frozenAlertEnabled: current.frozenAlertEnabled,
    frozenAlertMinimumGainPercentage: current.frozenAlertMinimumGainPercentage,
    ...changes,
  })
}

function saveFrequency(periodicReportFrequency: PeriodicReportFrequency) {
  return saveChanges({ periodicReportFrequency })
}

async function saveSwitch(changes: Partial<NotificationPreferencesRequest>) {
  try {
    await saveChanges(changes)
  } catch (error) {
    switchesRevision.value += 1
    throw error
  }
}

async function commitMinimumGain() {
  const value = minimumGainDraft.value
  minimumGainDraft.value = undefined
  if (value === undefined || value === '') return
  try {
    await saveChanges({ frozenAlertMinimumGainPercentage: value })
    minimumGainError.value = undefined
  } catch (error) {
    minimumGainError.value = problemDetailMessage(error)
  }
}
</script>

<template>
  <div class="page page-narrow">
    <PageHeader
      title="Notificações"
      description="Escolha quais e-mails você quer receber sobre seus investimentos."
    />

    <b-message
      v-if="preferences && !preferences.emailConfigured"
      type="is-warning"
      has-icon
      class="mb-0"
    >
      <strong>O envio de e-mails ainda não foi configurado.</strong>
      Suas escolhas ficam salvas e passam a valer assim que um administrador configurar o servidor
      de e-mail.
    </b-message>

    <SettingsSection
      title="Relatório de desempenho"
      description="Mostra, para cada carteira, o valor atual, o ganho e quanto o ganho variou em relação ao período anterior."
      :loading="loading"
    >
      <template v-if="preferences">
        <div class="is-flex is-justify-content-center">
          <SegmentedControl
            :model-value="preferences.periodicReportFrequency"
            :options="FREQUENCY_OPTIONS"
            group-label="Frequência do relatório"
            @update:model-value="saveFrequency"
          />
        </div>
        <p class="help mt-4" data-testid="frequency-schedule">
          {{ FREQUENCY_SCHEDULES[preferences.periodicReportFrequency] }}
        </p>
        <b-message
          v-if="preferences.periodicReportFrequency === 'DAILY'"
          type="is-info"
          size="is-small"
          class="mt-3 mb-0"
        >
          <strong>Fins de semana não geram relatório.</strong>
          Os e-mails chegam de terça a sábado, e o de terça-feira compara a segunda com a
          sexta-feira.
        </b-message>
      </template>
    </SettingsSection>

    <SettingsSection
      title="Relatório de fim de mês"
      description="Desempenho de todas as carteiras no mês que terminou, enviado no dia 1º às 07:00."
      :loading="loading"
    >
      <b-switch
        v-if="preferences"
        :key="`month-end-${switchesRevision}`"
        :model-value="preferences.monthEndReportEnabled"
        data-testid="month-end-switch"
        @update:model-value="(enabled: boolean) => saveSwitch({ monthEndReportEnabled: enabled })"
      >
        Receber o relatório de fim de mês
      </b-switch>
    </SettingsSection>

    <SettingsSection
      title="Congelados no positivo"
      description="Avisa quando um investimento congelado passa do ganho mínimo e já pode ser vendido. No máximo um e-mail por dia, entre 08:00 e 22:00."
      :loading="loading"
    >
      <div v-if="preferences" class="fixed-grid has-4-cols">
        <div class="grid is-gap-2">
          <div class="cell is-col-span-3 is-col-span-4-mobile is-flex is-align-items-flex-end pb-2">
            <b-switch
              :key="`frozen-alert-${switchesRevision}`"
              :model-value="preferences.frozenAlertEnabled"
              data-testid="frozen-alert-switch"
              @update:model-value="
                (enabled: boolean) => saveSwitch({ frozenAlertEnabled: enabled })
              "
            >
              Avisar quando um investimento congelado ficar positivo
            </b-switch>
          </div>
          <b-field
            class="cell is-col-span-4-mobile mb-0"
            :type="minimumGainError ? 'is-danger' : undefined"
            :message="minimumGainError"
          >
            <template #label>
              <FieldLabel text="Ganho mínimo" :tip="MINIMUM_GAIN_TIP" />
            </template>
            <NumberInput
              :model-value="minimumGainDraft ?? preferences.frozenAlertMinimumGainPercentage"
              suffix="%"
              step="0.01"
              min="0"
              max="100"
              :disabled="!preferences.frozenAlertEnabled"
              data-testid="minimum-gain"
              @update:model-value="minimumGainDraft = $event"
              @blur="commitMinimumGain"
              @keydown.enter="commitMinimumGain"
            />
          </b-field>
        </div>
      </div>
    </SettingsSection>
  </div>
</template>
