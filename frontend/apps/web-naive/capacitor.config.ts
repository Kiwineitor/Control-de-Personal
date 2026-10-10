import type { CapacitorConfig } from '@capacitor/cli';

const isDev = process.env.NODE_ENV !== 'production';

const config: CapacitorConfig = {
  appId: 'com.uap.sisu',
  appName: 'SICOPE',
  webDir: 'dist',
  server: {
    androidScheme: 'http',        // cambiar a 'https' en producción con SSL

    // ── Solo para desarrollo con Live Reload ──────────────────
    // Descomentar, poner la IP de tu PC, y ejecutar: npx cap run android
    // url      : 'http://192.168.20.171:5888',
    // cleartext: true,
  },

  plugins: {
    PushNotifications: {
      presentationOptions: ['badge', 'sound', 'alert'],
    },
    Geolocation: {
      // Android pide permiso automáticamente si está en el manifest
    },
  },
};

export default config;
