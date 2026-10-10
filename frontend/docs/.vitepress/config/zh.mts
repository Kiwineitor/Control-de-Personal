import type { DefaultTheme } from 'vitepress';

import { defineConfig } from 'vitepress';

import { version } from '../../../package.json';

export const es = defineConfig({
  description: 'Vben Admin & Framework de gestión empresarial',
  lang: 'es-ES',
  themeConfig: {
    darkModeSwitchLabel: 'Tema',
    darkModeSwitchTitle: 'Cambiar a modo oscuro',
    docFooter: {
      next: 'Siguiente página',
      prev: 'Página anterior',
    },
    editLink: {
      pattern:
        'https://github.com/vbenjs/vue-vben-admin/edit/main/docs/src/:path',
      text: 'Editar esta página en GitHub',
    },
    footer: {
      copyright: `Copyright © 2020-${new Date().getFullYear()} Vben`,
      message: 'Publicado bajo la licencia MIT.',
    },
    langMenuLabel: 'Idioma',
    lastUpdated: {
      formatOptions: {
        dateStyle: 'short',
        timeStyle: 'medium',
      },
      text: 'Última actualización',
    },
    lightModeSwitchTitle: 'Cambiar a modo claro',
    nav: nav(),

    outline: {
      label: 'En esta página',
    },
    returnToTopLabel: 'Volver arriba',

    sidebar: {
      '/commercial/': { base: '/commercial/', items: sidebarCommercial() },
      '/components/': { base: '/components/', items: sidebarComponents() },
      '/guide/': { base: '/guide/', items: sidebarGuide() },
    },
    sidebarMenuLabel: 'Menú',
  },
});

function sidebarGuide(): DefaultTheme.SidebarItem[] {
  return [
    {
      collapsed: false,
      text: 'Introducción',
      items: [
        {
          link: 'introduction/vben',
          text: 'Sobre Vben Admin',
        },
        {
          link: 'introduction/why',
          text: '¿Por qué elegirnos?',
        },
        { link: 'introduction/quick-start', text: 'Inicio rápido' },
        { link: 'introduction/thin', text: 'Versión simplificada' },
        {
          base: '/',
          link: 'components/introduction',
          text: 'Documentación de componentes',
        },
      ],
    },
    {
      text: 'Fundamentos',
      items: [
        { link: 'essentials/concept', text: 'Conceptos básicos' },
        { link: 'essentials/development', text: 'Desarrollo local' },
        { link: 'essentials/route', text: 'Rutas y menús' },
        { link: 'essentials/settings', text: 'Configuración' },
        { link: 'essentials/icons', text: 'Iconos' },
        { link: 'essentials/styles', text: 'Estilos' },
        { link: 'essentials/external-module', text: 'Módulos externos' },
        { link: 'essentials/build', text: 'Construcción y despliegue' },
        { link: 'essentials/server', text: 'Interacción con servidor y Mock de datos' },
      ],
    },
    {
      text: 'A fondo',
      items: [
        { link: 'in-depth/login', text: 'Inicio de sesión' },
        // { link: 'in-depth/layout', text: 'Diseño (Layout)' },
        { link: 'in-depth/theme', text: 'Temas' },
        { link: 'in-depth/access', text: 'Permisos de acceso' },
        { link: 'in-depth/locale', text: 'Internacionalización (i18n)' },
        { link: 'in-depth/features', text: 'Funciones comunes' },
        { link: 'in-depth/check-updates', text: 'Comprobar actualizaciones' },
        { link: 'in-depth/loading', text: 'Carga (Loading) global' },
        { link: 'in-depth/ui-framework', text: 'Cambio de framework de UI' },
      ],
    },
    {
      text: 'Ingeniería',
      items: [
        { link: 'project/standard', text: 'Estándares y convenciones' },
        { link: 'project/cli', text: 'CLI' },
        { link: 'project/dir', text: 'Estructura de directorios' },
        { link: 'project/test', text: 'Pruebas unitarias' },
        { link: 'project/tailwindcss', text: 'Tailwind CSS' },
        { link: 'project/changeset', text: 'Changeset' },
        { link: 'project/vite', text: 'Configuración de Vite' },
      ],
    },
    {
      text: 'Otros',
      items: [
        { link: 'other/project-update', text: 'Actualización del proyecto' },
        { link: 'other/remove-code', text: 'Eliminar código' },
        { link: 'other/faq', text: 'Preguntas frecuentes (FAQ)' },
      ],
    },
  ];
}

function sidebarCommercial(): DefaultTheme.SidebarItem[] {
  return [
    {
      link: 'community',
      text: 'Grupo de la comunidad',
    },
    {
      link: 'technical-support',
      text: 'Soporte técnico',
    },
    {
      link: 'customized',
      text: 'Desarrollo a medida',
    },
  ];
}

function sidebarComponents(): DefaultTheme.SidebarItem[] {
  return [
    {
      text: 'Componentes',
      items: [
        {
          link: 'introduction',
          text: 'Introducción',
        },
      ],
    },
    {
      collapsed: false,
      text: 'Componentes de Diseño (Layout)',
      items: [
        {
          link: 'layout-ui/page',
          text: 'Page Página',
        },
      ],
    },
    {
      collapsed: false,
      text: 'Componentes Comunes',
      items: [
        {
          link: 'common-ui/vben-api-component',
          text: 'ApiComponent Envoltorio de componente API',
        },
        {
          link: 'common-ui/vben-alert',
          text: 'Alert Alerta (Aviso ligero)',
        },
        {
          link: 'common-ui/vben-modal',
          text: 'Modal Ventana modal',
        },
        {
          link: 'common-ui/vben-drawer',
          text: 'Drawer Panel lateral (Panel oculto)',
        },
        {
          link: 'common-ui/vben-form',
          text: 'Form Formulario',
        },
        {
          link: 'common-ui/vben-vxe-table',
          text: 'Vxe Table Tabla',
        },
        {
          link: 'common-ui/vben-count-to-animator',
          text: 'CountToAnimator Animación de contador',
        },
        {
          link: 'common-ui/vben-ellipsis-text',
          text: 'EllipsisText Texto con puntos suspensivos',
        },
      ],
    },
  ];
}

function nav(): DefaultTheme.NavItem[] {
  return [
    {
      activeMatch: '^/(guide|components)/',
      text: 'Documentación',
      items: [
        {
          activeMatch: '^/guide/',
          link: '/guide/introduction/vben',
          text: 'Guía',
        },
        {
          activeMatch: '^/components/',
          link: '/components/introduction',
          text: 'Componentes',
        },
        {
          text: 'Versiones históricas',
          items: [
            {
              link: 'https://doc.vvbin.cn',
              text: 'Documentación v2.x',
            },
          ],
        },
      ],
    },
    {
      text: 'Demos',
      items: [
        {
          text: 'Vben Admin',
          items: [
            {
              link: 'https://www.vben.pro',
              text: 'Versión de demostración',
            },
            {
              link: 'https://ant.vben.pro',
              text: 'Versión Ant Design Vue',
            },
            {
              link: 'https://naive.vben.pro',
              text: 'Versión Naive UI',
            },
            {
              link: 'https://ele.vben.pro',
              text: 'Versión Element Plus',
            },
          ],
        },
        {
          text: 'Otros',
          items: [
            {
              link: 'https://vben.vvbin.cn',
              text: 'Vben Admin 2.x',
            },
          ],
        },
      ],
    },
    {
      text: version,
      items: [
        {
          link: 'https://github.com/vbenjs/vue-vben-admin/releases',
          text: 'Registro de cambios (Changelog)',
        },
        {
          link: 'https://github.com/orgs/vbenjs/projects/5',
          text: 'Hoja de ruta (Roadmap)',
        },
        {
          link: 'https://github.com/vbenjs/vue-vben-admin/blob/main/.github/contributing.md',
          text: 'Contribución',
        },
      ],
    },
    {
      link: '/commercial/technical-support',
      text: '🦄 Soporte técnico',
    },
    {
      link: '/sponsor/personal',
      text: '✨ Patrocinar',
    },
    {
      link: '/commercial/community',
      text: '👨‍👦‍👦 Comunidad',
    },
  ];
}

export const search: DefaultTheme.AlgoliaSearchOptions['locales'] = {
  root: {
    placeholder: 'Buscar en la documentación',
    translations: {
      button: {
        buttonAriaLabel: 'Buscar en la documentación',
        buttonText: 'Buscar',
      },
      modal: {
        errorScreen: {
          helpText: 'Es posible que debas verificar tu conexión de red',
          titleText: 'No se pudieron obtener resultados',
        },
        footer: {
          closeText: 'Cerrar',
          navigateText: 'Navegar',
          searchByText: 'Búsqueda por',
          selectText: 'Seleccionar',
        },
        noResultsScreen: {
          noResultsText: 'No se encontraron resultados relacionados',
          reportMissingResultsLinkText: 'Haz clic para enviarnos tus comentarios',
          reportMissingResultsText: '¿Crees que esta consulta debería tener resultados?',
          suggestedQueryText: 'Puedes intentar buscar',
        },
        searchBox: {
          cancelButtonAriaLabel: 'Cancelar',
          cancelButtonText: 'Cancelar',
          resetButtonAriaLabel: 'Limpiar condiciones de búsqueda',
          resetButtonTitle: 'Limpiar búsqueda',
        },
        startScreen: {
          favoriteSearchesTitle: 'Favoritos',
          noRecentSearchesText: 'No hay historial de búsqueda',
          recentSearchesTitle: 'Historial de búsqueda',
          removeFavoriteSearchButtonTitle: 'Eliminar de favoritos',
          removeRecentSearchButtonTitle: 'Eliminar del historial',
          saveRecentSearchButtonTitle: 'Guardar en el historial',
        },
      },
    },
  },
};
