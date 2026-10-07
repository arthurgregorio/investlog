<script setup lang="ts">
import LogoMark from '@/components/icons/LogoMark.vue'

const DECORATION_BAR_HEIGHTS = [34, 52, 46, 68, 60, 84, 76, 100]
</script>

<template>
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
        Registre aportes, acompanhe carteiras em várias moedas e veja seu patrimônio evoluir — tudo
        em um só lugar.
      </p>
    </div>
    <ul class="auth-points">
      <li><span class="ap-dot" />Carteiras multi-moeda consolidadas</li>
      <li><span class="ap-dot" />Resultado por ativo, tipo e período</li>
      <li><span class="ap-dot" />Histórico completo de cada aporte</li>
    </ul>
    <div class="auth-deco" aria-hidden="true">
      <span
        v-for="(barHeight, index) in DECORATION_BAR_HEIGHTS"
        :key="index"
        :style="{ height: `${barHeight}%` }"
      />
    </div>
  </aside>
</template>
