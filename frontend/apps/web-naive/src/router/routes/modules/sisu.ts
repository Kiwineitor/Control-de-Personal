/**
 * Módulo de rutas del panel administrativo SISU.
 *
 * Matriz de acceso por perfil:
 * ────────────────────────────────────────────────────────────────────────────
 *  Ruta                        │ ADMIN │ SUPERVISOR │ Méd/Enf/Caj/Farm/Limp │ Interno
 * ────────────────────────────────────────────────────────────────────────────────
 *  Jornadas                    │  ✓   │     ✓      │           –            │    –
 *  Gestión de Actividades      │  ✓   │     ✓      │           –            │    –
 *  Mis Actividades             │  –   │     –      │           ✓            │    ✓
 *  Permisos & Delegaciones     │  ✓   │     ✓      │           –            │    –
 *  Mis Permisos                │  –   │     –      │           ✓            │    –
 *  Vigilancia en Vivo          │  ✓   │     ✓      │           –            │    –
 *  Herramientas de Monitoreo   │  ✓   │     ✓      │           –            │    –
 * ────────────────────────────────────────────────────────────────────────────
 *
 * El portal móvil /app/* (AppLayout con bottom-nav) también incluye
 * /app/permisos para el personal operativo, definido en funcionario.ts.
 */
import type { RouteRecordRaw } from 'vue-router';
import { AUTH, ROLES, VISTAS } from '#/router/routes/sisu-roles';

// Personal médico+asistencial + internos (para actividades en el portal web)
const EMPLEADOS_MEDICOS = (AUTH['ACTIVIDADES'] ?? []).filter(
  (r) => r !== ROLES.ADMIN && r !== ROLES.SUPERVISOR,
);

// Funcionarios que pueden solicitar permisos (sin admin/supervisor/interno)
const FUNCIONARIOS_PERM = (AUTH['PERMISOS'] ?? []).filter(
  (r) => r !== ROLES.ADMIN && r !== ROLES.SUPERVISOR,
);

// Todo el personal operativo del portal app (funcionarios + internos)
const APP_PERSONAL = AUTH['APP'] ?? [];

const routes: RouteRecordRaw[] = [

  // ── Grupo: Operaciones Hospitalarias ────────────────────────────────────
  {
    meta: {
      icon:  'lucide:stethoscope',
      order: 5,
      title: 'Operaciones Hospitalarias',
    },
    name: 'Sisu',
    path: '/sisu',
    children: [

      {
        name:      'SisuJornadas',
        path:      '/sisu/jornadas',
        component: () => import('#/views/sisu/jornadas/index.vue'),
        meta: {
          icon:      'lucide:calendar',
          order:     1,
          title:     'Turnos',
          authority: [VISTAS.JORNADAS],
        },
      },

      {
        name:      'SisuActividades',
        path:      '/sisu/actividades',
        component: () => import('#/views/sisu/actividades/index.vue'),
        meta: {
          icon:      'lucide:clipboard-list',
          order:     2,
          title:     'Gestión de Actividades',
          authority: [VISTAS.ACTIVIDADES],
        },
      },

      {
        name:      'SisuPermisos',
        path:      '/sisu/permisos',
        component: () => import('#/views/sisu/permisos/index.vue'),
        meta: {
          icon:      'lucide:file-check-2',
          order:     3,
          title:     'Permisos & Delegaciones',
          authority: [VISTAS.PERMISOS],
        },
      },

      {
        name:      'SisuSeguimiento',
        path:      '/sisu/seguimiento',
        component: () => import('#/views/sisu/seguimiento/index.vue'),
        meta: {
          icon:      'lucide:radar',
          order:     4,
          title:     'Seguimiento de Turnos',
          authority: [VISTAS.MONITOREO],
        },
      },

      {
        name:      'SisuNotificaciones',
        path:      '/sisu/notificaciones',
        component: () => import('#/views/sisu/notificaciones/index.vue'),
        meta: {
          icon:      'lucide:bell',
          order:     5,
          title:     'Notificaciones',
          authority: [ROLES.ADMIN, ROLES.SUPERVISOR],
        },
      },

      // ── Personal operativo (web portal) ──────────────────────────────
      {
        name:      'SisuMisTurnos',
        path:      '/sisu/mis-turnos',
        component: () => import('#/views/sisu/movil/turnos/index.vue'),
        meta: {
          icon:      'lucide:calendar-days',
          order:     6,
          title:     'Mis Turnos',
          authority: APP_PERSONAL,
        },
      },

      {
        name:      'MisActividades',
        path:      '/sisu/mis-actividades',
        component: () => import('#/views/sisu/actividades/modules/mis-actividades.vue'),
        meta: {
          icon:      'lucide:clipboard-check',
          order:     7,
          title:     'Mis Actividades',
          authority: EMPLEADOS_MEDICOS,
        },
      },

      {
        name:      'MisPermisos',
        path:      '/sisu/mis-permisos',
        component: () => import('#/views/sisu/movil/permisos/index.vue'),
        meta: {
          icon:      'lucide:file-text',
          order:     8,
          title:     'Mis Permisos',
          authority: FUNCIONARIOS_PERM,
        },
      },

      {
        name:      'SisuMisFaltas',
        path:      '/sisu/mis-faltas',
        component: () => import('#/views/sisu/movil/faltas/index.vue'),
        meta: {
          icon:      'lucide:triangle-alert',
          order:     9,
          title:     'Mis Faltas',
          authority: APP_PERSONAL,
        },
      },

    ],
  },

  // ── Grupo: Monitoreo y Seguridad ─────────────────────────────────────────
  {
    meta: {
      authority: [VISTAS.MONITOREO],
      icon:      'lucide:shield-check',
      order:     15,
      title:     'Monitoreo y Seguridad',
    },
    name: 'SisuMonitoreoGrupo',
    path: '/monitoreo',
    children: [

      {
        name:      'SisuVigilancia',
        path:      '/sisu/vigilancia',
        component: () => import('#/views/sisu/vigilancia/index.vue'),
        meta: {
          icon:      'lucide:scan-eye',
          order:     1,
          title:     'Vigilancia en Vivo',
          authority: [VISTAS.MONITOREO],
        },
      },

      {
        name:      'SisuMapaGps',
        path:      '/sisu/mapa-gps',
        component: () => import('#/views/sisu/monitoreo/mapa-gps.vue'),
        meta: {
          icon:      'lucide:map-pin',
          order:     2,
          title:     'Mapa GPS en Vivo',
          authority: [VISTAS.MONITOREO],
        },
      },

      {
        name:      'SisuMonitoreo',
        path:      '/sisu/monitoreo',
        component: () => import('#/views/sisu/monitoreo/index.vue'),
        meta: {
          icon:      'lucide:monitor-check',
          order:     3,
          title:     'Herramientas de Monitoreo',
          authority: [VISTAS.MONITOREO],
        },
      },

    ],
  },

];

export default routes;
