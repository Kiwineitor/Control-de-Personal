<script lang="ts" setup>
import type {
  WorkbenchProjectItem,
  WorkbenchQuickNavItem,
  WorkbenchTodoItem,
  WorkbenchTrendItem,
} from '@vben/common-ui';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import {
  AnalysisChartCard,
  WorkbenchHeader,
  WorkbenchProject,
  WorkbenchQuickNav,
  WorkbenchTodo,
  WorkbenchTrends,
} from '@vben/common-ui';
import { preferences } from '@vben/preferences';
import { useUserStore } from '@vben/stores';
import { openWindow } from '@vben/utils';

import AnalyticsVisitsSource from '../analytics/analytics-visits-source.vue';

const userStore = useUserStore();

// Estos son datos de ejemplo; en el proyecto real deben ajustarse según el caso.
// La url también puede ser una ruta interna y se manejará en el método navTo para navegación interna.
// Por ejemplo: url: /dashboard/workspace
const projectItems: WorkbenchProjectItem[] = [
  {
    color: '',
    content: 'No esperes oportunidades, créalas.',
    date: '2021-04-01',
    group: 'Equipo Open Source',
    icon: 'carbon:logo-github',
    title: 'Github',
    url: 'https://github.com',
  },
  {
    color: '#3fb27f',
    content: 'El presente determina tu futuro.',
    date: '2021-04-01',
    group: 'Equipo de algoritmos',
    icon: 'ion:logo-vue',
    title: 'Vue',
    url: 'https://vuejs.org',
  },
  {
    color: '#e18525',
    content: 'Nada es más importante que el esfuerzo.',
    date: '2021-04-01',
    group: 'Trabajo informal',
    icon: 'ion:logo-html5',
    title: 'Html5',
    url: 'https://developer.mozilla.org/zh-CN/docs/Web/HTML',
  },
  {
    color: '#bf0c2c',
    content: 'La pasión y el deseo pueden superar cualquier obstáculo.',
    date: '2021-04-01',
    group: 'UI',
    icon: 'ion:logo-angular',
    title: 'Angular',
    url: 'https://angular.io',
  },
  {
    color: '#00d8ff',
    content: 'Un cuerpo sano es la base para lograr metas.',
    date: '2021-04-01',
    group: 'Equipo técnico',
    icon: 'bx:bxl-react',
    title: 'React',
    url: 'https://reactjs.org',
  },
  {
    color: '#EBD94E',
    content: 'El camino se hace caminando, no soñando.',
    date: '2021-04-01',
    group: 'Equipo de arquitectura',
    icon: 'ion:logo-javascript',
    title: 'Js',
    url: 'https://developer.mozilla.org/zh-CN/docs/Web/JavaScript',
  },
];

// Aquí la url también puede ser un enlace externo que comience con http.
const quickNavItems: WorkbenchQuickNavItem[] = [
  {
    color: '#1fdaca',
    icon: 'ion:home-outline',
    title: 'Inicio',
    url: '/',
  },
  {
    color: '#bf0c2c',
    icon: 'ion:grid-outline',
    title: 'Tablero',
    url: '/dashboard',
  },
  {
    color: '#e18525',
    icon: 'ion:layers-outline',
    title: 'Componentes',
    url: '/demos/features/icons',
  },
  {
    color: '#3fb27f',
    icon: 'ion:settings-outline',
    title: 'Administración del sistema',
    url: '/demos/features/login-expired', // Esta URL es un ejemplo; ajústala según el proyecto real.
  },
  {
    color: '#4daf1bc9',
    icon: 'ion:key-outline',
    title: 'Gestión de permisos',
    url: '/demos/access/page-control',
  },
  {
    color: '#00d8ff',
    icon: 'ion:bar-chart-outline',
    title: 'Gráficos',
    url: '/analytics',
  },
];

const todoItems = ref<WorkbenchTodoItem[]>([
  {
    completed: false,
    content: `Revisar el código frontend recientemente enviado al repositorio Git para garantizar la calidad y las normas.`,
    date: '2024-07-30 11:00:00',
    title: 'Revisar commits frontend',
  },
  {
    completed: true,
    content: `Comprobar y optimizar el rendimiento del sistema para reducir el uso de CPU.`,
    date: '2024-07-30 11:00:00',
    title: 'Optimización de rendimiento',
  },
  {
    completed: false,
    content: `Realizar una auditoría de seguridad del sistema para asegurar que no existan vulnerabilidades ni accesos no autorizados.`,
    date: '2024-07-30 11:00:00',
    title: 'Auditoría de seguridad',
  },
  {
    completed: false,
    content: `Actualizar todas las dependencias npm del proyecto para asegurarse de usar la versión más reciente.`,
    date: '2024-07-30 11:00:00',
    title: 'Actualizar dependencias',
  },
  {
    completed: false,
    content: `Corregir los problemas de visualización de la UI reportados por usuarios para asegurar consistencia entre navegadores.`,
    date: '2024-07-30 11:00:00',
    title: 'Corregir UI',
  },
]);
const trendItems: WorkbenchTrendItem[] = [
  {
    avatar: 'svg:avatar-1',
    content: `Creó el proyecto <a>Vue</a> en <a>Equipo Open Source</a>`,
    date: 'Justo ahora',
    title: 'William',
  },
  {
    avatar: 'svg:avatar-2',
    content: `Empezó a seguir a <a>William</a>`,
    date: 'Hace 1 hora',
    title: 'Evan',
  },
  {
    avatar: 'svg:avatar-3',
    content: `Publicó una <a>actualización personal</a>`,
    date: 'Hace 1 día',
    title: 'Chris',
  },
  {
    avatar: 'svg:avatar-4',
    content: `Publicó el artículo <a>Cómo escribir un plugin de Vite</a>`,
    date: 'Hace 2 días',
    title: 'Vben',
  },
  {
    avatar: 'svg:avatar-1',
    content: `Respondió a la pregunta de <a>Jack</a> <a>¿Cómo optimizar el proyecto?</a>`,
    date: 'Hace 3 días',
    title: 'Pete',
  },
  {
    avatar: 'svg:avatar-2',
    content: `Cerró la incidencia <a>Cómo ejecutar el proyecto</a>`,
    date: 'Hace 1 semana',
    title: 'Jack',
  },
  {
    avatar: 'svg:avatar-3',
    content: `Publicó una <a>actualización personal</a>`,
    date: 'Hace 1 semana',
    title: 'William',
  },
  {
    avatar: 'svg:avatar-4',
    content: `Envié código a <a>Github</a>`,
    date: '2021-04-01 20:00',
    title: 'William',
  },
  {
    avatar: 'svg:avatar-4',
    content: `Publicó el artículo <a>Cómo usar Admin Vben</a>`,
    date: '2021-03-01 20:00',
    title: 'Vben',
  },
];

const router = useRouter();

// 这是一个示例方法，实际项目中需要根据实际情况进行调整
// This is a sample method, adjust according to the actual project requirements
function navTo(nav: WorkbenchProjectItem | WorkbenchQuickNavItem) {
  if (nav.url?.startsWith('http')) {
    openWindow(nav.url);
    return;
  }
  if (nav.url?.startsWith('/')) {
    router.push(nav.url).catch((error) => {
      console.error('Navigation failed:', error);
    });
  } else {
    console.warn(`Unknown URL for navigation item: ${nav.title} -> ${nav.url}`);
  }
}
</script>

<template>
  <div class="p-5">
    <WorkbenchHeader
      :avatar="userStore.userInfo?.avatar || preferences.app.defaultAvatar"
    >
      <template #title>
        Buenos días, {{ userStore.userInfo?.realName }}, ¡comienza tu día de trabajo!
      </template>
      <template #description> Hoy soleado, 20℃ - 32℃. </template>
    </WorkbenchHeader>

    <div class="mt-5 flex flex-col lg:flex-row">
      <div class="mr-4 w-full lg:w-3/5">
        <WorkbenchProject :items="projectItems" title="Proyectos" @click="navTo" />
        <WorkbenchTrends :items="trendItems" class="mt-5" title="Últimas actividades" />
      </div>
      <div class="w-full lg:w-2/5">
        <WorkbenchQuickNav
          :items="quickNavItems"
          class="mt-5 lg:mt-0"
          title="Navegación rápida"
          @click="navTo"
        />
        <WorkbenchTodo :items="todoItems" class="mt-5" title="Tareas pendientes" />
        <AnalysisChartCard class="mt-5" title="Origen de visitas">
          <AnalyticsVisitsSource />
        </AnalysisChartCard>
      </div>
    </div>
  </div>
</template>
