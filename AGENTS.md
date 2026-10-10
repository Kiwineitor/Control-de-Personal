# AGENTS.md

**Sistema de Control Médico — Hospital UAP SISU** (branded "SICOPE"). Full-stack hospital staff management: shifts, activities, permits, GPS monitoring.

**Two projects in one tree**: repo root = Spring Boot 4.0 backend (`src/main/java/com/uap/controlMedico/`). `frontend/` = Turborepo + pnpm monorepo (Vue 3 + Naive UI on Vben5). Main app: `apps/web-naive/`.

## Commands

| Action | Command | From |
|---|---|---|
| Backend dev server (port 7050) | `mvn spring-boot:run` | repo root |
| With dev profile (localhost DB) | `mvn spring-boot:run "-Dspring-boot.run.profiles=kevin"` | repo root |
| With jhowill profile (port 8085) | `mvn spring-boot:run "-Dspring-boot.run.profiles=jhowill"` | repo root |
| Full-stack production build | `mvn clean package -Pfrontend` | repo root |
| Frontend dev server (port 5888) | `pnpm dev:naive` | `frontend/` |
| Frontend build (dist.zip) | `pnpm build:naive` | `frontend/` |
| Unit tests (vitest) | `pnpm test:unit` | `frontend/` |
| Typecheck | `pnpm --filter @vben/web-naive typecheck` | `frontend/` |
| Lint | `pnpm lint` | `frontend/` |
| Install deps | `pnpm bootstrap` | `frontend/` |

Requires: Java 17+, Node ^20.19.0\|\|^22.18.0\|\|^24.0.0, pnpm@10.33.0, PostgreSQL.

### Backend profiles (always activate one locally)

| Profile | Port | Datasource |
|---|---|---|
| *(none — default)* | **7050** | `172.16.21.12:5432/bdControlPersonaSisu` (shared LAN) |
| `kevin` | 7050 | `virtual.uap.edu.bo:5432` |
| `jhowill` | 8085 | `localhost:5432` |

No active profile by default — bare `mvn spring-boot:run` hits the shared LAN DB.

## Deep architecture docs (read these first)

- **`CLAUDE.md`** (root) — backend architecture, two security chains (JWT API + form-login admin), GPS module, push notifications, file storage.
- **`frontend/CLAUDE.md`** — frontend routing, role model, composables, API patterns, GPS/Push module.

## Gotchas & conventions (not obvious from CLAUDE.md)

- **Dev proxy** (`apps/web-naive/vite.config.ts`): target is `VITE_PROXY_TARGET`, resolved as shell env > `.env.<mode>.local` > `.env.<mode>` > `http://127.0.0.1:7050`. Vite does not populate `process.env` from `.env` files, so the config parses them itself with `node:fs` (no `vite` import, which would break the editor's inferred project). See `docs/despliegue-local-y-produccion.md`.
- **Mock backend is ON by default** (`VITE_NITRO_MOCK=true` in `.env.development`). Set to `false` to hit the real backend.
- **Schema**: `ddl-auto=update` — Hibernate manages the schema. No SQL migration files. Entity changes apply automatically (including against shared LAN DB).
- **No backend tests** — single Spring context loader test only. Don't assume test patterns exist.
- **No CI workflows** exist in `.github/workflows/`.
- **Secrets in repo**: `application.properties` contains live DB/JWT credentials and `serviceAccountKey.json` is committed. Do not propagate into new files, logs, or commits.
- **Service Worker (SW)** (`vite.config.ts` → `injectManifest` + `sw.ts`). Uses `skipWaiting()` + `clientsClaim()` + `cleanupOutdatedCaches()` so new APKs take control immediately — fixes the old bug where stale SWs served obsolete assets. Clears runtime caches on `activate`. Only registers in production (`import.meta.env.PROD`).
- **Monitoring alerts**: one message per event, pre-classified (falta, atraso, abandono de área, pérdida de señal). Via `NotificacionService.notificar*AlAdmin(...)` → admin bell + Telegram group (`SISU_TELEGRAM_BOT_TOKEN` / `SISU_TELEGRAM_CHAT_ID`). `AusenciaSchedulerService` no longer notifies: only feeds the report. Catalog & timings in `docs/monitoreo-gps-spec.md` §8.1.
- **Telegram destination**: ADMIN can save the group ID from notification tests. `configuracion_telegram` (id=1) overrides the server chat ID for subsequent sends and survives restart; the bot token and enable flag remain server settings. Instances sharing the DB share this destination. `TelegramPlantillaService` applies editable per-type templates and automatic/manual modes from `plantilla_telegram`; manual mode suppresses only Telegram, not internal notifications. See `docs/pruebas-notificaciones-telegram.md`.
- **Offline GPS queue** (`composables/offlineGpsQueue.ts`): when `POST /api/v1/monitoreo/ubicacion` fails, fix buffers in IndexedDB. On reconnect, re-sends via `POST /api/v1/monitoreo/ubicaciones/lote` (idempotent by `clientEventId`).
- **Capacitor Android**: App ID `com.uap.sisu`, HTTP scheme, push + geolocation plugins. Patched `@capacitor-community/background-geolocation` in pnpm. After any frontend change for APK: `pnpm build:capacitor` **and** `npx cap sync android` — skipping sync ships stale web assets and silently breaks GPS.
- **Lefthook** active in `frontend/lefthook.yml` (pre-commit: oxfmt + oxlint + eslint + stylelint; commit-msg: commitlint; post-merge: pnpm install). Root `lefthook.yml` is all commented out.
- **VISTAS system**: menu visibility gated by `VISTAS` codes (see `sisu-roles.ts:86`) — match backend `vista` table seeded by `VistaSeeder.java`. Adding an admin menu option requires **both** `meta.authority` and a `VISTAS` code.
- **Two Spring Security chains** (`SecurityConfig.java`): `@Order(1)` stateless JWT for `/api/v1/**` (SPA + Capacitor); `@Order(2)` session form-login for everything else (static assets + deep-link protection). The form-login chain was for Thymeleaf admin (no longer exists). Adding a top-level SPA route requires adding its path to the form-login chain, or deep links 302 to a missing `/login` page.
- **API contract**: every response wrapped as `{ code: 0, data: T, error? }`; `code: 0` = success. Wrapper: `model/dto/response/ApiResponse.java`.
- **CORS** (`SecurityConfig.corsConfigurationSource`): allows `localhost:*`, `127.0.0.1:*`, `172.16.21.5:*`, `virtual.uap.edu.bo:*`, `192.168.*.*:*`, Capacitor origins (`http(s)://localhost`, `capacitor://localhost`) with credentials. New deployment hosts must be added here.
- **STOMP/WebSocket**: `/ws` (SockJS), in-memory broker on `/topic` + `/queue`. `JwtChannelInterceptor` authenticates CONNECT frame with same JWT. Key topic: `/topic/admin/alertas-monitoreo`. In-memory broker = single-instance only.
- **Seeders run in order**: `DataInitializer` `@Order(10)` (roles, baseline), then `VistaSeeder` `@Order(20)`.
- **File storage**: local disk (`sisu.storage.path`, default `C:/sisu-files`) under `/api/v1/files`; SMB via `jcifs-ng` (`app.ipServidor` / `app.carpetaCompartida`).
