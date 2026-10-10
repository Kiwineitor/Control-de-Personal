import type { RouteRecordRaw } from 'vue-router';
import { ROLES, VISTAS } from '#/router/routes/sisu-roles';

// ADMIN y SUPERVISOR (por rol) + cualquier usuario con la vista pública INICIO.
// Sirve como "landing" para que un usuario recién creado nunca quede con menú vacío.
const ADMIN_ROLES = [ROLES.ADMIN, ROLES.SUPERVISOR, VISTAS.INICIO];

const routes: RouteRecordRaw[] = [
  {
    meta: {
      authority: ADMIN_ROLES,
      icon:      'lucide:layout-dashboard',
      order:     -1,
      title:     'Dashboard',
    },
    name: 'Dashboard',
    path: '/dashboard',
    children: [
      {
        name:      'Analytics',
        path:      '/analytics',
        component: () => import('#/views/dashboard/analytics/index.vue'),
        meta: {
          affixTab:  true,
          authority: ADMIN_ROLES,
          icon:      'lucide:area-chart',
          title:     'Analíticas',
        },
      },
      {
        name:      'Workspace',
        path:      '/workspace',
        component: () => import('#/views/dashboard/workspace/index.vue'),
        meta: {
          authority: ADMIN_ROLES,
          icon:      'carbon:workspace',
          title:     'Workspace',
        },
      },
    ],
  },
];

export default routes;