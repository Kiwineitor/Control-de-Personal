<script lang="ts" setup>
import type { VbenFormSchema } from '@vben/common-ui';

import { AuthenticationLogin, z } from '@vben/common-ui';

import { useAuthStore } from '#/store';

defineOptions({ name: 'Login' });

const authStore = useAuthStore();

const formSchema: VbenFormSchema[] = [
  {
    component: 'VbenInput',
    componentProps: {
      placeholder: 'Usuario',
    },
    fieldName: 'username',
    label: 'Usuario',
    rules: z.string().min(1, { message: 'El usuario es requerido' }),
  },
  {
    component: 'VbenInputPassword',
    componentProps: {
      placeholder: 'Contraseña',
    },
    fieldName: 'password',
    label: 'Contraseña',
    rules: z.string().min(1, { message: 'La contraseña es requerida' }),
  },
];
</script>

<template>
  <AuthenticationLogin
    :form-schema="formSchema"
    :loading="authStore.loginLoading"
    sub-title="Ingresa con tu usuario y contraseña para acceder al sistema"
    title="Bienvenido"
    @submit="authStore.authLogin"
  />
</template>

<!--
  SIN `scoped` a propósito: el título y el subtítulo los pinta AuthenticationLogin
  desde @vben/common-ui con las clases `text-foreground` / `text-muted-foreground`,
  que leen tokens globales del tema. Un estilo scoped nunca los alcanzaría.

  El override se limita a `.auth-shell` —la raíz del layout de autenticación—, así
  que sólo afecta a las pantallas de login/recuperación y el panel de escritorio
  conserva su tema intacto. Los valores van en el formato "H S% L%" que espera
  Vben, porque el consumo es `hsl(var(--foreground))`.

  Objetivo: que el login comparta la tinta navy del portal móvil en lugar del negro
  por defecto, para que no haya salto de color entre iniciar sesión y el inicio.
-->
<style>
/*
  `:not(.dark)` es imprescindible: el layout pone la clase `dark` en ESE MISMO
  elemento, así que sin el guard esta tinta navy se aplicaría también en modo
  oscuro y quedaría texto oscuro sobre fondo oscuro. En oscuro se dejan los
  tokens del tema, que ya tienen el contraste resuelto.
*/
.auth-shell:not(.dark) {
  /* #0f2547 — la misma tinta navy que usa la vista de inicio */
  --foreground: 216 65% 17%;
  /* #5b6b85 — gris azulado para el subtítulo, en la misma familia */
  --muted-foreground: 217 19% 44%;
}
</style>
