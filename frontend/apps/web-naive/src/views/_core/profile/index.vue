<script setup lang="ts">
import { ref } from 'vue';

import { Profile } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import ProfileBase from './modules/base-setting.vue';
import ProfileNotificationSetting from './modules/notification-setting.vue';
import ProfilePasswordSetting from './modules/password-setting.vue';
import ProfileSecuritySetting from './modules/security-setting.vue';

const userStore = useUserStore();

const tabsValue = ref<string>('basic');

const tabs = ref([
  {
    label: 'Datos personales',
    value: 'basic',
  },
  {
    label: 'Seguridad',
    value: 'security',
  },
  {
    label: 'Cambiar contraseña',
    value: 'password',
  },
  {
    label: 'Notificaciones',
    value: 'notice',
  },
]);
</script>
<template>
  <Profile
    v-model:model-value="tabsValue"
    title="Mi Perfil"
    :user-info="userStore.userInfo"
    :tabs="tabs"
  >
    <template #content>
      <ProfileBase v-if="tabsValue === 'basic'" />
      <ProfileSecuritySetting v-if="tabsValue === 'security'" />
      <ProfilePasswordSetting v-if="tabsValue === 'password'" />
      <ProfileNotificationSetting v-if="tabsValue === 'notice'" />
    </template>
  </Profile>
</template>
