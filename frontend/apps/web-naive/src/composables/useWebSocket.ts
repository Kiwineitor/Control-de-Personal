import type { NotificacionWs } from '#/api/sisu/app';
import type { UbicacionResponse } from '#/api/sisu/app';
import type { AlertaMonitoreoDto } from '#/api/sisu/monitoreo';
import type { NotificacionHistorial } from '#/api/sisu/notificaciones';

import { computed, ref } from 'vue';

import { useAccessStore } from '@vben/stores';

import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

import { getCountNoLeidas, getMisNotificaciones } from '#/api/sisu/notificaciones';

// ── Estado singleton — compartido entre todos los componentes ────────────────
const notificaciones       = ref<NotificacionWs[]>([]);
const conectado            = ref(false);
const gpsStatus            = ref<null | UbicacionResponse>(null);
const noLeidasBadge        = ref(0);   // count desde BD (se actualiza al conectar)
/** Se incrementa cada vez que llega un evento de permiso al canal admin. */
const permisosRefreshSignal = ref(0);
/** Se incrementa cuando el backend asigna un horario al interno (HORARIO_ASIGNADO). */
const turnosRefreshSignal   = ref(0);
/** Alertas de monitoreo GPS en tiempo real (solo admins/supervisores). */
const alertasMonitoreo      = ref<AlertaMonitoreoDto[]>([]);
/** Se incrementa cada vez que alguien se conecta o desconecta del WebSocket. */
const presenciaSignal       = ref(0);
/** Última notificación llegada por WS en tiempo real (no histórico de BD). Usar para toasts. */
const notifRtSignal         = ref<NotificacionWs | null>(null);
let   client: Client | null = null;
let   nextId = 1;
let   dbCargada = false;

const TIPOS_PERMISOS_REFRESH = new Set([
  'NUEVA_SOLICITUD',
  'PERMISO_ENVIADO',
  'SOLICITUD_MODIFICADA',
]);

/** Notificaciones que invalidan el calendario de turnos mostrado. */
const TIPOS_TURNOS_REFRESH = new Set([
  'HORARIO_ASIGNADO',
  'JORNADA_ASIGNADA',
  'JORNADA_ACTUALIZADA',
  'FIN_TURNO',
]);

/** Convierte un registro de BD al tipo reactivo de WS. */
function historialToWs(n: NotificacionHistorial): NotificacionWs {
  return {
    id:                 n.idNotificacion,
    idNotificacion:     n.idNotificacion,
    tipo:               n.tipo,
    titulo:             n.titulo,
    mensaje:            n.mensaje,
    timestamp:          n.timestamp,
    leida:              n.leida,
    idJornada:          n.idJornada   ?? undefined,
    idSolicitud:        n.idSolicitud ?? undefined,
    idActividadJornada: n.idActividadJornada ?? undefined,
    esGlobal:           n.esGlobal,
  };
}

export function useWebSocket() {
  const accessStore = useAccessStore();

  const noLeidas = computed(
    () => notificaciones.value.filter(n => !n.leida).length,
  );

  // ── Carga inicial desde BD ────────────────────────────────────────────────
  async function cargarDesdeDb() {
    if (dbCargada) return;
    try {
      const paged = await getMisNotificaciones(0, 50);
      if (paged?.content) {
        // Fusionar: poner las de BD, manteniendo las WS en tiempo real al frente
        const idsExistentes = new Set(notificaciones.value.map(n => n.idNotificacion));
        const nuevas = paged.content
          .filter(n => !idsExistentes.has(n.idNotificacion))
          .map(historialToWs);
        // Las WS en tiempo real van al frente; el historial de BD va detrás
        notificaciones.value = [...notificaciones.value, ...nuevas];
      }
      noLeidasBadge.value = await getCountNoLeidas();
      dbCargada = true;
    } catch (error) {
      console.warn('useWebSocket: no se pudo cargar historial de notificaciones', error);
    }
  }

  // ── Conexión STOMP ────────────────────────────────────────────────────────
  function connect(roles: string[]) {
    if (client?.active) return;

    const token = accessStore.accessToken;

    client = new Client({
      webSocketFactory: () => {
        const wsBase = import.meta.env.VITE_GLOB_API_URL ?? window.location.origin;
        return new SockJS(`${wsBase}/ws`);
      },

      connectHeaders: {
        Authorization: token ? `Bearer ${token}` : '',
      },

      reconnectDelay: 5000,

      onConnect: () => {
        conectado.value = true;

        // GPS
        client!.subscribe('/user/queue/gps-status', (msg) => {
          gpsStatus.value = JSON.parse(msg.body) as UbicacionResponse;
        });

        // Notificaciones personales
        client!.subscribe('/user/queue/notificaciones', (msg) => {
          const payload = JSON.parse(msg.body);
          const notif: NotificacionWs = {
            ...payload,
            id:     payload.idNotificacion ?? nextId++,
            leida:  false,
          };
          notificaciones.value.unshift(notif);
          if (!notif.esGlobal) noLeidasBadge.value++;
          notifRtSignal.value = notif;
          if (TIPOS_PERMISOS_REFRESH.has(notif.tipo)) {
            permisosRefreshSignal.value++;
          }
          // Cualquier cambio que altere el calendario de turnos debe refrescar la
          // pantalla de Turnos. Antes solo se contemplaba HORARIO_ASIGNADO, así que
          // editar los horarios de una jornada ya asignada no refrescaba nada.
          if (TIPOS_TURNOS_REFRESH.has(notif.tipo)) {
            turnosRefreshSignal.value++;
          }
        });

        // Broadcast global (admin → todos los usuarios)
        client!.subscribe('/topic/notificaciones/broadcast', (msg) => {
          const payload = JSON.parse(msg.body);
          const notif: NotificacionWs = {
            ...payload,
            id:     payload.idNotificacion ?? nextId++,
            leida:  false,
            esGlobal: true,
          };
          notificaciones.value.unshift(notif);
          notifRtSignal.value = notif;
        });

        // Admin: nuevas solicitudes de permiso
        // roles puede llegar como string[] o como {value,roleName}[] según el store
        const ADMIN_VALS = new Set(['ADMIN', 'ROLE_ADMIN', 'ROLE_SUPERVISOR', 'SUPERVISOR']);
        const esAdmin = roles.some((r: any) => {
          const v = typeof r === 'string' ? r : (r.value ?? r.roleName ?? '');
          return ADMIN_VALS.has(v);
        });
        if (esAdmin) {
          // Alertas de monitoreo GPS continuo
          client!.subscribe('/topic/admin/alertas-monitoreo', (msg) => {
            const alerta = JSON.parse(msg.body) as AlertaMonitoreoDto;
            // Actualizar o insertar en la lista de alertas
            const idx = alertasMonitoreo.value.findIndex(a => a.idEvento === alerta.idEvento);
            if (idx === -1) {
              alertasMonitoreo.value.unshift(alerta);
            } else {
              alertasMonitoreo.value[idx] = alerta;
            }
          });

          client!.subscribe('/topic/admin/permisos', (msg) => {
            const notif: NotificacionWs = {
              ...JSON.parse(msg.body),
              id:    nextId++,
              leida: false,
            };
            notificaciones.value.unshift(notif);
            noLeidasBadge.value++;
            if (TIPOS_PERMISOS_REFRESH.has(notif.tipo)) {
              permisosRefreshSignal.value++;
            }
          });
          client!.subscribe('/topic/admin/ausencias', (msg) => {
            const notif: NotificacionWs = {
              ...JSON.parse(msg.body),
              id:    nextId++,
              leida: false,
            };
            notificaciones.value.unshift(notif);
            noLeidasBadge.value++;
          });

          // Presencia: alguien se conecta o desconecta del WebSocket
          client!.subscribe('/topic/admin/presencia', () => {
            presenciaSignal.value++;
          });
        }

        // Cargar historial de BD al conectar
        cargarDesdeDb();
      },

      onDisconnect: () => {
        conectado.value = false;
        dbCargada = false;
      },
      onStompError: (frame) => console.warn('STOMP error:', frame),
    });

    client.activate();
  }

  function disconnect() {
    client?.deactivate();
    client = null;
    conectado.value = false;
    dbCargada = false;
    notificaciones.value = [];
    noLeidasBadge.value = 0;
    notifRtSignal.value = null;
    // Limpiar también datos por-usuario para que el siguiente login en el mismo
    // dispositivo no herede el estado GPS ni las alertas del usuario anterior.
    gpsStatus.value = null;
    alertasMonitoreo.value = [];
  }

  function marcarLeida(index: number) {
    if (notificaciones.value[index])
      notificaciones.value[index]!.leida = true;
  }

  function marcarLeidaPorId(id: number) {
    const item = notificaciones.value.find(n => n.id === id || n.idNotificacion === id);
    if (item) {
      item.leida = true;
      if (noLeidasBadge.value > 0) noLeidasBadge.value--;
    }
  }

  function eliminarPorId(id: number) {
    notificaciones.value = notificaciones.value.filter(
      n => n.id !== id && n.idNotificacion !== id,
    );
  }

  function marcarTodasLeidas() {
    notificaciones.value.forEach(n => (n.leida = true));
    noLeidasBadge.value = 0;
  }

  function limpiar() {
    notificaciones.value = [];
    noLeidasBadge.value = 0;
    dbCargada = false;
    notifRtSignal.value = null;
  }

  function enviarGps(
    lat: number,
    lon: number,
    precision?: number,
    senales?: Record<string, any>,
  ) {
    // [GPS-DEBUG] temporal
    if (!client?.active) {
      console.warn('[GPS-DEBUG] enviarGps OMITIDO: STOMP no activo (client?.active =', client?.active, ')');
      return;
    }
    console.info('[GPS-DEBUG] enviarGps → publicando /app/gps/location', { lat, lon, precision });
    client.publish({
      destination: '/app/gps/location',
      // Enviar la precisión (accuracy) permite al backend filtrar fixes imprecisos
      // igual que en nativo. Sin ella, una ubicación por red (km de error) se tomaba
      // como exacta y marcaba "en zona" estando lejos. Las señales de contexto
      // (batería/conectividad) son opcionales (Fase 2).
      body: JSON.stringify({
        latitud: lat,
        longitud: lon,
        ...(precision == null ? {} : { precision }),
        ...(senales ?? {}),
      }),
    });
  }

  function limpiarAlertasMonitoreo() {
    alertasMonitoreo.value = [];
  }

  return {
    notificaciones,
    noLeidas,
    noLeidasBadge,
    conectado,
    gpsStatus,
    permisosRefreshSignal,
    turnosRefreshSignal,
    alertasMonitoreo,
    presenciaSignal,
    notifRtSignal,
    enviarGps,
    connect,
    disconnect,
    marcarLeida,
    marcarLeidaPorId,
    eliminarPorId,
    marcarTodasLeidas,
    limpiar,
    limpiarAlertasMonitoreo,
    cargarDesdeDb,
  };
}