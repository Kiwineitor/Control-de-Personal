<script lang="ts" setup>
import type { NotificationType } from 'naive-ui';

import { Page } from '@vben/common-ui';

import { NButton, NCard, NSpace, useMessage, useNotification } from 'naive-ui';

const notification = useNotification();

const message = useMessage();

function error() {
  message.error('Érase una vez que vestías tan bien');
}

function warning() {
  message.warning('¿Cuántos caminos debe recorrer un hombre?');
}

function success() {
  message.success('Porque caminabas de la mano con otro hombre en mi lugar');
}

function loading() {
  message.loading(
    'Si yo fuera tú, me daría cuenta de que te amo más que cualquier otro chico',
  );
}

function notify(type: NotificationType) {
  notification[type]({
    content: '¿Qué debería decir?',
    duration: 2500,
    keepAliveOnHover: true,
    meta: 'No se me ocurre nada',
  });
}
</script>

<template>
  <Page 
    description="Soporta múltiples idiomas, integración de funciones de tema y cambio dinámico" 
    title="Demostración de uso de componentes Naive"
  >
    <NCard class="mb-5" title="Botones">
      <NSpace>
        <NButton>Predeterminado</NButton>
        <NButton type="tertiary"> Terciario </NButton>
        <NButton type="primary"> Primario </NButton>
        <NButton type="info"> Información </NButton>
        <NButton type="success"> Éxito </NButton>
        <NButton type="warning"> Advertencia </NButton>
        <NButton type="error"> Error </NButton>
      </NSpace>
    </NCard>

    <NCard class="mb-5" title="Mensajes (Message)">
      <NSpace>
        <NButton type="error" @click="error"> Error </NButton>
        <NButton type="warning" @click="warning"> Advertencia </NButton>
        <NButton type="success" @click="success"> Éxito </NButton>
        <NButton type="primary" @click="loading"> Cargando </NButton>
      </NSpace>
    </NCard>

    <NCard class="mb-5" title="Notificaciones (Notification)">
      <NSpace>
        <NButton type="error" @click="notify('error')"> Error </NButton>
        <NButton type="warning" @click="notify('warning')"> Advertencia </NButton>
        <NButton type="success" @click="notify('success')"> Éxito </NButton>
        <NButton type="primary" @click="notify('info')"> Información </NButton>
      </NSpace>
    </NCard>
  </Page>
</template>