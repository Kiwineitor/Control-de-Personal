<script setup lang="ts">
import type { VbenFormSchema } from '#/adapter/form';

import { computed, onMounted, ref } from 'vue';

import { ProfileBaseSetting } from '@vben/common-ui';

import { getUserInfoApi } from '#/api';

const profileBaseSettingRef = ref();

const formSchema = computed((): VbenFormSchema[] => {
  return [
    {
      fieldName: 'userId',
      component: 'Input',
      label: 'ID de usuario',
      componentProps: {
        disabled: true,
        class: 'w-full',
      },
    },
    {
      fieldName: 'realName',
      component: 'Input',
      label: 'Nombre completo',
    },
    {
      fieldName: 'username',
      component: 'Input',
      label: 'Usuario',
      componentProps: {
        disabled: true,
      },
    },
    {
      fieldName: 'roles',
      component: 'Input',
      label: 'Roles',
      componentProps: {
        disabled: true,
      },
    },
    {
      fieldName: 'desc',
      component: 'Textarea',
      label: 'Descripción',
    },
  ];
});

onMounted(async () => {
  const data = await getUserInfoApi();
  const values = {
    ...data,
    roles: Array.isArray(data.roles) ? data.roles.join(', ') : (data.roles ?? ''),
  };
  profileBaseSettingRef.value.getFormApi().setValues(values);
});
</script>
<template>
  <ProfileBaseSetting ref="profileBaseSettingRef" :form-schema="formSchema" />
</template>
