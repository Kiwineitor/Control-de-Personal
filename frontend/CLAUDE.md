# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Project Overview

**Sistema de Control Médico — Hospital UAP SISU**
Full-stack system for hospital staff management: shifts (jornadas), activities (actividades), permits (permisos), and continuous GPS monitoring.

- **Frontend**: Vue 3 + Naive UI, built on the Vben5 monorepo framework (`apps/web-naive`)
- **Backend**: Spring Boot (`../src/main/java/com/uap/controlMedico/`)
- The frontend workspace root is `frontend/` — all commands below run from there.

---

## Commands

### Frontend (run from `frontend/`)

```bash
# Start dev server for web-naive app (port 5888)
pnpm dev:naive

# Build production bundle (outputs dist.zip via archiver)
pnpm build:naive

# Run unit tests
pnpm test:unit

# Type-check
pnpm --filter @vben/web-naive typecheck

# Lint
pnpm lint

# Install dependencies (after cloning or adding packages)
pnpm bootstrap
```

### Backend (run from `controlMedico/` root)

The backend is a standard Spring Boot / Maven project. Run via IDE (IntelliJ) or:
```bash
mvn spring-boot:run
```
Backend serves on port **7050** by default (base `application.properties`); the `jhowill` profile uses 8085. See the root `CLAUDE.md` for the per-profile port/datasource table.

---

## Architecture

### Monorepo structure

Managed by **Turborepo** + **pnpm workspaces**.

```
frontend/
├── apps/web-naive/         ← Main application
├── packages/               ← Shared libs (@vben/request, @vben/stores, @vben/hooks, @vben/layouts, etc.)
├── internal/               ← Build tooling (vite-config, tsconfig, eslint configs)
└── docs/                   ← Framework documentation
```

All app code lives under `apps/web-naive/src/`.

### Frontend layers (apps/web-naive/src/)

| Layer | Path | Notes |
|---|---|---|
| Router | `router/routes/modules/` | `sisu.ts` (desktop ops dashboard), `admin.ts` (Personal del Hospital + Configuración del Sistema), `funcionario.ts` (mobile portal `/app/*`) |
| Roles & access | `router/routes/sisu-roles.ts` | `ROLES`, `AUTH`, `PERSONAL_MEDICO`, `PERSONAL_ADMIN` constants — see "Role model" below |
| API client | `api/request.ts` | Wraps `@vben/request`; Bearer token, locale header, token refresh, error handling |
| Domain APIs | `api/sisu/`, `api/admin/` | `sisu/`: `jornada.ts`, `monitoreo.ts`, `actividades.ts`, `permisos.ts`, `personal.ts`, `notificaciones.ts`, `bitacora.ts`, `logs.ts`, `app.ts`, `push.ts`. `admin/`: `personal.ts`, `configuracion.ts`, `vistas.ts` |
| Composables | `composables/` | `useGeolocalizacion`, `useWebSocket`, `useTurnoHoy`, `usePlatformPermisos`, `useCamera`, `usePushNotifications`, `useLogShipper`, `offlineGpsQueue` / `offlineQueue` (IndexedDB store-and-forward) |
| Layouts | `layouts/` | `app.vue` (mobile — bottom nav, WebSocket, GPS), `BasicLayout` (desktop dashboard) |
| Views | `views/sisu/`, `views/admin/` | `sisu/`: ops features (admin/supervisor desktop) + `sisu/movil/` (mobile portal). `admin/`: `personal/` (gestión de personal), `configuracion/` (usuarios, roles, lugares, mapa), `consola/` (log viewer) |

### Two interfaces in one app

**Desktop dashboard** (BasicLayout, routes in `sisu.ts` + `admin.ts`):
- Accessed by ADMIN and SUPERVISOR
- `sisu.ts` groups: "Operaciones Hospitalarias" (jornadas, actividades, permisos) + "Monitoreo y Seguridad"
- `admin.ts` groups: "Personal del Hospital" (ADMIN + SUPERVISOR) + "Configuración del Sistema" (ADMIN only — usuarios, roles, lugares)
- Also accessible to MEDICO/ENFERMERO/etc. for their own actividades

**Mobile portal** (AppLayout with bottom-nav, routes in `funcionario.ts`):
- All routes under `/app/*`, hidden from sidebar menu
- GPS tracking active, WebSocket connected
- Composables `useGeolocalizacion` + `useWebSocket` initialized in `layouts/app.vue`

### Role model (`sisu-roles.ts`)

Authority is enforced via `meta.authority` arrays from the `AUTH` map. The non-obvious distinctions:

- **`PERSONAL_MEDICO`** (MEDICO, ENFERMERO): full mobile portal — turnos, actividades, permisos, GPS alerts. Only MEDICO can manage internos (gated by `TipoPersonal.puedeGestionarInternos`).
- **`PERSONAL_ADMIN`** (CAJERO, FARMACIA, LIMPIEZA): restricted mobile portal — permisos + GPS abandono alerts only; **no clínical actividades** (`AUTH.ACTIVIDADES` excludes them).
- **`INTERNO`** (medical students): mobile portal + actividades, but **cannot request permisos/delegaciones** (`AUTH.PERMISOS` excludes them).
- **SUPERVISOR** = ADMIN minus Configuración del Sistema (`AUTH.CONFIGURACION` is ADMIN-only).

### API response contract

```ts
{ code: 0, data: T, error?: string }
```
`code: 0` = success. The interceptor in `api/request.ts` unwraps `data` or throws.

### Real-time (STOMP/WebSocket)

- SockJS + STOMP via `@stomp/stompjs`
- Composable: `composables/useWebSocket.ts`
- Admin alert topic: `/topic/admin/alertas-monitoreo`
- Dev proxy: `/ws` → `VITE_PROXY_TARGET` (see "Environment / proxy" below)

### GPS monitoring module

The continuous GPS monitoring system (introduced June 2026) is the most complex module:

- **Frontend**: `composables/useGeolocalizacion.ts` — adaptive interval (120s in-zone / 30s out-of-zone)
- **Admin map**: `views/sisu/monitoreo/modules/mapa-monitoreo.vue` — Leaflet + STOMP push
- **Backend key classes**: `SesionMonitoreo`, `EventoAbandono`, `ConfiguracionMonitoreo` (id=1 singleton), `MonitoreoSchedulerService` (@Scheduled), `UbicacionServiceImpl` (in-memory `ConcurrentHashMap` cache)
- Backend STOMP topic for alerts: `/topic/admin/alertas-monitoreo`
- Scheduler auto-creates/closes `SesionMonitoreo` based on active shift; checks `TitularJornada.estaEnTurnoAhora()` which supports ROL TURNO (specific days) and overnight shifts

### Backend structure (Spring Boot)

```
com.uap.controlMedico/
├── controller/api/         ← REST controllers (@RestController, /api/v1/*)
├── model/
│   ├── entity/             ← JPA entities
│   ├── dao/                ← Spring Data repositories
│   ├── dto/                ← Request/Response DTOs (grouped by domain)
│   ├── service/            ← Service interfaces + Impl
│   └── enums/              ← Domain enums
├── config/                 ← Spring config (WebSocket, Security, CORS, Auditing)
├── security/               ← Auth handlers
└── exception/              ← BusinessException, ResourceNotFoundException
```

API response wrapper: `model/dto/response/ApiResponse.java`

---

## Key Conventions

### Adding a new route

1. Add the view under `views/sisu/<feature>/`
2. Register in `router/routes/modules/sisu.ts` (desktop) or `funcionario.ts` (mobile)
3. Set `meta.authority` using constants from `sisu-roles.ts` (`AUTH.JORNADAS`, `AUTH.MONITOREO`, etc.)
4. If it appears in the **admin menu**, it also needs a `VISTAS` code (`sisu-roles.ts`) matching an entry seeded by the backend's `config/VistaSeeder.java`. Menu visibility is gated by both the role authority and the administrable VISTAS assignment — `meta.authority` alone is not enough.

### Adding a new API endpoint

1. Add types + function in `api/sisu/<domain>.ts`
2. Use the `requestClient` from `api/request.ts` — do not create new axios instances

### Environment / proxy

Dev proxy is configured in `apps/web-naive/vite.config.ts`:
- `/api` and `/ws` (WebSocket upgrade enabled) → `VITE_PROXY_TARGET`, read from the shell env and parsed out of the `.env` files by the config itself, falling back to `http://127.0.0.1:7050`
- `.env.development` sets `VITE_PROXY_TARGET=http://172.16.21.5:8085`; override it per machine in the gitignored `.env.development.local`. The 127.0.0.1 default is deliberate — going through the LAN IP hits a Windows-firewall `EACCES`.
- `VITE_GLOB_API_URL` must stay **empty** in dev so `/api` goes through the proxy, and empty in `.env.production` so the jar-served SPA stays same-origin. APK builds set the absolute URL (`http://virtual.uap.edu.bo:7050`) in the gitignored `.env.production.local`: the Capacitor WebView would otherwise resolve `/api` against its own localhost, and the native background-geolocation service silently drops every POST when its `backendUrl` is empty.
- **`VITE_NITRO_MOCK=true` by default in `.env.development`** — the mock backend is ON. Set it to `false` to hit the real API.
