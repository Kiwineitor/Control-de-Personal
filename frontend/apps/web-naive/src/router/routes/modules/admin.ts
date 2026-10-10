import type { RouteRecordRaw } from 'vue-router';
import { VISTAS } from '#/router/routes/sisu-roles';

const routes: RouteRecordRaw[] = [

  // ── Personal del Hospital (ADMIN + SUPERVISOR) ─────────────────────────
  {
    meta: {
      authority: [VISTAS.PERSONAL],
      icon:      'lucide:users',
      order:     10,
      title:     'Personal del Hospital',
    },
    name: 'PersonalHospital',
    path: '/personal-hospital',
    children: [
      {
        name:      'AdminPersonal',
        path:      '/admin/personal',
        component: () => import('#/views/admin/personal/index.vue'),
        meta: {
          icon:      'lucide:user-check',
          title:     'Gestión de Personal',
          authority: [VISTAS.PERSONAL],
        },
      },
    ],
  },

  // ── Configuración del Sistema (solo ADMIN) ────────────────────────────
  {
    meta: {
      authority: [VISTAS.CONFIGURACION],
      icon:      'lucide:settings-2',
      order:     20,
      title:     'Configuración del Sistema',
    },
    name: 'SistemaConfig',
    path: '/sistema',
    children: [
      {
        name:      'AdminConfiguracion',
        path:      '/admin/configuracion',
        component: () => import('#/views/admin/configuracion/index.vue'),
        meta: {
          icon:      'lucide:settings',
          title:     'Usuarios, Roles y Configuración',
          authority: [VISTAS.CONFIGURACION],
        },
      },
      {
        name: 'AdminPruebasNotificaciones',
        path: '/admin/pruebas-notificaciones',
        component: () => import('#/views/admin/pruebas-notificaciones/index.vue'),
        meta: {
          icon: 'lucide:send',
          title: 'Notificaciones y Telegram',
          authority: [VISTAS.CONFIGURACION],
        },
      },
      {
        name:      'AdminConsola',
        path:      '/admin/consola',
        component: () => import('#/views/admin/consola/index.vue'),
        meta: {
          icon:      'lucide:terminal',
          title:     'Consola del Sistema',
          authority: [VISTAS.CONSOLA],
        },
      },
    ],
  },

];

export default routes;
