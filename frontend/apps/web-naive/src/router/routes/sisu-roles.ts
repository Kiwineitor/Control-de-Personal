/**
 * Roles y permisos del sistema SISU.
 *
 * Perfiles de usuario y a qué acceden:
 * ══════════════════════════════════════════════════════════════════════════════
 *
 *  ADMIN        → Dashboard + Operaciones + Personal del Hospital + Monitoreo y Seguridad + Configuración del Sistema
 *  SUPERVISOR   → Dashboard + Operaciones (Jornadas, Actividades, Permisos) + Personal del Hospital + Monitoreo y Seguridad
 *               → NO tiene acceso a Configuración del Sistema (usuarios, roles)
 *
 *  ── Personal operativo del hospital (Titulares) ──────────────────────────
 *  MEDICO       → Menú Principal (Actividades) + Portal Móvil /app (con /app/permisos)
 *  ENFERMERO    → Menú Principal (Actividades) + Portal Móvil /app (con /app/permisos)
 *  CAJERO       → Menú Principal (Actividades) + Portal Móvil /app (con /app/permisos)
 *  FARMACIA     → Menú Principal (Actividades) + Portal Móvil /app (con /app/permisos)
 *  LIMPIEZA     → Menú Principal (Actividades) + Portal Móvil /app (con /app/permisos)
 *
 *  ── Internos (estudiantes de medicina) ───────────────────────────────────
 *  INTERNO      → Menú Principal (Actividades) + Portal Móvil /app
 *                 (no puede solicitar permisos/delegaciones)
 *
 *  El Portal Móvil (/app/*) usa AppLayout con bottom-nav y contiene:
 *  Inicio · Actividades · Permisos · GPS · Alertas
 * ══════════════════════════════════════════════════════════════════════════════
 */

export const ROLES = {
  ADMIN:      'ROLE_ADMIN',
  SUPERVISOR: 'ROLE_SUPERVISOR',
  MEDICO:     'ROLE_MEDICO',
  ENFERMERO:  'ROLE_ENFERMERO',
  CAJERO:     'ROLE_CAJERO',
  INTERNO:    'ROLE_INTERNO',
  FARMACIA:   'ROLE_FARMACIA',
  LIMPIEZA:   'ROLE_LIMPIEZA',
} as const;

/**
 * Personal MÉDICO/ASISTENCIAL: médicos y enfermeros.
 * Acceso completo al portal: turnos, actividades, permisos, alertas GPS.
 * Solo el médico puede gestionar internos (controlado por TipoPersonal.puedeGestionarInternos).
 */
export const PERSONAL_MEDICO: string[] = [
  ROLES.MEDICO,
  ROLES.ENFERMERO,
];

/**
 * Personal ADMINISTRATIVO/APOYO: cajero, farmacia, limpieza, etc.
 * Acceso restringido al portal: solo permisos + alertas de abandono GPS.
 */
export const PERSONAL_ADMIN: string[] = [
  ROLES.CAJERO,
  ROLES.FARMACIA,
  ROLES.LIMPIEZA,
];

/** Todo el personal permanente del hospital. */
const FUNCIONARIOS: string[] = [...PERSONAL_MEDICO, ...PERSONAL_ADMIN];

export const AUTH: Record<string, string[]> = {

  // Gestión administrativa
  PERSONAL:      [ROLES.ADMIN, ROLES.SUPERVISOR],
  JORNADAS:      [ROLES.ADMIN, ROLES.SUPERVISOR],
  MONITOREO:     [ROLES.ADMIN, ROLES.SUPERVISOR],   // Vigilancia y monitoreo GPS
  CONFIGURACION: [ROLES.ADMIN],                      // Usuarios, roles y configuración del sistema

  // Actividades: admin + personal médico/asistencial + internos
  // (el personal administrativo no tiene asignación de actividades clínicas)
  ACTIVIDADES:   [ROLES.ADMIN, ROLES.SUPERVISOR, ...PERSONAL_MEDICO, ROLES.INTERNO],

  // Solicitar permisos: todos los funcionarios (excepto internos)
  PERMISOS:      [ROLES.ADMIN, ROLES.SUPERVISOR, ...FUNCIONARIOS],

  // Portal móvil: todos los funcionarios + internos
  APP:           [...FUNCIONARIOS, ROLES.INTERNO],
};

/**
 * Códigos de VISTAS (opciones de menú administrables). Coinciden con el catálogo
 * sembrado en el backend (tabla `vista`). El menú WEB admin usa estos códigos en
 * `meta.authority`; el backend devuelve en /auth/codes los roles ∪ vistas efectivas,
 * así las rutas por rol (portal /app) siguen funcionando sin cambios.
 */
export const VISTAS = {
  INICIO:        'INICIO',
  JORNADAS:      'JORNADAS',
  ACTIVIDADES:   'ACTIVIDADES',
  PERMISOS:      'PERMISOS',
  MONITOREO:     'MONITOREO',
  PERSONAL:      'PERSONAL',
  CONFIGURACION: 'CONFIGURACION',
  CONSOLA:       'CONSOLA',
} as const;