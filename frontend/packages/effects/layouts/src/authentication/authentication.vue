<script setup lang="ts">
import { computed } from 'vue';

import { preferences, usePreferences } from '@vben/preferences';

import AuthenticationFormView from './form.vue';

interface Props {
  appName?: string;
  pageTitle?: string;
  pageDescription?: string;
  logo?: string;
}

const props = withDefaults(defineProps<Props>(), {
  appName: '',
  pageDescription: '',
  pageTitle: '',
  logo: '/logo512.png',
});

const { isDark } = usePreferences();

const brandName = computed(
  () => props.appName || preferences.app.name || 'SICOPE',
);
</script>

<template>
  <div
    :class="[isDark ? 'dark' : '']"
    class="auth-shell flex min-h-full flex-1 overflow-hidden select-none"
  >
    <!-- ───────────── Panel de marca (sólo escritorio / web) ───────────── -->
    <div class="brand-panel relative hidden flex-1 overflow-hidden lg:block">
      <!-- Capas decorativas -->
      <div class="brand-gradient absolute inset-0"></div>
      <div class="brand-grid absolute inset-0"></div>
      <div class="brand-blob brand-blob--red"></div>
      <div class="brand-blob brand-blob--blue"></div>

      <!-- Línea de electrocardiograma (motivo médico) -->
      <svg
        class="brand-ekg absolute inset-x-0 bottom-[12%]"
        fill="none"
        preserveAspectRatio="none"
        viewBox="0 0 1200 120"
        xmlns="http://www.w3.org/2000/svg"
      >
        <path
          class="brand-ekg__path"
          d="M0 60 H360 l24 -44 l30 88 l26 -68 l22 44 H760 l24 -52 l30 96 l26 -76 l22 44 H1200"
          stroke="currentColor"
          stroke-linecap="round"
          stroke-linejoin="round"
          stroke-width="3"
        />
      </svg>

      <!-- Contenido centrado -->
      <div
        class="relative z-10 flex h-full flex-col items-center justify-center px-12 text-center"
      >
        <div class="-enter-x logo-halo">
          <span class="pulse-ring"></span>
          <span class="pulse-ring pulse-ring--delay"></span>
          <!-- Panel navy (fondo oscuro) → variante BLANCA del logo. -->
          <img :alt="brandName" src="/logoBlanco.png" class="brand-logo" />
        </div>

        <h1 class="-enter-x brand-name">{{ brandName }}</h1>

        <p v-if="pageTitle" class="-enter-x brand-subtitle">{{ pageTitle }}</p>

        <div class="-enter-x brand-divider"></div>

        <span v-if="pageDescription" class="-enter-x brand-tagline">
          {{ pageDescription }}
        </span>
      </div>
    </div>

    <!-- ───────────── Panel del formulario (web + app) ───────────── -->
    <AuthenticationFormView
      class="auth-form-panel min-h-full w-full lg:w-[460px] lg:flex-none"
      data-side="right"
    >
      <!-- Cabecera de marca compacta — sólo móvil / app -->
      <div class="mobile-brand flex flex-col items-center lg:hidden">
        <img
          :alt="brandName"
          :src="logo"
          class="enter-y mobile-logo"
        />
        <h1 class="enter-y mobile-name">{{ brandName }}</h1>
        <span v-if="pageTitle" class="enter-y mobile-subtitle">
          {{ pageTitle }}
        </span>
      </div>
    </AuthenticationFormView>
  </div>
</template>

<style scoped>
.auth-shell {
  /* Paleta de identidad SICOPE (extraída del logo institucional) */
  --brand-navy-900: #11293f;
  --brand-navy: #21476f;
  --brand-blue: #3a78b5;
  --brand-red: #e0483b;
}

/* ───────────── Panel de marca ───────────── */
.brand-gradient {
  background: linear-gradient(
    155deg,
    var(--brand-navy-900) 0%,
    #1d4063 42%,
    #285085 74%,
    #2f5d97 100%
  );
}

.brand-grid {
  background-image:
    linear-gradient(rgb(255 255 255 / 6%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(255 255 255 / 6%) 1px, transparent 1px);
  background-size: 46px 46px;
  -webkit-mask-image: radial-gradient(
    circle at 50% 38%,
    #000 0%,
    transparent 72%
  );
  mask-image: radial-gradient(circle at 50% 38%, #000 0%, transparent 72%);
}

.brand-blob {
  position: absolute;
  border-radius: 9999px;
  filter: blur(90px);
  pointer-events: none;
}

.brand-blob--red {
  top: -90px;
  right: -60px;
  width: 380px;
  height: 380px;
  background: var(--brand-red);
  opacity: 0.32;
}

.brand-blob--blue {
  bottom: -140px;
  left: -110px;
  width: 480px;
  height: 480px;
  background: var(--brand-blue);
  opacity: 0.45;
}

.brand-ekg {
  height: 120px;
  color: #fff;
  opacity: 0.22;
}

.brand-ekg__path {
  stroke-dasharray: 1600;
  stroke-dashoffset: 1600;
  animation: ekg-draw 6s ease-in-out infinite;
}

@keyframes ekg-draw {
  0% {
    stroke-dashoffset: 1600;
  }

  55%,
  100% {
    stroke-dashoffset: 0;
  }
}

/* Logo con halo y anillos de pulso */
.logo-halo {
  position: relative;
  display: grid;
  place-items: center;
  width: 200px;
  height: 200px;
  margin-bottom: 2rem;
}

.brand-logo {
  position: relative;
  z-index: 2;
  width: 150px;
  height: 150px;
  object-fit: contain;
  animation: float 6s ease-in-out infinite;
}

.pulse-ring {
  position: absolute;
  inset: 18px;
  border: 2px solid rgb(255 255 255 / 35%);
  border-radius: 9999px;
  animation: brand-pulse 3.2s ease-out infinite;
}

.pulse-ring--delay {
  animation-delay: 1.6s;
}

@keyframes brand-pulse {
  0% {
    transform: scale(0.75);
    opacity: 0.7;
  }

  100% {
    transform: scale(1.9);
    opacity: 0;
  }
}

.brand-name {
  font-size: 3.1rem;
  font-weight: 800;
  line-height: 1;
  letter-spacing: 0.09em;
  color: #fff;
  text-shadow: 0 8px 30px rgb(0 0 0 / 35%);
}

.brand-subtitle {
  max-width: 27rem;
  margin-top: 1.05rem;
  font-size: 1rem;
  font-weight: 500;
  line-height: 1.5;
  letter-spacing: 0.04em;
  color: rgb(255 255 255 / 80%);
}

.brand-divider {
  width: 66px;
  height: 3px;
  margin: 1.7rem 0;
  border-radius: 9999px;
  background: linear-gradient(90deg, var(--brand-red), #ff927f);
}

.brand-tagline {
  display: inline-block;
  max-width: 30rem;
  padding: 0.55rem 1.15rem;
  font-size: 0.84rem;
  color: rgb(255 255 255 / 88%);
  background: rgb(255 255 255 / 8%);
  border: 1px solid rgb(255 255 255 / 16%);
  border-radius: 9999px;
  backdrop-filter: blur(6px);
  word-break: break-word;
  overflow-wrap: anywhere;
}

/* ───────────── Cabecera de marca móvil ───────────── */
.mobile-brand {
  margin-bottom: 1.75rem;
}

.mobile-logo {
  width: 132px;
  height: 132px;
  object-fit: contain;
}

.mobile-name {
  margin-top: 0.95rem;
  font-size: 1.7rem;
  font-weight: 800;
  letter-spacing: 0.06em;
  color: var(--brand-navy);
}

.mobile-subtitle {
  max-width: 18rem;
  margin-top: 0.3rem;
  font-size: 0.82rem;
  font-weight: 500;
  line-height: 1.4;
  letter-spacing: 0.02em;
  text-align: center;
  color: var(--color-muted-foreground);
}

.dark .mobile-name {
  color: #fff;
}

/* ───────────── Modernización del formulario (web + app) ───────────── */
.auth-form-panel :deep(.side-content input) {
  height: 2.75rem;
  border-radius: 0.65rem;
  transition:
    border-color 0.15s ease,
    box-shadow 0.2s ease;
}

.auth-form-panel :deep(.side-content input:focus),
.auth-form-panel :deep(.side-content input:focus-visible) {
  border-color: var(--brand-blue);
  box-shadow: 0 0 0 3px rgb(58 120 181 / 18%);
}

.auth-form-panel :deep(.side-content [aria-label='login']) {
  height: 2.85rem;
  font-weight: 600;
  letter-spacing: 0.02em;
  border: none;
  border-radius: 0.75rem;
  background-image: linear-gradient(
    120deg,
    var(--brand-navy) 0%,
    var(--brand-blue) 100%
  );
  box-shadow: 0 12px 26px -12px rgb(33 71 111 / 75%);
  transition:
    transform 0.15s ease,
    box-shadow 0.2s ease,
    filter 0.2s ease;
}

.auth-form-panel :deep(.side-content [aria-label='login']:hover) {
  filter: brightness(1.07);
  box-shadow: 0 16px 30px -12px rgb(33 71 111 / 85%);
  transform: translateY(-1px);
}

.auth-form-panel :deep(.side-content [aria-label='login']:active) {
  transform: translateY(0);
}
</style>
