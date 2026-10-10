<script lang="ts" setup>
import { ref, watch } from 'vue';
import { NCard, NSpin, NEmpty, NTag, NText, NDivider } from 'naive-ui';
import type { TitularJornadaDto, InternoJornadaDto, EstadoAsignacion } from '#/api/sisu/jornada';
import { getTitularesJornada, getInternosJornada } from '#/api/sisu/jornada';
import { message } from '#/adapter/naive';

const props = defineProps<{ idJornada: number | null }>();

const titulares = ref<TitularJornadaDto[]>([]);
const internos = ref<InternoJornadaDto[]>([]);
const cargando = ref(false);

const ESTADO_META: Record<EstadoAsignacion, { label: string; type: any }> = {
  PROGRAMADO: { label: 'Programado', type: 'info' },
  CONFIRMADO: { label: 'Confirmado', type: 'success' },
  EN_CURSO:   { label: 'En curso',   type: 'warning' },
  COMPLETADO: { label: 'Completado', type: 'default' },
  CANCELADO:  { label: 'Cancelado',  type: 'error' },
};

function formatearHora(hora: string | null): string {
  return hora?.substring(0, 5) ?? '--:--';
}

function tieneOverride(t: TitularJornadaDto | InternoJornadaDto): boolean {
  return t.horaInicioOverride !== null || t.horaFinOverride !== null;
}

watch(() => props.idJornada, async (id) => {
  if (!id) { titulares.value = []; internos.value = []; return; }
  cargando.value = true;
  try {
    const [tits, ints] = await Promise.all([
      getTitularesJornada(id),
      getInternosJornada(id),
    ]);
    titulares.value = tits ?? [];
    internos.value = ints ?? [];
  } catch {
    message.error('Error al cargar personal de la jornada');
  } finally {
    cargando.value = false;
  }
}, { immediate: true });
</script>

<template>
  <NSpin :show="cargando">
    <div v-if="!cargando && titulares.length === 0 && internos.length === 0" class="py-10">
      <NEmpty description="No hay personal asignado a esta jornada" />
    </div>

    <div v-else>
      <template v-if="titulares.length > 0">
        <NDivider title-placement="left">
          <NText strong style="font-size: 0.9rem;">
            Titulares ({{ titulares.length }})
          </NText>
        </NDivider>

        <div class="personal-grid">
          <NCard
            v-for="t in titulares"
            :key="t.idTitularJornada"
            size="small"
            class="persona-card"
            :style="{ borderLeft: `4px solid ${
              t.estado === 'PROGRAMADO' ? '#3b82f6'
              : t.estado === 'CONFIRMADO' ? '#22c55e'
              : t.estado === 'EN_CURSO' ? '#f59e0b'
              : t.estado === 'COMPLETADO' ? '#94a3b8'
              : '#ef4444'
            }` }"
          >
            <div class="flex items-center justify-between mb-2">
              <NText strong style="font-size: 0.95rem;">
                {{ t.nombreCompleto }}
              </NText>
              <NTag :type="ESTADO_META[t.estado]?.type ?? 'default'" size="small" round>
                {{ ESTADO_META[t.estado]?.label ?? t.estado }}
              </NTag>
            </div>

            <div class="text-xs text-muted-foreground space-y-1">
              <div v-if="t.cargo">
                Cargo: <NText strong>{{ t.cargo }}</NText>
              </div>
              <div>
                Horario efectivo:
                <NText strong>
                  {{ formatearHora(t.horaInicioEfectiva) }} — {{ formatearHora(t.horaFinEfectiva) }}
                </NText>
                <NTag
                  v-if="tieneOverride(t)"
                  size="tiny"
                  type="warning"
                  style="margin-left: 6px;"
                >
                  Horario personalizado
                </NTag>
              </div>
              <div v-if="t.especialidad">
                Especialidad: {{ t.especialidad }}
              </div>
              <div v-if="t.observaciones">
                Observaciones: {{ t.observaciones }}
              </div>
            </div>
          </NCard>
        </div>
      </template>

      <template v-if="internos.length > 0">
        <NDivider title-placement="left">
          <NText strong style="font-size: 0.9rem;">
            Internos ({{ internos.length }})
          </NText>
        </NDivider>

        <div class="personal-grid">
          <NCard
            v-for="i in internos"
            :key="i.idInternoJornada"
            size="small"
            class="persona-card"
            :style="{ borderLeft: `4px solid ${
              i.estado === 'PROGRAMADO' ? '#3b82f6'
              : i.estado === 'CONFIRMADO' ? '#22c55e'
              : i.estado === 'EN_CURSO' ? '#f59e0b'
              : i.estado === 'COMPLETADO' ? '#94a3b8'
              : '#ef4444'
            }` }"
          >
            <div class="flex items-center justify-between mb-2">
              <NText strong style="font-size: 0.95rem;">
                {{ i.nombreCompleto }}
              </NText>
              <NTag :type="ESTADO_META[i.estado]?.type ?? 'default'" size="small" round>
                {{ ESTADO_META[i.estado]?.label ?? i.estado }}
              </NTag>
            </div>

            <div class="text-xs text-muted-foreground space-y-1">
              <div v-if="i.carrera">
                Carrera: <NText strong>{{ i.carrera }}</NText>
              </div>
              <div v-if="i.universidad">
                Universidad: {{ i.universidad }}
              </div>
              <div>
                Horario efectivo:
                <NText strong>
                  {{ formatearHora(i.horaInicioEfectiva) }} — {{ formatearHora(i.horaFinEfectiva) }}
                </NText>
                <NTag
                  v-if="tieneOverride(i)"
                  size="tiny"
                  type="warning"
                  style="margin-left: 6px;"
                >
                  Horario personalizado
                </NTag>
              </div>
              <div v-if="i.nombreResponsable">
                Responsable: {{ i.nombreResponsable }}
              </div>
              <div v-if="i.observaciones">
                Observaciones: {{ i.observaciones }}
              </div>
            </div>
          </NCard>
        </div>
      </template>
    </div>
  </NSpin>
</template>

<style scoped>
.personal-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 12px;
}
.persona-card {
  transition: box-shadow 0.15s;
}
.persona-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}
</style>
