<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  NButton, NCard, NDrawer, NDrawerContent, NSpace, NSpin, NEmpty,
  NForm, NFormItem, NSelect, NInput, NTag, NDivider, NAlert,
} from 'naive-ui';
import { message } from '#/adapter/naive';
import type { TitularJornadaDto } from '#/api/sisu/jornada';
import type { RolTurnoDto, RolTurnoRequest } from '#/api/sisu/monitoreo';
import {
  guardarRolTurno, getRolTurnoByTitularJornada, eliminarRolTurno,
} from '#/api/sisu/monitoreo';

// ── Props / Emits ──────────────────────────────────────────
interface Props {
  visible:   boolean;
  titulares: TitularJornadaDto[];
  idJornada: number | null;
  // Rango de fechas de la jornada para limitar la selección de días
  fechaInicio: string | null;
  fechaFin:    string | null;
  horaInicio:  string | null;
  horaFin:     string | null;
}
const props = defineProps<Props>();
const emit  = defineEmits<{(e: 'update:visible', v: boolean): void; (e: 'saved'): void; }>();

const drawerVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
});

// ── State ──────────────────────────────────────────────────
const cargando       = ref(false);
const guardando      = ref(false);
const rolTurnoActual = ref<RolTurnoDto | null>(null);

const titularSeleccionado = ref<number | null>(null);   // idTitularJornada
const mesSeleccionado     = ref<number>(new Date().getMonth() + 1);
const anioSeleccionado    = ref<number>(new Date().getFullYear());
const diasSeleccionados   = ref<Set<string>>(new Set());
const observaciones       = ref('');

// Opciones mes
const MES_OPTS = [
  'Enero','Febrero','Marzo','Abril','Mayo','Junio',
  'Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre',
].map((label, i) => ({ label, value: i + 1 }));

const titularOpts = computed(() =>
  props.titulares.map(t => ({
    label: `${t.nombre} · ${t.cargo ?? ''}`,
    value: t.idTitularJornada,
  })),
);

// Días del mes seleccionado dentro del rango de la jornada
const diasDelMes = computed(() => {
  const year = anioSeleccionado.value;
  const mes  = mesSeleccionado.value;
  const total = new Date(year, mes, 0).getDate();
  const result: { fecha: string; label: string; diaNum: number; enRango: boolean }[] = [];

  const inicio = props.fechaInicio ? new Date(props.fechaInicio) : null;
  const fin    = props.fechaFin    ? new Date(props.fechaFin)    : null;

  for (let d = 1; d <= total; d++) {
    const fecha = `${year}-${String(mes).padStart(2,'0')}-${String(d).padStart(2,'0')}`;
    const dt    = new Date(fecha);
    const diaSemana = dt.toLocaleDateString('es', { weekday: 'short' });
    const enRango   = (!inicio || dt >= inicio) && (!fin || dt <= fin);
    result.push({ fecha, label: `${d} ${diaSemana}`, diaNum: d, enRango });
  }
  return result;
});

function toggleDia(fecha: string) {
  const nuevos = new Set(diasSeleccionados.value);
  if (nuevos.has(fecha)) nuevos.delete(fecha);
  else                   nuevos.add(fecha);
  diasSeleccionados.value = nuevos;
}

// ── Cargar ROL TURNO existente al cambiar titular/mes/año ──
watch(
  [titularSeleccionado, mesSeleccionado, anioSeleccionado],
  async ([idTJ]) => {
    rolTurnoActual.value  = null;
    diasSeleccionados.value = new Set();
    observaciones.value   = '';
    if (!idTJ) return;
    cargando.value = true;
    try {
      const lista = await getRolTurnoByTitularJornada(idTJ);
      const rt = lista.find(r =>
        r.mes === mesSeleccionado.value && r.anio === anioSeleccionado.value,
      );
      if (rt) {
        rolTurnoActual.value = rt;
        diasSeleccionados.value = new Set(rt.dias.map(d => d.fecha));
        observaciones.value   = rt.observaciones ?? '';
      }
    } catch {
      // sin rol turno previo
    } finally {
      cargando.value = false;
    }
  },
);

// ── Guardar ────────────────────────────────────────────────
async function guardar() {
  if (!titularSeleccionado.value) {
    message.warning('Selecciona un titular');
    return;
  }
  if (diasSeleccionados.value.size === 0) {
    message.warning('Selecciona al menos un día de trabajo');
    return;
  }
  guardando.value = true;
  try {
    const req: RolTurnoRequest = {
      idTitularJornada: titularSeleccionado.value,
      mes:              mesSeleccionado.value,
      anio:             anioSeleccionado.value,
      observaciones:    observaciones.value || undefined,
      dias:             [...diasSeleccionados.value].sort().map(f => ({ fecha: f })),
    };
    await guardarRolTurno(req);
    message.success('ROL TURNO guardado correctamente');
    emit('saved');
    // Recargar para mostrar el resultado
    const lista = await getRolTurnoByTitularJornada(titularSeleccionado.value);
    const rt = lista.find(r =>
      r.mes === mesSeleccionado.value && r.anio === anioSeleccionado.value,
    );
    if (rt) { rolTurnoActual.value = rt; }
  } catch (e: any) {
    message.error(e?.message ?? 'Error al guardar');
  } finally {
    guardando.value = false;
  }
}

async function limpiar() {
  diasSeleccionados.value = new Set();
  observaciones.value     = '';
  rolTurnoActual.value    = null;
}
</script>

<template>
  <NDrawer v-model:show="drawerVisible" :width="480" placement="right">
    <NDrawerContent title="Gestionar ROL TURNO" closable>

      <div style="padding:0 4px">

        <!-- Selector de titular -->
        <NFormItem label="Titular" style="margin-bottom:16px">
          <NSelect
            v-model:value="titularSeleccionado"
            :options="titularOpts"
            placeholder="Seleccionar titular..."
            clearable
          />
        </NFormItem>

        <!-- Mes / Año -->
        <div style="display:flex;gap:12px;margin-bottom:16px">
          <NFormItem label="Mes" style="flex:1;margin-bottom:0">
            <NSelect v-model:value="mesSeleccionado" :options="MES_OPTS" />
          </NFormItem>
          <NFormItem label="Año" style="width:100px;margin-bottom:0">
            <NSelect
              v-model:value="anioSeleccionado"
              :options="[2024,2025,2026,2027].map(y=>({label:String(y),value:y}))"
            />
          </NFormItem>
        </div>

        <!-- Info de horario base -->
        <NAlert v-if="horaInicio && horaFin" type="info" style="margin-bottom:16px" :show-icon="false">
          Horario de la jornada: <strong>{{ horaInicio }} – {{ horaFin }}</strong>
          (los días seleccionados usarán este horario por defecto)
        </NAlert>

        <!-- Loading -->
        <div v-if="cargando" style="text-align:center;padding:30px">
          <NSpin />
        </div>

        <!-- Rol turno existente -->
        <NCard
          v-if="rolTurnoActual && !cargando"
          size="small"
          style="margin-bottom:16px;background:#f0fff4;border-color:#18a058"
        >
          <p style="margin:0;font-size:13px;color:#18a058">
            ✅ ROL TURNO existente — {{ rolTurnoActual.dias.length }} días asignados.
            Puedes modificar la selección y guardar de nuevo.
          </p>
        </NCard>

        <!-- Calendariito de días -->
        <template v-if="titularSeleccionado && !cargando">
          <p style="font-size:13px;color:#666;margin-bottom:8px">
            Selecciona los días de trabajo en el mes
            (solo días dentro del rango de la jornada están disponibles):
          </p>
          <div class="dias-grid">
            <div
              v-for="dia in diasDelMes"
              :key="dia.fecha"
              class="dia-chip"
              :class="{
                'dia-chip--activo':    diasSeleccionados.has(dia.fecha),
                'dia-chip--fuera':     !dia.enRango,
              }"
              @click="dia.enRango && toggleDia(dia.fecha)"
            >
              <span class="dia-chip__num">{{ dia.diaNum }}</span>
              <span class="dia-chip__label">{{ dia.label.split(' ')[1] }}</span>
            </div>
          </div>

          <p style="font-size:12px;color:#888;margin:8px 0 16px">
            {{ diasSeleccionados.size }} día(s) seleccionado(s)
          </p>

          <NFormItem label="Observaciones (opcional)">
            <NInput
              v-model:value="observaciones"
              type="textarea"
              :rows="2"
              placeholder="Notas sobre este ROL TURNO..."
            />
          </NFormItem>

          <NSpace style="margin-top:8px">
            <NButton type="primary" :loading="guardando" @click="guardar">
              💾 Guardar ROL TURNO
            </NButton>
            <NButton @click="limpiar">Limpiar selección</NButton>
          </NSpace>
        </template>

        <NEmpty
          v-else-if="!titularSeleccionado && !cargando"
          description="Selecciona un titular para gestionar su ROL TURNO"
          style="padding:40px 0"
        />

      </div>

    </NDrawerContent>
  </NDrawer>
</template>

<style scoped>
.dias-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 6px;
  margin-bottom: 4px;
}
.dia-chip {
  display: flex;
  flex-direction: column;
  align-items: center;
  border-radius: 8px;
  padding: 6px 2px;
  cursor: pointer;
  border: 1.5px solid #e0e0e0;
  background: #fafafa;
  transition: all .15s;
  user-select: none;
}
.dia-chip:hover:not(.dia-chip--fuera) {
  border-color: #18a058;
  background: #f0fff4;
}
.dia-chip--activo {
  background: #18a058;
  border-color: #18a058;
  color: white;
}
.dia-chip--fuera {
  opacity: .35;
  cursor: not-allowed;
}
.dia-chip__num   { font-size: 15px; font-weight: 700; line-height: 1; }
.dia-chip__label { font-size: 9px; opacity: .8; text-transform: uppercase; }
</style>
