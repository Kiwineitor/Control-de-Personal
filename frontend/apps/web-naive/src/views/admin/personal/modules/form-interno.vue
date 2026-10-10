<script lang="ts" setup>
import type { InternoDto, PersonaResumenDto } from '#/api/sisu/personal';
import { ref, computed, watch } from 'vue';
import { useVbenModal } from '@vben/common-ui';
import { message } from '#/adapter/naive';
import {
  NForm, NFormItem, NInput, NModal,
  NSelect, NDivider, NAlert, NGrid, NGridItem,
  NButton, NSwitch, NTag, NSpin, NSpace,
} from 'naive-ui';
import type { SelectOption } from 'naive-ui';
import { createInterno, updateInterno, buscarPersonas } from '#/api/sisu/personal';
import { getRolesActivosApi } from '#/api/admin/configuracion';

const emit = defineEmits<{ success: [] }>();

// ── Opciones estáticas ─────────────────────────────────────────────────────
type ModoPersona = 'buscar' | 'manual';
const modoPersona = ref<ModoPersona>('buscar');

const CARRERAS = [
  'MEDICINA', 'ENFERMERÍA', 'ODONTOLOGÍA', 'FARMACIA',
  'LABORATORIO CLÍNICO', 'FISIOTERAPIA', 'NUTRICIÓN',
].map(c => ({ label: c, value: c }));

const ANOS = [1,2,3,4,5,6].map(n => ({ label: `${n}° año`, value: n }));

// ── Estado ─────────────────────────────────────────────────────────────────
const loading         = ref(false);
const buscandoPersona = ref(false);
const roles           = ref<Array<{ label: string; value: number }>>([]);
const isEdit          = computed(() => !!form.value.idInterno);

const personaOptions      = ref<SelectOption[]>([]);
const personaSeleccionada = ref<PersonaResumenDto | null>(null);
const personaMap          = ref<Map<number, PersonaResumenDto>>(new Map());

// Estado del form (sin fechas de rotación - se definen al asignar jornada)
const form = ref<any>({
  idInterno:    undefined,
  idPersona:    undefined,
  nombre:       '', apellido:    '', ci:    '',
  email:        '', telefono:    '',
  universidad:  '', carrera:     '',
  anoFormacion: undefined, nroMatricula: '',
  activo:       true,
  // Cuenta de usuario (opcional)
  crearUsuario: false, username: '', password: '', idRol: undefined,
});

function resetForm() {
  form.value = {
    idInterno: undefined, idPersona: undefined,
    nombre: '', apellido: '', ci: '', email: '', telefono: '',
    universidad: '', carrera: '', anoFormacion: undefined, nroMatricula: '',
    activo: true,
    crearUsuario: false, username: '', password: '', idRol: undefined,
  };
}

// ── Modal de credenciales ──────────────────────────────────────────────────
const credencial = ref({ visible: false, nombre: '', username: '', password: '' });

async function copiarTexto(texto: string) {
  try {
    await navigator.clipboard.writeText(texto);
    message.success('Copiado al portapapeles');
  } catch { message.error('No se pudo copiar automáticamente'); }
}

async function copiarTodo() {
  await copiarTexto([
    `Persona:    ${credencial.value.nombre}`,
    `Usuario:    ${credencial.value.username}`,
    `Contraseña: ${credencial.value.password}`,
  ].join('\n'));
}

function cerrarCredencial() {
  credencial.value.visible = false;
  modalApi.close();
  emit('success');
}

// ── Cargar roles ───────────────────────────────────────────────────────────
async function cargarRoles() {
  if (roles.value.length > 0) return;
  try {
    const r = await getRolesActivosApi();
    roles.value = (r as any[]).map((x: any) => ({ label: x.nombre, value: x.idRol }));
  } catch { /* silencioso */ }
}

// ── Modal lifecycle ────────────────────────────────────────────────────────
const [Modal, modalApi] = useVbenModal({
  onOpenChange(isOpen: boolean) {
    if (!isOpen) return;
    const data = modalApi.getData<InternoDto>();
    personaSeleccionada.value = null;
    personaOptions.value = [];
    personaMap.value.clear();

    if (data?.idInterno) {
      modoPersona.value = 'manual';
      form.value = {
        idInterno:    data.idInterno,
        idPersona:    undefined,
        nombre:       data.nombre       ?? '',
        apellido:     data.apellido     ?? '',
        ci:           data.ci           ?? '',
        email:        data.email        ?? '',
        telefono:     data.telefono     ?? '',
        universidad:  data.universidad  ?? '',
        carrera:      data.carrera      ?? '',
        anoFormacion: data.anoFormacion,
        nroMatricula: data.nroMatricula ?? '',
        activo:       data.activo,
        crearUsuario: false, username: '', password: '', idRol: undefined,
      };
    } else {
      modoPersona.value = 'buscar';
      resetForm();
      cargarRoles();
    }
  },
});

watch(modoPersona, () => {
  form.value.idPersona = undefined;
  personaSeleccionada.value = null;
  form.value.nombre = form.value.apellido = form.value.ci = form.value.email = form.value.telefono = '';
});

function onPersonaChange(idPersona: number | null) {
  personaSeleccionada.value = idPersona ? (personaMap.value.get(idPersona) ?? null) : null;
}

async function onBuscarPersona(q: string) {
  if (!q || q.length < 2) { personaOptions.value = []; return; }
  buscandoPersona.value = true;
  try {
    const personas = await buscarPersonas(q);
    personaMap.value.clear();
    personas.forEach(p => personaMap.value.set(p.idPersona, p));
    personaOptions.value = personas.map(p => ({
      label: `${p.nombreCompleto}${p.ci ? ` — CI: ${p.ci}` : ''}`,
      value: p.idPersona,
    }));
  } finally { buscandoPersona.value = false; }
}

// ── Validación y submit ────────────────────────────────────────────────────
function validar(): string | null {
  if (modoPersona.value === 'buscar' && !form.value.idPersona)
    return 'Selecciona una persona o cambia a "Registrar nueva persona"';
  if (modoPersona.value === 'manual' && !form.value.nombre?.trim()) return 'El nombre es obligatorio';
  if (modoPersona.value === 'manual' && !form.value.apellido?.trim()) return 'El apellido es obligatorio';
  if (!form.value.universidad?.trim()) return 'La universidad es obligatoria';
  if (!form.value.carrera)             return 'La carrera es obligatoria';
  if (!isEdit.value && form.value.crearUsuario && !form.value.username?.trim())
    return 'El nombre de usuario es obligatorio para crear la cuenta';
  return null;
}

async function handleSubmit() {
  const error = validar();
  if (error) { message.warning(error); return; }

  loading.value = true;
  try {
    const payload: any = {
      idPersona:    modoPersona.value === 'buscar' ? form.value.idPersona : undefined,
      nombre:       modoPersona.value === 'manual' ? (form.value.nombre    || undefined) : undefined,
      apellido:     modoPersona.value === 'manual' ? (form.value.apellido  || undefined) : undefined,
      ci:           modoPersona.value === 'manual' ? (form.value.ci        || undefined) : undefined,
      email:        modoPersona.value === 'manual' ? (form.value.email     || undefined) : undefined,
      telefono:     modoPersona.value === 'manual' ? (form.value.telefono  || undefined) : undefined,
      universidad:  form.value.universidad,
      carrera:      form.value.carrera,
      anoFormacion: form.value.anoFormacion,
      nroMatricula: form.value.nroMatricula || undefined,
      activo:       form.value.activo,
      // Campos opcionales de usuario
      crearUsuario: form.value.crearUsuario || undefined,
      username:     form.value.crearUsuario ? (form.value.username || undefined) : undefined,
      password:     form.value.crearUsuario ? (form.value.password || undefined) : undefined,
      idRol:        form.value.crearUsuario ? (form.value.idRol    || undefined) : undefined,
    };

    if (isEdit.value) {
      await updateInterno(form.value.idInterno!, payload);
      message.success('Interno actualizado');
      modalApi.close();
      emit('success');
    } else {
      await createInterno(payload);
      if (form.value.crearUsuario && form.value.username) {
        // Mostrar credenciales antes de cerrar
        const nombreCompleto = personaSeleccionada.value?.nombreCompleto
          ?? `${form.value.nombre} ${form.value.apellido}`.trim()
          ?? 'Interno registrado';
        credencial.value = {
          visible:  true,
          nombre:   nombreCompleto,
          username: form.value.username,
          password: form.value.password || '(auto-generada por el servidor)',
        };
        // cerrarCredencial() llamará emit('success') al cerrar el modal
      } else {
        message.success('Interno registrado');
        modalApi.close();
        emit('success');
      }
    }
  } catch (e: any) {
    message.error(e?.message ?? 'Error al guardar el interno');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <Modal
    :title="isEdit ? '✏️ Editar Interno' : '➕ Nuevo Interno'"
    :footer="false"
    style="width: 720px"
  >
    <NForm label-placement="top" :show-require-mark="false">

      <!-- ── Datos de la persona (solo creación) ─────────────────────── -->
      <template v-if="!isEdit">
        <NDivider style="margin:4px 0 12px">
          <span style="font-size:.78rem;color:#94a3b8;font-weight:500">DATOS PERSONALES</span>
        </NDivider>

        <div class="flex gap-2 mb-3">
          <NButton size="small"
            :type="modoPersona === 'buscar' ? 'primary' : 'default'"
            :style="modoPersona === 'buscar' ? { background: '#1d4ed8', borderColor: '#1d4ed8' } : {}"
            @click="modoPersona = 'buscar'">
            🔍 Buscar persona existente
          </NButton>
          <NButton size="small"
            :type="modoPersona === 'manual' ? 'primary' : 'default'"
            :style="modoPersona === 'manual' ? { background: '#1d4ed8', borderColor: '#1d4ed8' } : {}"
            @click="modoPersona = 'manual'">
            ✏️ Registrar nueva persona
          </NButton>
        </div>

        <!-- Buscar persona -->
        <template v-if="modoPersona === 'buscar'">
          <NFormItem label="Buscar persona *">
            <NSpin :show="buscandoPersona" style="width:100%">
              <NSelect
                v-model:value="form.idPersona"
                :options="personaOptions"
                placeholder="Escribir nombre o CI para buscar…"
                filterable remote clearable :loading="buscandoPersona"
                :clear-filter-after-select="false" style="width:100%"
                @search="onBuscarPersona" @update:value="onPersonaChange"
              />
            </NSpin>
          </NFormItem>
          <div v-if="personaSeleccionada"
               class="mb-3 p-3 rounded-lg border border-blue-200 bg-blue-50 dark:border-blue-800/60 dark:bg-blue-950/40">
            <div class="flex items-center gap-2 mb-1">
              <NTag type="info" size="small" round>Seleccionada</NTag>
              <span class="font-semibold text-sm">{{ personaSeleccionada.nombreCompleto }}</span>
            </div>
            <div class="grid grid-cols-3 gap-x-4 text-xs text-gray-500">
              <span>CI: {{ personaSeleccionada.ci || '—' }}</span>
              <span>Email: {{ personaSeleccionada.email || '—' }}</span>
              <span>Tel: {{ personaSeleccionada.telefono || '—' }}</span>
            </div>
          </div>
          <NAlert v-else type="default" :show-icon="false" style="font-size:.78rem" class="mb-3">
            Escribe al menos 2 caracteres para buscar personas ya registradas.
          </NAlert>
        </template>

        <!-- Nueva persona manual -->
        <template v-else>
          <NGrid :cols="2" :x-gap="16">
            <NGridItem>
              <NFormItem label="Nombre(s) *">
                <NInput v-model:value="form.nombre" placeholder="Ej: Juan Carlos" maxlength="100" clearable />
              </NFormItem>
            </NGridItem>
            <NGridItem>
              <NFormItem label="Apellido(s) *">
                <NInput v-model:value="form.apellido" placeholder="Ej: Mamani Quispe" maxlength="100" clearable />
              </NFormItem>
            </NGridItem>
            <NGridItem>
              <NFormItem label="CI">
                <NInput v-model:value="form.ci" placeholder="Ej: 8754321" maxlength="20" clearable />
              </NFormItem>
            </NGridItem>
            <NGridItem>
              <NFormItem label="Teléfono">
                <NInput v-model:value="form.telefono" placeholder="+591 7xxxxxxx" maxlength="20" clearable />
              </NFormItem>
            </NGridItem>
          </NGrid>
          <NFormItem label="Email">
            <NInput v-model:value="form.email" placeholder="juan@uap.edu.bo" maxlength="150" clearable />
          </NFormItem>
        </template>
      </template>

      <!-- EDICIÓN: datos personales directamente -->
      <template v-else>
        <NDivider style="margin:4px 0 12px">
          <span style="font-size:.78rem;color:#94a3b8;font-weight:500">DATOS PERSONALES</span>
        </NDivider>
        <NGrid :cols="2" :x-gap="16">
          <NGridItem><NFormItem label="Nombre(s) *"><NInput v-model:value="form.nombre" maxlength="100" clearable /></NFormItem></NGridItem>
          <NGridItem><NFormItem label="Apellido(s) *"><NInput v-model:value="form.apellido" maxlength="100" clearable /></NFormItem></NGridItem>
          <NGridItem><NFormItem label="CI"><NInput v-model:value="form.ci" maxlength="20" clearable /></NFormItem></NGridItem>
          <NGridItem><NFormItem label="Teléfono"><NInput v-model:value="form.telefono" maxlength="20" clearable /></NFormItem></NGridItem>
        </NGrid>
        <NFormItem label="Email"><NInput v-model:value="form.email" maxlength="150" clearable /></NFormItem>
      </template>

      <!-- ── Datos Académicos ─────────────────────────────────────────── -->
      <NDivider style="margin:12px 0">
        <span style="font-size:.78rem;color:#94a3b8;font-weight:500">DATOS ACADÉMICOS</span>
      </NDivider>
      <NGrid :cols="2" :x-gap="16">
        <NGridItem>
          <NFormItem label="Universidad *">
            <NInput v-model:value="form.universidad" placeholder="Ej: UAP, UMSA…" maxlength="150" clearable />
          </NFormItem>
        </NGridItem>
        <NGridItem>
          <NFormItem label="Carrera *">
            <NSelect v-model:value="form.carrera" :options="CARRERAS" placeholder="Seleccionar carrera" clearable />
          </NFormItem>
        </NGridItem>
        <NGridItem>
          <NFormItem label="Año de formación">
            <NSelect v-model:value="form.anoFormacion" :options="ANOS" placeholder="— No especificado —" clearable />
          </NFormItem>
        </NGridItem>
        <NGridItem>
          <NFormItem label="N° Matrícula">
            <NInput v-model:value="form.nroMatricula" placeholder="Ej: MED-2025-001" maxlength="50" clearable />
          </NFormItem>
        </NGridItem>
      </NGrid>

      <!-- ── Cuenta de usuario (solo creación) ───────────────────────── -->
      <template v-if="!isEdit">
        <NDivider style="margin:12px 0">
          <span style="font-size:.78rem;color:#94a3b8;font-weight:500">CUENTA DE ACCESO</span>
        </NDivider>

        <div class="usuario-toggle">
          <NSwitch v-model:value="form.crearUsuario" />
          <span>Crear cuenta de usuario para este interno</span>
        </div>

        <template v-if="form.crearUsuario">
          <NGrid :cols="2" :x-gap="16" style="margin-top:12px">
            <NGridItem>
              <NFormItem label="Username *">
                <NInput v-model:value="form.username" placeholder="ej: jmamani" clearable />
              </NFormItem>
            </NGridItem>
            <NGridItem>
              <NFormItem label="Contraseña inicial">
                <NInput v-model:value="form.password" type="password" show-password-on="click"
                        placeholder="Vacío = auto-generada" clearable />
              </NFormItem>
            </NGridItem>
            <NGridItem :span="2">
              <NFormItem label="Rol de acceso">
                <NSelect v-model:value="form.idRol" :options="roles"
                         placeholder="Inferido por el sistema si no se elige" filterable clearable />
              </NFormItem>
            </NGridItem>
          </NGrid>
          <NAlert type="warning" :show-icon="false" style="font-size:12px;margin-top:4px">
            ⚠ Guarda las credenciales cuando aparezcan — no se podrán recuperar desde aquí.
          </NAlert>
        </template>
      </template>

      <!-- ── Estado ──────────────────────────────────────────────────── -->
      <NDivider style="margin:12px 0" />
      <NFormItem label="Estado">
        <NSwitch v-model:value="form.activo">
          <template #checked>Activo</template>
          <template #unchecked>Inactivo</template>
        </NSwitch>
      </NFormItem>

      <div class="flex justify-end gap-2 mt-4">
        <NButton @click="modalApi.close()">Cancelar</NButton>
        <NButton type="primary" :loading="loading"
                 :style="{ background: '#1d4ed8', borderColor: '#1d4ed8' }"
                 @click="handleSubmit">
          {{ isEdit ? 'Actualizar Interno' : 'Registrar Interno' }}
        </NButton>
      </div>

    </NForm>
  </Modal>

  <!-- ══ MODAL DE CREDENCIALES ═════════════════════════════════════════════ -->
  <NModal
    v-model:show="credencial.visible"
    preset="card"
    style="width:460px"
    :mask-closable="false"
    :closable="false"
  >
    <template #header>
      <span style="color:#18a058;font-size:1.05rem">✅ Interno registrado exitosamente</span>
    </template>

    <div style="font-size:.9rem;margin-bottom:16px">
      🎓 <strong>{{ credencial.nombre }}</strong>
    </div>

    <div class="cred-box">
      <div class="cred-row">
        <span class="cred-label">Usuario</span>
        <code class="cred-value">{{ credencial.username }}</code>
        <NButton size="tiny" ghost @click="copiarTexto(credencial.username)">📋 Copiar</NButton>
      </div>
      <div class="cred-row">
        <span class="cred-label">Contraseña</span>
        <code class="cred-value">{{ credencial.password }}</code>
        <NButton size="tiny" ghost @click="copiarTexto(credencial.password)">📋 Copiar</NButton>
      </div>
    </div>

    <NAlert type="warning" :show-icon="false" style="font-size:12px;margin-top:14px">
      ⚠ Guarda estas credenciales ahora. Una vez que cierres esta ventana no podrás verlas de nuevo.
    </NAlert>

    <template #footer>
      <NSpace justify="end">
        <NButton @click="copiarTodo">📋 Copiar todo</NButton>
        <NButton type="primary" style="background:#18a058;border-color:#18a058" @click="cerrarCredencial">
          ✓ Guardé las credenciales, continuar
        </NButton>
      </NSpace>
    </template>
  </NModal>
</template>

<style scoped>
.usuario-toggle {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: .88rem;
  color: hsl(var(--foreground));
}
.cred-box {
  border: 1.5px solid hsl(var(--border));
  border-radius: 10px;
  overflow: hidden;
}
.cred-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-bottom: 1px solid hsl(var(--border));
}
.cred-row:last-child { border-bottom: none; }
.cred-label {
  font-size: .75rem;
  font-weight: 700;
  color: hsl(var(--muted-foreground));
  width: 88px;
  flex-shrink: 0;
}
.cred-value {
  flex: 1;
  font-size: .88rem;
  font-weight: 600;
  background: hsl(var(--muted));
  padding: 2px 8px;
  border-radius: 5px;
  word-break: break-all;
}
</style>
