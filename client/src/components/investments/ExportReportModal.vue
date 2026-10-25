<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import axios from 'axios'
import AppModal from '@/components/ui/AppModal.vue'
import { holdingsApi, type HoldingsFilterParams, type ReportPdf } from '@/api/holdings'
import { saveBlob } from '@/utils/saveBlob'

type ExportState = 'generating' | 'done' | 'empty' | 'error'

const props = defineProps<{ params: HoldingsFilterParams }>()

const emit = defineEmits<{ close: [] }>()

const state = ref<ExportState>('generating')
const report = ref<ReportPdf | null>(null)
const generating = computed(() => state.value === 'generating')

let abortController: AbortController | undefined

async function generate() {
  abortController = new AbortController()
  state.value = 'generating'
  try {
    const result = await holdingsApi.downloadReportPdf(props.params, abortController.signal)
    if (result === null) {
      state.value = 'empty'
      return
    }
    report.value = result
    saveBlob(result.blob, result.fileName)
    state.value = 'done'
  } catch (error) {
    if (axios.isCancel(error)) return
    state.value = 'error'
  }
}

function cancel() {
  abortController?.abort()
  emit('close')
}

function downloadAgain() {
  if (report.value) saveBlob(report.value.blob, report.value.fileName)
}

onMounted(generate)
onBeforeUnmount(() => abortController?.abort())
</script>

<template>
  <AppModal title="Exportar relatório" :dismissible="!generating" @close="emit('close')">
    <div
      class="is-flex is-flex-direction-column is-align-items-center has-text-centered is-gap-3 py-4"
    >
      <template v-if="state === 'generating'">
        <b-icon
          icon="loading"
          custom-class="mdi-spin"
          type="is-primary"
          size="is-large"
          data-testid="export-spinner"
        />
        <div>
          <p class="has-text-weight-semibold is-size-5">Gerando o PDF…</p>
          <p class="has-text-grey">Consolidando as posições por classe e tipo.</p>
        </div>
      </template>

      <template v-else-if="state === 'done' && report">
        <b-icon icon="check-circle" type="is-success" size="is-large" />
        <div>
          <p class="has-text-weight-semibold is-size-5">Relatório gerado</p>
          <p class="has-text-grey">O download começou automaticamente.</p>
        </div>
        <div
          class="is-flex is-align-items-center is-align-self-stretch is-gap-2 box has-background-white-bis has-text-left py-2 px-3 mb-0"
          data-testid="export-file"
        >
          <b-icon icon="file-pdf-box" type="is-danger" />
          <span class="has-text-weight-semibold is-size-7">{{ report.fileName }}</span>
        </div>
      </template>

      <template v-else-if="state === 'empty'">
        <b-icon icon="file-document-outline" type="is-grey" size="is-large" />
        <p class="has-text-weight-semibold is-size-5">Nenhum investimento para os filtros atuais</p>
      </template>

      <template v-else>
        <b-icon icon="alert-circle-outline" type="is-danger" size="is-large" />
        <p class="has-text-weight-semibold is-size-5">Não foi possível gerar o relatório</p>
      </template>

      <p v-if="state === 'generating' || state === 'done'" class="has-text-grey is-size-7">
        O relatório considera os filtros aplicados na lista de investimentos.
      </p>
    </div>

    <template #footer>
      <b-button v-if="generating" data-testid="export-cancel" @click="cancel">Cancelar</b-button>
      <template v-else>
        <b-button
          v-if="state === 'done'"
          icon-left="download"
          data-testid="export-download-again"
          @click="downloadAgain"
        >
          Baixar novamente
        </b-button>
        <b-button v-if="state === 'error'" data-testid="export-retry" @click="generate">
          Tentar novamente
        </b-button>
        <b-button type="is-primary" data-testid="export-close" @click="emit('close')">
          Fechar
        </b-button>
      </template>
    </template>
  </AppModal>
</template>
