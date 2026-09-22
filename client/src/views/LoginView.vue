<script setup lang="ts">
import AuthAside from '@/components/auth/AuthAside.vue'
import CredentialsStep from '@/components/auth/CredentialsStep.vue'
import GoogleLinkStep from '@/components/auth/GoogleLinkStep.vue'
import RegisterStep from '@/components/auth/RegisterStep.vue'
import TotpEnrollStep from '@/components/auth/TotpEnrollStep.vue'
import TotpVerifyStep from '@/components/auth/TotpVerifyStep.vue'
import LogoMark from '@/components/icons/LogoMark.vue'
import { useLoginFlow } from '@/composables/useLoginFlow'

const {
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
} = useLoginFlow()
</script>

<template>
  <div class="auth-root">
    <AuthAside />

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

        <CredentialsStep
          v-if="step === 'credentials'"
          v-model:email="email"
          v-model:password="password"
          :submitting="submitting"
          :google-auth-enabled="googleAuthEnabled"
          @submit="submitCredentials"
          @toggle-register="toggleRegister"
        />

        <RegisterStep
          v-else-if="step === 'register'"
          v-model:email="email"
          v-model:password="password"
          :submitting="submitting"
          @submit="submitRegistration"
          @toggle-register="toggleRegister"
        />

        <TotpEnrollStep
          v-else-if="step === 'enroll'"
          :submitting="submitting"
          :qr-code-data-uri="qrCodeDataUri"
          @submit="submitEnrollment"
        />

        <TotpVerifyStep
          v-else-if="step === 'totp'"
          :submitting="submitting"
          @submit="submitTotpCode"
        />

        <GoogleLinkStep v-else :submitting="submitting" @submit="submitGoogleLink" />
      </div>
    </main>
  </div>
</template>
