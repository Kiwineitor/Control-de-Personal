package com.equimaps.capacitor_background_geolocation;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.graphics.Color;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Logger;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import org.json.JSONObject;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

@CapacitorPlugin(
        name = "BackgroundGeolocation",
        permissions = {
                @Permission(
                        strings = {
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION
                        },
                        alias = "location"
                )
        }
)
public class BackgroundGeolocation extends Plugin {
    private BackgroundGeolocationService.LocalBinder service = null;
    private Boolean stoppedWithoutPermissions = false;

    private void fetchLastLocation(PluginCall call) {
        try {
            LocationServices.getFusedLocationProviderClient(
                    getContext()
            ).getLastLocation().addOnSuccessListener(
                    getActivity(),
                    new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {
                                call.resolve(formatLocation(location));
                            }
                        }
                    }
            );
        } catch (SecurityException ignore) {}
    }

    @PluginMethod(returnType = PluginMethod.RETURN_CALLBACK)
    public void addWatcher(final PluginCall call) {
        if (service == null) {
            call.reject("Service not running.");
            return;
        }
        call.setKeepAlive(true);

        if (getPermissionState("location") != PermissionState.GRANTED) {
            if (call.getBoolean("requestPermissions", true)) {
                requestPermissionForAlias("location", call, "locationPermissionsCallback");
            } else {
                call.reject("Permission denied.", "NOT_AUTHORIZED");
            }
        } else if (!isLocationEnabled(getContext())) {
            call.reject("Location services disabled.", "NOT_AUTHORIZED");
        }
        if (call.getBoolean("stale", false)) {
            fetchLastLocation(call);
        }
        Notification backgroundNotification = null;
        String backgroundMessage = call.getString("backgroundMessage");

        if (backgroundMessage != null) {
            Notification.Builder builder = new Notification.Builder(getContext())
                    .setContentTitle(
                            call.getString(
                                "backgroundTitle",
                                "Using your location"
                            )
                    )
                    .setContentText(backgroundMessage)
                    .setOngoing(true)
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setWhen(System.currentTimeMillis());

            try {
                String name = getAppString(
                        "capacitor_background_geolocation_notification_icon",
                        "mipmap/ic_launcher"
                );
                String[] parts = name.split("/");
                // It is actually necessary to set a valid icon for the notification to behave
                // correctly when tapped. If there is no icon specified, tapping it will open the
                // app's settings, rather than bringing the application to the foreground.
                builder.setSmallIcon(
                        getAppResourceIdentifier(parts[1], parts[0])
                );
            } catch (Exception e) {
                Logger.error("Could not set notification icon", e);
            }

            try {
                String color = getAppString(
                        "capacitor_background_geolocation_notification_color",
                        null
                );
                if (color != null) {
                    builder.setColor(Color.parseColor(color));
                }
            } catch (Exception e) {
                Logger.error("Could not set notification color", e);
            }

            Intent launchIntent = getContext().getPackageManager().getLaunchIntentForPackage(
                    getContext().getPackageName()
            );
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                builder.setContentIntent(
                        PendingIntent.getActivity(
                                getContext(),
                                0,
                                launchIntent,
                                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE
                        )
                );
            }

            // Set the Channel ID for Android O.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setChannelId(BackgroundGeolocationService.class.getPackage().getName());
            }

            backgroundNotification = builder.build();
        }
        // Config de subida NATIVA: el servicio hara el POST al backend con estos
        // datos (URL absoluta + JWT) para que el reporte sobreviva a la app
        // cerrada / pantalla bloqueada, sin depender del JS/WebView.
        Integer intervalSeconds = call.getInt("intervalSeconds", 0);
        service.setUploadConfig(
                call.getString("url"),
                call.getString("authToken"),
                call.getString("locale", "es-ES"),
                intervalSeconds == null ? 0 : intervalSeconds.intValue()
        );
        service.addWatcher(
                call.getCallbackId(),
                backgroundNotification,
                call.getFloat("distanceFilter", 0f)
        );
    }

    // Apagado total del monitoreo (logout / fin de turno): detiene TODOS los
    // watchers nativos, baja el servicio en primer plano y olvida el token. Sin
    // esto, un watcher huerfano seguiria posteando ubicacion tras cerrar sesion.
    @PluginMethod()
    public void stopAll(PluginCall call) {
        if (service != null) {
            service.stopAll();
        }
        call.resolve();
    }

    // Refresca la config de subida sin reiniciar el watch. La usa el JS cuando el
    // JWT rota (p.ej. al volver a primer plano) para que el POST nativo en
    // background siga autenticando.
    @PluginMethod()
    public void updateUploadConfig(PluginCall call) {
        if (service == null) {
            call.reject("Service not running.");
            return;
        }
        Integer intervalSeconds = call.getInt("intervalSeconds", 0);
        service.setUploadConfig(
                call.getString("url"),
                call.getString("authToken"),
                call.getString("locale", "es-ES"),
                intervalSeconds == null ? 0 : intervalSeconds.intValue()
        );
        call.resolve();
    }

    @PermissionCallback
    private void locationPermissionsCallback(PluginCall call) {

        if (getPermissionState("location") != PermissionState.GRANTED) {
            call.reject("User denied location permission", "NOT_AUTHORIZED");
            return;
        }
        if (call.getBoolean("stale", false)) {
            fetchLastLocation(call);
        }
        if (service != null) {
            service.onPermissionsGranted();
            // The handleOnResume method will now be called, and we don't need it to call
            // service.onPermissionsGranted again so we reset this flag.
            stoppedWithoutPermissions = false;
        }
    }

    @PluginMethod()
    public void removeWatcher(PluginCall call) {
        String callbackId = call.getString("id");
        if (callbackId == null) {
            call.reject("Missing id.");
            return;
        }
        service.removeWatcher(callbackId);
        PluginCall savedCall = getBridge().getSavedCall(callbackId);
        if (savedCall != null) {
            savedCall.release(getBridge());
        }
        call.resolve();
    }

    @PluginMethod()
    public void openSettings(PluginCall call) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getContext().getPackageName(), null);
        intent.setData(uri);
        getContext().startActivity(intent);
        call.resolve();
    }

    // Checks if device-wide location services are disabled
    private static Boolean isLocationEnabled(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            return lm != null && lm.isLocationEnabled();
        } else {
            return (
                    Settings.Secure.getInt(
                            context.getContentResolver(),
                            Settings.Secure.LOCATION_MODE,
                            Settings.Secure.LOCATION_MODE_OFF
                    ) != Settings.Secure.LOCATION_MODE_OFF
            );
        }
    }

    private static JSObject formatLocation(Location location) {
        JSObject obj = new JSObject();
        obj.put("latitude", location.getLatitude());
        obj.put("longitude", location.getLongitude());
        // The docs state that all Location objects have an accuracy, but then why is there a
        // hasAccuracy method? Better safe than sorry.
        obj.put("accuracy", location.hasAccuracy() ? location.getAccuracy() : JSONObject.NULL);
        obj.put("altitude", location.hasAltitude() ? location.getAltitude() : JSONObject.NULL);
        if (Build.VERSION.SDK_INT >= 26 && location.hasVerticalAccuracy()) {
            obj.put("altitudeAccuracy", location.getVerticalAccuracyMeters());
        } else {
            obj.put("altitudeAccuracy", JSONObject.NULL);
        }
        // In addition to mocking locations in development, Android allows the
        // installation of apps which have the power to simulate location
        // readings in other apps.
        obj.put("simulated", location.isFromMockProvider());
        obj.put("speed", location.hasSpeed() ? location.getSpeed() : JSONObject.NULL);
        obj.put("bearing", location.hasBearing() ? location.getBearing() : JSONObject.NULL);
        obj.put("time", location.getTime());
        return obj;
    }

    // Receives messages from the service.
    private class ServiceReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String id = intent.getStringExtra("id");
            PluginCall call = getBridge().getSavedCall(id);
            if (call == null) {
                return;
            }
            Location location = intent.getParcelableExtra("location");
            if (location != null) {
                JSObject obj = formatLocation(location);
                // El servicio adjunta la ultima respuesta del backend (estado de
                // zona, intervalo, etc.) para que el JS pinte la UI sin tener que
                // postear el mismo (en nativo, el upload lo hace el servicio).
                String serverResponse = intent.getStringExtra("serverResponse");
                if (serverResponse != null) {
                    obj.put("serverResponse", serverResponse);
                }
                obj.put("serverOk", intent.getBooleanExtra("serverOk", false));
                obj.put("gpsActivo", intent.getBooleanExtra("gpsActivo", true));
                obj.put("proveedor", intent.getStringExtra("proveedor"));
                call.resolve(obj);
            } else {
                Logger.debug("No locations received");
            }
        }
    }

    // Gets the identifier of the app's resource by name, returning 0 if not found.
    private int getAppResourceIdentifier(String name, String defType) {
        return getContext().getResources().getIdentifier(
                name,
                defType,
                getContext().getPackageName()
        );
    }

    // Gets a string from the app's strings.xml file, resorting to a fallback if it is not defined.
    private String getAppString(String name, String fallback) {
        int id = getAppResourceIdentifier(name, "string");
        return id == 0 ? fallback : getContext().getString(id);
    }

    @Override
    public void load() {
        super.load();
        Log.d("GPS-CHECK", "Plugin load() called - PATCHED VERSION ACTIVA");

        // Android O requires a Notification Channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getContext().getSystemService(
                    Context.NOTIFICATION_SERVICE
            );
            NotificationChannel channel = new NotificationChannel(
                    BackgroundGeolocationService.class.getPackage().getName(),
                    getAppString(
                            "capacitor_background_geolocation_notification_channel_name",
                            "Background Tracking"
                    ),
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.enableLights(false);
            channel.enableVibration(false);
            channel.setSound(null, null);
            manager.createNotificationChannel(channel);
        }

        // Iniciar el servicio (started + bound) para que START_STICKY aplique y
        // sobreviva a la destruccion de la Activity, incluso en ROMs agresivas
        // (XOS, MIUI). Usamos startService PLANO (no startForegroundService):
        // load() corre con la app en primer plano, asi que es legal, y evita el
        // requisito de llamar startForeground() en <5s que crashea la app
        // (ForegroundServiceDidNotStartInTimeException) cuando addWatcher tarda.
        // El servicio se promueve a foreground dentro de addWatcher (notificacion
        // real) o en onStartCommand al recuperarse tras un kill.
        Intent srvIntent = new Intent(this.getContext(), BackgroundGeolocationService.class);
        getContext().startService(srvIntent);

        this.getContext().bindService(
                srvIntent,
                new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder binder) {
                        BackgroundGeolocation.this.service = (BackgroundGeolocationService.LocalBinder) binder;
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                    }
                },
                Context.BIND_AUTO_CREATE
        );

        LocalBroadcastManager.getInstance(this.getContext()).registerReceiver(
                new ServiceReceiver(),
                new IntentFilter(BackgroundGeolocationService.ACTION_BROADCAST)
        );
    }

    @Override
    protected void handleOnResume() {
        if (service != null) {
            if (stoppedWithoutPermissions && getPermissionState("location") == PermissionState.GRANTED) {
                service.onPermissionsGranted();
            }
        }
        super.handleOnResume();
    }

    @Override
    protected void handleOnPause() {
        stoppedWithoutPermissions = getPermissionState("location") != PermissionState.GRANTED;
        super.handleOnPause();
    }

    @Override
    protected void handleOnDestroy() {
        // No detener el servicio: Android (especialmente XOS y ROMs chinas) destruye
        // la Activity agresivamente en segundo plano, y si matamos el servicio aquí
        // el GPS deja de funcionar hasta que el usuario vuelva a abrir la app.
        super.handleOnDestroy();
    }
}
