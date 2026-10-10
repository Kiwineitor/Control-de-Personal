<script setup lang="ts">
import type { SelectOption } from 'naive-ui';

import { computed, ref } from 'vue';

import {
  NAlert, NButton, NCard, NDivider, NEmpty, NForm, NFormItem,
  NGrid, NGridItem, NInput, NRadioButton, NRadioGroup, NSelect,
  NSpin, NTag,
} from 'naive-ui';

import { message } from '#/adapter/naive';
import {
  getUsuariosPaginadoApi,
  type UsuarioResponse,
} from '#/api/admin/configuracion';
import {
  enviarNotificacion,
  type EnviarNotificacionRequest,
  getMisNotificaciones,
  type NotificacionHistorial,
} from '#/api/sisu/notificaciones';

// ── Formulario de envío ───────────────────────────────────────────────────────
const enviando = ref(false);
const enviado  = ref(false);

const TIPO_OPTIONS: SelectOption[] = [
  { label: '📢 Aviso general',       value: 'BROADCAST' },
  { label: '📬 Mensaje personalizado', value: 'CUSTOM'    },
  { label: '📋 Jornada asignada',    value: 'JORNADA_ASIGNADA'   },
  { label: '✅ Actividad asignada',  value: 'ACTIVIDAD_ASIGNADA' },
  { label: '⚠️ Alerta',             value: 'ALERTA'              },
];

const form = ref<EnviarNotificacionRequest>({
  usernameDestinatario: '',
  tipo:     'CUSTOM',
  titulo:   '',
  mensaje:  '',
});

// ── Modo de destinatario: usuario específico vs broadcast a todos ─────────────
const modo = ref<'todos' | 'usuario'>('usuario');
const esBroadcast = computed(() => modo.value === 'todos');

function onModoChange(v: 'todos' | 'usuario') {
  modo.value = v;
  if (v === 'todos') form.value.usernameDestinatario = '';
}

// ── Selector dinámico de usuarios activos ─────────────────────────────────────
const usuarios         = ref<UsuarioResponse[]>([]);
const cargandoUsuarios = ref(false);

function nombreUsuario(u: UsuarioResponse): string {
  return u.nombreCompleto?.trim() || `${u.nombre} ${u.apellido}`.trim();
}

const usuarioOptions = computed<SelectOption[]>(() =>
  usuarios.value.map((u) => ({
    label: `${nombreUsuario(u)} — @${u.username}`,
    value: u.username,
  })),
);

const destinatarioLabel = computed(() => {
  const u = usuarios.value.find((x) => x.username === form.value.usernameDestinatario);
  return u ? `${nombreUsuario(u)} (@${u.username})` : form.value.usernameDestinatario;
});

async function cargarUsuarios() {
  cargandoUsuarios.value = true;
  try {
    const res: any = await getUsuariosPaginadoApi({ page: 0, size: 500, activo: true });
    usuarios.value = res?.content ?? [];
  } catch {
    message.error('No se pudieron cargar los usuarios');
  } finally {
    cargandoUsuarios.value = false;
  }
}
cargarUsuarios();

async function enviar() {
  if (!esBroadcast.value && !form.value.usernameDestinatario?.trim()) {
    message.warning('Selecciona un usuario destinatario'); return;
  }
  if (!form.value.titulo.trim()) { message.warning('El título es obligatorio'); return; }
  if (!form.value.mensaje.trim()) { message.warning('El mensaje es obligatorio'); return; }

  enviando.value = true;
  enviado.value  = false;
  try {
    const payload: EnviarNotificacionRequest = {
      ...form.value,
      usernameDestinatario: esBroadcast.value
        ? undefined
        : form.value.usernameDestinatario?.trim() || undefined,
    };
    const destino = destinatarioLabel.value;
    await enviarNotificacion(payload);
    enviado.value = true;
    message.success(esBroadcast.value
      ? 'Notificación enviada a todos los usuarios'
      : `Notificación enviada a ${destino}`);
    form.value = { usernameDestinatario: '', tipo: 'CUSTOM', titulo: '', mensaje: '' };
    cargarHistorial();
  } catch (error: any) {
    message.error(error?.message ?? 'Error al enviar la notificación');
  } finally {
    enviando.value = false;
  }
}

// ── Historial reciente enviado ────────────────────────────────────────────────
const historial       = ref<NotificacionHistorial[]>([]);
const cargandoHist    = ref(false);

async function cargarHistorial() {
  cargandoHist.value = true;
  try {
    const paged = await getMisNotificaciones(0, 20);
    historial.value = paged?.content ?? [];
  } catch { /* silencioso */ }
  finally { cargandoHist.value = false; }
}
cargarHistorial();

// ── Helpers ───────────────────────────────────────────────────────────────────
const TIPO_TAG: Record<string, string> = {
  JORNADA_ASIGNADA:   'info',
  ACTIVIDAD_ASIGNADA: 'success',
  PERMISO_APROBADO:   'success',
  PERMISO_RECHAZADO:  'error',
  NUEVA_SOLICITUD:    'warning',
  AUSENCIA_DETECTADA: 'error',
  CUSTOM:             'default',
  BROADCAST:          'warning',
  ALERTA:             'error',
};
const TIPO_ICONO: Record<string, string> = {
  JORNADA_ASIGNADA:   '📋',
  ACTIVIDAD_ASIGNADA: '✅',
  PERMISO_APROBADO:   '✅',
  PERMISO_RECHAZADO:  '❌',
  NUEVA_SOLICITUD:    '📩',
  AUSENCIA_DETECTADA: '⚠️',
  CUSTOM:             '📬',
  BROADCAST:          '📢',
  ALERTA:             '⚠️',
};

function fmtTs(ts: string) {
  try { return new Date(ts).toLocaleString('es-PE'); } catch { return ts; }
}
</script>

<template>
  <div class="notif-admin-page">
    <!-- ── Formulario de envío ─────────────────────────────────────────── -->
    <NCard title="📣 Enviar Notificación" class="mb-4">
      <NAlert v-if="esBroadcast" type="warning" :show-icon="true" class="mb-4" style="font-size:.82rem">
        <strong>Broadcast global</strong> — la notificación llegará a <em>todos</em> los
        usuarios. Cambia a "Usuario específico" si solo quieres notificar a una persona.
      </NAlert>
      <NAlert v-else type="info" :show-icon="true" class="mb-4" style="font-size:.82rem">
        Notificación <strong>personal</strong> — solo llegará a
        <strong>{{ destinatarioLabel || 'el usuario que selecciones' }}</strong>.
      </NAlert>

      <NForm label-placement="top" :show-require-mark="false">
        <NFormItem label="Destinatario *">
          <NRadioGroup :value="modo" @update:value="onModoChange">
            <NRadioButton value="usuario">👤 Usuario específico</NRadioButton>
            <NRadioButton value="todos">📢 Todos los usuarios</NRadioButton>
          </NRadioGroup>
        </NFormItem>

        <NGrid :cols="2" :x-gap="16">
          <NGridItem>
            <NFormItem label="Usuario destinatario *">
              <NSelect
                v-model:value="form.usernameDestinatario"
                :options="usuarioOptions"
                :loading="cargandoUsuarios"
                :disabled="esBroadcast"
                filterable
                clearable
                :placeholder="esBroadcast
                  ? 'Se enviará a todos los usuarios'
                  : 'Busca por nombre o username…'"
                style="width:100%"
              />
            </NFormItem>
          </NGridItem>
          <NGridItem>
            <NFormItem label="Tipo de notificación *">
              <NSelect
                v-model:value="form.tipo"
                :options="TIPO_OPTIONS"
                style="width:100%"
              />
            </NFormItem>
          </NGridItem>
        </NGrid>

        <NFormItem label="Título *">
          <NInput
            v-model:value="form.titulo"
            placeholder="Título de la notificación"
            maxlength="200"
            show-count
            clearable
          />
        </NFormItem>

        <NFormItem label="Mensaje *">
          <NInput
            v-model:value="form.mensaje"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 6 }"
            placeholder="Escribe el mensaje completo de la notificación…"
            maxlength="1000"
            show-count
          />
        </NFormItem>
      </NForm>

      <div class="flex justify-end gap-3 mt-2">
        <NButton
          type="primary"
          :loading="enviando"
          :disabled="!form.titulo.trim() || !form.mensaje.trim()
            || (!esBroadcast && !form.usernameDestinatario?.trim())"
          :style="{ background: esBroadcast ? '#b45309' : '#0f766e',
                    borderColor: esBroadcast ? '#b45309' : '#0f766e' }"
          @click="enviar"
        >
          {{ esBroadcast ? '📢 Enviar a todos' : '📬 Enviar al usuario' }}
        </NButton>
      </div>
    </NCard>

    <!-- ── Historial reciente ─────────────────────────────────────────── -->
    <NCard title="📋 Historial reciente">
      <template #header-extra>
        <NButton size="small" secondary :loading="cargandoHist" @click="cargarHistorial">
          Actualizar
        </NButton>
      </template>

      <NSpin :show="cargandoHist">
        <div v-if="historial.length === 0" style="padding:32px 0">
          <NEmpty description="Sin notificaciones recientes" />
        </div>

        <div v-else class="hist-list">
          <NDivider style="margin:8px 0" />
          <div
            v-for="n in historial"
            :key="n.idNotificacion"
            class="hist-item"
            :class="{ 'hist-item--leida': n.leida }"
          >
            <div class="hist-item__head">
              <span class="hist-item__icono">{{ TIPO_ICONO[n.tipo] ?? '🔔' }}</span>
              <div class="hist-item__info">
                <div class="hist-item__titulo">{{ n.titulo }}</div>
                <div class="hist-item__ts">{{ fmtTs(n.timestamp) }}</div>
                <div class="hist-item__dest">
                  <span v-if="n.esGlobal" class="dest-chip dest-chip--global">
                    📢 Broadcast global
                  </span>
                  <span v-else-if="n.remitenteUsername" class="dest-chip">
                    De: {{ n.remitenteUsername }}
                  </span>
                </div>
              </div>
              <div class="hist-item__tags">
                <NTag
                  :type="(TIPO_TAG[n.tipo] ?? 'default') as any"
                  size="small" :bordered="false"
                >
                  {{ n.tipo }}
                </NTag>
                <NTag v-if="n.leida" size="small" :bordered="false">Leída</NTag>
                <NTag v-else type="error" size="small" :bordered="false">No leída</NTag>
              </div>
            </div>
            <p class="hist-item__msg">{{ n.mensaje }}</p>
            <NDivider style="margin:8px 0" />
          </div>
        </div>
      </NSpin>
    </NCard>
  </div>
</template>

<style scoped>
.notif-admin-page { display: flex; flex-direction: column; gap: 0; }

/* Historial */
.hist-list { display: flex; flex-direction: column; }

.hist-item { opacity: 1; transition: opacity .15s; }
.hist-item--leida { opacity: .6; }

.hist-item__head {
  display: flex; align-items: flex-start; gap: 10px; margin-bottom: 5px;
}
.hist-item__icono { font-size: 20px; flex-shrink: 0; }
.hist-item__info  { flex: 1; min-width: 0; }
.hist-item__titulo {
  font-size: 13.5px; font-weight: 600; color: hsl(var(--card-foreground));
}
.hist-item__ts { font-size: 11px; color: hsl(var(--muted-foreground)); margin-top: 2px; }
.hist-item__dest { margin-top: 3px; }
.hist-item__tags { display: flex; flex-direction: column; gap: 3px; align-items: flex-end; }

.hist-item__msg {
  font-size: 12.5px; color: hsl(var(--muted-foreground));
  margin: 0 0 0 30px; line-height: 1.5;
}

.dest-chip {
  font-size: 11px; padding: 1px 7px; border-radius: 10px;
  background: hsl(var(--muted)); color: hsl(var(--muted-foreground));
}
.dest-chip--global {
  background: rgba(245,158,11,.15); color: #b45309;
}
</style>
