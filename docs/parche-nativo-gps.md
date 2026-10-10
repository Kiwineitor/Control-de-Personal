# Parche nativo GPS — señales (F2) + buffer offline (F3)

> **Qué hace:** que el servicio nativo `BackgroundGeolocationService.java` (1) adjunte
> batería, conectividad, isMock, timestamp del dispositivo y un id de evento en cada POST;
> y (2) si el POST falla por falta de internet, **bufferice el fix localmente y lo sincronice
> en lote** (`/ubicaciones/lote`) al recuperar conexión. El backend ya está listo para ambos.
>
> **Buffer:** se usa **SharedPreferences (JSON)**, no SQLite. Para la cadencia real de GPS
> (≈1 fix cada 1–2 min) el buffer queda pequeño aun con horas sin red; evita añadir una
> dependencia nativa frágil. Cap de seguridad: 1000 fixes (descarta los más viejos).
>
> **Requisitos:** minSdk 23 (Capacitor 8 ya lo cumple). Probar SIEMPRE en dispositivo real.

---

## 0. Workflow de aplicación

```bash
# desde frontend/
pnpm patch "@capacitor-community/background-geolocation@1.2.26"
# pnpm imprime una ruta temporal tipo:
#   node_modules/.pnpm_patches/@capacitor-community/background-geolocation@1.2.26  (o un temp)
# Edita en ESA ruta el archivo:
#   android/src/main/java/com/equimaps/capacitor_background_geolocation/BackgroundGeolocationService.java
# aplicando los cambios 1–4 de abajo.

pnpm patch-commit "<ruta-temporal-que-imprimio-pnpm>"
# Esto regenera patches/@capacitor-community__background-geolocation@1.2.26.patch
# y puede cambiar el hash del store (es normal; cap sync lo reescribe).

cd apps/web-naive
npx cap sync android          # reescribe capacitor.settings.gradle al nuevo hash
# Abrir Android Studio y recompilar (o):  cd android && ./gradlew.bat assembleDebug
```

Verificación al final (sección 5) con los **logs del backend**, no logcat.

---

## 1. Imports — añadir al bloque de imports

```java
import android.os.BatteryManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.UUID;
```

(`org.json.JSONObject`, `android.os.Build`, `SharedPreferences`, `HttpURLConnection`, etc.
ya están importados.)

---

## 2. Constantes — añadir junto a `PREFS`

Busca:

```java
    private static final String PREFS = "bg_geo_upload";
    private static final String RECOVERY_CHANNEL = "bg_geolocation";
```

Añade debajo:

```java
    private static final String BUFFER_KEY = "fix_buffer";   // cola JSON de fixes offline
    private static final int    BUFFER_MAX = 1000;           // tope de seguridad
    private static final int    BATERIA_DESCONOCIDA = -1;
```

---

## 3. Reemplazar `maybePostLocation` y `postLocation`

**Reemplaza** el método `maybePostLocation(Location loc, boolean force)` y el método
`postLocation(double lat, double lon, float precision)` **completos** por lo siguiente
(se construye el fix como JSON con todas las señales y se decide bufferizar en caso de fallo):

```java
    // ── POST nativo al backend ───────────────────────────────────────────────
    private void maybePostLocation(Location loc, boolean force) {
        if (loc == null) return;
        if (backendUrl == null || authToken == null) return;  // sin config no se sube
        long now = System.currentTimeMillis();
        if (!force && (now - lastPostMs) < (long) (postIntervalMs * 0.9)) return;
        lastPostMs = now;
        // Construir el fix (con señales) AQUI, cuando 'loc' es estable.
        final String fixJson = buildFixJson(loc).toString();
        httpExecutor.execute(new Runnable() {
            @Override
            public void run() {
                postFix(fixJson);
            }
        });
    }

    // Arma el cuerpo del fix con las señales de contexto (Fase 2) + datos de
    // store-and-forward (Fase 3: timestamp real del dispositivo + id de evento).
    private JSONObject buildFixJson(Location loc) {
        JSONObject o = new JSONObject();
        try {
            o.put("latitud", loc.getLatitude());
            o.put("longitud", loc.getLongitude());
            o.put("precision", loc.hasAccuracy() ? loc.getAccuracy() : 9999f);
            int bat = readBatteryLevel();
            if (bat != BATERIA_DESCONOCIDA) o.put("bateria", bat);
            String conn = readConnectivity();
            if (conn != null) o.put("conectividad", conn);
            o.put("esMock", isMockLocation(loc));
            o.put("timestampDispositivoMs", loc.getTime() > 0 ? loc.getTime() : System.currentTimeMillis());
            o.put("clientEventId", UUID.randomUUID().toString());
        } catch (JSONException ignore) {
        }
        return o;
    }

    // POST individual a /ubicacion. Si falla por red/401/5xx, bufferiza el fix
    // para sincronizarlo luego; si tiene éxito, drena el buffer pendiente.
    private void postFix(String fixJson) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(backendUrl + "/api/v1/monitoreo/ubicacion");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            if (authToken != null) conn.setRequestProperty("Authorization", "Bearer " + authToken);
            if (locale != null) conn.setRequestProperty("Accept-Language", locale);

            OutputStream os = conn.getOutputStream();
            os.write(fixJson.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            Log.d("GPS-CHECK", "postFix HTTP " + code);
            if (code >= 200 && code < 300) {
                String resp = readStream(conn.getInputStream());
                try {
                    JSONObject json = new JSONObject(resp);
                    int apiCode = json.optInt("code", -1);
                    JSONObject data = json.optJSONObject("data");
                    if (apiCode == 0 && data != null) {
                        lastServerResponse = data.toString();
                        lastServerOk = true;
                        int nextSec = data.optInt("intervaloProximoEnvioSeg", 0);
                        if (nextSec > 0) {
                            postIntervalMs = nextSec * 1000L;
                            persistInterval(nextSec);
                        }
                    } else {
                        lastServerOk = false;
                    }
                } catch (Exception parse) {
                    lastServerOk = false;
                    Log.e("GPS-CHECK", "postFix parse error: " + parse.getMessage());
                }
                // Hubo conexión → intentar drenar lo que quedó bufferizado.
                flushBuffer();
            } else {
                lastServerOk = false;
                readStream(conn.getErrorStream()); // drenar para reusar la conexión
                // Reintentables: token pendiente de refresco (401) o servidor caído (5xx).
                if (code == 401 || code >= 500) enqueueFix(fixJson);
            }
        } catch (Exception e) {
            // Sin internet / timeout → bufferizar para sincronizar al reconectar.
            lastServerOk = false;
            enqueueFix(fixJson);
            Log.e("GPS-CHECK", "postFix error (bufferizado): " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
```

---

## 4. Añadir helpers nuevos (señales + buffer)

Pégalos dentro de la clase (p. ej. justo después de `postFix`):

```java
    // ── Señales de contexto (Fase 2) ─────────────────────────────────────────
    private int readBatteryLevel() {
        try {
            BatteryManager bm = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                int lvl = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                if (lvl >= 0 && lvl <= 100) return lvl;
            }
        } catch (Exception ignore) {
        }
        return BATERIA_DESCONOCIDA;
    }

    private String readConnectivity() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return null;
            Network n = cm.getActiveNetwork();
            if (n == null) return "NINGUNA";
            NetworkCapabilities caps = cm.getNetworkCapabilities(n);
            if (caps == null) return "NINGUNA";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "WIFI";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "CELULAR";
            return "NINGUNA";
        } catch (Exception ignore) {
            return null;
        }
    }

    private boolean isMockLocation(Location loc) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return loc.isMock();                 // API 31+
            }
            return loc.isFromMockProvider();         // API 18–30 (deprecado en 31)
        } catch (Exception ignore) {
            return false;
        }
    }

    // ── Buffer offline / store-and-forward (Fase 3) ──────────────────────────
    private synchronized void enqueueFix(String fixJson) {
        try {
            SharedPreferences sp = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(sp.getString(BUFFER_KEY, "[]"));
            arr.put(new JSONObject(fixJson));
            while (arr.length() > BUFFER_MAX) arr.remove(0);  // descartar los más viejos
            sp.edit().putString(BUFFER_KEY, arr.toString()).apply();
            Log.d("GPS-CHECK", "fix bufferizado (offline). buffer=" + arr.length());
        } catch (Exception e) {
            Log.e("GPS-CHECK", "enqueueFix error: " + e.getMessage());
        }
    }

    // Envía el buffer al endpoint de lote. Solo limpia los fixes que efectivamente
    // se enviaron (puede haberse encolado alguno más mientras se enviaba).
    private synchronized void flushBuffer() {
        if (backendUrl == null || authToken == null) return;
        SharedPreferences sp = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray arr;
        try {
            arr = new JSONArray(sp.getString(BUFFER_KEY, "[]"));
        } catch (Exception e) {
            sp.edit().remove(BUFFER_KEY).apply();
            return;
        }
        final int enviados = arr.length();
        if (enviados == 0) return;
        Log.d("GPS-CHECK", "flushBuffer: enviando lote de " + enviados);

        HttpURLConnection conn = null;
        try {
            URL url = new URL(backendUrl + "/api/v1/monitoreo/ubicaciones/lote");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
            if (locale != null) conn.setRequestProperty("Accept-Language", locale);

            OutputStream os = conn.getOutputStream();
            os.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            Log.d("GPS-CHECK", "flushBuffer HTTP " + code);
            if (code >= 200 && code < 300) {
                // Conservar solo lo encolado DESPUÉS de lo que acabamos de enviar.
                JSONArray actual = new JSONArray(sp.getString(BUFFER_KEY, "[]"));
                JSONArray resto = new JSONArray();
                for (int i = enviados; i < actual.length(); i++) resto.put(actual.get(i));
                sp.edit().putString(BUFFER_KEY, resto.toString()).apply();
            } else {
                readStream(conn.getErrorStream());
            }
        } catch (Exception e) {
            Log.e("GPS-CHECK", "flushBuffer error: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
```

> **Nota concurrencia:** `enqueueFix`/`flushBuffer` corren en el `httpExecutor`
> (un solo hilo) y van `synchronized` por si el heartbeat coincide. El drenado
> ocurre en el primer POST exitoso tras recuperar internet (el heartbeat de 60s
> garantiza un intento aunque el usuario esté quieto).

---

## 5. Verificación en dispositivo (con logs del BACKEND)

1. Inicia turno con la app; confirma en backend que llegan POST a `/monitoreo/ubicacion`
   y que ahora el body trae `bateria`, `conectividad`, `esMock`, `timestampDispositivoMs`.
2. **Corta el internet del teléfono** (modo avión con GPS… o solo datos off) y camina
   unos minutos. En logcat verás `fix bufferizado (offline). buffer=N` creciendo.
3. **Reactiva internet.** En el siguiente fix verás `flushBuffer: enviando lote de N` y
   en el backend un `POST /ubicaciones/lote` con N fixes. El log del servidor dirá
   `Lote de ubicaciones procesado: ... procesados=N`.
4. Si durante el corte el scheduler marcó un abandono provisional, al llegar el lote con
   fixes DENTRO debe revertirse a RETORNADO (reconciliación).

---

## 6. Opcional / futuro (no incluido para acotar el parche)

- **Eventos explícitos GPS_OFF / PERMISO_REVOCADO**: emitir un fix-evento desde
  `onProviderDisabled` (cuando `provider == GPS_PROVIDER`) o un `BroadcastReceiver` de
  `PROVIDERS_CHANGED`, para que el backend conozca la *causa* del silencio. Hoy el
  `onProviderDisabled` solo loguea; convertirlo en un POST de evento es el siguiente paso.
- **Idempotencia ya cubierta:** cada fix lleva `clientEventId`; el backend deduplica, así
  que reintentar un lote no duplica registros.

---

## 7. Rollback

Si algo sale mal: borra `patches/@capacitor-community__background-geolocation@1.2.26.patch`
(o revierte sus líneas nuevas), quita `patchedDependencies` del `package.json` si quedó
inconsistente, `pnpm install`, `npx cap sync android`, rebuild. Vuelve al POST simple sin
señales ni buffer (el backend sigue aceptando ambos formatos: los campos nuevos son opcionales).
