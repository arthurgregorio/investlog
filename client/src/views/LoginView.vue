<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import LogoMark from '@/components/icons/LogoMark.vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

type Step = 'credentials' | 'register' | 'enroll' | 'totp'

const step = ref<Step>('credentials')
const name = ref('')
const email = ref('')
const password = ref('')
const totpCode = ref('')
const qrCodeDataUri = ref('')
const error = ref('')
const submitting = ref(false)

const title = computed(() => {
  if (step.value === 'register') return 'Criar conta'
  if (step.value === 'enroll') return 'Configure a autenticação em duas etapas'
  if (step.value === 'totp') return 'Confirme o código de autenticação'
  return 'Bem-vindo de volta'
})

const subtitle = computed(() => {
  if (step.value === 'register') return 'Sua conta ficará pendente até que um administrador a aprove.'
  if (step.value === 'enroll') return 'Escaneie o QR code com um aplicativo autenticador e digite o código gerado.'
  if (step.value === 'totp') return 'Digite o código do seu aplicativo autenticador.'
  return 'Entre para acompanhar seus investimentos.'
})

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
      await router.push({ name: 'overview' })
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
  } catch {
    error.value = 'Não foi possível concluir o cadastro. Verifique os dados e tente novamente.'
  } finally {
    submitting.value = false
  }
}

async function submitEnrollment() {
  error.value = ''
  submitting.value = true
  try {
    await auth.verifyTotp(email.value, password.value, totpCode.value)
    await router.push({ name: 'overview' })
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
      await router.push({ name: 'overview' })
      return
    }
    error.value = 'Código inválido. Tente novamente.'
  } catch {
    error.value = 'Código inválido. Tente novamente.'
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
          Registre aportes, acompanhe carteiras em várias moedas e veja seu patrimônio
          evoluir — tudo em um só lugar.
        </p>
      </div>
      <ul class="auth-points">
        <li><span class="ap-dot" />Carteiras multi-moeda consolidadas</li>
        <li><span class="ap-dot" />Resultado por ativo, tipo e período</li>
        <li><span class="ap-dot" />Histórico completo de cada aporte</li>
      </ul>
      <div class="auth-deco" aria-hidden="true">
        <span style="height: 34%" /><span style="height: 52%" />
        <span style="height: 46%" /><span style="height: 68%" />
        <span style="height: 60%" /><span style="height: 84%" />
        <span style="height: 76%" /><span style="height: 100%" />
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
          <b-button type="is-primary" expanded native-type="submit" :loading="submitting" class="auth-submit">
            Entrar
          </b-button>
          <button type="button" class="auth-toggle" data-testid="toggle-register" @click="toggleRegister">
            Não tem uma conta? Criar conta
          </button>
        </form>

        <form v-else-if="step === 'register'" class="form-stack" @submit.prevent="submitRegistration">
          <b-field label="Nome">
            <b-input v-model="name" type="text" placeholder="Seu nome" required />
          </b-field>
          <b-field label="E-mail">
            <b-input v-model="email" type="email" placeholder="voce@email.com" required />
          </b-field>
          <b-field label="Senha">
            <b-input v-model="password" type="password" placeholder="••••••••" required />
          </b-field>
          <b-button type="is-primary" expanded native-type="submit" :loading="submitting" class="auth-submit">
            Criar conta
          </b-button>
          <button type="button" class="auth-toggle" data-testid="toggle-register" @click="toggleRegister">
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
          <b-button type="is-primary" expanded native-type="submit" :loading="submitting" class="auth-submit">
            Confirmar
          </b-button>
        </form>

        <form v-else class="form-stack" @submit.prevent="submitTotpCode">
          <b-field label="Código de 6 dígitos">
            <b-input v-model="totpCode" maxlength="6" placeholder="000000" required />
          </b-field>
          <b-button type="is-primary" expanded native-type="submit" :loading="submitting" class="auth-submit">
            Entrar
          </b-button>
        </form>
      </div>
    </main>
  </div>
</template>
