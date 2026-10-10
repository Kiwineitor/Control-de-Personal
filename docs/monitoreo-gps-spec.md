# Especificación — Control de Presencia por GPS (SICOPE)

> **Estado:** borrador de diseño aprobado (decisiones de negocio confirmadas con el responsable del proyecto).
> **Alcance:** este documento define el comportamiento esperado. **No** describe código ya implementado salvo en la sección 11 (Estado actual). Sirve como base para validar reglas con RRHH y como guía de implementación por fases (sección 13).

---

## 0. Propósito

Confirmar la **presencia del personal en su lugar asignado durante su turno**, de forma:
- **Tolerante a internet inestable** (no generar faltas falsas por cortes de red).
- **Respetuosa de la privacidad** (solo se rastrea dentro del turno; al cerrar sesión se detiene todo).
- **Justa y auditable** (toda falta/atraso/abandono queda registrado, es explicable y es justificable).

---

## 1. Principios de diseño

1. **Ausencia de datos ≠ ausencia de persona.** El servidor por sí solo no puede saber por qué se cortaron los pings; el teléfono sí. La inteligencia de causa vive en el cliente.
2. **Local-first (caja negra).** El dispositivo graba localmente un registro de eventos con timestamp y **motivo**, aunque no haya red. Es la fuente de verdad.
3. **Store-and-forward.** Cuando vuelve internet, el dispositivo sincroniza el buffer en lote. El servidor reconstruye la línea de tiempo real.
4. **Decisión en dos fases:**
   - **Provisional** (tiempo real, **reversible**): "sin señal / verificando".
   - **Confirmada** (tras reconciliar el buffer, o tras timeout duro sin sync): recién aquí se registra la falta/abandono.
   - Si llega data bufferizada que prueba presencia durante el hueco → **se revierte** lo provisional.
5. **Privacidad por turno.** Captura de ubicación y notificaciones de control **solo dentro del turno y con sesión iniciada**. Fuera de eso, se apaga.
6. **La referencia de "puesto" es la geocerca (Lugar asignado)**, no el lugar de login.

---

## 2. Glosario y máquina de estados

**Estados del trabajador en turno:**

```
SIN_TURNO
  └─(inicia turno)→ ESPERANDO_CHECKIN
                      └─(primer fix confiable)→ PRESENTE_EN_ZONA
PRESENTE_EN_ZONA  ⇄  FUERA_DE_ZONA      (con debounce temporal de salida)
cualquiera        →  SIN_SEÑAL (provisional)
SIN_SEÑAL         →  (reconciliación) vuelve a estado real
SIN_SEÑAL         →  (timeout duro)   AUSENTE (confirmado)
```

**Vocabulario de incidencias** (a usar igual por RRHH, admin y desarrollo):
- **Atraso:** llegó/checó tarde respecto al inicio del turno.
- **Falta (ausencia):** no se presentó / nunca reportó durante el turno.
- **Abandono:** se presentó y se fue antes de tiempo (salió de zona o dejó de reportar).
- **Salida temprana:** dejó el puesto antes del fin del turno.
- **Incidencia (no punible):** evento técnico sin culpa atribuible (ej. batería agotada). Se registra, no penaliza.
- **Justificado / Injustificado:** dimensión transversal según exista permiso aprobado o justificación posterior aprobada.

---

## 3. Captura: qué, cuándo y contra qué se compara

### Checkpoint de login
Tras conceder **GPS** + **Notificaciones**, se captura un *checkpoint de sesión*: primer fix confiable, nivel de batería, conectividad, estado de permisos, ID de dispositivo. Sirve para confirmar que el usuario está operativo y para auditoría. **No** es la referencia de abandono.

### Referencia de presencia
La **geocerca del Lugar asignado** a la jornada (centro + radio). Como el responsable confirmó que **siempre habrá Lugar asignado**, no se requiere fallback.

### Señales a capturar en cada ping/evento
Además de `lat/lon/precision`:

| Señal | Para qué |
|---|---|
| Batería (nivel + tendencia) | Distinguir "se le murió el cel" de "apagó el GPS adrede". |
| Conectividad (wifi/celular/none) | Saber si un hueco fue *offline buffereado* (no es abandono). |
| Evento explícito **GPS_OFF** | Avisar al instante que se apagó la ubicación (no inferir por silencio). |
| Evento **PERMISO_REVOCADO** | Igual que GPS_OFF. |
| Estado de app (foreground/background/killed) | Saber si el OEM la mató. |
| `isMock` (ubicación simulada) | Anti-fraude. |
| Timestamp dispositivo + servidor | Detectar relojes manipulados/desfasados. |
| ID de evento generado en cliente | Idempotencia al reintentar sync. |

---

## 4. Clasificación de incidencias — catálogo de casos

Matriz base para validar con RRHH. "Reversible" = puede auto-revertirse si llega evidencia. "Justificable" = admite justificación posterior con aprobación.

| # | Situación | Clasificación | Notifica a | Reversible | Justificable |
|---|---|---|---|---|---|
| 1 | Tiene turno y nunca inició sesión / nunca reportó | **Falta** | admin | sí (si aparece) | sí |
| 2 | Primer fix después de inicio + tolerancia | **Atraso** (registra minutaje) | funcionario + admin | no | sí |
| 3 | En zona, reportando | **Presente** ✅ | — | — | — |
| 4 | Estaba en zona y sale, sostenido > debounce | **Abandono (salida de zona)** | funcionario + admin | sí (si retorna) | sí |
| 5 | Silencio + evento GPS_OFF | aviso → si no vuelve en X → **Abandono por desactivar ubicación** | funcionario + admin | sí | sí |
| 6 | Silencio + batería venía a 0% | **Incidencia: posible batería agotada** (no punible) | funcionario (suave) + admin | — | n/a |
| 7 | Silencio + estaba offline + luego sincroniza en zona | **Presente** (NO abandono) | — | — (nunca se confirma) | — |
| 8 | Se va antes del fin del turno | **Salida temprana** | admin | — | sí |
| 9 | Cierra sesión durante el turno | **Abandono (registrado desde el evento de logout)** | **solo admin** (privacidad) | sí (si re-loguea y reanuda) | sí |
| 10 | Tiene permiso aprobado que cubre la ventana | **Justificado** → suprime alertas | — | — | — |

> Celdas a confirmar con RRHH: umbrales exactos de los casos 1, 2, 4, 5; y si el caso 6 (batería) realmente nunca penaliza.

---

## 5. Umbrales y tiempos (configurables)

Todos viven (o vivirán) en `ConfiguracionMonitoreo` (singleton id=1). Valores **propuestos por defecto**, ajustables sin recompilar.

| Parámetro | Default propuesto | Notas |
|---|---|---|
| Tolerancia de atraso | 10 min | Después del inicio del turno antes de marcar atraso. |
| Umbral de falta | 30 min (o X% del turno) | Si nunca checa/reporta → falta. |
| Intervalo GPS en zona | 120 s | Ya existe (`intervaloGpsActivoSeg`). |
| Intervalo GPS fuera de zona | 30 s | Ya existe. |
| Debounce de salida de zona | máx(60 s, 2× intervalo fuera) | Anti-parpadeo (ya existe). |
| Aviso "sin señal" (provisional) | máx(180 s, 3× intervalo) | Ya existe. |
| Abandono provisional | = timeout de abandono ≈ 5 min | Aviso al admin, reversible. |
| **Confirmación dura de ausencia** | **15 min** de silencio total **sin sync** | **Nuevo.** Solo si nada lo desmiente. |
| Margen de precisión GPS confiable | 100 m | Ya existe (`PRECISION_MAX_CONFIABLE_METROS`). |

**Tolerancia adaptativa a la red:** si el último latido del dispositivo indicó "perdiendo conexión / offline", el servidor **extiende** la ventana de confirmación; si indicó "online y en zona" y luego silencio seco, la **acorta**. El tiempo no es fijo: depende de lo que el teléfono alcanzó a avisar.

---

## 6. Almacenamiento local + sincronización

### En el dispositivo
- **Buffer local en SQLite** dentro del servicio nativo (sobrevive a que maten la app), con flag `synced` por registro y un **id de evento generado en cliente**.
- Cuando un POST falla (sin red), el registro se encola; un worker lo reintenta al recuperar conectividad (`@capacitor/network` dispara el flush).
- El buffer guarda tanto **posiciones** como **eventos** (GPS_OFF, offline, foreground/background, batería).

### En el servidor
- **Endpoint de lote:** `POST /api/v1/monitoreo/ubicaciones/lote` recibe un array ordenado por timestamp de dispositivo.
- **Idempotencia:** deduplica por id de evento de cliente (reintentos no duplican).
- **Reconciliación:** procesa los huecos **después** de recibir el lote → revierte abandonos provisionales que el buffer desmiente.
- **Confirmación dura:** solo se compromete una ausencia si pasa la ventana (sección 5) **y** ningún lote la contradice.

### Resultado
Internet inestable deja de generar faltas falsas: el hueco se rellena al reconectar y la decisión definitiva se toma con la línea de tiempo completa.

---

## 7. Protocolo de privacidad

### Regla central
> **El logout es el evento. No se mantiene una sesión viva pingeando ni notificando.**

### Al hacer logout (completo):
1. **Borrar TODAS las suscripciones push del dispositivo (FCM y Web Push).** Desde ese momento el dispositivo no recibe nada.
2. **Detener captura de ubicación** de inmediato (incl. servicio nativo).
3. **Cerrar la sesión de monitoreo.** Si era en turno, **registrar el evento "cerró sesión durante el turno"** → se evalúa como abandono **justificable** (sección 9). No se sigue pingeando para "descubrir" la falta: el logout ya es la evidencia.
4. **Alertar al admin/supervisor**: "🚪 [Usuario] cerró sesión durante su turno".
5. **Al funcionario: nada** (ya se fue).

### Alcance por turno
- **Fuera de turno:** sin captura de ubicación (gating de cliente vía `useTurnoHoy`) **y** el servidor no envía notificaciones de **control** (gating de servidor al enviar).
- El gating de privacidad debe aplicarse **en el envío del servidor**, no solo en la captura del cliente.

---

## 8. Taxonomía de notificaciones

| Categoría | Ejemplos | Logueado + en turno | Logueado fuera de turno | Deslogueado |
|---|---|---|---|---|
| **A — Control/Exigencia** | abandono, sin señal, GPS desactivado, recordatorio de turno | ✅ | ❌ | ❌ |
| **B — Gestión** | permiso aprobado/rechazado, delegación | ✅ | ✅ | ❌ |
| **C — Sistema (targeted)** | aviso individual de cuenta/ajuste | ✅ | ✅ | ❌ |
| **C-broadcast — Sistema general** | actualización de app, mantenimiento, aviso institucional | ✅ | ✅ | ❌ (no hay token) |

Reglas:
- **Deslogueado = no recibe nada** (se borró el token). Privacidad garantizada.
- **Broadcast general (sin destinatario):** capacidad nueva solicitada — el admin envía un aviso del sistema **a todos los dispositivos con sesión activa** (no por usuario individual). Se implementa iterando las suscripciones activas (o vía *topic* FCM). No es de control: nunca acusa faltas.

---

## 8.1 Catálogo de avisos: qué se manda, cuándo y por dónde

Un aviso por hecho. Cada fila se emite **una sola vez** por sesión o por evento;
la deduplicación vive en `MonitoreoSchedulerService` (sets en memoria por sesión)
y en `NotificacionServiceImpl` (clave por id de evento).

| Hecho | Cuándo se dispara (valores por defecto) | Al funcionario | Al grupo de Telegram | Detector |
|---|---|---|---|---|
| **Recordatorio de turno** | al abrir la primera sesión del día del turno | push | — | `gestionarSesiones` |
| **GPS no activado** | entre 5 y 14 min del inicio, si nunca reportó | push | `📡 GPS NO ACTIVADO` | `alertarGpsPendiente` |
| **Atraso** | en la **llegada del día**, si excede `toleranciaAtrasoMin` (10 min) | push | `⏰ ATRASO` | `UbicacionServiceImpl` |
| **Señal perdida** | ~4 min sin reportar = `max(90 s, 2 × intervaloGpsActivoSeg)` | push | `📡 SEÑAL PERDIDA` | `detectarSinSenal` |
| **Señal recuperada** | al primer reporte tras un corte avisado | — | `✅ SEÑAL RECUPERADA` | `detectarSinSenal` |
| **Falta (no se presentó)** | `umbralFaltaMin` (20 min) sin un solo reporte | push | `🚫 FALTA — NO SE PRESENTÓ` | `limpiarSesionesObsoletas` |
| **Salió del área** | fix FUERA sostenido `confirmacionFueraSeg` (60 s) | — (provisional) | — (provisional) | `UbicacionServiceImpl` |
| **Abandono de área** | `timeoutAbandonoMinutos` (20 min) fuera sin volver | push | `📍 ABANDONO DE ÁREA` | `detectarAbandonos` |
| **Regresó al área** | al volver, solo si el abandono se había avisado | push | `✅ REGRESÓ AL ÁREA` | `UbicacionServiceImpl` |
| **Ausencia por señal** | `timeoutSinSenalMinutos` (15 min) sin reportar | push | `📡 SIN SEÑAL — AUSENCIA REGISTRADA` | `detectarSinSenal` |
| **Cerró sesión en turno** | al hacer logout estando de turno | — (privacidad) | `🚪 CERRÓ SESIÓN EN TURNO` | `UbicacionServiceImpl` |

**El atraso se mide una sola vez al día, en la llegada.** Se evalúa contra el
primer fix DENTRO de la geocerca y queda cerrado ahí. Antes el guard vivía en la
sesión, y como el scheduler recrea la sesión cada vez que la anterior se cierra
por inactividad, cada reconexión volvía a medir el atraso contra la hora de
inicio del turno: alguien que llegó puntual y perdió señal recibía "atraso de
208 min", y uno mayor en cada reconexión. Ahora se consulta si esa persona ya
marcó llegada hoy en ese turno (`SesionMonitoreoDao.yaRegistroLlegadaHoy`) y,
además, el evento se deduplica por persona + turno + día, igual que la falta.

**La salida del área es provisional a propósito.** Un salto de GPS por multipath
en interiores produce pares "salió/volvió" en cuestión de segundos; avisarlos
sería ruido. El admin los ve en vivo en el mapa, que es donde corresponde.

### Periodicidad de los jobs

| Job | Cada | Papel |
|---|---|---|
| `gestionarSesiones` | 60 s | abre y cierra sesiones de monitoreo |
| `detectarSinSenal` | 30 s | avisa el corte de señal y confirma la ausencia |
| `detectarAbandonos` | 60 s | confirma el abandono de área |
| `limpiarSesionesObsoletas` | 5 min | registra faltas y cierra sesiones inactivas |
| `alertarGpsPendiente` | 5 min (arranca a los 2 min) | avisa a quien nunca activó el GPS |
| `AusenciaSchedulerService` | 5 min | **solo reporte**, ya no notifica |

Tras reiniciar el backend hay **180 s de calentamiento** en los que ningún job
alarma: las sesiones activas conservan la última actualización previa al
reinicio y, sin esa gracia, todas parecerían sin señal a la vez.

### Qué ajustar y dónde

Los umbrales son el singleton `ConfiguracionMonitoreo` (id=1), editable en
caliente por `GET/PUT /api/v1/monitoreo/configuracion` — no requiere recompilar
ni reinstalar la app:

| Campo | Default | Qué mueve |
|---|---|---|
| `toleranciaAtrasoMin` | 10 min | gracia antes de contar atraso |
| `umbralFaltaMin` | 20 min | silencio total antes de contar falta |
| `timeoutAbandonoMinutos` | 20 min | fuera de zona antes de confirmar abandono |
| `timeoutSinSenalMinutos` | 15 min | silencio antes de confirmar ausencia por señal |
| `intervaloGpsActivoSeg` | 120 s | cadencia dentro de zona; **también define el aviso de señal perdida (2×)** |
| `intervaloGpsFueraZonaSeg` | 30 s | cadencia fuera de zona |
| `confirmacionFueraSeg` | 60 s | antirrebote de la salida de zona |
| `precisionMaxConfiableMetros` | 100 m | tope de indulgencia de la geocerca |

La periodicidad de los jobs sí está en el código (`@Scheduled(fixedDelay = …)`)
y cambiarla exige recompilar el backend.

### Aviso local en el teléfono

`composables/useAvisoSenal.ts` levanta una notificación **generada por el propio
dispositivo** cuando el turno deja de registrarse, tras 90 s de gracia. Existe
porque las tres causas del corte —ubicación apagada, sin internet, sin fix— son
justamente las que impiden que llegue un push del servidor: sin este aviso el
funcionario se entera de la ausencia cuando ya está registrada. Distingue
`GPS_APAGADO`, `SIN_INTERNET` y `SIN_SENAL`, y se retira solo al reanudarse.

---

## 9. Flujo de justificación de abandono

Espejo del flujo de permisos existente:

```
ABANDONO (confirmado)
  └─(funcionario sube justificación)→ EN_JUSTIFICACION
        ├─(admin APRUEBA)→ JUSTIFICADO   (no penaliza)
        └─(admin RECHAZA / OBSERVA)→ ABANDONO (firme, penaliza)
```

- El registro del abandono **persiste siempre** en el historial, cambie o no su estado final.
- Un **permiso aprobado** que cubra la ventana lo marca JUSTIFICADO automáticamente.

---

## 10. Pantalla de onboarding / consentimiento

Aparece en el primer login y queda accesible desde "ℹ️ Cómo funciona / Privacidad":
- **Qué hace:** "Durante tu turno, registra tu ubicación para confirmar tu presencia en tu lugar asignado."
- **Cuándo:** "Solo dentro de tu horario de turno. Fuera de turno **no** rastreamos tu ubicación."
- **Si fallas:** explicación honesta de atraso / falta / abandono y que **puedes justificarlos**.
- **Privacidad:** "Al cerrar sesión se detiene todo y no recibirás más notificaciones de control."
- **Permisos:** solicita GPS ("Permitir todo el tiempo") y Notificaciones, explicando el porqué (mejora la tasa de concesión).

---

## 11. Estado actual del código y brechas a corregir

Diagnóstico al momento de redactar (verificado en código):

1. **🔴 El logout NO borra el token FCM.** `store/auth.ts` → `usePushNotifications().desuscribir()` solo cancela **Web Push** (`navigator.serviceWorker`/`pushManager`); el token FCM nativo queda en `push_subscripcion`. Por eso llegan notificaciones tras cerrar sesión. **Causa raíz #1 de la fuga de privacidad.**
2. **🟠 La sesión de monitoreo se mantiene viva tras logout.** `AuthRestController.logout` → `cerrarSesionActiva` no la cierra si hay turno; la marca `cerradaPorLogout`. `detectarSinSenal` respeta ese flag, **pero `detectarAbandonos` (geocerca) y los avisos de "GPS pendiente/denegado" NO lo chequean** → pueden seguir notificando al funcionario.
3. **🟠 El gating "solo en turno" está en el cliente, no en el envío del servidor.** Falta filtrar por categoría/turno al **enviar** la notificación.
4. **🟢 Ya existen:** debounce de salida, filtro de precisión, warmup tras reinicio, aviso vs abandono escalonado, `notificarLogoutEnTurnoAlAdmin`, distinción de mensajes "fuera de zona" vs "pérdida de señal".

**Lo que falta construir:** buffer local + endpoint de lote + reconciliación; borrado real del token FCM en logout; gating de envío por categoría/turno; campos de configuración nuevos (sección 5); flujo de justificación de abandono; broadcast general; pantalla de onboarding; captura de señales extra (batería, conectividad, GPS_OFF, isMock).

---

## 12. Decisiones abiertas (validar con RRHH/dirección)

- Umbrales exactos: tolerancia de atraso, umbral de falta, confirmación dura de ausencia.
- ¿La batería agotada (caso 6) es siempre incidencia no punible, o reincidente penaliza?
- ¿Plazo máximo para justificar un abandono después de ocurrido?
- ¿La salida temprana (caso 8) tiene su propio umbral de tolerancia?

---

## 13. Plan de implementación por fases (orden por dependencia, sin fechas)

> Recordatorio: **aún no implementado.** Este es el orden propuesto cuando se autorice a tocar código.

- **Fase 0 — Privacidad (prioridad alta, bajo riesgo):** borrar token FCM en logout; gating de envío por categoría/turno; que todos los caminos del scheduler respeten el logout. Resuelve la fuga ya observada.
- **Fase 1 — Modelo de incidencias:** atraso/falta/salida temprana como registros con minutaje + estado justificable; flujo de justificación de abandono.
- **Fase 2 — Captura de señales:** batería, conectividad, GPS_OFF, isMock en el ping/eventos.
- **Fase 3 — Offline / store-and-forward:** buffer SQLite nativo + endpoint de lote + idempotencia + reconciliación + decisión en dos fases + tolerancia adaptativa a red.
- **Fase 4 — UX:** pantalla de onboarding/consentimiento; broadcast general del sistema; honestidad de estados en el home.

Cada fase es desplegable de forma independiente (Fase 0 solo requiere backend + rebuild APK; las demás según toquen nativo).

---

## 14. Apéndice — Contrato de sincronización (Fase 3) y trabajo nativo pendiente

### Contrato del endpoint de lote (ya implementado en backend)

`POST /api/v1/monitoreo/ubicaciones/lote` — body: array de fixes. Cada fix:

```jsonc
{
  "latitud": -16.5, "longitud": -68.1,
  "precision": 15,                 // opcional (accuracy en metros)
  "bateria": 42, "cargando": false,// opcional (Fase 2)
  "conectividad": "CELULAR",       // WIFI | CELULAR | NINGUNA
  "esMock": false,                 // anti-fraude
  "timestampDispositivoMs": 1719960000000, // epoch millis del fix real
  "clientEventId": "uuid-...."     // idempotencia
}
```

Respuesta: `{ recibidos, procesados, omitidos, ultimoEstado }`. El servidor ordena por
`timestampDispositivoMs`, deduplica por `clientEventId` y reproduce cada fix con la
lógica normal de geocerca. **Reconciliación**: al reproducir un fix DENTRO de zona, el
bloque existente de `registrarUbicacion` resuelve cualquier `EventoAbandono` abierto →
un abandono provisional se revierte a RETORNADO. Así internet inestable no deja faltas falsas.

### Pendiente: cliente nativo (requiere device, NO hecho)

El envío de GPS en producción lo hace el **servicio nativo Java** del plugin parcheado
`@capacitor-community/background-geolocation` (`BackgroundGeolocationService.java`), no el JS
(que en background está congelado). Por eso el buffer real debe vivir en el servicio nativo:

1. **Señales (Fase 2) en `postLocation`**: añadir al JSON `bateria` (BatteryManager),
   `conectividad` (ConnectivityManager: WIFI/CELULAR/NINGUNA), `esMock`
   (`Location.isMock()` API 31+ / `isFromMockProvider()`), `timestampDispositivoMs`
   (`loc.getTime()`), `clientEventId` (UUID por fix).
2. **Buffer SQLite**: si el POST a `/ubicacion` falla (sin red), insertar el fix en una
   tabla local con flag `synced`. Cubre app cerrada/congelada porque es nativo y persistido.
3. **Worker de flush**: al recuperar conectividad (o en el `tickHeartbeat`), leer los no
   sincronizados, hacer `POST /ubicaciones/lote`, y marcar `synced=true` con la respuesta OK.
4. **Eventos explícitos** GPS_OFF / PERMISO_REVOCADO: emitir un fix-evento al detectar que
   se apagó el proveedor de ubicación o se revocó el permiso (broadcast Android), para que el
   servidor sepa la CAUSA del silencio en vez de inferirla.

Workflow: `pnpm patch @capacitor-community/background-geolocation@1.2.26` → editar →
`pnpm patch-commit` → `npx cap sync android` → rebuild APK → **verificar con logs del backend**
(cortar internet caminando, ver que al reconectar llega el `POST /ubicaciones/lote`).
