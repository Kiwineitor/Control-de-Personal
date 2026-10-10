import type { Recordable, UserInfo } from '@vben/types';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { preferences } from '@vben/preferences';
import { resetAllStores, useAccessStore, useUserStore } from '@vben/stores';

import { defineStore } from 'pinia';

import { notification } from '#/adapter/naive';
import { getAccessCodesApi, getUserInfoApi, loginApi, logoutApi } from '#/api';
import { useGeolocalizacion } from '#/composables/useGeolocalizacion';
import { desuscribirDispositivo } from '#/composables/usePushNotifications';
import { useTurnoHoy } from '#/composables/useTurnoHoy';
import { useWebSocket } from '#/composables/useWebSocket';
import { $t } from '#/locales';

export const useAuthStore = defineStore('auth', () => {
  const accessStore = useAccessStore();
  const userStore = useUserStore();
  const router = useRouter();

  const loginLoading = ref(false);

  /**
   * 异步处理登录操作
   * Asynchronously handle the login process
   * @param params 登录表单数据
   */
  async function authLogin(
    params: Recordable<any>,
    onSuccess?: () => Promise<void> | void,
  ) {
    // 异步处理用户登录操作并获取 accessToken
    let userInfo: null | UserInfo = null;
    try {
      loginLoading.value = true;
      const { accessToken } = await loginApi(params);

      // 如果成功获取到 accessToken
      if (accessToken) {
        // 将 accessToken 存储到 accessStore 中
        accessStore.setAccessToken(accessToken);

        // 获取用户信息并存储到 accessStore 中
        const [fetchUserInfoResult, accessCodes] = await Promise.all([
          fetchUserInfo(),
          getAccessCodesApi(),
        ]);

        userInfo = fetchUserInfoResult;

        userStore.setUserInfo(userInfo);
        accessStore.setAccessCodes(accessCodes);

        if (accessStore.loginExpired) {
          accessStore.setLoginExpired(false);
        } else {
          if (onSuccess) {
            await onSuccess();
          } else {
            const FUNCIONARIO_ROLES = new Set([
              'ROLE_CAJERO', 'ROLE_ENFERMERO', 'ROLE_FARMACIA',
              'ROLE_INTERNO', 'ROLE_LIMPIEZA', 'ROLE_MEDICO',
            ]);
            const roles: string[] = (userInfo.roles ?? []).map((r: any) =>
              typeof r === 'string' ? r : (r.value ?? r.roleName ?? ''),
            );
            const destino = roles.some((r) => FUNCIONARIO_ROLES.has(r))
              ? '/app/home'
              : (userInfo.homePath || preferences.app.defaultHomePath);
            await router.push(destino);
          }
        }

        if (userInfo?.realName) {
          notification.success({
            content: $t('authentication.loginSuccess'),
            description: `${$t('authentication.loginSuccessDesc')}:${userInfo?.realName}`,
            duration: 3000,
          });
        }
      }
    } finally {
      loginLoading.value = false;
    }

    return {
      userInfo,
    };
  }

  async function logout(redirect: boolean = true) {
    // ¿Queda un token utilizable? En el logout por token expirado/invalidado, el
    // interceptor ya lo puso a null antes de llamarnos. En ese caso NO se puede
    // hablar con el backend: cada llamada devolvería 401, y ese 401 vuelve a entrar
    // al interceptor → doReAuthenticate → logout → … El bucle infinito de peticiones
    // que dejaba la app cargando sin fin y había que cerrar a la fuerza.
    const sesionVigente = !!accessStore.accessToken;

    // Dar de baja las suscripciones push del dispositivo (FCM + Web Push): por
    // privacidad, tras el logout el dispositivo NO debe recibir más notificaciones
    // (el token FCM entrega aun con la app cerrada). Con la sesión ya caída se hace
    // solo la parte local; la fila del backend queda huérfana y se reasigna sola en
    // el próximo login de este dispositivo (upsert por endpoint).
    try {
      await desuscribirDispositivo(sesionVigente);
    } catch { /* no bloquear logout */ }

    if (sesionVigente) {
      try {
        await logoutApi();
      } catch {
        // 不做任何处理
      }
    }

    // Limpiar el estado singleton (module-level) de los composables. Sin esto, el
    // siguiente usuario en el MISMO dispositivo hereda turno/GPS/notificaciones del
    // anterior (p.ej. ve "hoy no te toca turno" con datos del usuario previo).
    try { useTurnoHoy().reset(); } catch { /* noop */ }
    try { await useGeolocalizacion().reset(); } catch { /* noop */ }
    try {
      const ws = useWebSocket();
      ws.disconnect();
      ws.limpiar();
    } catch { /* noop */ }

    resetAllStores();
    accessStore.setLoginExpired(false);

    // 回登录页带上当前路由地址
    await router.replace({
      path: LOGIN_PATH,
      query: redirect
        ? {
            redirect: encodeURIComponent(router.currentRoute.value.fullPath),
          }
        : {},
    });
  }

  async function fetchUserInfo() {
    const userInfo = await getUserInfoApi();
    userStore.setUserInfo(userInfo);
    return userInfo;
  }

  function $reset() {
    loginLoading.value = false;
  }

  return {
    $reset,
    authLogin,
    fetchUserInfo,
    loginLoading,
    logout,
  };
});
