<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import {
  NButton, NDivider, NForm, NFormItem, NInput, NModal, NSelect, NSwitch,
  NSpace, NAlert,
} from 'naive-ui';
import { message } from '#/adapter/naive';
import type { PersonalHospitalDto, RegistrarPersonalRequest, TipoPersonalDto } from '#/api/admin/personal';
import { getTiposPersonal, registrarPersonal, actualizarPersonal } from '#/api/admin/personal';
import { getLugaresActivosApi, getRolesActivosApi } from '#/api/admin/configuracion';

const props = defineProps<{ personal: PersonalHospitalDto | null }>();
const emit  = defineEmits<{ (e: 'saved'): void; (e: 'cancel'): void }>();

const saving  = ref(false);
const tipos   = ref<TipoPersonalDto[]>([]);
const lugares = ref<Array<{ label: string; value: number }>>([]);
const roles   = ref<Array<{ label: string; value: number }>>([]);

const isEdit = computed(() => !!props.personal);

const form = ref<RegistrarPersonalRequest>({
  nombre: '', apellido: '', ci: '', email: '', telefono: '',
  idTipoPersonal: 0, cargo: '', especialidad: '', idLugar: undefined,
  crearUsuario: false, username: '', password: '', idRol: undefined,
});

// ── Modal de credenciales ──────────────────────────────────────────────────
const credencial = ref({ visible: false, nombre: '', username: '', password: '' });

async function copiarTexto(texto: string) {
  try {
    await navigator.clipboard.writeText(texto);
    message.success('Copiado al portapapeles');
  } catch {
    message.error('No se pudo copiar automáticamente');
  }
}

async function copiarTodo() {
  const lines = [
    `Persona:    ${credencial.value.nombre}`,
    `Usuario:    ${credencial.value.username}`,
    `Contraseña: ${credencial.value.password}`,
  ].join('\n');
  await copiarTexto(lines);
}

function cerrarCredencial() {
  credencial.value.visible = false;
  emit('saved');
}

// ── Carga de opciones ──────────────────────────────────────────────────────
onMounted(async () => {
  const [t, l, r] = await Promise.all([getTiposPersonal(), getLugaresActivosApi(), getRolesActivosApi()]);
  tipos.value   = t;
  lugares.value = (l as any[]).map((x: any) => ({ label: x.nombre, value: x.idLugar }));
  roles.value   = (r as any[]).map((x: any) => ({ label: x.nombre, value: x.idRol }));
});

watch(() => props.personal, (p) => {
  if (p) {
    form.value = {
      nombre:         p.nombreCompleto.split(' ').slice(1).join(' '),
      apellido:       p.nombreCompleto.split(' ')[0] ?? '',
      ci:             p.ci ?? '',
      email:          p.email ?? '',
      telefono:       p.telefono ?? '',
      idTipoPersonal: p.idTipoPersonal ?? 0,
      cargo:          p.cargo ?? '',
      especialidad:   p.especialidad ?? '',
      idLugar:        p.idLugar ?? undefined,
      crearUsuario: false, username: '', password: '', idRol: undefined,
    };
  } else {
    form.value = {
      nombre: '', apellido: '', ci: '', email: '', telefono: '',
      idTipoPersonal: 0, cargo: '', especialidad: '', idLugar: undefined,
      crearUsuario: false, username: '', password: '', idRol: undefined,
    };
  }
}, { immediate: true });

const tipoActual = computed(() => tipos.value.find(t => t.idTipoPersonal === form.value.idTipoPersonal));

watch(() => form.value.idTipoPersonal, (id) => {
  if (!isEdit.value && id) {
    const t = tipos.value.find(x => x.idTipoPersonal === id);
    if (t && !form.value.cargo) form.value.cargo = t.nombre;
  }
});

const tipoOpts = computed(() =>
  tipos.value.map(t => ({ label: `${t.nombre}${t.esPersonalMedico ? ' 🏥' : ''}`, value: t.idTipoPersonal, disabled: !t.activo })),
);

function sanitizarPayload(f: RegistrarPersonalRequest): RegistrarPersonalRequest {
  const trim = (v?: string | null) => v?.trim() || null;
  return { ...f, email: trim((f as any).email) as any, telefono: trim((f as any).telefono) as any, ci: trim((f as any).ci) as any };
}

async function guardar() {
  if (!form.value.nombre.trim())   { message.warning('El nombre es obligatorio'); return; }
  if (!form.value.apellido.trim()) { message.warning('El apellido es obligatorio'); return; }
  if (!form.value.idTipoPersonal)  { message.warning('Selecciona el tipo de personal'); return; }
  if (!isEdit.value && form.value.crearUsuario && !form.value.username?.trim()) {
    message.warning('El nombre de usuario es obligatorio para crear la cuenta');
    return;
  }
  saving.value = true;
  const payload = sanitizarPayload(form.value);
  try {
    if (isEdit.value) {
      await actualizarPersonal(props.personal!.idTitular, payload);
      message.success('Personal actualizado');
      emit('saved');
    } else {
      await registrarPersonal(payload);
      if (form.value.crearUsuario && form.value.username) {
        // Mostrar modal con credenciales para guardar
        credencial.value = {
          visible:  true,
          nombre:   `${form.value.nombre} ${form.value.apellido}`,
          username: form.value.username,
          password: form.value.password || '(auto-generada por el servidor)',
        };
        // emit('saved') se llama al cerrar el modal de credenciales
      } else {
        message.success('Personal registrado');
        emit('saved');
      }
    }
  } catch (e: any) {
    message.error(e?.message ?? 'Error al guardar');
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <NForm label-placement="top" style="max-width:560px">

    <NAlert v-if="tipoActual?.esPersonalMedico" type="info" :show-icon="false"
            style="margin-bottom:16px;font-size:13px">
      Personal médico/asistencial — acceso a: Turnos, Actividades, Permisos y GPS.
    </NAlert>
    <NAlert v-else-if="tipoActual" type="default" :show-icon="false"
            style="margin-bottom:16px;font-size:13px">
      Personal de apoyo — acceso a: Solicitar Permisos.
    </NAlert>

    <!-- DATOS PERSONALES -->
    <div class="sec-title">📋 Datos personales</div>
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:0 14px">
      <NFormItem label="Nombre *">
        <NInput v-model:value="form.nombre" placeholder="Ej: Juan" />
      </NFormItem>
      <NFormItem label="Apellido *">
        <NInput v-model:value="form.apellido" placeholder="Ej: Pérez" />
      </NFormItem>
      <NFormItem label="CI">
        <NInput v-model:value="(form as any).ci" placeholder="1234567" />
      </NFormItem>
      <NFormItem label="Email">
        <NInput v-model:value="(form as any).email" placeholder="correo@ejemplo.com" />
      </NFormItem>
      <NFormItem label="Teléfono">
        <NInput v-model:value="(form as any).telefono" placeholder="71234567" />
      </NFormItem>
    </div>

    <NDivider />

    <!-- ROL EN EL HOSPITAL -->
    <div class="sec-title">👔 Rol en el hospital</div>
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:0 14px">
      <NFormItem label="Tipo de personal *" style="grid-column:1/-1">
        <NSelect v-model:value="form.idTipoPersonal" :options="tipoOpts" placeholder="Seleccionar tipo..." filterable />
      </NFormItem>
      <NFormItem label="Cargo">
        <NInput v-model:value="(form as any).cargo" placeholder="Ej: Médico General" />
      </NFormItem>
      <NFormItem label="Especialidad">
        <NInput v-model:value="(form as any).especialidad" placeholder="Ej: Cardiología" />
      </NFormItem>
      <NFormItem label="Lugar de trabajo" style="grid-column:1/-1">
        <NSelect v-model:value="form.idLugar" :options="lugares" placeholder="Lugar asignado (opcional)" filterable clearable />
      </NFormItem>
    </div>

    <NDivider />

    <!-- CUENTA DE USUARIO -->
    <div class="sec-title">🔐 Cuenta de acceso al sistema</div>

    <!-- Al crear: check para activar -->
    <template v-if="!isEdit">
      <div class="usuario-toggle">
        <NSwitch v-model:value="form.crearUsuario" />
        <span>Crear cuenta de usuario ahora</span>
      </div>

      <template v-if="form.crearUsuario">
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:0 14px;margin-top:10px">
          <NFormItem label="Username *">
            <NInput v-model:value="(form as any).username" placeholder="ej: jperez" />
          </NFormItem>
          <NFormItem label="Contraseña inicial">
            <NInput v-model:value="(form as any).password" type="password" show-password-on="click"
                    placeholder="Vacío = auto-generada" />
          </NFormItem>
          <NFormItem label="Rol de acceso" style="grid-column:1/-1">
            <NSelect v-model:value="form.idRol" :options="roles"
                     placeholder="Inferido del tipo de personal" filterable clearable />
          </NFormItem>
        </div>
        <NAlert type="warning" :show-icon="false" style="font-size:12px;margin-top:4px">
          ⚠ Guarda las credenciales cuando aparezcan — no se podrán recuperar desde aquí.
        </NAlert>
      </template>
    </template>

    <!-- Al editar: info del usuario existente -->
    <template v-else>
      <NAlert
        :type="(props.personal as any)?.tieneUsuario ? 'success' : 'warning'"
        :show-icon="false" style="font-size:12px"
      >
        <template v-if="(props.personal as any)?.tieneUsuario">
          ✓ Cuenta activa — usuario: <strong>{{ (props.personal as any)?.username }}</strong>
        </template>
        <template v-else>
          Sin cuenta de usuario. Asigna una desde Administración → Usuarios.
        </template>
      </NAlert>
    </template>

    <NSpace justify="end" style="margin-top:20px">
      <NButton @click="emit('cancel')">Cancelar</NButton>
      <NButton type="primary" :loading="saving" @click="guardar">
        {{ isEdit ? 'Actualizar' : 'Registrar personal' }}
      </NButton>
    </NSpace>
  </NForm>

  <!-- ══ MODAL DE CREDENCIALES ═════════════════════════════════════════════ -->
  <NModal
    v-model:show="credencial.visible"
    preset="card"
    style="width:460px"
    :mask-closable="false"
    :closable="false"
  >
    <template #header>
      <span style="color:#18a058;font-size:1.05rem">✅ Personal registrado exitosamente</span>
    </template>

    <div style="font-size:.9rem;margin-bottom:16px">
      👤 <strong>{{ credencial.nombre }}</strong>
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
.sec-title { font-weight: 600; margin-bottom: 10px; font-size: .9rem; }
.usuario-toggle {
  display: flex; align-items: center; gap: 10px;
  font-size: .88rem; color: hsl(var(--foreground));
}
.cred-box {
  border: 1.5px solid hsl(var(--border));
  border-radius: 10px;
  overflow: hidden;
}
.cred-row {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 14px;
  border-bottom: 1px solid hsl(var(--border));
}
.cred-row:last-child { border-bottom: none; }
.cred-label {
  font-size: .75rem; font-weight: 700; color: hsl(var(--muted-foreground));
  width: 88px; flex-shrink: 0;
}
.cred-value {
  flex: 1; font-size: .88rem; font-weight: 600;
  background: hsl(var(--muted)); padding: 2px 8px; border-radius: 5px;
  word-break: break-all;
}
</style>
