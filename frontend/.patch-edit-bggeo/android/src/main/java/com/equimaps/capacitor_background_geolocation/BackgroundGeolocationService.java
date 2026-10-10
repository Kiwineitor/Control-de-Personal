package com.equimaps.capacitor_background_geolocation;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import com.getcapacitor.Logger;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

// A bound and started service that is promoted to a foreground service
// (showing a persistent notification) when the first background watcher is
// added, and demoted when the last background watcher is removed.
public class BackgroundGeolocationService extends Service {
    static final String ACTION_BROADCAST = (
            BackgroundGeolocationService.class.getPackage().getName() + ".broadcast"
    );
    private final IBinder binder = new LocalBinder();

    // Must be unique for this application.
    private static final int NOTIFICATION_ID = 28351;
    private static final long HEARTBEAT_MS = 60_000L; // 60s
    private static final long GPS_MIN_TIME_MS = 1000L;  // 1 segundo
    private static final long NET_MIN_TIME_MS = 5000L;  // 5 segundos (fallback)
    private static final String PREFS = "bg_geo_upload";
    private static final String RECOVERY_CHANNEL = "bg_geolocation";

    private class Watcher {
        public String id;
        public LocationManager locationManager;
        public LocationListener locationListener;
        public Notification backgroundNotification;
        public float distanceFilter;
    }
    private HashSet<Watcher> watchers = new HashSet<Watcher>();
    private Location lastLocation = null;
    private PowerManager.WakeLock wakeLock = null;
    private Handler heartbeatHandler = null;
    private Runnable heartbeatTask = null;

    // ── Subida HTTP nativa ───────────────────────────────────────────────────
    // El POST al backend lo hace ESTE servicio (no el JavaScript). Asi sobrevive
    // a que la ROM congele/mate la WebView con la pantalla bloqueada o la app
    // cerrada. La config se persiste en SharedPreferences para que un reinicio
    // por START_STICKY (sin JS vivo) pueda seguir reportando.
    private final ExecutorService httpExecutor = Executors.newSingleThreadExecutor();
    private volatile String backendUrl = null;   // ej: http://192.168.20.145:8085
    private volatile String authToken = null;    // JWT Bearer
    private volatile String locale = "es-ES";
    private volatile long postIntervalMs = 120_000L;  // adaptativo (lo ajusta el backend)
    private volatile long lastPostMs = 0L;
    private volatile String lastServerResponse = null; // JSON 'data' de la ultima respuesta OK
    private volatile boolean lastServerOk = false;
    private volatile boolean gpsActivo = true;     // true mientras el GPS_PROVIDER este habilitado
    private volatile long ultimoFixGpsMs = 0L;     // timestamp del ultimo fix de GPS (0 = nunca)

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // START_STICKY: si el sistema mata el servicio (por presion de memoria o
    // ROM agresiva), Android lo recreara automaticamente cuando pueda.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("GPS-CHECK", "onStartCommand - watchers=" + watchers.size() + " intent=" + intent);

        // RECOVERY: solo en un REINICIO real por START_STICKY (proceso muerto).
        // Android entrega intent==null al recrear un servicio sticky; en el arranque
        // normal (startService desde load()) el intent es explicito (no null) y el
        // JS llamara addWatcher en breve, asi que NO recuperamos (evita un watcher
        // "recovery" huerfano que removeWatcher no limpiaria -> fuga de GPS tras
        // logout). En la recuperacion no hay WebView/JS, asi que debemos:
        //  1) recuperar la config de subida persistida,
        //  2) volvernos foreground de forma legal (notificacion + startForeground),
        //  3) re-registrar el GPS y reanudar los POST NATIVOS sin depender del JS.
        if (intent == null && watchers.isEmpty()) {
            Log.d("GPS-CHECK", "onStartCommand - RECOVERY: reinicio sticky, recuperando");
            loadUploadConfigFromPrefs();
            if (backendUrl == null || authToken == null) {
                // Sin sesion persistida (logout / nunca arranco) no hay a quien
                // reportar: detener el servicio en vez de gastar GPS en vano.
                Log.d("GPS-CHECK", "onStartCommand - RECOVERY sin config, deteniendo");
                stopSelf();
                return START_NOT_STICKY;
            }
            try {
                startForeground(NOTIFICATION_ID, buildFallbackNotification());
            } catch (Exception e) {
                Log.e("GPS-CHECK", "Recovery - startForeground error: " + e.getMessage());
            }
            registerRecoveryWatcher();
            acquireWakeLock();
            startHeartbeat();
        }

        return START_STICKY;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true;
    }

    @Override
    public void onDestroy() {
        stopHeartbeat();
        releaseWakeLock();
        httpExecutor.shutdown();
        super.onDestroy();
    }

    Notification getNotification() {
        for (Watcher watcher : watchers) {
            if (watcher.backgroundNotification != null) {
                return watcher.backgroundNotification;
            }
        }
        return null;
    }

    private void acquireWakeLock() {
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "BackgroundGeolocation:LocationWakeLock"
            );
            wakeLock.acquire(10 * 60 * 1000L); // 10 min timeout safety
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    private void startHeartbeat() {
        if (heartbeatHandler != null) return;
        heartbeatHandler = new Handler(Looper.getMainLooper());
        heartbeatTask = new Runnable() {
            @Override
            public void run() {
                tickHeartbeat();
                heartbeatHandler.postDelayed(this, HEARTBEAT_MS);
            }
        };
        heartbeatHandler.postDelayed(heartbeatTask, HEARTBEAT_MS);
    }

    private void stopHeartbeat() {
        if (heartbeatHandler != null && heartbeatTask != null) {
            heartbeatHandler.removeCallbacks(heartbeatTask);
        }
        heartbeatHandler = null;
        heartbeatTask = null;
    }

    // Heartbeat nativo: cada 60s re-postea nativamente la ultima ubicacion (cubre
    // al usuario quieto y la recuperacion sin JS) y la reemite al JS si hay UI viva.
    // Solo repostea si la ultima ubicacion es de GPS, o si GPS no ha dado
    // señales en los ultimos 60s (NETWORK como respaldo indoor).
    private void tickHeartbeat() {
        if (lastLocation == null) return;
        String provider = lastLocation.getProvider();
        boolean gpsReciente = (System.currentTimeMillis() - ultimoFixGpsMs) < 60_000L;
        boolean esNetwork = LocationManager.NETWORK_PROVIDER.equals(provider);
        if (esNetwork && gpsReciente) return;
        maybePostLocation(lastLocation, false);
        for (Watcher watcher : watchers) {
            broadcastLocation(lastLocation, watcher.id);
        }
    }

    // Crea un LocationListener que postea nativamente y reemite broadcast al JS.
    private LocationListener createListener(final String id) {
        return new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (location == null) return;
                String provider = location.getProvider();
                boolean isGps = LocationManager.GPS_PROVIDER.equals(provider);
                boolean isNetwork = LocationManager.NETWORK_PROVIDER.equals(provider);

                // Actualizar timestamp si es fix de GPS
                if (isGps) ultimoFixGpsMs = System.currentTimeMillis();

                // Filtro: si llegó un fix de GPS hace menos de 30s, ignorar
                // los fixes de NETWORK. El GPS es más preciso; las ubicaciones
                // por WiFi/torre solo causan falsos saltos de zona.
                boolean gpsReciente = (System.currentTimeMillis() - ultimoFixGpsMs) < 30_000L;
                if (isNetwork && gpsReciente) {
                    broadcastLocation(location, id);
                    return;
                }

                // Filtro de precisión: si es NETWORK (GPS apagado o sin señal)
                // y la precisión es > 100m, no postear (muy impreciso).
                if (isNetwork) {
                    float acc = location.hasAccuracy() ? location.getAccuracy() : 9999f;
                    if (acc > 100f) {
                        broadcastLocation(location, id);
                        return;
                    }
                }

                Log.d("GPS-CHECK", "onLocationChanged id=" + id + " provider=" + provider);
                lastLocation = location;
                maybePostLocation(location, false);
                broadcastLocation(location, id);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
                Logger.debug("Location provider " + provider + " status: " + status);
            }

            @Override
            public void onProviderEnabled(String provider) {
                Log.d("GPS-CHECK", "Location provider " + provider + " enabled");
                if (LocationManager.GPS_PROVIDER.equals(provider)) {
                    gpsActivo = true;
                }
            }

            @Override
            public void onProviderDisabled(String provider) {
                Log.d("GPS-CHECK", "Location provider " + provider + " disabled");
                if (LocationManager.GPS_PROVIDER.equals(provider)) {
                    gpsActivo = false;
                }
            }
        };
    }

    // Reemite la ubicacion al JS (UI/mapa) adjuntando la ultima respuesta del
    // servidor para que el front actualice zona/intervalo y el estado de senal.
    private void broadcastLocation(Location location, String id) {
        Intent intent = new Intent(ACTION_BROADCAST);
        intent.putExtra("location", location);
        intent.putExtra("id", id);
        intent.putExtra("serverResponse", lastServerResponse);
        intent.putExtra("serverOk", lastServerOk);
        intent.putExtra("gpsActivo", gpsActivo);
        intent.putExtra("proveedor", location.getProvider());
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    private void requestUpdatesFrom(Watcher watcher, String provider, long minTimeMs) {
        try {
            watcher.locationManager.requestLocationUpdates(
                    provider,
                    minTimeMs,
                    watcher.distanceFilter,
                    watcher.locationListener,
                    Looper.getMainLooper()
            );
        } catch (SecurityException ignore) {
        } catch (IllegalArgumentException e) {
            Logger.warn("Provider " + provider + " not available: " + e.getMessage());
        }
    }

    private void removeUpdatesFrom(Watcher watcher, String provider) {
        try {
            watcher.locationManager.removeUpdates(watcher.locationListener);
        } catch (SecurityException ignore) {
        }
    }

    // ── Config de subida (persistente) ───────────────────────────────────────
    void setUploadConfig(String url, String token, String localeStr, long intervalSec) {
        if (url != null && !url.isEmpty()) this.backendUrl = stripTrailingSlash(url);
        if (token != null && !token.isEmpty()) this.authToken = token;
        if (localeStr != null && !localeStr.isEmpty()) this.locale = localeStr;
        if (intervalSec > 0) this.postIntervalMs = intervalSec * 1000L;
        SharedPreferences sp = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit()
                .putString("url", this.backendUrl)
                .putString("token", this.authToken)
                .putString("locale", this.locale)
                .putLong("intervalMs", this.postIntervalMs)
                .apply();
        Log.d("GPS-CHECK", "setUploadConfig url=" + this.backendUrl + " token=" + (this.authToken != null ? "(set)" : "null"));
    }

    private void loadUploadConfigFromPrefs() {
        SharedPreferences sp = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.backendUrl = sp.getString("url", this.backendUrl);
        this.authToken = sp.getString("token", this.authToken);
        this.locale = sp.getString("locale", this.locale);
        this.postIntervalMs = sp.getLong("intervalMs", this.postIntervalMs);
    }

    // Limpia la config (logout / ultimo watcher): un reinicio posterior NO debe
    // seguir reportando con el token de un usuario que cerro sesion.
    void clearUploadConfig() {
        this.backendUrl = null;
        this.authToken = null;
        SharedPreferences sp = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().clear().apply();
    }

    private static String stripTrailingSlash(String s) {
        return (s != null && s.endsWith("/")) ? s.substring(0, s.length() - 1) : s;
    }

    // ── POST nativo al backend ───────────────────────────────────────────────
    private void maybePostLocation(Location loc, boolean force) {
        if (loc == null) return;
        if (backendUrl == null || authToken == null) return;  // sin config no se puede subir
        long now = System.currentTimeMillis();
        if (!force && (now - lastPostMs) < (long) (postIntervalMs * 0.9)) return;
        lastPostMs = now;
        final double lat = loc.getLatitude();
        final double lon = loc.getLongitude();
        // Precisión del fix: el backend la usa para no marcar falso "fuera de zona"
        // con fixes imprecisos (NETWORK_PROVIDER). Sin accuracy -> 9999 (no confiable).
        final float acc = loc.hasAccuracy() ? loc.getAccuracy() : 9999f;
        httpExecutor.execute(new Runnable() {
            @Override
            public void run() {
                postLocation(lat, lon, acc);
            }
        });
    }

    private void postLocation(double lat, double lon, float precision) {
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

            String body = "{\"latitud\":" + lat + ",\"longitud\":" + lon
                    + ",\"precision\":" + precision + "}";
            OutputStream os = conn.getOutputStream();
            os.write(body.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            Log.d("GPS-CHECK", "postLocation HTTP " + code + " lat=" + lat + " lon=" + lon + " acc=" + precision);
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
                    Log.e("GPS-CHECK", "postLocation parse error: " + parse.getMessage());
                }
            } else {
                // 401 (token vencido), 5xx, etc. -> sin senal confirmada.
                lastServerOk = false;
                readStream(conn.getErrorStream()); // drenar para reusar la conexion
            }
        } catch (Exception e) {
            lastServerOk = false;
            Log.e("GPS-CHECK", "postLocation error: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void persistInterval(int sec) {
        SharedPreferences sp = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putLong("intervalMs", sec * 1000L).apply();
    }

    private String readStream(InputStream is) {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
        } catch (Exception ignore) {
        }
        return sb.toString();
    }

    // ── Notificacion de recuperacion (cuando no hay watcher con notificacion) ──
    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(RECOVERY_CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(
                        RECOVERY_CHANNEL,
                        "Monitoreo de ubicacion",
                        NotificationManager.IMPORTANCE_LOW
                );
                ch.setDescription("Mantiene el seguimiento GPS durante el turno.");
                nm.createNotificationChannel(ch);
            }
        }
    }

    private Notification buildFallbackNotification() {
        ensureChannel();
        int icon = getApplicationInfo().icon;
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, RECOVERY_CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        b.setContentTitle("Monitoreo activo")
                .setContentText("Seguimiento de ubicacion en curso.")
                .setSmallIcon(icon)
                .setOngoing(true);
        return b.build();
    }

    private void registerRecoveryWatcher() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        Watcher w = new Watcher();
        w.id = "recovery";
        w.locationManager = lm;
        w.locationListener = createListener("recovery");
        w.distanceFilter = 0f;
        watchers.add(w);
        requestUpdatesFrom(w, LocationManager.GPS_PROVIDER, GPS_MIN_TIME_MS);
        requestUpdatesFrom(w, LocationManager.NETWORK_PROVIDER, NET_MIN_TIME_MS);
    }

    // Da de baja TODOS los watchers (corta sus updates de GPS/NETWORK) y vacia el
    // set, SIN tocar el servicio/foreground/config. Lo usa addWatcher para ser
    // idempotente (nuestra app usa un solo watcher; asi nunca se acumulan
    // huerfanos que seguirian posteando tras un logout).
    private void clearAllWatchersInternal() {
        for (Watcher w : watchers) {
            removeUpdatesFrom(w, LocationManager.GPS_PROVIDER);
            removeUpdatesFrom(w, LocationManager.NETWORK_PROVIDER);
        }
        watchers.clear();
    }

    // Apagado TOTAL (logout / fin de monitoreo): corta todos los watchers, detiene
    // heartbeat y wakelock, OLVIDA las credenciales (token) para que ni un reinicio
    // por START_STICKY siga reportando, baja el foreground y detiene el servicio.
    void stopEverything() {
        Log.d("GPS-CHECK", "stopEverything - apagando monitoreo (logout)");
        clearAllWatchersInternal();
        stopHeartbeat();
        releaseWakeLock();
        clearUploadConfig();
        try {
            stopForeground(true);
        } catch (Exception ignore) {
        }
        stopSelf();
    }

    // Handles requests from the activity.
    public class LocalBinder extends Binder {
        // Config de subida: el plugin la pasa con la URL del backend + el JWT del
        // usuario para que el POST nativo pueda autenticarse en background.
        void setUploadConfig(String url, String token, String localeStr, long intervalSec) {
            BackgroundGeolocationService.this.setUploadConfig(url, token, localeStr, intervalSec);
        }

        void addWatcher(
                final String id,
                Notification backgroundNotification,
                float distanceFilter
        ) {
            Log.d("GPS-CHECK", "addWatcher id=" + id + " distanceFilter=" + distanceFilter);
            // Idempotencia: limpiar cualquier watcher previo (incl. el de recovery
            // o uno huerfano de un ciclo de foreground) para que SIEMPRE haya uno solo.
            clearAllWatchersInternal();
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            LocationListener listener = createListener(id);

            Watcher watcher = new Watcher();
            watcher.id = id;
            watcher.locationManager = locationManager;
            watcher.locationListener = listener;
            watcher.backgroundNotification = backgroundNotification;
            watcher.distanceFilter = distanceFilter;
            watchers.add(watcher);

            // GPS_PROVIDER: principal. Funciona directamente con el chip GPS,
            // no depende de Google Play Services. Sigue funcionando con la
            // pantalla bloqueada, incluso en ROMs agresivas (XOS, MIUI, etc.).
            requestUpdatesFrom(watcher, LocationManager.GPS_PROVIDER, GPS_MIN_TIME_MS);

            // NETWORK_PROVIDER: fallback para cuando el GPS no tiene senal
            // (interior de edificios, tuneles, etc.). Menos preciso pero evita
            // "Location not available" cuando no hay fix de GPS.
            requestUpdatesFrom(watcher, LocationManager.NETWORK_PROVIDER, NET_MIN_TIME_MS);

            // Promote the service to the foreground if necessary.
            if (backgroundNotification != null) {
                try {
                    startForeground(NOTIFICATION_ID, backgroundNotification);
                } catch (Exception exception) {
                    Logger.error("Failed to foreground service", exception);
                }
            }

            // Adquirir wake lock para evitar que la ROM agresiva (XOS, MIUI, etc.)
            // duerma la CPU y congele el proceso.
            acquireWakeLock();
            // Heartbeat nativo periodico como respaldo.
            startHeartbeat();
        }

        void removeWatcher(String id) {
            for (Watcher watcher : watchers) {
                if (watcher.id.equals(id)) {
                    removeUpdatesFrom(watcher, LocationManager.GPS_PROVIDER);
                    removeUpdatesFrom(watcher, LocationManager.NETWORK_PROVIDER);
                    watchers.remove(watcher);
                    if (getNotification() == null) {
                        stopForeground(true);
                    }
                    if (watchers.isEmpty()) {
                        stopHeartbeat();
                        releaseWakeLock();
                        // Logout / fin de turno: olvidar credenciales para que un
                        // reinicio por START_STICKY no siga reportando.
                        clearUploadConfig();
                    }
                    return;
                }
            }
        }

        void onPermissionsGranted() {
            for (Watcher watcher : watchers) {
                removeUpdatesFrom(watcher, LocationManager.GPS_PROVIDER);
                removeUpdatesFrom(watcher, LocationManager.NETWORK_PROVIDER);
                requestUpdatesFrom(watcher, LocationManager.GPS_PROVIDER, GPS_MIN_TIME_MS);
                requestUpdatesFrom(watcher, LocationManager.NETWORK_PROVIDER, NET_MIN_TIME_MS);
            }
        }

        void stopService() {
            stopHeartbeat();
            releaseWakeLock();
            BackgroundGeolocationService.this.stopSelf();
        }

        // Apagado total para el logout (lo invoca el plugin via stopAll()).
        void stopAll() {
            BackgroundGeolocationService.this.stopEverything();
        }
    }
}
