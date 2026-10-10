<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  NButton, NDrawer, NDrawerContent, NSpace, NSpin, NEmpty,
  NFormItem, NSelect, NInput, NTag, NAlert, NCollapse, NCollapseItem,
  NTimePicker,
} from 'naive-ui';
import { message } from '#/adapter/naive';
import type { InternoJornadaDto } from '#/api/sisu/jornada';
import type { HorarioInternoDto, HorarioInternoRequest, DiaHorarioRequest } from '#/api/sisu/jornada';
import {
  guardarHorarioInterno, getHorarioInternoByInternoJornada,
} from '#/api/sisu/jornada';

// ── Props / Emits ─────────────────────────────────────────────────────────
interface Props {
  visible:     boolean;
  internos:    InternoJornadaDto[];
  idJornada:   number | null;
  diasDefault: string | null;   // CSV ISO: "2026-05-01,2026-05-04,..."
  horaInicio:  string | null;   // Hora base de la jornada HH:mm
  horaFin:     string | null;
}
const props = defineProps<Props>();
const emit  = defineEmits<{
  (e: 'update:visible', v: boolean): void;
  (e: 'saved'): void;
}>();

const drawerVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
});

// ── State ─────────────────────────────────────────────────────────────────
const cargando            = ref(false);
const guardando           = ref(false);
const horarioActual       = ref<HorarioInternoDto | null>(null);
const internoSeleccionado = ref<number | null>(null);  // idInternoJornada
const diasSeleccionados   = ref<Set<string>>(new Set());
const horasCustom         = ref<Record<string, { inicio: string | null; fin: string | null }>>({});
const observaciones       = ref('');
const expandedDias        = ref<string[]>([]);  // días expandidos para editar horas

// Opciones de internos
const internoOpts = computed(() =>
  props.internos.map(i => ({
    label: `${i.nombreCompleto} · ${i.carrera ?? ''}`,
    value: i.idInternoJornada,
  })),
);

// Días definidos en la jornada (diasDefault CSV → array de objetos)
const diasDelTurno = computed(() => {
  if (!props.diasDefault) return [];
  return props.diasDefault
    .split(',')
    .map(f => f.trim())
    .filter(f => f.length > 0)
    .map(fecha => {
      const dt = new Date(fecha + 'T00:00:00');
      const diaSemana = dt.toLocaleDateString('es', { weekday: 'short' });
      const diaNum    = dt.getDate();
      const mesCorto  = dt.toLocaleDateString('es', { month: 'short' });
      return { fecha, label: `${diaNum} ${diaSemana} ${mesCorto}`, diaNum, diaSemana };
    })
    .sort((a, b) => a.fecha.localeCompare(b.fecha));
});

// ── Carga del horario existente al cambiar interno ─────────────────────────
watch(internoSeleccionado, async (idIJ) => {
  horarioActual.value    = null;
  diasSeleccionados.value = new Set();
  horasCustom.value       = {};
  observaciones.value     = '';
  expandedDias.value      = [];
  if (!idIJ) return;
  cargando.value = true;
  try {
    const lista = await getHorarioInternoByInternoJornada(idIJ);
    if (lista.length > 0) {
      // Tomar el más reciente (o el que corresponde al mes de la jornada)
      const h = lista[0];
      horarioActual.value = h;
      const fechas = new Set(h.dias.map(d => d.fecha));
      diasSeleccionados.value = fechas;
      observaciones.value     = h.observaciones ?? '';
      // Restaurar horas custom
      const custom: Record<string, { inicio: string | null; fin: string | null }> = {};
      for (const d of h.dias) {
        if (d.horaInicio || d.horaFin) {
          custom[d.fecha] = { inicio: d.horaInicio ?? null, fin: d.horaFin ?? null };
        }
      }
      horasCustom.value = custom;
    }
  } catch {
    // sin horario previo
  } finally {
    cargando.value = false;
  }
});

// Cuando cambia el drawer, limpiar selección
watch(() => props.visible, (v) => {
  if (!v) {
    internoSeleccionado.value = null;
    horarioActual.value = null;
    diasSeleccionados.value = new Set();
    horasCustom.value = {};
    observaciones.value = '';
    expandedDias.value = [];
  }
});

// ── Toggle días ────────────────────────────────────────────────────────────
function toggleDia(fecha: string) {
  const s = new Set(diasSeleccionados.value);
  if (s.has(fecha)) {
    s.delete(fecha);
    // Limpiar horas custom si se deselecciona
    const h = { ...horasCustom.value };
    delete h[fecha];
    horasCustom.value = h;
  } else {
    s.add(fecha);
  }
  diasSeleccionados.value = s;
}

// ── Selecciones rápidas ────────────────────────────────────────────────────
function seleccionarTodos() {
  diasSeleccionados.value = new Set(diasDelTurno.value.map(d => d.fecha));
}

function limpiarSeleccion() {
  diasSeleccionados.value = new Set();
  horasCustom.value = {};
  expandedDias.value = [];
}

function seleccionarAlternos(inicio: 0 | 1) {
  const fechas = diasDelTurno.value.map(d => d.fecha);
  const seleccion = new Set(fechas.filter((_, i) => i % 2 === inicio));
  // Limpiar horas custom de días que quedaron fuera
  const nuevasHoras = { ...horasCustom.value };
  for (const f of Object.keys(nuevasHoras)) {
    if (!seleccion.has(f)) delete nuevasHoras[f];
  }
  horasCustom.value = nuevasHoras;
  diasSeleccionados.value = seleccion;
}

// ── Edición de horas por día ───────────────────────────────────────────────
function getHoraCustom(fecha: string, tipo: 'inicio' | 'fin'): string | null {
  return horasCustom.value[fecha]?.[tipo] ?? null;
}

function setHoraCustom(fecha: string, tipo: 'inicio' | 'fin', valor: number | null) {
  const actual = { ...(horasCustom.value[fecha] ?? { inicio: null, fin: null }) };
  if (valor === null) {
    actual[tipo] = null;
  } else {
    const h = String(Math.floor(valor / 3_600_000)).padStart(2, '0');
    const m = String(Math.floor((valor % 3_600_000) / 60_000)).padStart(2, '0');
    actual[tipo] = `${h}:${m}`;
  }
  // Si ambas son null, eliminar la entrada
  if (!actual.inicio && !actual.fin) {
    const h = { ...horasCustom.value };
    delete h[fecha];
    horasCustom.value = h;
  } else {
    horasCustom.value = { ...horasCustom.value, [fecha]: actual };
  }
}

function horaToMs(hora: string | null | undefined): number | null {
  if (!hora) return null;
  const [h, m] = hora.split(':').map(Number);
  return (h * 60 + m) * 60_000;
}

function tieneHoraCustom(fecha: string): boolean {
  const c = horasCustom.value[fecha];
  return !!(c?.inicio || c?.fin);
}

// ── Guardar ────────────────────────────────────────────────────────────────
async function guardar() {
  if (!internoSeleccionado.value) {
    message.warning('Selecciona un interno');
    return;
  }
  if (diasSeleccionados.value.size === 0) {
    message.warning('Selecciona al menos un día');
    return;
  }
  guardando.value = true;
  try {
    // Deducir mes/año del primer día seleccionado (o del primer día del turno)
    const primeraFecha = [...diasSeleccionados.value].sort()[0];
    const [anio, mes] = primeraFecha.split('-').map(Number);

    const dias: DiaHorarioRequest[] = [...diasSeleccionados.value].sort().map(fecha => {
      const custom = horasCustom.value[fecha];
      return {
        fecha,
        horaInicio:   custom?.inicio  ?? undefined,
        horaFin:      custom?.fin     ?? undefined,
      };
    });

    const req: HorarioInternoRequest = {
      idInternoJornada: internoSeleccionado.value,
      mes,
      anio,
      observaciones:    observaciones.value || undefined,
      dias,
    };
    await guardarHorarioInterno(req);
    message.success('Horario guardado — el interno será notificado');
    emit('saved');
    drawerVisible.value = false;
  } catch (e: any) {
    message.error(e?.message ?? 'Error al guardar el horario');
  } finally {
    guardando.value = false;
  }
}

// ── Helpers ────────────────────────────────────────────────────────────────
const countSeleccionados = computed(() => diasSeleccionados.value.size);
const totalDias          = computed(() => diasDelTurno.value.length);
</script>

<template>
  <NDrawer v-model:show="drawerVisible" :width="500" placement="right">
    <NDrawerContent title="Horario de Internos" closable>
      <div style="padding: 0 4px">

        <!-- Selector de interno -->
        <NFormItem label="Interno" style="margin-bottom: 16px">
          <NSelect
            v-model:value="internoSeleccionado"
            :options="internoOpts"
            placeholder="Seleccionar interno..."
            clearable
          />
        </NFormItem>

        <!-- Info del horario base -->
        <NAlert
          v-if="horaInicio && horaFin"
          type="info"
          style="margin-bottom: 16px"
          :show-icon="false"
        >
          Horario base de la jornada: <strong>{{ horaInicio }} – {{ horaFin }}</strong>
          · Los días sin hora personalizada usarán este horario.
        </NAlert>

        <!-- Loading -->
        <div v-if="cargando" style="text-align: center; padding: 30px">
          <NSpin />
        </div>

        <!-- Horario existente -->
        <div
          v-if="horarioActual && !cargando"
          class="alert-existente"
        >
          ✅ Horario existente — {{ horarioActual.dias.length }} días asignados.
          Puedes modificar y guardar de nuevo.
        </div>

        <!-- Contenido principal -->
        <template v-if="internoSeleccionado && !cargando">

          <!-- Sin días configurados en el turno -->
          <NEmpty
            v-if="diasDelTurno.length === 0"
            description="Este turno no tiene días específicos definidos (diasDefault vacío)"
            style="padding: 30px 0"
          />

          <template v-else>
            <!-- Selección rápida -->
            <div style="margin-bottom: 12px">
              <div class="quick-label">Selección rápida</div>
              <NSpace size="small" wrap>
                <NButton size="small" type="primary" ghost @click="seleccionarTodos">
                  ☑ Todos ({{ totalDias }})
                </NButton>
                <NButton size="small" ghost @click="seleccionarAlternos(0)">
                  ⇌ Pares
                </NButton>
                <NButton size="small" ghost @click="seleccionarAlternos(1)">
                  ⇌ Impares
                </NButton>
                <NButton size="small" type="error" ghost @click="limpiarSeleccion">
                  ✕ Limpiar
                </NButton>
              </NSpace>
            </div>

            <!-- Chips de días -->
            <p class="days-hint">
              Toca un día para activarlo/desactivarlo. Usa ▾ para personalizar horas.
            </p>

            <div class="dias-lista">
              <div
                v-for="dia in diasDelTurno"
                :key="dia.fecha"
                class="dia-row"
                :class="{ 'dia-row--activo': diasSeleccionados.has(dia.fecha) }"
              >
                <!-- Chip del día (click toggle) -->
                <div class="dia-chip" @click="toggleDia(dia.fecha)">
                  <span class="dia-chip__num">{{ dia.diaNum }}</span>
                  <span class="dia-chip__sem">{{ dia.diaSemana }}</span>
                  <span v-if="tieneHoraCustom(dia.fecha)" class="dia-chip__custom">✏</span>
                </div>

                <!-- Horas custom (solo si está seleccionado) -->
                <NCollapse
                  v-if="diasSeleccionados.has(dia.fecha)"
                  v-model:expanded-names="expandedDias"
                  class="hora-collapse"
                >
                  <NCollapseItem :name="dia.fecha" :title="tieneHoraCustom(dia.fecha) ? `${getHoraCustom(dia.fecha,'inicio') ?? horaInicio} – ${getHoraCustom(dia.fecha,'fin') ?? horaFin}` : 'Hora base'">
                    <div class="hora-row">
                      <div class="hora-field">
                        <span class="hora-label">Inicio</span>
                        <NTimePicker
                          :value="horaToMs(getHoraCustom(dia.fecha, 'inicio'))"
                          format="HH:mm"
                          :minutes-step="15"
                          size="small"
                          clearable
                          :placeholder="horaInicio ?? 'Hora'"
                          style="width: 110px"
                          @update:value="(v) => setHoraCustom(dia.fecha, 'inicio', v)"
                        />
                      </div>
                      <span class="hora-sep">–</span>
                      <div class="hora-field">
                        <span class="hora-label">Fin</span>
                        <NTimePicker
                          :value="horaToMs(getHoraCustom(dia.fecha, 'fin'))"
                          format="HH:mm"
                          :minutes-step="15"
                          size="small"
                          clearable
                          :placeholder="horaFin ?? 'Hora'"
                          style="width: 110px"
                          @update:value="(v) => setHoraCustom(dia.fecha, 'fin', v)"
                        />
                      </div>
                    </div>
                    <p class="hora-hint">
                      Deja vacío para usar el horario base de la jornada.
                    </p>
                  </NCollapseItem>
                </NCollapse>
              </div>
            </div>

            <p class="count-label">
              {{ countSeleccionados }} de {{ totalDias }} día(s) seleccionado(s)
            </p>

            <!-- Observaciones -->
            <NFormItem label="Observaciones (opcional)" style="margin-top: 12px">
              <NInput
                v-model:value="observaciones"
                type="textarea"
                :rows="2"
                placeholder="Notas sobre este horario del interno..."
              />
            </NFormItem>

            <!-- Acciones -->
            <NSpace style="margin-top: 8px">
              <NButton
                type="primary"
                :loading="guardando"
                :disabled="countSeleccionados === 0"
                @click="guardar"
              >
                💾 Guardar horario
              </NButton>
              <NButton @click="limpiarSeleccion">Limpiar</NButton>
            </NSpace>
          </template>
        </template>

        <!-- Placeholder: ningún interno seleccionado -->
        <NEmpty
          v-else-if="!internoSeleccionado && !cargando"
          description="Selecciona un interno para gestionar su horario"
          style="padding: 40px 0"
        />

      </div>
    </NDrawerContent>
  </NDrawer>
</template>

<style scoped>
/* Alerta de horario existente — semi-transparente para funcionar en ambos modos */
.alert-existente {
  margin-bottom: 16px;
  padding: 10px 14px;
  border-radius: 8px;
  background: rgba(24, 160, 88, 0.12);
  border: 1px solid rgba(24, 160, 88, 0.45);
  font-size: 13px;
  color: hsl(var(--foreground));
}

.quick-label {
  font-size: 11px;
  font-weight: 600;
  color: hsl(var(--muted-foreground));
  text-transform: uppercase;
  letter-spacing: .04em;
  margin-bottom: 6px;
}

.days-hint {
  font-size: 12px;
  color: hsl(var(--muted-foreground));
  margin: 0 0 10px;
}

.dias-lista {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 4px;
}

/* ── Fila de un día ─── */
.dia-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  border-radius: 10px;
  padding: 6px 8px;
  border: 1.5px solid hsl(var(--border));
  background: hsl(var(--card));
  transition: border-color .15s, background .15s;
}

/* Activo: overlay semitransparente azul → funciona en modo oscuro y claro */
.dia-row--activo {
  border-color: #3b82f6;
  background: rgba(59, 130, 246, 0.12);
}

/* ── Chip del número + día ─── */
.dia-chip {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 40px;
  cursor: pointer;
  user-select: none;
  padding-top: 2px;
}
.dia-chip__num {
  font-size: 17px;
  font-weight: 700;
  line-height: 1;
  color: hsl(var(--foreground));
  transition: color .15s;
}
.dia-chip__sem {
  font-size: 9px;
  text-transform: uppercase;
  color: hsl(var(--muted-foreground));
}
.dia-chip__custom {
  font-size: 10px;
  color: #f97316;
  margin-top: 1px;
}

/* Número en azul legible en ambos modos (blue-400, contrasta sobre fondos claros y oscuros) */
.dia-row--activo .dia-chip__num {
  color: #60a5fa;
}

/* ── Collapse de horas ─── */
.hora-collapse {
  flex: 1;
}
:deep(.n-collapse-item__header-main) {
  font-size: 12px;
  font-weight: 500;
  color: hsl(var(--foreground));
}

.hora-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.hora-field {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.hora-label {
  font-size: 10px;
  color: hsl(var(--muted-foreground));
  text-transform: uppercase;
}
.hora-sep {
  margin-top: 18px;
  color: hsl(var(--muted-foreground));
}
.hora-hint {
  margin: 6px 0 0;
  font-size: 11px;
  color: hsl(var(--muted-foreground));
}

.count-label {
  font-size: 12px;
  color: hsl(var(--muted-foreground));
  margin: 8px 0 0;
}
</style>
