<script lang="ts" setup>
import type { NotificationItem } from '@vben/layouts';
import type { NotificacionWs } from '#/api/sisu/app';

import { computed, onMounted, onUnmounted, watch } from 'vue';
import { useRouter } from 'vue-router';

import { AuthenticationLoginExpiredModal } from '@vben/common-ui';
import { useWatermark } from '@vben/hooks';
import {
  BasicLayout,
  LockScreen,
  Notification,
  UserDropdown,
} from '@vben/layouts';
import { preferences, usePreferences } from '@vben/preferences';
import { useAccessStore, useUserStore } from '@vben/stores';
import { useNotification } from 'naive-ui';

import { $t } from '#/locales';
import { loadLocaleMessages } from '@vben/locales';
import { useAuthStore } from '#/store';
import { useWebSocket } from '#/composables/useWebSocket';
import LoginForm from '#/views/_core/authentication/login.vue';

const router = useRouter();
const userStore = useUserStore();
const authStore = useAuthStore();
const accessStore = useAccessStore();
const { destroyWatermark, updateWatermark } = useWatermark();
const { isDark } = usePreferences();

const {
  notificaciones,
  connect,
  disconnect,
  marcarLeidaPorId,
  eliminarPorId,
  marcarTodasLeidas,
  limpiar,
} = useWebSocket();

onMounted(() => {
  // userRoles puede ser string[] o {value,roleName}[] según cómo lo pobló el store
  const rawRoles = userStore.userRoles ?? [];
  const roles = rawRoles.map((r: any) =>
    typeof r === 'string' ? r : (r.value ?? r.roleName ?? ''),
  );
  connect(roles);
  // El panel administrativo siempre usa español
  loadLocaleMessages('es-ES');
});
onUnmounted(disconnect);

// ── Convierte NotificacionWs → NotificationItem para el componente ──

const TIPO_AVATAR: Record<string, string> = {
  JORNADA_ASIGNADA:    'https://avatar.vercel.sh/jornada?text=J',
  ACTIVIDAD_ASIGNADA:  'https://avatar.vercel.sh/actividad?text=A',
  PERMISO_APROBADO:    'https://avatar.vercel.sh/ok?text=✓',
  PERMISO_RECHAZADO:   'https://avatar.vercel.sh/no?text=✗',
  PERMISO_OBSERVADO:   'https://avatar.vercel.sh/obs?text=👁',
  NUEVA_SOLICITUD:     'https://avatar.vercel.sh/sol?text=S',
  AUSENCIA_DETECTADA:  'https://avatar.vercel.sh/aus?text=!',
};

function tipoLink(n: NotificacionWs): string | undefined {
  if (n.idJornada)   return '/sisu/jornadas';
  if (n.idSolicitud) return '/sisu/permisos';
  return undefined;
}

function formatFecha(ts: string): string {
  try {
    const d = new Date(ts);
    const diff = Date.now() - d.getTime();
    const mins = Math.floor(diff / 60_000);
    if (mins < 1)   return 'ahora';
    if (mins < 60)  return `hace ${mins} min`;
    const hrs = Math.floor(mins / 60);
    if (hrs < 24)   return `hace ${hrs} h`;
    return d.toLocaleDateString('es-PE', { day: '2-digit', month: 'short' });
  } catch {
    return ts;
  }
}

const notifications = computed<NotificationItem[]>(() =>
  notificaciones.value.map((n) => ({
    id:      n.id,
    avatar:  TIPO_AVATAR[n.tipo] ?? 'https://avatar.vercel.sh/sisu?text=N',
    date:    formatFecha(n.timestamp),
    isRead:  n.leida,
    message: n.mensaje,
    title:   n.titulo,
    link:    tipoLink(n),
  })),
);

const showDot = computed(() => notifications.value.some((item) => !item.isRead));

// ── Toast al llegar nueva notificación ──────────────────────────────────────
const notification = useNotification();

const TIPO_NOTIF_TYPE: Record<string, 'error' | 'info' | 'success' | 'warning'> = {
  JORNADA_ASIGNADA:   'info',
  ACTIVIDAD_ASIGNADA: 'success',
  PERMISO_APROBADO:   'success',
  PERMISO_RECHAZADO:  'error',
  NUEVA_SOLICITUD:    'warning',
  AUSENCIA_DETECTADA: 'error',
};

const TIPO_ICONO_NOTIF: Record<string, string> = {
  JORNADA_ASIGNADA:   '📋',
  ACTIVIDAD_ASIGNADA: '✅',
  PERMISO_APROBADO:   '✅',
  PERMISO_RECHAZADO:  '❌',
  NUEVA_SOLICITUD:    '📩',
  AUSENCIA_DETECTADA: '⚠️',
};

watch(
  () => notificaciones.value.length,
  (newLen, oldLen) => {
    if (newLen > (oldLen ?? 0)) {
      const latest = notificaciones.value[0];
      if (!latest) return;
      const icono = TIPO_ICONO_NOTIF[latest.tipo] ?? '🔔';
      notification.create({
        title:            `${icono}  ${latest.titulo}`,
        content:          latest.mensaje,
        type:             TIPO_NOTIF_TYPE[latest.tipo] ?? 'info',
        duration:         5000,
        keepAliveOnHover: true,
      });
    }
  },
);

const menus = computed(() => [
  {
    handler: () => {
      router.push({ name: 'Profile' });
    },
    icon: 'lucide:user',
    text: $t('page.auth.profile'),
  },
]);

const avatar = computed(() => {
  return userStore.userInfo?.avatar ?? preferences.app.defaultAvatar;
});

async function handleLogout() {
  await authStore.logout(false);
}

function handleNoticeClear() {
  limpiar();
}

function markRead(id: number | string) {
  marcarLeidaPorId(Number(id));
}

function remove(id: number | string) {
  eliminarPorId(Number(id));
}

function handleMakeAll() {
  marcarTodasLeidas();
}

const viewAll = () => {};

const handleClick = (item: NotificationItem) => {
  // 如果通知项有链接，点击时跳转
  if (item.link) {
    navigateTo(item.link, item.query, item.state);
  }
};

function navigateTo(
  link: string,
  query?: Record<string, any>,
  state?: Record<string, any>,
) {
  if (link.startsWith('http://') || link.startsWith('https://')) {
    // 外部链接，在新标签页打开
    window.open(link, '_blank');
  } else {
    // 内部路由链接，支持 query 参数和 state
    router.push({
      path: link,
      query: query || {},
      state,
    });
  }
}

watch(
  () => ({
    enable: preferences.app.watermark,
    content: preferences.app.watermarkContent,
    isDark: isDark.value,
  }),
  async ({ enable, content, isDark: isDarkValue }) => {
    if (enable) {
      const watermarkColor = isDarkValue
        ? 'rgba(255, 255, 255, 0.12)'
        : 'rgba(0, 0, 0, 0.12)';

      await updateWatermark({
        advancedStyle: {
          colorStops: [
            {
              color: watermarkColor,
              offset: 0,
            },
            {
              color: watermarkColor,
              offset: 1,
            },
          ],
          type: 'linear',
        },
        content:
          content ||
          `${userStore.userInfo?.username} - ${userStore.userInfo?.realName}`,
      });
    } else {
      destroyWatermark();
    }
  },
  {
    immediate: true,
  },
);
</script>

<template>
  <BasicLayout @clear-preferences-and-logout="handleLogout">
    <template #user-dropdown>
      <UserDropdown
        :avatar
        :menus
        :text="userStore.userInfo?.realName"
        :description="userStore.userInfo?.username ?? ''"
        @logout="handleLogout"
      />
    </template>
    <template #notification>
      <Notification
        :dot="showDot"
        :notifications="notifications"
        @clear="handleNoticeClear"
        @read="(item) => item.id && markRead(item.id)"
        @remove="(item) => item.id && remove(item.id)"
        @make-all="handleMakeAll"
        @on-click="handleClick"
        @view-all="viewAll"
      />
    </template>
    <template #extra>
      <AuthenticationLoginExpiredModal
        v-model:open="accessStore.loginExpired"
        :avatar
      >
        <LoginForm />
      </AuthenticationLoginExpiredModal>
    </template>
    <template #lock-screen>
      <LockScreen :avatar @to-login="handleLogout" />
    </template>
  </BasicLayout>
</template>
