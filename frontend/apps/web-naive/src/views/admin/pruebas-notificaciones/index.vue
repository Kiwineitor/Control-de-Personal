<script lang="ts" setup>
import type { SelectOption } from 'naive-ui';

import type { UsuarioResponse } from '#/api/admin/configuracion';
import type {
  TelegramEstado,
  TelegramPruebaRequest,
  TelegramPruebaResultado,
} from '#/api/admin/telegram';

import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import {
  NAlert,
  NButton,
  NCard,
  NCheckbox,
  NFormItem,
  NInput,
  NSelect,
  NTabPane,
  NTabs,
  NTag,
} from 'naive-ui';

import { getUsuariosPaginadoApi } from '#/api/admin/configuracion';
import {
  enviarPruebaTelegram,
  getTelegramEstado,
  guardarTelegramConfiguracion,
} from '#/api/admin/telegram';

import { catalogoNotificaciones } from './catalogo';
import TelegramPlantillas from './plantillas.vue';

const estado = ref<null | TelegramEstado>(null);
const resultado = ref<null | TelegramPruebaResultado>(null);
const cargando = ref(false);
const enviando = ref(false);
const guardando = ref(false);
const chatIdEdicion = ref('');
const guardado = ref(false);
const errorGuardado = ref(false);
const cambioDestino = computed(
  () => chatIdEdicion.value.trim() !== (estado.value?.chatId ?? ''),
);
const idValido = computed(() =>
  /^-[1-9]\d{0,18}$/.test(chatIdEdicion.value.trim()),
);
const puedeGuardar = computed(
  () =>
    estado.value &&
    cambioDestino.value &&
    idValido.value &&
    !guardando.value &&
    !cargando.value &&
    !enviando.value,
);
const buscando = ref(false);
const errorEstado = ref(false);
const errorEnvio = ref(false);
const errorUsuarios = ref(false);
const opcionesUsuarios = ref<SelectOption[]>([]);
const usuarioSeleccionado = ref<SelectOption>();
const form = reactive<TelegramPruebaRequest>({
  tipo: 'PERSONALIZADO',
  idUsuario: null,
  mensaje: '',
  enviarEnSicope: false,
});
const tipos = [
  { label: 'Mensaje personalizado', value: 'PERSONALIZADO' },
  { label: 'Simular señal perdida (4 minutos)', value: 'SENAL_PERDIDA' },
];
const puedeEnviar = computed(
  () =>
    estado.value?.habilitado &&
    !cargando.value &&
    !enviando.value &&
    !guardando.value &&
    !cambioDestino.value &&
    (form.tipo !== 'PERSONALIZADO' || form.mensaje.trim().length > 0) &&
    (!form.enviarEnSicope || form.idUsuario !== null),
);
const opciones = computed(() => {
  const selected = usuarioSeleccionado.value;
  return selected &&
    !opcionesUsuarios.value.some((u) => u.value === selected.value)
    ? [selected, ...opcionesUsuarios.value]
    : opcionesUsuarios.value;
});

async function cargarEstado() {
  cargando.value = true;
  errorEstado.value = false;
  estado.value = null;
  try {
    estado.value = await getTelegramEstado();
    chatIdEdicion.value = estado.value.chatId ?? '';
  } catch {
    errorEstado.value = true;
  } finally {
    cargando.value = false;
  }
}

watch(chatIdEdicion, () => {
  guardado.value = false;
  errorGuardado.value = false;
});

async function guardarDestino() {
  if (!puedeGuardar.value) return;
  guardando.value = true;
  guardado.value = false;
  errorGuardado.value = false;
  try {
    estado.value = await guardarTelegramConfiguracion(
      chatIdEdicion.value.trim(),
    );
    guardado.value = true;
    resultado.value = null;
  } catch {
    errorGuardado.value = true;
  } finally {
    guardando.value = false;
  }
}

let busquedaId = 0;
let timer: ReturnType<typeof setTimeout> | undefined;
async function buscarUsuarios(termino = '') {
  const id = ++busquedaId;
  buscando.value = true;
  errorUsuarios.value = false;
  try {
    const pagina = (await getUsuariosPaginadoApi({
      termino,
      activo: true,
      page: 0,
      size: 30,
    })) as { content: UsuarioResponse[] };
    if (id !== busquedaId) return;
    opcionesUsuarios.value = pagina.content.map((u) => ({
      label: `${u.nombre} ${u.apellido} (${u.username})`,
      value: u.idUsuario,
    }));
  } catch {
    if (id === busquedaId) errorUsuarios.value = true;
  } finally {
    if (id === busquedaId) buscando.value = false;
  }
}
function buscar(termino: string) {
  clearTimeout(timer);
  ++busquedaId;
  timer = setTimeout(() => void buscarUsuarios(termino), 300);
}
function seleccionarUsuario(value: null | number) {
  form.idUsuario = value;
  usuarioSeleccionado.value = opciones.value.find((u) => u.value === value);
  if (value === null) form.enviarEnSicope = false;
}
async function enviar() {
  if (!puedeEnviar.value) return;
  enviando.value = true;
  resultado.value = null;
  errorEnvio.value = false;
  try {
    resultado.value = await enviarPruebaTelegram({ ...form });
  } catch {
    errorEnvio.value = true;
  } finally {
    enviando.value = false;
  }
}
onMounted(() => {
  void cargarEstado();
  void buscarUsuarios();
});
onUnmounted(() => {
  clearTimeout(timer);
  ++busquedaId;
});
</script>

<template>
  <Page
    title="Notificaciones y Telegram"
    description="Comprueba el grupo de Telegram y ensaya un aviso sin crear incidencias reales."
  >
    <NTabs type="line" default-value="configuracion">
      <NTabPane name="configuracion" tab="Configuración y pruebas">
        <div class="grid gap-4 lg:grid-cols-2">
          <div class="flex flex-col gap-4">
            <NCard title="Destino de Telegram">
              <div class="flex flex-wrap items-center gap-3">
                <NTag
                  v-if="estado"
                  :type="estado.habilitado ? 'success' : 'warning'"
                >
                  {{ estado.habilitado ? 'Configurado' : 'Deshabilitado' }}
                </NTag>
                <span v-if="estado">ID del grupo:
                  <strong>{{ estado.chatId || 'Sin configurar' }}</strong></span>
                <NButton
                  :loading="cargando"
                  :disabled="enviando || guardando"
                  @click="cargarEstado"
                >
                  Actualizar estado
                </NButton>
              </div>
              <p v-if="estado" class="mt-3 text-sm text-gray-500">
                {{
                  estado.origen === 'SISTEMA'
                    ? 'Destino guardado en SICOPE.'
                    : 'Destino tomado del servidor.'
                }}
              </p>
              <NFormItem
                label="ID del grupo de Telegram"
                class="mt-4"
                :validation-status="
                  cambioDestino && !idValido ? 'error' : undefined
                "
                :feedback="
                  cambioDestino && !idValido
                    ? 'Introduce un ID numérico con signo negativo, por ejemplo -1001234567890.'
                    : undefined
                "
              >
                <NInput
                  v-model:value="chatIdEdicion"
                  :maxlength="20"
                  :disabled="cargando || guardando || enviando || !estado"
                  placeholder="Ejemplo: -1001234567890"
                />
              </NFormItem>
              <NButton
                type="primary"
                :loading="guardando"
                :disabled="!puedeGuardar"
                @click="guardarDestino"
              >
                Guardar grupo
              </NButton>
              <p class="mt-3 text-sm text-gray-500">
                El cambio se aplica a todas las próximas alertas y pruebas de
                Telegram y se conserva al reiniciar. El ID guardado aquí tiene
                prioridad sobre el del servidor. El bot debe pertenecer al nuevo
                grupo.
              </p>
              <NAlert v-if="guardado" class="mt-3" type="success">
                Grupo guardado. Puedes enviar una prueba para comprobar la
                recepción.
              </NAlert>
              <NAlert v-if="errorGuardado" class="mt-3" type="warning">
                No se pudo confirmar el guardado. Actualiza el estado para
                comprobar el grupo activo antes de enviar.
              </NAlert>
              <NAlert v-if="cambioDestino" class="mt-3" type="info">
                Guarda el cambio de grupo antes de enviar una prueba.
              </NAlert>
              <NAlert v-if="errorEstado" class="mt-3" type="error">
                No se pudo consultar la configuración. Vuelve a actualizar el
                estado.
              </NAlert>
              <NAlert
                v-if="estado && !estado.habilitado"
                class="mt-3"
                type="warning"
              >
                <span v-if="!estado.botConfigurado">Falta configurar el token del bot en el servidor y
                  reiniciarlo. Puedes guardar el grupo desde aquí.</span>
                <span v-else-if="!estado.activadoEnServidor">El canal está desactivado en el servidor. Activa
                  SISU_TELEGRAM_ENABLED y reinicia el servicio.</span>
                <span v-else>Guarda el ID del grupo para habilitar los envíos.</span>
              </NAlert>
              <p class="mt-3 text-sm text-gray-500">
                Los mensajes se envían al grupo configurado, no al Telegram
                privado del usuario. La prueba confirma si Telegram acepta el
                envío; no confirma su lectura.
              </p>
            </NCard>

            <NCard title="Enviar una prueba">
              <NFormItem label="Tipo de prueba">
                <NSelect
                  v-model:value="form.tipo"
                  :options="tipos"
                  :disabled="enviando"
                />
              </NFormItem>
              <NFormItem label="Usuario de referencia (opcional)">
                <NSelect
                  :value="form.idUsuario"
                  :options="opciones"
                  :loading="buscando"
                  :disabled="enviando"
                  filterable
                  remote
                  clearable
                  placeholder="Buscar por nombre o usuario"
                  @search="buscar"
                  @update:value="seleccionarUsuario"
                />
              </NFormItem>
              <p v-if="errorUsuarios" class="mb-3 text-red-600">
                No se pudieron cargar los usuarios. Escribe en el buscador para
                reintentar.
              </p>
              <p class="mb-3 text-sm text-gray-500">
                Se muestran hasta 30 coincidencias; escribe para buscar más
                usuarios.
              </p>
              <NFormItem
                v-if="form.tipo === 'PERSONALIZADO'"
                label="Mensaje personalizado"
              >
                <NInput
                  v-model:value="form.mensaje"
                  type="textarea"
                  :maxlength="2000"
                  show-count
                  :disabled="enviando"
                  :autosize="{ minRows: 4, maxRows: 8 }"
                  placeholder="Escribe el mensaje que quieres comprobar en el grupo"
                />
              </NFormItem>
              <NAlert
                v-else
                type="info"
                class="mb-4"
                title="Ejemplo de señal perdida"
              >
                Se utilizará la misma plantilla de la alerta real, con 4 minutos
                sin ubicación, un área de prueba y la última posición dentro del
                área. El mensaje indicará que es una simulación.
              </NAlert>
              <NCheckbox
                v-model:checked="form.enviarEnSicope"
                :disabled="enviando || form.idUsuario === null"
              >
                Enviar también una notificación de prueba al usuario en SICOPE
              </NCheckbox>
              <p class="my-3 text-sm text-gray-500">
                Todos los envíos se identifican como PRUEBA. No se modifican
                turnos, asistencia ni incidencias. La opción adicional guarda
                una notificación personal e intenta enviarla por push.
              </p>
              <NButton
                type="primary"
                :loading="enviando"
                :disabled="!puedeEnviar"
                @click="enviar"
              >
                Enviar prueba al grupo
              </NButton>
            </NCard>

            <NAlert
              v-if="errorEnvio"
              type="warning"
              title="No se obtuvo el resultado"
            >
              Revisa el grupo y, si seleccionaste el envío adicional, las
              notificaciones del usuario antes de repetir. La conexión pudo
              interrumpirse después del envío.
            </NAlert>
            <NCard v-if="resultado" title="Resultado del último envío">
              <NAlert
                :type="
                  resultado.telegram.estado === 'CONFIRMADO'
                    ? 'success'
                    : 'warning'
                "
                :title="
                  resultado.telegram.estado === 'CONFIRMADO'
                    ? 'Aceptado por Telegram'
                    : resultado.telegram.estado
                "
              >
                {{ resultado.telegram.detalle }}
              </NAlert>
              <p class="mt-3">
                Grupo:
                {{ resultado.telegram.chatTitulo || 'Sin título disponible' }} ·
                ID:
                {{ resultado.telegram.chatId || 'Sin configurar' }}
              </p>
              <p v-if="resultado.telegram.messageId != null">
                ID del mensaje en Telegram: {{ resultado.telegram.messageId }}
              </p>
              <p v-if="resultado.sicope === 'REGISTRADO'" class="mt-2">
                SICOPE: notificación personal registrada (ID
                {{ resultado.idNotificacion }}). La recepción del push depende
                del dispositivo y su suscripción.
              </p>
              <NAlert
                v-if="resultado.sicope === 'NO_CONFIRMADO'"
                class="mt-3"
                type="warning"
              >
                No se confirmó el registro en SICOPE. Comprueba las
                notificaciones del usuario antes de repetir; el resultado de
                Telegram se muestra por separado.
              </NAlert>
              <p v-if="resultado.sicope === 'NO_SOLICITADO'" class="mt-2">
                No se solicitó envío al usuario en SICOPE.
              </p>
              <pre
                class="mt-3 whitespace-pre-wrap break-words rounded bg-gray-100 p-3 text-sm dark:bg-gray-800"
                >{{ resultado.texto }}</pre>
            </NCard>
          </div>

          <NCard title="Notificaciones existentes">
            <p class="mb-4 text-sm text-gray-500">
              Canales disponibles; el envío automático a Telegram depende del
              modo guardado en “Mensajes y envíos”. Esta versión permite probar
              un mensaje personalizado o simular señal perdida.
            </p>
            <ul class="flex flex-col gap-4">
              <li
                v-for="item in catalogoNotificaciones"
                :key="item.nombre"
                class="border-b border-gray-200 pb-3 dark:border-gray-700"
              >
                <div class="flex flex-wrap items-center gap-2">
                  <strong>{{ item.nombre }}</strong>
                  <NTag size="small" :type="item.telegram ? 'info' : 'default'">
                    {{ item.telegram ? 'Telegram' : 'Sin Telegram automático' }}
                  </NTag>
                </div>
                <p class="mt-1 text-sm text-gray-500">{{ item.detalle }}</p>
              </li>
            </ul>
            <p class="mt-4 text-sm text-gray-500">
              La salida provisional del área se muestra en monitoreo, sin aviso
              a Telegram. “Salida temprana” tiene una plantilla prevista, pero
              no se encontró un detector que la genere automáticamente.
            </p>
          </NCard>
        </div>
      </NTabPane>
      <NTabPane name="mensajes" tab="Mensajes y envíos">
        <TelegramPlantillas
          :chat-id="estado?.chatId ?? null"
          :habilitado="!!estado?.habilitado && !cambioDestino && !guardando"
        />
      </NTabPane>
    </NTabs>
  </Page>
</template>
