<script setup lang="ts">
import type {
  TelegramPlantilla,
  TelegramPruebaResultado,
  TelegramValores,
} from '#/api/admin/telegram';

import { computed, onMounted, reactive, ref } from 'vue';

import {
  NAlert,
  NButton,
  NCard,
  NFormItem,
  NInput,
  NSelect,
  NSwitch,
} from 'naive-ui';

import {
  enviarTelegramManual,
  getTelegramPlantillas,
  guardarTelegramPlantilla,
  previsualizarTelegram,
} from '#/api/admin/telegram';

const props = defineProps<{ chatId: null | string; habilitado: boolean }>();
const lista = ref<TelegramPlantilla[]>([]);
const tipo = ref<string>();
const texto = ref('');
const automatico = ref(true);
const ocupado = ref(false);
const error = ref('');
const aviso = ref('');
const preview = ref('');
const firmaPreview = ref('');
const resultado = ref<null | TelegramPruebaResultado['telegram']>(null);
const valores = reactive<TelegramValores>({
  nombre: '',
  contexto: '',
  momento: '',
  dispositivo: '',
  detalle: '',
});
const campos: { clave: keyof TelegramValores; etiqueta: string }[] = [
  { clave: 'nombre', etiqueta: 'Nombre del funcionario' },
  { clave: 'contexto', etiqueta: 'Área y turno' },
  { clave: 'momento', etiqueta: 'Fecha, hora o duración' },
  { clave: 'dispositivo', etiqueta: 'Batería y conexión' },
  { clave: 'detalle', etiqueta: 'Descripción del hecho' },
];
const seleccionada = computed(() =>
  lista.value.find((p) => p.tipo === tipo.value),
);
const cambios = computed(
  () =>
    seleccionada.value &&
    (texto.value !== seleccionada.value.texto ||
      automatico.value !== seleccionada.value.automatico),
);
const firma = computed(() =>
  JSON.stringify({
    tipo: tipo.value,
    texto: texto.value,
    valores,
    chatId: props.chatId,
  }),
);
const previewActual = computed(
  () => preview.value !== '' && firmaPreview.value === firma.value,
);
const opciones = computed(() =>
  lista.value.map((p) => ({
    label: `${p.nombre} · ${p.automatico ? 'Automático' : 'Solo manual'}`,
    value: p.tipo,
  })),
);

function seleccionar(value: string) {
  tipo.value = value;
  const p = lista.value.find((item) => item.tipo === value);
  texto.value = p?.texto ?? '';
  automatico.value = p?.automatico ?? true;
  preview.value = '';
  resultado.value = null;
  aviso.value = '';
  error.value = '';
}
async function cargar() {
  ocupado.value = true;
  error.value = '';
  try {
    lista.value = await getTelegramPlantillas();
    const primera = lista.value[0];
    if (primera) seleccionar(tipo.value ?? primera.tipo);
  } catch {
    error.value = 'No se pudieron cargar los mensajes. Vuelve a intentar.';
  } finally {
    ocupado.value = false;
  }
}
async function guardar() {
  if (!tipo.value || ocupado.value) return;
  ocupado.value = true;
  error.value = '';
  aviso.value = '';
  try {
    const saved = await guardarTelegramPlantilla(
      tipo.value,
      texto.value,
      automatico.value,
    );
    lista.value = lista.value.map((p) => (p.tipo === saved.tipo ? saved : p));
    texto.value = saved.texto;
    aviso.value =
      'Texto y modo guardados. Se aplicarán a los próximos avisos de este tipo.';
    preview.value = '';
  } catch {
    error.value =
      'No se confirmó el guardado. Revisa el error y vuelve a cargar para comprobar la configuración.';
  } finally {
    ocupado.value = false;
  }
}
async function previsualizar() {
  ocupado.value = true;
  error.value = '';
  resultado.value = null;
  try {
    preview.value = await previsualizarTelegram(texto.value, { ...valores });
    firmaPreview.value = firma.value;
  } catch {
    preview.value = '';
    error.value =
      'No se pudo preparar el mensaje. Revisa el texto y las variables.';
  } finally {
    ocupado.value = false;
  }
}
async function enviar() {
  if (
    !tipo.value ||
    ocupado.value ||
    cambios.value ||
    !previewActual.value ||
    !props.habilitado
  )
    return;
  ocupado.value = true;
  error.value = '';
  resultado.value = null;
  try {
    resultado.value = await enviarTelegramManual(tipo.value, { ...valores }, preview.value);
  } catch {
    error.value =
      'No se pudo confirmar el envío. Comprueba Telegram antes de repetir: el mensaje pudo llegar.';
  } finally {
    ocupado.value = false;
  }
}
onMounted(cargar);
</script>

<template>
  <div class="grid gap-4 lg:grid-cols-2">
    <NCard title="Gestionar mensajes de Telegram">
      <NAlert type="info" class="mb-4">
        Edita el texto completo y elige el modo de cada aviso. “Solo manual”
        impide el envío automático a Telegram; las incidencias y notificaciones
        internas de SICOPE siguen funcionando. No se crea una cola de avisos
        pendientes.
      </NAlert>
      <NFormItem label="Tipo de notificación">
        <NSelect
          :value="tipo"
          :options="opciones"
          :disabled="ocupado || !!cambios"
          @update:value="seleccionar"
        />
      </NFormItem>
      <p v-if="tipo === 'SALIDA_TEMPRANA'" class="mb-3 text-sm">
        Salida temprana tiene plantilla, pero todavía no tiene detector
        automático.
      </p>
      <NFormItem label="Envío automático a Telegram">
        <NSwitch
          v-model:value="automatico"
          :disabled="ocupado || !seleccionada"
        >
          <template #checked>Automático</template><template #unchecked>Solo manual</template>
        </NSwitch>
      </NFormItem>
      <NFormItem label="Texto de la notificación (texto plano)">
        <NInput
          v-model:value="texto"
          type="textarea"
          :maxlength="3000"
          show-count
          :autosize="{ minRows: 9, maxRows: 18 }"
          :disabled="ocupado || !seleccionada"
        />
      </NFormItem>
      <p class="mb-3 text-sm">
        Variables disponibles: <code>{nombre}</code>, <code>{contexto}</code>,
        <code>{momento}</code>, <code>{dispositivo}</code> y
        <code>{detalle}</code>. En las alertas automáticas se reemplazan por los
        datos del hecho; puedes omitirlas o cambiar todo el texto.
      </p>
      <div class="flex flex-wrap gap-2">
        <NButton
          type="primary"
          :loading="ocupado"
          :disabled="!cambios || !texto.trim() || ocupado"
          @click="guardar"
          >
Guardar texto y modo
</NButton>
        <NButton :disabled="ocupado" @click="cargar">
{{
          cambios ? 'Descartar cambios y recargar' : 'Recargar mensajes'
        }}
</NButton>
      </div>
      <p v-if="cambios" class="mt-3 text-sm">
        Guarda o descarta los cambios antes de cambiar de tipo o enviar
        manualmente.
      </p>
      <NAlert v-if="aviso" class="mt-3" type="success">{{ aviso }}</NAlert>
      <NAlert v-if="error" class="mt-3" type="warning">{{ error }}</NAlert>
    </NCard>
    <NCard title="Vista previa y envío manual">
      <p class="mb-4">
        Grupo de destino: <strong>{{ chatId || 'Sin configurar' }}</strong>. Completa los datos que quieras incluir. Este envío es un mensaje real
        al grupo, sin prefijo de prueba.
      </p>
      <NFormItem
        v-for="campo in campos"
        :key="campo.clave"
        :label="campo.etiqueta"
      >
        <NInput
          v-model:value="valores[campo.clave]"
          :type="campo.clave === 'detalle' ? 'textarea' : 'text'"
          :maxlength="1000"
          :disabled="ocupado"
        />
      </NFormItem>
      <NButton :disabled="ocupado || !texto.trim()" @click="previsualizar">
Previsualizar con estos datos
</NButton>
      <pre
        v-if="previewActual"
        class="my-4 whitespace-pre-wrap break-words rounded bg-gray-100 p-3 dark:bg-gray-800"
        >{{ preview }}</pre>
      <p v-else class="my-3 text-sm">
        Genera la vista previa antes de enviar. No se envía nada al
        previsualizar o guardar.
      </p>
      <NButton
        type="primary"
        :loading="ocupado"
        :disabled="ocupado || !!cambios || !previewActual || !habilitado"
        @click="enviar"
        >
Enviar ahora al grupo
</NButton>
      <NAlert v-if="!habilitado" type="warning" class="mt-3">
Revisa el grupo y el estado del bot en “Configuración y pruebas” antes
        de enviar.
</NAlert>
      <NAlert
        v-if="resultado"
        class="mt-3"
        :type="resultado.estado === 'CONFIRMADO' ? 'success' : 'warning'"
      >
        {{ resultado.estado }}: {{ resultado.detalle }} Grupo:
        {{ resultado.chatId }}. Mensaje:
        {{ resultado.messageId ?? 'Sin confirmar' }}.
      </NAlert>
    </NCard>
  </div>
</template>
