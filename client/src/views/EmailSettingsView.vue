<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { isAxiosError } from 'axios'
import { useToast } from 'buefy'
import SettingsSection from '@/components/settings/SettingsSection.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import { mailSettingsApi } from '@/api/mailSettings'
import { useAuthStore } from '@/stores/auth'
import { useMailSettingsStore } from '@/stores/mailSettings'
import { problemDetailMessage } from '@/utils/apiErrors'
import { DEMO_CREDENTIALS } from '@/utils/demoCredentials'
import type { MailSecurity, MailSettingsRequest, MailSettingsResponse } from '@/types'

const SECURITY_OPTIONS: { value: MailSecurity; label: string }[] = [
  { value: 'NONE', label: 'Nenhuma' },
  { value: 'STARTTLS', label: 'STARTTLS' },
  { value: 'SSL_TLS', label: 'SSL/TLS' },
]
const PASSWORD_HINT = 'A senha salva nunca é exibida. Deixe em branco para mantê-la.'
const PUBLIC_URL_HINT =
  'Endereço que os usuários acessam no navegador. É usado nos links dos e-mails.'

const toast = useToast()
const auth = useAuthStore()
const mailSettingsStore = useMailSettingsStore()

const form = reactive({
  host: '',
  port: '',
  security: 'STARTTLS' as MailSecurity,
  username: '',
  password: '',
  fromAddress: '',
  fromName: '',
  publicUrl: '',
})
const saving = ref(false)
const saveError = ref<string | null>(null)
const switchingEnabled = ref(false)
const switchRenderKey = ref(0)
const recipient = ref('')
const sendingTest = ref(false)
const testOutcome = ref<{ sent: true; recipient: string } | { sent: false; detail: string } | null>(
  null,
)

const demoModeEnabled = computed(() => auth.session?.demoModeEnabled === true)
const configured = computed(() => mailSettingsStore.settings?.configured === true)
const sendingEnabled = computed(() => mailSettingsStore.settings?.enabled === true)
const passwordPlaceholder = computed(() =>
  mailSettingsStore.settings?.passwordSet ? '•••••••• definida' : '',
)
const switchDescription = computed(() =>
  configured.value
    ? 'Liga ou desliga todos os e-mails do InvestLog: aprovação de contas, relatórios e alertas de investimentos.'
    : 'Preencha e salve o servidor SMTP abaixo para poder ativar o envio.',
)
const testDisabled = computed(() => demoModeEnabled.value || !configured.value)

onMounted(async () => {
  const sessionEmail = auth.session?.email ?? ''
  recipient.value = sessionEmail === DEMO_CREDENTIALS.email ? '' : sessionEmail
  await mailSettingsStore.load()
  fillForm(mailSettingsStore.settings)
})

function fillForm(settings: MailSettingsResponse | null) {
  form.host = settings?.host ?? ''
  form.port = settings?.port?.toString() ?? ''
  form.security = settings?.security ?? 'STARTTLS'
  form.username = settings?.username ?? ''
  form.password = ''
  form.fromAddress = settings?.fromAddress ?? ''
  form.fromName = settings?.fromName ?? ''
  form.publicUrl = settings?.publicUrl ?? ''
}

function buildRequest(): MailSettingsRequest {
  return {
    host: form.host.trim(),
    port: form.port.trim() === '' ? null : Number(form.port),
    security: form.security,
    username: form.username.trim() === '' ? null : form.username.trim(),
    password: form.password === '' ? null : form.password,
    fromAddress: form.fromAddress.trim(),
    fromName: form.fromName.trim(),
    publicUrl: form.publicUrl.trim(),
  }
}

async function saveSettings() {
  saving.value = true
  saveError.value = null
  try {
    await mailSettingsStore.save(buildRequest())
    fillForm(mailSettingsStore.settings)
    toast.open({ message: 'Servidor SMTP salvo.', type: 'is-success' })
  } catch (caughtError) {
    saveError.value = problemDetailMessage(caughtError) ?? null
  } finally {
    saving.value = false
  }
}

async function setSendingEnabled(enabled: boolean) {
  switchingEnabled.value = true
  try {
    await mailSettingsStore.setEnabled(enabled)
    toast.open({
      message: enabled ? 'Envio de e-mails ativado.' : 'Envio de e-mails desativado.',
      type: 'is-success',
    })
  } catch {
    switchRenderKey.value += 1
  } finally {
    switchingEnabled.value = false
  }
}

async function sendTest() {
  const testRecipient = recipient.value.trim()
  sendingTest.value = true
  testOutcome.value = null
  try {
    await mailSettingsApi.sendTest(testRecipient)
    testOutcome.value = { sent: true, recipient: testRecipient }
  } catch (caughtError) {
    const detail = isAxiosError(caughtError) ? caughtError.response?.data?.detail : undefined
    testOutcome.value = { sent: false, detail: typeof detail === 'string' ? detail : '' }
  } finally {
    sendingTest.value = false
  }
}
</script>

<template>
  <div class="page page-narrow">
    <PageHeader
      title="E-mail"
      description="Configure o servidor que envia os e-mails do InvestLog."
    />

    <b-message v-if="demoModeEnabled" type="is-warning" has-icon :closable="false" class="mb-0">
      <strong>Indisponível no modo demonstração.</strong> As configurações de e-mail não podem ser
      alteradas e nenhum e-mail é enviado.
    </b-message>

    <SettingsSection
      title="Envio de e-mails"
      :description="switchDescription"
      :loading="mailSettingsStore.loading"
    >
      <b-switch
        :key="switchRenderKey"
        :model-value="sendingEnabled"
        :disabled="demoModeEnabled || !configured || switchingEnabled"
        @update:model-value="setSendingEnabled"
      >
        Enviar e-mails
      </b-switch>
    </SettingsSection>

    <SettingsSection
      title="Servidor SMTP"
      description="Dados do servidor que entrega as mensagens e do remetente que aparece para quem recebe."
      :loading="mailSettingsStore.loading"
    >
      <div class="fixed-grid has-4-cols">
        <div class="grid is-gap-2">
          <b-field label="Servidor" class="cell is-col-span-2 is-col-span-4-mobile">
            <b-input
              v-model="form.host"
              placeholder="smtp.seudominio.com"
              :disabled="demoModeEnabled"
            />
          </b-field>
          <b-field label="Porta" class="cell is-col-span-4-mobile">
            <b-input
              v-model="form.port"
              inputmode="numeric"
              placeholder="587"
              :disabled="demoModeEnabled"
            />
          </b-field>
          <b-field label="Segurança" class="cell is-col-span-4-mobile">
            <b-select v-model="form.security" expanded :disabled="demoModeEnabled">
              <option v-for="option in SECURITY_OPTIONS" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </b-select>
          </b-field>
          <b-field label="Usuário" class="cell is-col-span-2 is-col-span-4-mobile">
            <b-input
              v-model="form.username"
              autocomplete="off"
              placeholder="Opcional"
              :disabled="demoModeEnabled"
            />
          </b-field>
          <b-field class="cell is-col-span-2 is-col-span-4-mobile">
            <template #label>
              Senha
              <b-tooltip
                :label="PASSWORD_HINT"
                position="is-top"
                :triggers="['hover', 'focus']"
                multilined
                class="ml-1"
              >
                <span tabindex="0" :aria-label="PASSWORD_HINT">
                  <b-icon icon="information-outline" size="is-small" class="has-text-grey" />
                </span>
              </b-tooltip>
            </template>
            <b-input
              v-model="form.password"
              type="password"
              autocomplete="new-password"
              :placeholder="passwordPlaceholder"
              :disabled="demoModeEnabled"
            />
          </b-field>
          <b-field label="E-mail do remetente" class="cell is-col-span-2 is-col-span-4-mobile">
            <b-input
              v-model="form.fromAddress"
              type="email"
              placeholder="nao-responda@seudominio.com"
              :disabled="demoModeEnabled"
            />
          </b-field>
          <b-field label="Nome do remetente" class="cell is-col-span-2 is-col-span-4-mobile">
            <b-input v-model="form.fromName" placeholder="InvestLog" :disabled="demoModeEnabled" />
          </b-field>
          <b-field class="cell is-col-span-4">
            <template #label>
              URL pública do InvestLog
              <b-tooltip
                :label="PUBLIC_URL_HINT"
                position="is-top"
                :triggers="['hover', 'focus']"
                multilined
                class="ml-1"
              >
                <span tabindex="0" :aria-label="PUBLIC_URL_HINT">
                  <b-icon icon="information-outline" size="is-small" class="has-text-grey" />
                </span>
              </b-tooltip>
            </template>
            <b-input
              v-model="form.publicUrl"
              placeholder="https://investlog.seudominio.com"
              :disabled="demoModeEnabled"
            />
          </b-field>
        </div>
      </div>
      <div class="is-flex is-align-items-center is-gap-2">
        <p v-if="saveError" class="help is-danger m-0" data-testid="save-error">{{ saveError }}</p>
        <b-button
          type="is-success"
          class="ml-auto"
          :loading="saving"
          :disabled="demoModeEnabled"
          @click="saveSettings"
        >
          Salvar
        </b-button>
      </div>
    </SettingsSection>

    <SettingsSection
      title="Testar envio"
      description="Envia uma mensagem para o endereço informado usando as configurações salvas."
    >
      <b-field label="Destinatário" grouped class="mb-0">
        <b-input
          v-model="recipient"
          type="email"
          placeholder="voce@email.com"
          expanded
          :disabled="testDisabled"
        />
        <p class="control">
          <b-button
            icon-left="email-fast-outline"
            :loading="sendingTest"
            :disabled="testDisabled || recipient.trim() === ''"
            @click="sendTest"
          >
            Enviar teste
          </b-button>
        </p>
      </b-field>
      <b-notification
        v-if="testOutcome?.sent"
        type="is-success"
        :closable="false"
        class="mt-3 mb-0"
      >
        E-mail de teste enviado para {{ testOutcome.recipient }}. Confira a caixa de entrada.
      </b-notification>
      <b-notification v-else-if="testOutcome" type="is-danger" :closable="false" class="mt-3 mb-0">
        <strong>Não foi possível enviar.</strong> {{ testOutcome.detail }}
      </b-notification>
    </SettingsSection>
  </div>
</template>
