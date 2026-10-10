/**
 * Portal Móvil — rutas del App para personal operativo.
 *
 * Este módulo usa AppLayout (header + bottom-nav), no BasicLayout.
 * Por eso todas las rutas tienen hideInMenu: true — la navegación
 * se hace desde la barra inferior del AppLayout, no desde el sidebar.
 *
 * Rutas del portal (/app/*):
 *   /app/home           → Mi Jornada (resumen del turno activo)
 *   /app/actividades    → Mis Actividades (registro y seguimiento)
 *   /app/permisos       → Mis Permisos (solicitudes y delegaciones)
 *   /app/notificaciones → Notificaciones en tiempo real
 *   /app/perfil         → Mi Perfil
 *
 * Acceso por perfil:
 *   Médico / Enfermero / Cajero / Farmacia / Limpieza → todas las rutas
 *   Interno → home, actividades, ubicacion, notificaciones, perfil
 *              (no permisos — no aplica para internos)
 */
import type { RouteRecordRaw } from 'vue-router';

import { AppLayout } from '#/layouts';

import { AUTH, PERSONAL_MEDICO, ROLES } from '../sisu-roles';

// Todo el personal que accede al portal móvil
const APP_ROLES = AUTH.APP ?? [];

// Médico/Enfermero + Internos: pueden ver turnos y actividades
const MEDICO_ROLES: string[] = [...PERSONAL_MEDICO, ROLES.INTERNO];

// Roles que pueden ver actividades en el portal (excluye ADMIN/SUPERVISOR)
const ACT_ROLES = (AUTH.ACTIVIDADES ?? []).filter(
  (r) => r !== ROLES.ADMIN && r !== ROLES.SUPERVISOR,
);

// Roles que pueden solicitar permisos (excluye ADMIN/SUPERVISOR)
const PERM_ROLES = (AUTH.PERMISOS ?? []).filter(
  (r) => r !== ROLES.ADMIN && r !== ROLES.SUPERVISOR,
);

const routes: RouteRecordRaw[] = [
  {
    component: AppLayout,
    meta: {
      authority:  APP_ROLES,
      hideInMenu: true,
      hideInTab:  true,
      // CRÍTICO: sin esto Vben BORRA el `component: AppLayout` (porque la ruta tiene
      // hijos) y cuelga /app dentro de BasicLayout → se vería el dashboard de
      // escritorio, NO la app móvil, y `iniciarPushNativo()`/GPS/WS del AppLayout
      // jamás correrían. `noBasicLayout` registra /app como ruta top-level con su
      // propio AppLayout intacto. Ver packages/effects/access/src/accessible.ts.
      noBasicLayout: true,
      title:      'App',
    },
    name:     'AppRoot',
    path:     '/app',
    redirect: '/app/home',
    children: [

      {
        name:      'AppHome',
        path:      'home',
        component: () => import('#/views/sisu/movil/home/index.vue'),
        meta: {
          authority:  MEDICO_ROLES,
          hideInMenu: true,
          title:      'Mi Turno',
        },
      },

      {
        name:      'AppTurnos',
        path:      'turnos',
        component: () => import('#/views/sisu/movil/turnos/index.vue'),
        meta: {
          authority:  MEDICO_ROLES,  // médico/enfermero/interno
          hideInMenu: true,
          title:      'Mis Turnos',
        },
      },

      {
        name:      'AppActividades',
        path:      'actividades',
        component: () => import('#/views/sisu/movil/actividades/index.vue'),
        meta: {
          authority:  ACT_ROLES,    // médico/enfermero + internos
          hideInMenu: true,
          title:      'Mis Actividades',
        },
      },

      {
        name:      'AppBitacora',
        path:      'bitacora',
        component: () => import('#/views/sisu/movil/bitacora/index.vue'),
        meta: {
          authority:  MEDICO_ROLES,
          hideInMenu: true,
          title:      'Bitácora',
        },
      },

      {
        name:      'AppPermisos',
        path:      'permisos',
        component: () => import('#/views/sisu/movil/permisos/index.vue'),
        meta: {
          authority:  PERM_ROLES,   // todos los funcionarios (no internos)
          hideInMenu: true,
          title:      'Mis Permisos',
        },
      },

      {
        name:      'AppFaltas',
        path:      'faltas',
        component: () => import('#/views/sisu/movil/faltas/index.vue'),
        meta: {
          authority:  APP_ROLES,
          hideInMenu: true,
          title:      'Mis Faltas',
        },
      },

      {
        name:      'AppNotificaciones',
        path:      'notificaciones',
        component: () => import('#/views/sisu/movil/notificaciones/index.vue'),
        meta: {
          authority:  APP_ROLES,
          hideInMenu: true,
          title:      'Notificaciones',
        },
      },

      {
        name:      'AppPerfil',
        path:      'perfil',
        component: () => import('#/views/sisu/movil/perfil/index.vue'),
        meta: {
          authority:  APP_ROLES,
          hideInMenu: true,
          title:      'Mi Perfil',
        },
      },

    ],
  },
];

export default routes;
