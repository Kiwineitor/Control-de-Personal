import { defineOverridesPreferences } from '@vben/preferences';

export const overridesPreferences = defineOverridesPreferences({
  app: {
    name: import.meta.env.VITE_APP_TITLE,
    accessMode: 'frontend',
  },
  logo: {
    // Logo del sistema (login + sidebar). Vive en apps/web-naive/public/
    // Variante AZUL (fondos claros: favicon, sidebar, login móvil).
    source: '/logoAzul.png',
  },
  widget: {
    globalSearch:    true,   // buscador global en el header
    themeToggle:     true,   // toggle modo oscuro/claro
    languageToggle:  true,   // selector de idioma
    notification:    true,   // campana de notificaciones
    sidebarToggle:   true,   // botón colapsar sidebar
  },
});
