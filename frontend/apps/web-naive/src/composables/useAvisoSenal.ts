/**
 * useAvisoSenal — avisa al funcionario, en su propio teléfono, cuando su turno
 * deja de registrarse.
 *
 * Por qué no basta el push del servidor: los tres motivos por los que un turno
 * deja de registrarse (ubicación del sistema apagada, GPS sin fix, o sin
 * internet) son justamente los que impiden que un push llegue. El backend
 * detecta el silencio y avisa al grupo de supervisión, pero al interesado no le
 * llega nada hasta que recupera la conexión — que suele ser cuando ya tiene la
 * ausencia registrada. Este aviso lo genera el propio dispositivo, así que
 * aparece aunque no haya red y aunque la app esté minimizada.
 *
 * Reglas de ruido, deliberadas:
 *  - Solo durante el turno. Fuera de horario la ubicación es asunto del usuario.
 *  - Un aviso por corte, no uno por chequeo. Se rearma al recuperar la señal.
 *  - Espera GRACIA_MS antes de avisar: un túnel, un ascensor o un cambio de
 *    celda cortan la señal unos segundos y avisar de eso sería spam.
 *  - Al recuperarse se retira el aviso y se confirma que ya se está registrando.
 */
import { watch } from 'vue';

import { Capacitor } from '@capacitor/core';

import { useEstadoRed } from './useEstadoRed';
import { useGeolocalizacion } from './useGeolocalizacion';

/** Motivo del corte. Determina el texto y evita mezclar avisos distintos. */
type MotivoCorte = 'GPS_APAGADO' | 'SIN_INTERNET' | 'SIN_SENAL';

/**
 * Segundos de tolerancia antes de avisar. Alineado con el umbral del backend
 * (`detectarSinSenal` avisa a los ~4 min): el usuario recibe el aviso en su
 * teléfono ANTES de que el corte llegue a contar, no después.
 */
const GRACIA_MS = 90_000;

/** Ids fijos: reusarlos hace que un aviso nuevo reemplace al anterior. */
const ID_AVISO = 9101;
const ID_OK = 9102;

/**
 * Mismo canal e ícono que los push del backend, para que el usuario vea un solo
 * tipo de aviso de SICOPE. El canal ya lo crea app.vue al registrar FCM, pero se
 * recrea aquí porque aquel camino depende del permiso de push: si el usuario lo
 * rechazó, el canal no existiría y este aviso quedaría sin sonido.
 * Android ignora la recreación de un canal existente, así que es idempotente.
 */
const CANAL = 'sisu_alertas';
const ICONO = 'ic_notif';

const TEXTOS: Record<MotivoCorte, { cuerpo: string; titulo: string }> = {
  GPS_APAGADO: {
    titulo: 'Tu ubicación está apagada',
    cuerpo:
      'Tu turno dejó de registrarse. Enciende la ubicación del teléfono para no acumular una ausencia.',
  },
  SIN_INTERNET: {
    titulo: 'Sin conexión a internet',
    cuerpo:
      'Tu ubicación se está guardando en el teléfono y se enviará sola al recuperar la señal. No cierres SICOPE.',
  },
  SIN_SENAL: {
    titulo: 'No se está registrando tu turno',
    cuerpo:
      'Dejamos de recibir tu ubicación. Abre SICOPE y déjala minimizada para reanudar el registro.',
  },
};

let iniciado = false;
let temporizador: null | ReturnType<typeof setTimeout> = null;
let motivoAvisado: MotivoCorte | null = null;
let quitarWatchers: (() => void) | null = null;
let permisoPedido = false;

/** Carga perezosa: si el APK no tiene el plugin sincronizado, se degrada a log. */
async function plugin() {
  if (!Capacitor.isNativePlatform()) return null;
  try {
    const { LocalNotifications } = await import('@capacitor/local-notifications');
    if (!permisoPedido) {
      permisoPedido = true;
      const estado = await LocalNotifications.checkPermissions();
      if (estado.display !== 'granted') await LocalNotifications.requestPermissions();
      try {
        await LocalNotifications.createChannel({
          id: CANAL,
          name: 'Alertas SICOPE',
          description: 'Avisos de monitoreo, turnos y permisos',
          importance: 5,
          sound: 'notificacion_nutria',
          vibration: true,
          visibility: 1,
          lights: true,
        });
      } catch (error) {
        console.warn('[senal] No se pudo crear el canal de avisos:', error);
      }
    }
    return LocalNotifications;
  } catch (error) {
    console.warn('[senal] @capacitor/local-notifications no disponible:', error);
    return null;
  }
}

async function mostrarAviso(motivo: MotivoCorte) {
  const { cuerpo, titulo } = TEXTOS[motivo];
  console.warn(`[senal] Corte detectado (${motivo}): ${titulo}`);
  const notificaciones = await plugin();
  if (!notificaciones) return;
  try {
    await notificaciones.schedule({
      notifications: [
        {
          id: ID_AVISO,
          title: titulo,
          body: cuerpo,
          // Persistente: el aviso debe seguir visible hasta que el usuario actúe.
          ongoing: motivo !== 'SIN_INTERNET',
          autoCancel: false,
          channelId: CANAL,
          smallIcon: ICONO,
        },
      ],
    });
  } catch (error) {
    console.warn('[senal] No se pudo mostrar el aviso local:', error);
  }
}

async function mostrarRecuperacion() {
  console.info('[senal] Señal recuperada: el turno vuelve a registrarse.');
  const notificaciones = await plugin();
  if (!notificaciones) return;
  try {
    await notificaciones.cancel({ notifications: [{ id: ID_AVISO }] });
    await notificaciones.schedule({
      notifications: [
        {
          id: ID_OK,
          title: 'Registro reanudado',
          body: 'Tu ubicación volvió a enviarse. Tu turno se está registrando con normalidad.',
          autoCancel: true,
          channelId: CANAL,
          smallIcon: ICONO,
        },
      ],
    });
  } catch (error) {
    console.warn('[senal] No se pudo limpiar el aviso local:', error);
  }
}

/**
 * Arranca la vigilancia. Idempotente.
 *
 * @param enTurno Función que dice si el usuario está de turno AHORA. Se consulta
 *   en cada evaluación en vez de recibir un booleano fijo, para que el fin del
 *   turno silencie los avisos sin tener que reiniciar nada.
 */
export function iniciarAvisoSenal(enTurno: () => boolean): void {
  if (iniciado) return;
  iniciado = true;

  const { enLinea } = useEstadoRed();
  const { senalPerdida, servicioUbicacionActivo, watchActivo } = useGeolocalizacion();

  /** Motivo actual del corte, o null si todo va bien. Orden = prioridad. */
  const motivoActual = (): MotivoCorte | null => {
    if (!enTurno()) return null;
    // La ubicación apagada es la causa más grave y la más accionable.
    if (!servicioUbicacionActivo.value) return 'GPS_APAGADO';
    if (!enLinea.value) return 'SIN_INTERNET';
    // senalPerdida solo es concluyente si el watcher debía estar enviando.
    if (watchActivo.value && senalPerdida.value) return 'SIN_SENAL';
    return null;
  };

  const evaluar = () => {
    const motivo = motivoActual();

    if (motivo === null) {
      if (temporizador) {
        clearTimeout(temporizador);
        temporizador = null;
      }
      if (motivoAvisado) {
        motivoAvisado = null;
        void mostrarRecuperacion();
      }
      return;
    }

    // Ya se avisó ESTE motivo: no repetir. Si cambió el motivo, sí se reemplaza,
    // porque "sin internet" y "ubicación apagada" piden acciones distintas.
    if (motivoAvisado === motivo) return;

    if (temporizador) clearTimeout(temporizador);
    temporizador = setTimeout(() => {
      temporizador = null;
      const confirmado = motivoActual();
      // Reconfirmar: el corte tiene que seguir ahí pasada la gracia.
      if (confirmado === null || confirmado === motivoAvisado) return;
      motivoAvisado = confirmado;
      void mostrarAviso(confirmado);
    }, GRACIA_MS);
  };

  const parar = [
    watch(enLinea, evaluar),
    watch(senalPerdida, evaluar),
    watch(servicioUbicacionActivo, evaluar),
    watch(watchActivo, evaluar, { immediate: true }),
  ];
  quitarWatchers = () => parar.forEach((detener) => detener());
}

/** Detiene la vigilancia y retira cualquier aviso pendiente (logout, fin de turno). */
export function detenerAvisoSenal(): void {
  if (temporizador) {
    clearTimeout(temporizador);
    temporizador = null;
  }
  quitarWatchers?.();
  quitarWatchers = null;
  motivoAvisado = null;
  iniciado = false;
  void plugin().then((n) => n?.cancel({ notifications: [{ id: ID_AVISO }] }));
}
