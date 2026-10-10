<script setup lang="ts">
import { ref } from 'vue';

import { Page } from '@vben/common-ui';

import { NDataTable } from 'naive-ui';

const columns = ref([
  {
    key: 'no',
    title: 'No',
  },
  {
    key: 'title',
    title: 'Title',
  },
  {
    key: 'length',
    title: 'Length',
  },
]);
const data = [
  { length: '4:18', no: 3, title: 'Wonderwall' },
  { length: '4:48', no: 4, title: "Don't Look Back in Anger" },
  { length: '7:27', no: 12, title: 'Champagne Supernova' },
];
</script>

<template>
  <Page
    description="La página de formulario se utiliza para recopilar o verificar información del usuario. Los formularios básicos son comunes en escenarios con pocos elementos de datos."
    title="NDataTable"
  >
    <NDataTable :columns="columns" :data="data" />
  </Page>
</template>

<style scoped></style>
