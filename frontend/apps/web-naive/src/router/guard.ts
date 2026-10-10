import type { Router, RouteRecordRaw } from 'vue-router';

import { START_LOCATION } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { preferences } from '@vben/preferences';
import { useAccessStore, useUserStore } from '@vben/stores';
import { startProgress, stopProgress } from '@vben/utils';

import { accessRoutes, coreRouteNames } from '#/router/routes';
import { useAuthStore } from '#/store';

import { generateAccess } from './access';

const APP_FUNCIONARIO_ROLES = new Set([
  'ROLE_CAJERO', 'ROLE_ENFERMERO', 'ROLE_FARMACIA',
  'ROLE_INTERNO', 'ROLE_LIMPIEZA', 'ROLE_MEDICO',
]);

/**
 * Tope de espera para validar la sesión durante el arranque.
 *
 * El cliente HTTP corta a los 10 s, pero eso son 10 s de pantalla de carga con la
 * app congelada — justo el síntoma de "se queda cargando y hay que forzar el
 * cierre". Pasado este tope se entra en modo offline con la sesión cacheada; si la
 * respuesta llega tarde y resulta ser un 401, el interceptor cierra sesión igual.
 */
const TIMEOUT_VALIDACION_MS = 6000;

function conTiempoLimite<T>(promesa: Promise<T>, ms: number): Promise<T> {
  let temporizador: ReturnType<typeof setTimeout>;
  return Promise.race([
    promesa,
    new Promise<T>((_, reject) => {
      temporizador = setTimeout(
        () => reject(new Error('Tiempo de espera agotado validando la sesión')),
        ms,
      );
    }),
  ]).finally(() => clearTimeout(temporizador));
}

function filterRoutesByRoles(routes: RouteRecordRaw[], roles: string[]): RouteRecordRaw[] {
  return routes.reduce((acc: RouteRecordRaw[], route) => {
    const auth = route.meta?.authority;
    const hasAccess = !auth
      || (Array.isArray(auth) ? auth : [auth]).some(r => roles.includes(r as string));
    if (!hasAccess) return acc;
    const filteredChildren = route.children
      ? filterRoutesByRoles(route.children, roles)
      : undefined;
    if (filteredChildren && filteredChildren.length === 0 && route.children) return acc;
    acc.push({ ...route, children: filteredChildren } as RouteRecordRaw);
    return acc;
  }, []);
}

/**
 * 通用守卫配置
 * @param router
 */
function setupCommonGuard(router: Router) {
  const loadedPaths = new Set<string>();

  router.beforeEach((to) => {
    to.meta.loaded = loadedPaths.has(to.path);
    if (!to.meta.loaded && preferences.transition.progress) {
      startProgress();
    }
    return true;
  });

  router.afterEach((to) => {
    loadedPaths.add(to.path);
    if (preferences.transition.progress) {
      stopProgress();
    }
  });
}

/**
 * 权限访问守卫配置
 * @param router
 */
function setupAccessGuard(router: Router) {
  router.beforeEach(async (to, from) => {
    const accessStore = useAccessStore();
    const userStore = useUserStore();
    const authStore = useAuthStore();

    if (coreRouteNames.includes(to.name as string)) {
      if (to.path === LOGIN_PATH && accessStore.accessToken) {
        return decodeURIComponent(
          (to.query?.redirect as string) ||
            userStore.userInfo?.homePath ||
            preferences.app.defaultHomePath,
        );
      }
      return true;
    }

    if (!accessStore.accessToken) {
      if (to.meta.ignoreAccess) return true;
      if (to.fullPath !== LOGIN_PATH) {
        return {
          path: LOGIN_PATH,
          query: to.fullPath === preferences.app.defaultHomePath
            ? {} : { redirect: encodeURIComponent(to.fullPath) },
          replace: true,
        };
      }
      return to;
    }

    if (accessStore.isAccessChecked) {
      return true;
    }

    // ── Validar la sesión ANTES de renderizar (API, con caída a cache offline) ──
    //
    // Este bloque corre UNA vez por arranque de la app: isAccessChecked no se
    // persiste, así que siempre entra en false. Antes se consultaba al servidor
    // sólo si no había userInfo cacheado — y como userInfo SÍ se persiste, un
    // token ya vencido nunca se detectaba: la app se pintaba entera desde caché y
    // recién moría en la primera petición real.
    //
    // Ahora se distinguen los dos motivos de fallo, que exigen respuestas opuestas:
    //   401  → el token está muerto. El interceptor ya cerró sesión; al login.
    //   red  → el servidor no responde. La sesión puede seguir siendo válida, así
    //          que se entra con la caché: la app tiene que funcionar sin cobertura.
    // Sólo en el arranque real de la app (primera navegación del router). Viniendo
    // del login no hace falta: authLogin() acaba de traer el userInfo fresco, y
    // repetirlo aquí añadiría una petición extra a cada inicio de sesión.
    const esArranqueDeLaApp = from === START_LOCATION;

    // Tipado desde el store: es más laxo que UserInfo (roles opcional) y admite
    // tanto la respuesta de la API como el valor cacheado.
    let userInfo: typeof userStore.userInfo = userStore.userInfo;

    if (esArranqueDeLaApp || !userInfo) {
      try {
        userInfo = await conTiempoLimite(
          authStore.fetchUserInfo(),
          TIMEOUT_VALIDACION_MS,
        );
      } catch (error: any) {
        if (error?.response?.status === 401) {
          return { path: LOGIN_PATH, replace: true };
        }
        userInfo = userStore.userInfo;
        console.warn(
          '[arranque] No se pudo validar la sesión contra el servidor — se entra con la caché local.',
        );
      }
    }

    // Sin respuesta del servidor y sin caché no hay forma de saber quién es.
    if (!userInfo) {
      return { path: LOGIN_PATH, replace: true };
    }

    const userRoles = (userInfo.roles ?? []).map((r: any) =>
      typeof r === 'string' ? r : (r.value ?? r.roleName ?? ''),
    );
    const accessCodes = accessStore.accessCodes ?? [];
    const effectiveRoles = [...new Set([...userRoles, ...accessCodes])];

    // ── Generar rutas y menús (API o fallback offline) ──
    try {
      const { accessibleMenus, accessibleRoutes } = await generateAccess({
        roles: effectiveRoles,
        router,
        routes: accessRoutes,
      });
      accessStore.setAccessMenus(accessibleMenus);
      accessStore.setAccessRoutes(accessibleRoutes);
    } catch {
      const filtradas = filterRoutesByRoles(accessRoutes, effectiveRoles);
      filtradas.forEach(r => router.addRoute(r));
      accessStore.setAccessRoutes(filtradas);
    }

    accessStore.setIsAccessChecked(true);

    const esFuncionario = userRoles.some(r => APP_FUNCIONARIO_ROLES.has(r));
    const homeDestino = esFuncionario
      ? '/app/home'
      : (userInfo.homePath || preferences.app.defaultHomePath);

    const explicitRedirect = from.query.redirect
      ? decodeURIComponent(from.query.redirect as string)
      : null;

    // Un ?redirect= sólo se honra si lleva a un sitio coherente con el usuario.
    // El logout estampa ahí la ruta donde se cayó la sesión; si esa ruta era del
    // escritorio, al reloguear el funcionario terminaba en la vista web de admin
    // en lugar de su portal móvil, sin GPS ni turnos. Para un funcionario sólo
    // valen rutas dentro de /app.
    const redirectUtil =
      explicitRedirect &&
      explicitRedirect !== '/' &&
      explicitRedirect !== preferences.app.defaultHomePath &&
      (!esFuncionario || explicitRedirect.startsWith('/app'));

    const redirectPath = redirectUtil ? explicitRedirect : homeDestino;

    return { path: redirectPath, replace: true };
  });
}

/**
 * 项目守卫配置
 * @param router
 */
function createRouterGuard(router: Router) {
  setupCommonGuard(router);
  setupAccessGuard(router);
}

export { createRouterGuard };
