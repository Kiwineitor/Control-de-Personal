# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Project Overview

**Sistema de Control Médico — Hospital UAP SISU** (branded "SICOPE").
Full-stack system for hospital staff management: shifts (jornadas), activities (actividades), permits (permisos/delegaciones), attendance (asistencia), and continuous GPS presence control of on-shift personnel.

This repository is **two projects in one tree**:

| Part | Location | Stack |
|---|---|---|
| **Backend** | repo root (`src/main/java/com/uap/controlMedico/`) | Spring Boot 4.0.6 / Java 17 / Maven, PostgreSQL, Spring Security (JWT), STOMP WebSocket |
| **Frontend** | `frontend/` | Vue 3 + Naive UI on the **Vben5** Turborepo monorepo (`apps/web-naive`), packaged as a Capacitor Android app |

Companion docs:
- **`frontend/CLAUDE.md`** — frontend routing, role model, composables, views. Read it for any frontend work.
- **`docs/monitoreo-gps-spec.md`** — the design spec for GPS presence control (privacy, offline store-and-forward, atraso/falta detection). Behavior contract, not a code description.
- **`docs/parche-nativo-gps.md`** — how the patched native Android background-geolocation service is built/applied.
- **`AGENTS.md`** — short cross-agent summary; keep in sync when the facts below change.

---

## Commands

### Backend (run from repo root)

```bash
# Run the API server — port 7050 by default (base application.properties)
mvn spring-boot:run

# Run with a per-developer profile
mvn spring-boot:run "-Dspring-boot.run.profiles=kevin"

# Build jar / run tests
mvn clean package
mvn test
mvn test "-Dtest=ClassName#methodName"
```

**Ports and datasources differ per profile — check before assuming:**

| Profile | Port | Datasource |
|---|---|---|
| *(none — default)* | **7050** | `172.16.21.12:5432/bdControlPersonaSisu` (shared LAN) |
| `kevin` | 7050 (inherited) | `virtual.uap.edu.bo:5432` |
| `jhowill` | 8085 | `localhost:5432` |

There is **no active profile by default**, so a bare `mvn spring-boot:run` hits the shared LAN database. Activate a profile explicitly when you want anything else.

**There are effectively no backend tests** — `src/test/` contains only `ControlMedicoApplicationTests` (context load). Don't assume test fixtures or patterns exist.

### Frontend (run from `frontend/`)

```bash
pnpm bootstrap        # install (pnpm only — preinstall enforces it)
pnpm dev:naive        # dev server, port 5888
pnpm build:naive      # production bundle (emits dist.zip, VITE_ARCHIVER=true)
pnpm build:capacitor  # same build, used before `npx cap sync android`
pnpm test:unit        # vitest
pnpm --filter @vben/web-naive typecheck
pnpm lint
```

Requires Node `^20.19.0 || ^22.18.0 || ^24.0.0` and `pnpm@10.33.0`. See `frontend/CLAUDE.md` for the rest.

### Full-stack production build (Maven `frontend` profile)

```bash
mvn clean package -Pfrontend
```

Runs `pnpm install --frozen-lockfile` + `pnpm build:naive`, then copies `frontend/apps/web-naive/dist` into `target/classes/static` so the Spring Boot jar **serves the SPA directly**. Note this profile also sets `defaultGoal` to `spring-boot:run`.

---

## How the two halves connect

- **Dev proxy** (`frontend/apps/web-naive/vite.config.ts`): `/api` and `/ws` proxy to `VITE_PROXY_TARGET`, resolved as **shell env > `.env.<mode>.local` > `.env.<mode>` > `http://127.0.0.1:7050`**. Vite never copies `.env` values into `process.env`, so the config parses the files itself with `node:fs` — a `VITE_PROXY_TARGET` written only in `.env.development` used to be silently ignored. It deliberately imports nothing from `vite`: the file sits outside `tsconfig.json`'s include, so editors resolve it node10-style where `vite`'s types are unreachable. The 127.0.0.1 default exists deliberately — crossing the LAN IP triggers Windows-firewall `EACCES`.
- **`VITE_NITRO_MOCK=true` in `.env.development`** — the mock backend is ON by default. Set it to `false` to exercise the real API.
- **`VITE_GLOB_API_URL`**: empty in dev (so everything routes through the Vite proxy) and empty in `.env.production`, because the jar serves the SPA same-origin. The Capacitor APK needs the **absolute URL** (`http://virtual.uap.edu.bo:7050`) — set it in the gitignored `.env.production.local` for APK builds only, and remove it before `mvn package -Pfrontend`. Without it the APK resolves `/api` against its own WebView localhost, and the patched native GPS service drops every background POST.
- **Prod**: backend serves the built SPA from `static/` and exposes the same `/api/v1/**` + `/ws` endpoints.
- **API contract**: every response is wrapped as `{ code: 0, data: T, error? }`; `code: 0` = success. Wrapper: `model/dto/response/ApiResponse.java`.
- **CORS** (`SecurityConfig.corsConfigurationSource`, registered only for `/api/v1/**`) allows `localhost:*`, `127.0.0.1:*`, `172.16.21.5:*`, `virtual.uap.edu.bo:*`, `192.168.*.*:*`, and the Capacitor origins (`http(s)://localhost`, `capacitor://localhost`) with credentials. New deployment hosts must be added here.

---

## Backend architecture

### Package layout (`com.uap.controlMedico`)

```
controller/api/          ← REST controllers (@RestController, /api/v1/*) — the only real UI API
controller/appController ← AppFuncionarioController (/api/v1/app — mobile funcionario endpoints)
controller/GpsStompController ← @MessageMapping("/gps/location") — STOMP ingest path
model/
  entity/                ← JPA entities (ddl-auto=update)
  dao/                   ← Spring Data repositories
  dto/<domain>Dto/       ← Request/Response DTOs; response/ApiResponse.java
  service/<domain>/      ← interface + *Impl per domain
  enums/
config/                  ← Security, WebSocket, scheduling, auditing, seeders
security/                ← JwtAuthFilter, JwtUtil, UserDetailsServiceImpl, success handler
mapper/                  ← Entity↔DTO mappers
shared/logging/          ← LogBuffer, MemoryLogAppender (Consola del Sistema)
shared/storage/          ← local + SMB file storage
shared/util/HaversineUtil ← geofence distance math
exception/               ← BusinessException, ResourceNotFoundException
```

Every service domain follows **interface + `*Impl`** under `model/service/<domain>/`. A few legacy services still sit loose at `model/service/*.java`; new work goes in a domain folder.

### API surface (`/api/v1/*` prefixes)

`actividades`, `app`, `asistencia`, `auth`, `delegaciones`, `files`, `historial`, `horario-interno`, `internos`, `jornadas`, `logs`, `lugares`, `monitoreo`, `notificaciones`, `permanencia`, `permisos`, `personal`, `personas`, `push`, `reemplazos`, `responsables`, `rol-turno`, `roles`, `titulares`, `user`, `usuarios`, `vistas`.

`MonitoreoApiController` is by far the largest — it owns QR codes, rondas de verificación, asistencia entrada/salida, GPS ingest, alerts, incidencias/justificaciones, and the monitoring config singleton.

### Two Spring Security filter chains (`config/SecurityConfig.java`)

The same app authenticates two different ways:

1. **`@Order(1)` — `/api/v1/**`: stateless JWT.** Everything the SPA and Capacitor app use. `JwtAuthFilter` validates the Bearer token. Public: `/api/v1/auth/login`, `/api/v1/auth/reset-password`, `/api/v1/asistencia/qr/escanear`. Failures return the JSON `{code:-1,...}` envelope (401/403), never a redirect.
2. **`@Order(2)` — everything else: session form-login.** The Thymeleaf admin web this chain was built for **no longer exists** (no `templates/`, no MVC `@Controller`s — the Thymeleaf deps in `pom.xml` are vestigial). In practice this chain now only permits the SPA's static asset paths (`/`, `/assets/**`, `/app/**`, `/sisu/**`, `/admin/**`, `/monitoreo/**`, `/sistema/**`, `/ws/**`, …) and enforces single-session. Adding a new top-level SPA route means adding its path here, or the deep link 302s to a `/login` page that isn't there.

JWT settings live under `sisu.jwt.*`. `@EnableMethodSecurity` is on — `@PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")` is the norm on privileged endpoints.

### Real-time (STOMP / WebSocket) — `config/WebSocketConfig.java`

- Endpoint `/ws` (SockJS), in-memory simple broker on `/topic` + `/queue`, app prefix `/app`, user prefix `/user`.
- **`JwtChannelInterceptor`** authenticates the STOMP CONNECT frame with the same JWT, so socket sessions are authorized exactly like REST calls.
- Key topic: **`/topic/admin/alertas-monitoreo`** (GPS alerts → admin map). `WebSocketPresenceService` tracks who is connected.
- In-memory broker = single-instance only; swap for RabbitMQ/ActiveMQ before scaling out.

### GPS presence control (the most complex subsystem)

Read `docs/monitoreo-gps-spec.md` before changing behavior — the rules (provisional vs. confirmed faults, revert-on-late-sync) are deliberate.

- `SchedulingConfig` enables `@Scheduled`. `MonitoreoSchedulerService` auto-opens/closes `SesionMonitoreo` from whether each `TitularJornada` is on shift (`estaEnTurnoAhora()` — handles ROL TURNO specific-day schedules and overnight shifts). `AusenciaSchedulerService` materializes `AusenciaNoJustificada` **for the report only — it no longer notifies** (it could not tell "GPS off" from "left the geofence" and announced both as "no está en su área asignada", every 15 min).
- `UbicacionServiceImpl` keeps an in-memory `ConcurrentHashMap` of latest fixes; detected abandonment creates an `EventoAbandono` and pushes a STOMP alert.
- **Offline store-and-forward**: `POST /api/v1/monitoreo/ubicacion` is the live path; `POST /api/v1/monitoreo/ubicaciones/lote` accepts a buffered batch and is **idempotent by `clientEventId`**. Frontend side: `composables/offlineGpsQueue.ts` (IndexedDB) plus the patched native service (`frontend/patches/@capacitor-community__background-geolocation@1.2.26.patch`).
- `JustificacionService` handles the incidencia → justificación → aprobar/rechazar flow.
- `ConfiguracionMonitoreo` is an **id=1 singleton** of tunable thresholds, edited via `GET/PUT /api/v1/monitoreo/configuracion`.
- **Alerting is single-sourced.** Only the detector that already classified the incidence alerts, via `NotificacionService.notificar*AlAdmin(...)`: bell notification for ADMIN/SUPERVISOR plus one Telegram message per event, deduped by event id. `TelegramNotificationService` uses the server bot token (`SISU_TELEGRAM_BOT_TOKEN`) and enable flag. The ADMIN notification-test module can persist the group ID in `configuracion_telegram` (singleton id=1), which overrides `SISU_TELEGRAM_CHAT_ID` immediately and survives restarts. Until saved, the server chat ID is used. This destination is shared by instances using the same DB. ADMIN can edit per-type plain-text templates and select automatic/manual delivery in `plantilla_telegram`; all monitoring Telegram alerts go through `TelegramPlantillaService`. Missing rows preserve the initial automatic behavior; manual mode suppresses only Telegram and creates no pending queue. See `docs/pruebas-notificaciones-telegram.md`. See `docs/monitoreo-gps-spec.md` §8.1 for the full catalogue, timings and what to tune.
- **Device-side alert**: `composables/useAvisoSenal.ts` raises a *local* Android notification when the shift stops being recorded. The server cannot cover this — the causes (location off, no internet, no fix) are exactly what stops a push from arriving.
- Frontend counterpart: `composables/useGeolocalizacion.ts` + `views/sisu/monitoreo/.../mapa-monitoreo.vue` (Leaflet).

### VISTAS — administrable menu permissions

A hybrid role+user model layered **on top of** the role authorities, not replacing them:

- Entities `Vista` / `RolVista` / `UsuarioVista`; API at `/api/v1/vistas` (`GET|PUT /rol/{id}`, `GET|PUT /usuario/{id}`); frontend client `api/admin/vistas.ts`.
- **`config/VistaSeeder.java` (`@Order(20)`, runs every startup, idempotent)** seeds the catalog — `INICIO, JORNADAS, ACTIVIDADES, PERMISOS, MONITOREO, PERSONAL, CONFIGURACION, CONSOLA` — and per-role defaults that reproduce the existing role-based access, so enabling it changes nothing for existing users.
- The codes are mirrored in the frontend as `VISTAS` in `router/routes/sisu-roles.ts`. **Adding an admin menu option requires both** a `meta.authority` entry and a `VISTAS` code seeded here.
- Funcionario access (the `/app` mobile portal) stays purely role-based; those roles only get `INICIO` in the admin menu.

### Consola del Sistema (log viewer)

- `shared/logging/MemoryLogAppender` is a Logback appender feeding an in-memory ring `LogBuffer`; `LogBufferInitializer` wires it at startup.
- `LogController` (`/api/v1/logs`): `GET` and `DELETE` are `ADMIN`/`SUPERVISOR` only; `POST /cliente` ingests browser-side logs shipped by `composables/useLogShipper.ts`.
- UI: `views/admin/consola/`.

### Push notifications (two channels)

- **FCM** (`firebase-admin`, `FcmService`) for native Android push when the app is closed — credentials in `src/main/resources/serviceAccountKey.json`.
- **Web Push / VAPID** (`nl.martijndwars:web-push` + BouncyCastle) for browsers.
- Routed by a `platform` column on `PushSubscripcion`. Backend: `service/push/`, `controller/api/PushController.java`.

### Other cross-cutting config

- **Seeders run in order**: `DataInitializer` `@Order(10)` (roles, baseline data) then `VistaSeeder` `@Order(20)`.
- **JPA auditing** (`AuditoriaConfig` + `AuditorAwareImpl`) fills created/modified-by from the security context. `HistorialCambio` / `BitacoraEntrada` hold domain-level audit trails.
- **File storage** (`shared/storage`, `ServerFileConfig`): local disk (`sisu.storage.path`, default `C:/sisu-files`) served under `/api/v1/files`; SMB via `jcifs-ng` (`app.ipServidor` / `app.carpetaCompartida`).
- **`ddl-auto=update`** — Hibernate owns the schema; there are **no SQL migration files**. Entity edits migrate live, including against the shared LAN DB, so be deliberate about renames and non-null columns.
- `LondraService` is a **stub** (empty `sincronizar*` methods) for a future external integration — don't mistake it for working code.

---

## Conventions

- **New REST endpoint**: controller method under `controller/api/` returning `ApiResponse` (`{code:0,data}`) → service interface method + `*Impl` → frontend consumes it via `api/sisu/<domain>.ts` or `api/admin/<domain>.ts` using the shared `requestClient` (never a new axios instance).
- **Authorization is declared twice and must stay in sync**: `@PreAuthorize` on the API method, `meta.authority` on the route (`router/routes/sisu-roles.ts`), plus a `VISTAS` code + `VistaSeeder` entry if it appears in the admin menu.
- **Capacitor**: app ID `com.uap.sisu`, `androidScheme: 'http'`. After any frontend change intended for the APK, `pnpm build:capacitor` **and** `npx cap sync android` — a skipped sync ships stale web assets and has silently broken GPS before. The `@capacitor-community/background-geolocation` patch is applied via pnpm `patchedDependencies`; re-derive it per `docs/parche-nativo-gps.md` if the dependency version moves.
- **Service Worker** (`injectManifest`, `src/sw.ts`) registers only in production builds; it uses `skipWaiting()` + `clientsClaim()` + `cleanupOutdatedCaches()` so a new APK takes control immediately instead of serving previous-version assets.
- **Lefthook**: `frontend/lefthook.yml` is live (pre-commit oxfmt/oxlint/eslint/stylelint, commit-msg commitlint, post-merge install). The root `lefthook.yml` is entirely commented out. No CI workflows exist in `.github/workflows/`.
- **Secrets**: `application.properties` holds live DB/mail/JWT credentials and `serviceAccountKey.json` is committed. Do not copy them into new files, logs, or commits; real deployments need these externalized.
