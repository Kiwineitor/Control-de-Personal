<script lang="ts" setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import {
  NAlert, NButton, NDivider, NForm, NFormItem,
  NInput, NModal, NSelect, NSwitch, NTag, NTabs, NTabPane,
} from 'naive-ui';
import { message } from '#/adapter/naive';
import {
  crearUsuarioApi, actualizarUsuarioApi,
  getRolesActivosApi, getPersonasParaUsuarioApi,
  type RolResponse, type UsuarioResponse, type PersonaParaUsuario,
} from '#/api/admin/configuracion';

const props = defineProps<{ show: boolean; usuario: UsuarioResponse | null }>();
const emit  = defineEmits<{
  (e: 'update:show', val: boolean): void;
  (e: 'saved'): void;
}>();

const loading       = ref(false);
const esEdicion     = ref(false);
const mostrarPass   = ref(false);
const cambiarPass   = ref(false);
const modoPersona   = ref<'existente' | 'nueva'>('existente');

const rolesOpciones    = ref<{ label: string; value: number }[]>([]);
const personasLista    = ref<PersonaParaUsuario[]>([]);

// ── Formulario ─────────────────────────────────────────────────────────
const form = reactive({
  username:  '',
  password:  '',
  idRol:     null as number | null,
  activo:    true,
  // Persona existente
  idPersona: null as number | null,
  // Nueva persona
  pNombre:   '',
  pApellido: '',
  pCi:       '',
  pEmail:    '',
  pTelefono: '',
});

// ── Opciones del select de personas ───────────────────────────────────
const personasOpciones = computed(() =>
  personasLista.value.map(p => ({
    label:    p.nombreCompleto,
    value:    p.idPersona,
    disabled: p.tieneUsuario && p.idPersona !== props.usuario?.idPersona,
  }))
);

// Persona seleccionada actualmente para mostrar su badge
const personaSeleccionada = computed(() =>
  personasLista.value.find(p => p.idPersona === form.idPersona) ?? null
);

// ── Cargar datos ───────────────────────────────────────────────────────
async function cargarDatos() {
  try {
    const [rolesRes, personasRes] = await Promise.all([
      getRolesActivosApi(),
      getPersonasParaUsuarioApi(),
    ]);
    const roles: RolResponse[] = (rolesRes as any)?.data ?? rolesRes ?? [];
    rolesOpciones.value = roles.map(r => ({ label: r.nombre, value: r.idRol }));

    const personas: PersonaParaUsuario[] = (personasRes as any)?.data ?? personasRes ?? [];
    personasLista.value = personas;
  } catch {
    message.error('Error al cargar datos del formulario');
  }
}

onMounted(cargarDatos);

// ── Watch: abrir modal ─────────────────────────────────────────────────
watch(() => props.show, (val) => {
  if (!val) return;
  resetForm();

  if (props.usuario) {
    esEdicion.value    = true;
    form.username      = props.usuario.username;
    form.idRol         = props.usuario.idRol ?? null;
    form.activo        = props.usuario.activo;
    form.idPersona     = props.usuario.idPersona ?? null;
    modoPersona.value  = form.idPersona ? 'existente' : 'nueva';
    cambiarPass.value  = false;
  }
});

function resetForm() {
  esEdicion.value   = false;
  cambiarPass.value = false;
  mostrarPass.value = false;
  modoPersona.value = 'existente';
  form.username  = '';
  form.password  = '';
  form.idRol     = null;
  form.activo    = true;
  form.idPersona = null;
  form.pNombre   = '';
  form.pApellido = '';
  form.pCi       = '';
  form.pEmail    = '';
  form.pTelefono = '';
}

// ── Validación ─────────────────────────────────────────────────────────
function validar(): boolean {
  if (!form.username.trim()) {
    message.warning('El username es obligatorio'); return false;
  }
  if (!esEdicion.value && !form.password.trim()) {
    message.warning('La contraseña es obligatoria al crear un usuario'); return false;
  }
  if (esEdicion.value && cambiarPass.value && form.password.length < 6) {
    message.warning('La contraseña debe tener al menos 6 caracteres'); return false;
  }
  if (!form.idRol) {
    message.warning('Debes asignar un rol al usuario'); return false;
  }
  if (modoPersona.value === 'existente' && !form.idPersona) {
    message.warning('Selecciona una persona'); return false;
  }
  if (modoPersona.value === 'nueva') {
    if (!form.pNombre.trim()) { message.warning('El nombre de la persona es obligatorio'); return false; }
    if (!form.pApellido.trim()) { message.warning('El apellido de la persona es obligatorio'); return false; }
  }
  return true;
}

// ── Guardar ────────────────────────────────────────────────────────────
async function guardar() {
  if (!validar()) return;

  loading.value = true;
  try {
    const payload: any = {
      username: form.username.trim(),
      idRol:    form.idRol,
      activo:   form.activo,
    };

    if (modoPersona.value === 'existente') {
      payload.idPersona = form.idPersona;
    } else {
      payload.personaNueva = {
        nombre:    form.pNombre.trim(),
        apellido:  form.pApellido.trim(),
        ci:        form.pCi.trim()       || undefined,
        email:     form.pEmail.trim()    || undefined,
        telefono:  form.pTelefono.trim() || undefined,
      };
    }

    if (!esEdicion.value || (esEdicion.value && cambiarPass.value)) {
      payload.password = form.password;
    }

    if (esEdicion.value && props.usuario) {
      await actualizarUsuarioApi(props.usuario.idUsuario, payload);
      message.success('Usuario actualizado correctamente');
    } else {
      await crearUsuarioApi(payload);
      message.success('Usuario creado correctamente');
    }

    emit('saved');
    emit('update:show', false);
  } catch (err: any) {
    message.error(err?.response?.data?.message ?? 'Error al guardar el usuario');
  } finally {
    loading.value = false;
  }
}

// ── Render helper para opciones del select de personas ─────────────────
function renderPersonaLabel(option: any) {
  const p = personasLista.value.find(x => x.idPersona === option.value);
  if (!p) return option.label;
  return p.nombreCompleto + (p.ci ? ` — CI: ${p.ci}` : '');
}
</script>

<template>
  <NModal
    :show="show"
    preset="card"
    :title="esEdicion ? 'Editar Usuario' : 'Nuevo Usuario'"
    style="width: 600px"
    :mask-closable="false"
    @update:show="$emit('update:show', $event)"
  >
    <NForm label-placement="top">

      <!-- ── Persona vinculada ─────────────────────────────────────── -->
      <NDivider>
        <span class="text-xs text-gray-400">Persona vinculada</span>
      </NDivider>

      <NTabs v-model:value="modoPersona" type="segment" size="small" class="mb-4">
        <NTabPane name="existente" tab="Seleccionar persona existente" />
        <NTabPane name="nueva"     tab="Crear nueva persona" />
      </NTabs>

      <!-- Seleccionar persona existente -->
      <template v-if="modoPersona === 'existente'">
        <NFormItem label="Persona *">
          <NSelect
            v-model:value="form.idPersona"
            placeholder="Buscar persona por nombre..."
            filterable
            :options="personasOpciones"
            :render-label="renderPersonaLabel"
            clearable
          />
        </NFormItem>

        <!-- Badge de estado de la persona seleccionada -->
        <div v-if="personaSeleccionada" class="mb-3 flex flex-wrap gap-2 text-xs text-gray-500">
          <span v-if="personaSeleccionada.ci">CI: <strong>{{ personaSeleccionada.ci }}</strong></span>
          <span v-if="personaSeleccionada.email">Email: <strong>{{ personaSeleccionada.email }}</strong></span>
          <NTag v-if="personaSeleccionada.tieneUsuario" type="warning" size="small">
            Ya tiene usuario {{ personaSeleccionada.usuarioActivo ? '(activo)' : '(inactivo)' }}
          </NTag>
          <NTag v-else type="success" size="small">Sin usuario</NTag>
        </div>
      </template>

      <!-- Crear nueva persona -->
      <template v-else>
        <div class="grid grid-cols-2 gap-3">
          <NFormItem label="Nombre *">
            <NInput v-model:value="form.pNombre" placeholder="Nombres" maxlength="100" />
          </NFormItem>
          <NFormItem label="Apellido *">
            <NInput v-model:value="form.pApellido" placeholder="Apellidos" maxlength="100" />
          </NFormItem>
          <NFormItem label="CI">
            <NInput v-model:value="form.pCi" placeholder="Cédula de identidad" maxlength="20" />
          </NFormItem>
          <NFormItem label="Teléfono">
            <NInput v-model:value="form.pTelefono" placeholder="Ej: 70012345" maxlength="20" />
          </NFormItem>
          <NFormItem label="Email" class="col-span-2">
            <NInput v-model:value="form.pEmail" placeholder="correo@hospital.com" maxlength="150" />
          </NFormItem>
        </div>
      </template>

      <!-- ── Credenciales de acceso ────────────────────────────────── -->
      <NDivider>
        <span class="text-xs text-gray-400">Credenciales de acceso</span>
      </NDivider>

      <div class="grid grid-cols-2 gap-3">
        <NFormItem label="Username *">
          <NInput
            v-model:value="form.username"
            placeholder="Ej: jperez o CI: 8394821"
            maxlength="50"
            :disabled="esEdicion"
          />
        </NFormItem>

        <NFormItem label="Rol *">
          <NSelect
            v-model:value="form.idRol"
            placeholder="Seleccionar rol"
            :options="rolesOpciones"
          />
        </NFormItem>
      </div>

      <!-- Contraseña -->
      <template v-if="!esEdicion">
        <NFormItem label="Contraseña *">
          <NInput
            v-model:value="form.password"
            :type="mostrarPass ? 'text' : 'password'"
            placeholder="Mínimo 6 caracteres"
          >
            <template #suffix>
              <span class="cursor-pointer text-gray-400" @click="mostrarPass = !mostrarPass">
                {{ mostrarPass ? '🙈' : '👁' }}
              </span>
            </template>
          </NInput>
        </NFormItem>
      </template>

      <template v-else>
        <div class="mb-3 flex items-center gap-2">
          <NSwitch v-model:value="cambiarPass" size="small" />
          <span class="text-sm text-gray-500">Cambiar contraseña</span>
        </div>
        <NFormItem v-if="cambiarPass" label="Nueva contraseña">
          <NInput
            v-model:value="form.password"
            :type="mostrarPass ? 'text' : 'password'"
            placeholder="Nueva contraseña (mín. 6 caracteres)"
          >
            <template #suffix>
              <span class="cursor-pointer text-gray-400" @click="mostrarPass = !mostrarPass">
                {{ mostrarPass ? '🙈' : '👁' }}
              </span>
            </template>
          </NInput>
        </NFormItem>
      </template>

      <!-- Estado -->
      <NFormItem label="Estado">
        <div class="flex items-center gap-2">
          <NSwitch v-model:value="form.activo" />
          <span class="text-sm">{{ form.activo ? 'Activo' : 'Inactivo' }}</span>
        </div>
      </NFormItem>

      <NAlert v-if="modoPersona === 'nueva'" type="info" class="mt-2" :show-icon="false">
        <span class="text-xs">
          Se creará un nuevo registro de persona con los datos ingresados.
        </span>
      </NAlert>

    </NForm>

    <div class="mt-4 flex justify-end gap-2">
      <NButton @click="$emit('update:show', false)">Cancelar</NButton>
      <NButton type="primary" :loading="loading" @click="guardar">
        {{ esEdicion ? 'Actualizar' : 'Crear usuario' }}
      </NButton>
    </div>
  </NModal>
</template>
