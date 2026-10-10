<script lang="ts" setup>
import { Page, useVbenModal } from '@vben/common-ui';

import { NButton, NCard, useMessage } from 'naive-ui';

import { useVbenForm } from '#/adapter/form';
import { getAllMenusApi } from '#/api';

import modalDemo from './modal.vue';

const message = useMessage();

const [Form, formApi] = useVbenForm({
  commonConfig: {
    // Todos los elementos del formulario
    componentProps: {
      class: 'w-full',
    },
  },
  layout: 'vertical',
  // Pantalla grande: 3 por fila, pantalla mediana: 2 por fila, pantalla pequeña: 1 por fila
  wrapperClass: 'grid-cols-1 md:grid-cols-2 lg:grid-cols-3',
  handleSubmit: (values) => {
    message.success(`Datos del formulario: ${JSON.stringify(values)}`);
  },
  schema: [
    {
      // El componente debe registrarse en #/adapter.ts con su tipo correspondiente
      component: 'ApiSelect',
      // Parámetros del componente
      componentProps: {
        // Convertir la interfaz de menú a formato de opciones (options)
        afterFetch: (data: { name: string; path: string }[]) => {
          return data.map((item: any) => ({
            label: item.name,
            value: item.path,
          }));
        },
        // API de menús
        api: getAllMenusApi,
      },
      // Nombre del campo
      fieldName: 'api',
      // Etiqueta mostrada en la interfaz
      label: 'ApiSelect',
      rules: 'required',
    },
    {
      component: 'ApiTreeSelect',
      // Parámetros del componente
      componentProps: {
        // API de menús
        api: getAllMenusApi,
        childrenField: 'children',
        // Convertir la interfaz de menú a formato de opciones (options)
        labelField: 'name',
        valueField: 'path',
      },
      // Nombre del campo
      fieldName: 'apiTree',
      // Etiqueta mostrada en la interfaz
      label: 'ApiTreeSelect',
      rules: 'required',
    },
    {
      component: 'Input',
      fieldName: 'string',
      label: 'Texto (String)',
      rules: 'required',
    },
    {
      component: 'InputNumber',
      fieldName: 'number',
      label: 'Número (Number)',
      rules: 'required',
    },
    {
      component: 'RadioGroup',
      fieldName: 'radio',
      label: 'Radio',
      componentProps: {
        options: [
          { value: 'A', label: 'A' },
          { value: 'B', label: 'B' },
          { value: 'C', label: 'C' },
          { value: 'D', label: 'D' },
          { value: 'E', label: 'E' },
        ],
      },
      rules: 'selectRequired',
    },
    {
      component: 'RadioGroup',
      fieldName: 'radioButton',
      label: 'Botón de Radio',
      componentProps: {
        isButton: true,
        class: 'flex flex-wrap', // Si hay demasiadas opciones, añadir clase para auto-ajuste
        options: [
          { value: 'A', label: 'Opción A' },
          { value: 'B', label: 'Opción B' },
          { value: 'C', label: 'Opción C' },
          { value: 'D', label: 'Opción D' },
          { value: 'E', label: 'Opción E' },
        ],
      },
      rules: 'selectRequired',
    },
    {
      component: 'CheckboxGroup',
      fieldName: 'checkbox',
      label: 'Checkbox',
      componentProps: {
        options: [
          { value: 'A', label: 'Opción A' },
          { value: 'B', label: 'Opción B' },
          { value: 'C', label: 'Opción C' },
        ],
      },
      rules: 'selectRequired',
    },
    {
      component: 'DatePicker',
      fieldName: 'date',
      label: 'Fecha',
      rules: 'required',
    },
    {
      component: 'Input',
      fieldName: 'textArea',
      label: 'Área de Texto',
      componentProps: {
        type: 'textarea',
      },
      rules: 'required',
    },
    {
      component: 'Input',
      fieldName: 'collapsibleTextArea',
      label: 'Plegable en modo vertical',
      componentProps: {
        type: 'textarea',
      },
      collapsible: true,
    },
  ],
});

function setFormValues() {
  formApi.setValues({
    string: 'texto',
    number: 123,
    radio: 'B',
    radioButton: 'C',
    checkbox: ['A', 'C'],
    date: Date.now(),
  });
}

const [Modal, modalApi] = useVbenModal({
  connectedComponent: modalDemo,
});
</script>

<template>
  <Page
    description="El adaptador de formulario encapsula CheckboxGroup y RadioGroup; los datos de las opciones pueden pasarse mediante la propiedad 'options' (los datos se pasarán como propiedades de los subcomponentes)."
    title="Demostración de Formulario"
  >
    <NCard title="Formulario Básico" header-extra-class="gap-4">
      <template #header-extra>
        <NButton type="primary" @click="setFormValues">Rellenar Formulario</NButton>
        <NButton type="primary" @click="modalApi.open()" class="ml-2">
          Abrir Ventana Modal
        </NButton>
      </template>
      <Form />
    </NCard>
    <Modal />
  </Page>
</template>