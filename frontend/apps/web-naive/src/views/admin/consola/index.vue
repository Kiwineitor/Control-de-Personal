<script lang="ts" setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue';
import { Page } from '@vben/common-ui';
import { NButton, NSelect, NInput, NSwitch, NTag } from 'naive-ui';
import type { SelectOption } from 'naive-ui';
import { dialog, message } from '#/adapter/naive';
import { getLogs, limpiarLogs, type LogEntry } from '#/api/sisu/logs';

const ORIGEN_OPTS: SelectOption[] = [
  { label: 'Todos',    value: '' },
  { label: 'Backend',  value: 'BACKEND' },
  { label: 'Frontend', value: 'FRONTEND' },
];
const NIVEL_OPTS: SelectOption[] = [
  { label: 'Todos',        value: '' },
  { label: 'DEBUG y +',    value: 'DEBUG' },
  { label: 'INFO y +',     value: 'INFO' },
  { label: 'WARN y +',     value: 'WARN' },
  { label: 'Solo ERROR',   value: 'ERROR' },
];

const origin   = ref('');
const level     = ref('INFO');
const q         = ref('');
const auto      = ref(true);
const cargando  = ref(false);

const logs   = ref<LogEntry[]>([]);
let lastId    = 0;
const MAX_VISIBLE = 2000;

const panel = ref<HTMLElement | null>(null);
let timer: any = null;

const total = computed(() => logs.value.length);

function colorNivel(lvl: string): string {
  switch ((lvl || '').toUpperCase()) {
    case 'ERROR': return '#f87171';
    case 'WARN':  return '#fbbf24';
    case 'INFO':  return '#93c5fd';
    case 'DEBUG': return '#94a3b8';
    default:      return '#cbd5e1';
  }
}

async function cargar(reset = false) {
  if (cargando.value) return;
  cargando.value = true;
  try {
    if (reset) { logs.value = []; lastId = 0; }
    const data = await getLogs({
      origin:  origin.value || undefined,
      level:   level.value  || undefined,
      q:       q.value      || undefined,
      sinceId: reset ? undefined : (lastId || undefined),
      limit:   500,
    });
    const nuevos = data ?? [];
    if (nuevos.length > 0) {
      const cercaDelFondo = estaCercaDelFondo();
      logs.value = reset ? nuevos : [...logs.value, ...nuevos];
      if (logs.value.length > MAX_VISIBLE) {
        logs.value = logs.value.slice(logs.value.length - MAX_VISIBLE);
      }
      lastId = Math.max(lastId, ...nuevos.map((l) => l.id));
      if (cercaDelFondo) await scrollAlFondo();
    }
  } catch {
    /* silencioso: el polling reintenta */
  } finally {
    cargando.value = false;
  }
}

function estaCercaDelFondo(): boolean {
  const el = panel.value;
  if (!el) return true;
  return el.scrollHeight - el.scrollTop - el.clientHeight < 120;
}
async function scrollAlFondo() {
  await nextTick();
  const el = panel.value;
  if (el) el.scrollTop = el.scrollHeight;
}

async function limpiar() {
  dialog.warning({
    title: 'Vaciar consola',
    content: 'Esto borra el buffer de logs en el servidor. ¿Continuar?',
    positiveText: 'Vaciar',
    negativeText: 'Cancelar',
    onPositiveClick: async () => {
      try {
        await limpiarLogs();
        logs.value = []; lastId = 0;
        message.success('Buffer de logs vaciado.');
      } catch {
        message.error('No se pudo vaciar.');
      }
    },
  });
}

async function copiar() {
  const texto = logs.value
    .map((l) => `${l.timestamp} [${l.origin}] ${l.level} ${l.logger ?? ''} ${l.usuario ? '(' + l.usuario + ')' : ''} ${l.message}${l.detalle ? '\n' + l.detalle : ''}`)
    .join('\n');
  try {
    await navigator.clipboard.writeText(texto);
    message.success(`Copiados ${logs.value.length} registros.`);
  } catch {
    message.error('No se pudo copiar al portapapeles.');
  }
}

watch([origin, level, q], () => { void cargar(true); });

onMounted(() => {
  void cargar(true);
  timer = setInterval(() => { if (auto.value) void cargar(false); }, 3000);
});
onUnmounted(() => { if (timer) clearInterval(timer); });
</script>

<template>
  <Page auto-content-height>
    <div class="mb-3 flex flex-wrap items-center gap-2">
      <NSelect v-model:value="origin" :options="ORIGEN_OPTS" style="width:130px" size="small" />
      <NSelect v-model:value="level"  :options="NIVEL_OPTS"  style="width:140px" size="small" />
      <NInput
        v-model:value="q"
        placeholder="Buscar en mensaje / logger…"
        clearable size="small" style="width:240px"
      />
      <div class="flex items-center gap-1 text-sm text-gray-500">
        <NSwitch v-model:value="auto" size="small" /> Auto
      </div>
      <NButton size="small" :loading="cargando" @click="cargar(true)">Recargar</NButton>
      <div class="ml-auto flex items-center gap-2">
        <span class="text-xs text-gray-400">{{ total }} líneas</span>
        <NButton size="small" @click="copiar">Copiar</NButton>
        <NButton size="small" type="error" ghost @click="limpiar">Vaciar</NButton>
      </div>
    </div>

    <div ref="panel" class="consola">
      <div v-if="logs.length === 0" class="consola-vacia">
        Sin logs para los filtros actuales. (El frontend se ve cuando la app envía su consola.)
      </div>
      <div v-for="l in logs" :key="l.id" class="linea">
        <span class="ts">{{ l.timestamp }}</span>
        <NTag
          size="tiny" :bordered="false"
          :type="l.origin === 'BACKEND' ? 'info' : 'warning'"
          style="margin:0 6px"
        >{{ l.origin === 'BACKEND' ? 'BE' : 'FE' }}</NTag>
        <span class="nivel" :style="{ color: colorNivel(l.level) }">{{ l.level }}</span>
        <span v-if="l.logger" class="logger">{{ l.logger }}</span>
        <span v-if="l.usuario" class="usuario">@{{ l.usuario }}</span>
        <span class="msg">{{ l.message }}</span>
        <pre v-if="l.detalle" class="detalle">{{ l.detalle }}</pre>
      </div>
    </div>
  </Page>
</template>

<style scoped>
.consola {
  background: #0b1220;
  color: #e2e8f0;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.55;
  border-radius: 10px;
  padding: 10px 12px;
  height: calc(100vh - 200px);
  overflow: auto;
  border: 1px solid #1e293b;
}
.consola-vacia { color: #64748b; padding: 24px 4px; }
.linea {
  padding: 1px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.03);
  white-space: pre-wrap;
  word-break: break-word;
}
.ts      { color: #475569; }
.nivel   { font-weight: 700; margin-right: 6px; }
.logger  { color: #64748b; margin-right: 6px; }
.usuario { color: #a78bfa; margin-right: 6px; }
.msg     { color: #e2e8f0; }
.detalle {
  margin: 2px 0 4px 0;
  color: #fca5a5;
  background: rgba(248, 113, 113, 0.07);
  border-left: 2px solid #ef4444;
  padding: 4px 8px;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 11px;
}
</style>
