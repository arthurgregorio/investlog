<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import LogoMark from '@/components/icons/LogoMark.vue'
import PasswordRequirementHint from '@/components/forms/PasswordRequirementHint.vue'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api/auth'
import { fieldValidationMessage } from '@/utils/apiErrors'
import { meetsPasswordRequirements } from '@/utils/passwordRules'

const router = useRouter()
const auth = useAuthStore()
const route = useRoute()
const googleAuthEnabled = ref(false)

type Step = 'credentials' | 'register' | 'enroll' | 'totp' | 'link'

const step = ref<Step>('credentials')
const name = ref('')
const email = ref('')
const password = ref('')
const totpCode = ref('')
const qrCodeDataUri = ref('')
const error = ref('')
const submitting = ref(false)
const linkToken = ref('')
const linkEmail = ref('')
const linkPassword = ref('')

onMounted(async () => {
  const queryLinkToken = route.query.linkToken
  if (route.query.error === 'email_in_use' && typeof queryLinkToken === 'string') {
    linkToken.value = queryLinkToken
    linkEmail.value = typeof route.query.linkEmail === 'string' ? route.query.linkEmail : ''
    step.value = 'link'
  } else if (route.query.error === 'email_in_use') {
    error.value = 'Já existe uma conta com este e-mail. Entre com e-mail e senha.'
  } else if (route.query.error === 'oauth_failed') {
    error.value = 'Não foi possível entrar com o Google. Tente novamente.'
  }
  const config = await authApi.fetchConfig()
  googleAuthEnabled.value = config.googleAuthEnabled
})

const title = computed(() => {
  if (step.value === 'register') return 'Criar conta'
  if (step.value === 'enroll') return 'Configure a autenticação em duas etapas'
  if (step.value === 'totp') return 'Confirme o código de autenticação'
  if (step.value === 'link') return 'Vincular conta ao Google'
  return 'Bem-vindo de volta'
})

const subtitle = computed(() => {
  if (step.value === 'register')
    return 'Sua conta ficará pendente até que um administrador a aprove.'
  if (step.value === 'enroll')
    return 'Escaneie o QR code com um aplicativo autenticador e digite o código gerado.'
  if (step.value === 'totp') return 'Digite o código do seu aplicativo autenticador.'
  if (step.value === 'link')
    return `Já existe uma conta para ${linkEmail.value}. Informe a senha para vincular ao Google.`
  return 'Entre para acompanhar seus investimentos.'
})

const registrationValid = computed(
  () =>
    name.value.trim().length > 0 &&
    email.value.trim().length > 0 &&
    meetsPasswordRequirements(password.value),
)

function toggleRegister() {
  error.value = ''
  step.value = step.value === 'register' ? 'credentials' : 'register'
}

async function submitCredentials() {
  error.value = ''
  submitting.value = true
  try {
    const status = await auth.login(email.value, password.value)
    if (status === 'authenticated') {
      return
    }
    if (status === 'needs_enrollment') {
      const enrollment = await auth.enrollTotp(email.value, password.value)
      qrCodeDataUri.value = enrollment.qrCodeDataUri
      step.value = 'enroll'
      return
    }
    if (status === 'totp_required') {
      step.value = 'totp'
      return
    }
    error.value = 'E-mail ou senha inválidos.'
  } catch {
    error.value = 'E-mail ou senha inválidos.'
  } finally {
    submitting.value = false
  }
}

async function submitRegistration() {
  error.value = ''
  submitting.value = true
  try {
    await auth.register(name.value, email.value, password.value)
    await router.push({ name: 'pending-approval' })
  } catch (caughtError) {
    error.value =
      fieldValidationMessage(caughtError) ??
      'Não foi possível concluir o cadastro. Verifique os dados e tente novamente.'
  } finally {
    submitting.value = false
  }
}

async function submitEnrollment() {
  error.value = ''
  submitting.value = true
  try {
    await auth.verifyTotp(email.value, password.value, totpCode.value)
  } catch {
    error.value = 'Código inválido. Tente novamente.'
  } finally {
    submitting.value = false
  }
}

async function submitTotpCode() {
  error.value = ''
  submitting.value = true
  try {
    const status = await auth.login(email.value, password.value, totpCode.value)
    if (status === 'authenticated') {
      return
    }
    error.value = 'Código inválido. Tente novamente.'
  } catch {
    error.value = 'Código inválido. Tente novamente.'
  } finally {
    submitting.value = false
  }
}

async function submitGoogleLink() {
  error.value = ''
  submitting.value = true
  try {
    await auth.linkGoogleAccount(linkToken.value, linkPassword.value)
  } catch {
    error.value = 'Senha incorreta ou link expirado. Tente entrar com o Google novamente.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="auth-root">
    <aside class="auth-aside">
      <div class="auth-aside-top">
        <div class="auth-logo">
          <span class="auth-logo-mark"><LogoMark :size="26" /></span>
          <span class="auth-logo-name">Invest<b>Log</b></span>
        </div>
      </div>
      <div class="auth-aside-mid">
        <h2 class="auth-headline">Seu diário de<br />investimentos,<br />sempre em dia.</h2>
        <p class="auth-tagline">
          Registre aportes, acompanhe carteiras em várias moedas e veja seu patrimônio evoluir —
          tudo em um só lugar.
        </p>
      </div>
      <ul class="auth-points">
        <li><span class="ap-dot" />Carteiras multi-moeda consolidadas</li>
        <li><span class="ap-dot" />Resultado por ativo, tipo e período</li>
        <li><span class="ap-dot" />Histórico completo de cada aporte</li>
      </ul>
      <div class="auth-deco" aria-hidden="true">
        <span style="height: 34%" /><span style="height: 52%" /> <span style="height: 46%" /><span
          style="height: 68%"
        />
        <span style="height: 60%" /><span style="height: 84%" /> <span style="height: 76%" /><span
          style="height: 100%"
        />
      </div>
    </aside>

    <main class="auth-main">
      <div class="auth-card">
        <div class="auth-card-brand">
          <span class="brand-mark"><LogoMark :size="20" /></span>
          <span class="brand-name">Invest<b>Log</b></span>
        </div>

        <div class="auth-head">
          <h1 class="auth-title">{{ title }}</h1>
          <p class="auth-sub">{{ subtitle }}</p>
        </div>

        <p v-if="error" class="auth-error">{{ error }}</p>

        <form v-if="step === 'credentials'" class="form-stack" @submit.prevent="submitCredentials">
          <b-field label="E-mail">
            <b-input v-model="email" type="email" placeholder="voce@email.com" required />
          </b-field>
          <b-field label="Senha">
            <b-input v-model="password" type="password" placeholder="••••••••" required />
          </b-field>
          <b-button
            type="is-primary"
            expanded
            native-type="submit"
            :loading="submitting"
            class="auth-submit has-text-light"
          >
            Entrar
          </b-button>
          <button
            type="button"
            class="auth-toggle"
            data-testid="toggle-register"
            @click="toggleRegister"
          >
            Não tem uma conta? Criar conta
          </button>
          <div v-if="googleAuthEnabled" class="auth-divider">ou</div>
          <a
            v-if="googleAuthEnabled"
            href="/private/oauth2/authorization/google"
            class="auth-google-button"
          >
            Continuar com Google
          </a>
        </form>

        <form
          v-else-if="step === 'register'"
          class="form-stack"
          @submit.prevent="submitRegistration"
        >
          <b-field label="Nome">
            <b-input v-model="name" type="text" placeholder="Seu nome" required />
          </b-field>
          <b-field label="E-mail">
            <b-input v-model="email" type="email" placeholder="voce@email.com" required />
          </b-field>
          <div class="field-with-hint">
            <b-field label="Senha">
              <b-input v-model="password" type="password" placeholder="••••••••" required />
            </b-field>
            <PasswordRequirementHint :password="password" />
          </div>
          <b-button
            type="is-primary"
            expanded
            native-type="submit"
            :loading="submitting"
            :disabled="!registrationValid"
            class="auth-submit has-text-light"
          >
            Criar conta
          </b-button>
          <button
            type="button"
            class="auth-toggle"
            data-testid="toggle-register"
            @click="toggleRegister"
          >
            Já tem uma conta? Entrar
          </button>
        </form>

        <form v-else-if="step === 'enroll'" class="form-stack" @submit.prevent="submitEnrollment">
          <img
            :src="qrCodeDataUri"
            alt="QR code para configurar a autenticação em duas etapas"
            class="auth-totp-qr"
          />
          <b-field label="Código de 6 dígitos">
            <b-input v-model="totpCode" maxlength="6" placeholder="000000" required />
          </b-field>
          <b-button
            type="is-primary"
            expanded
            native-type="submit"
            :loading="submitting"
            class="auth-submit has-text-light"
          >
            Confirmar
          </b-button>
        </form>

        <form v-else-if="step === 'totp'" class="form-stack" @submit.prevent="submitTotpCode">
          <b-field label="Código de 6 dígitos">
            <b-input v-model="totpCode" maxlength="6" placeholder="000000" required />
          </b-field>
          <b-button
            type="is-primary"
            expanded
            native-type="submit"
            :loading="submitting"
            class="auth-submit has-text-light"
          >
            Entrar
          </b-button>
        </form>

        <form v-else class="form-stack" @submit.prevent="submitGoogleLink">
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
      </div>
    </main>
  </div>
</template>
