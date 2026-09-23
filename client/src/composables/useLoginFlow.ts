import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { fieldValidationMessage } from '@/utils/apiErrors'

export type LoginStep = 'credentials' | 'register' | 'enroll' | 'totp' | 'link'

export function useLoginFlow() {
  const router = useRouter()
  const route = useRoute()
  const auth = useAuthStore()

  const step = ref<LoginStep>('credentials')
  const email = ref('')
  const password = ref('')
  const error = ref('')
  const submitting = ref(false)
  const googleAuthEnabled = ref(false)
  const qrCodeDataUri = ref('')
  const linkToken = ref('')
  const linkEmail = ref('')

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

  function toggleRegister() {
    error.value = ''
    step.value = step.value === 'register' ? 'credentials' : 'register'
  }

  async function submit(action: () => Promise<void>) {
    error.value = ''
    submitting.value = true
    try {
      await action()
    } finally {
      submitting.value = false
    }
  }

  async function submitCredentials() {
    await submit(async () => {
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
      }
    })
  }

  async function submitRegistration(name: string) {
    await submit(async () => {
      try {
        await auth.register(name, email.value, password.value)
        await router.push({ name: 'pending-approval' })
      } catch (caughtError) {
        error.value =
          fieldValidationMessage(caughtError) ??
          'Não foi possível concluir o cadastro. Verifique os dados e tente novamente.'
      }
    })
  }

  async function submitEnrollment(totpCode: string) {
    await submit(async () => {
      try {
        await auth.verifyTotp(email.value, password.value, totpCode)
      } catch {
        error.value = 'Código inválido. Tente novamente.'
      }
    })
  }

  async function submitTotpCode(totpCode: string, trustDevice: boolean) {
    await submit(async () => {
      try {
        const status = await auth.login(email.value, password.value, totpCode, trustDevice)
        if (status === 'authenticated') {
          return
        }
        error.value = 'Código inválido. Tente novamente.'
      } catch {
        error.value = 'Código inválido. Tente novamente.'
      }
    })
  }

  async function submitGoogleLink(linkPassword: string) {
    await submit(async () => {
      try {
        await auth.linkGoogleAccount(linkToken.value, linkPassword)
      } catch {
        error.value = 'Senha incorreta ou link expirado. Tente entrar com o Google novamente.'
      }
    })
  }

  return {
    step,
    email,
    password,
    error,
    submitting,
    googleAuthEnabled,
    qrCodeDataUri,
    title,
    subtitle,
    toggleRegister,
    submitCredentials,
    submitRegistration,
    submitEnrollment,
    submitTotpCode,
    submitGoogleLink,
  }
}
