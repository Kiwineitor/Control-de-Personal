<script setup lang="ts">
import type { Recordable } from '@vben/types';

import type { VbenFormSchema } from '@vben-core/form-ui';

import { computed, onMounted, reactive } from 'vue';

import { useVbenForm } from '@vben-core/form-ui';
import { VbenButton } from '@vben-core/shadcn-ui';

import Title from './auth-title.vue';

interface Props {
  formSchema?: VbenFormSchema[];
  loading?: boolean;
  submitButtonText?: string;
  subTitle?: string;
  title?: string;
}

defineOptions({
  name: 'AuthenticationLogin',
});

const props = withDefaults(defineProps<Props>(), {
  formSchema: () => [],
  loading: false,
  submitButtonText: 'Iniciar sesión',
  subTitle: 'Ingresa tus credenciales para acceder al sistema',
  title: 'Bienvenido',
});

const emit = defineEmits<{
  submit: [Recordable<any>];
}>();

const [Form, formApi] = useVbenForm(
  reactive({
    commonConfig: {
      hideLabel: true,
      hideRequiredMark: true,
    },
    schema: computed(() => props.formSchema),
    showDefaultActions: false,
  }),
);

async function handleSubmit() {
  const { valid } = await formApi.validate();
  const values = await formApi.getValues();
  if (valid) {
    emit('submit', values);
  }
}

onMounted(() => {});

defineExpose({
  getFormApi: () => formApi,
});
</script>

<template>
  <div @keydown.enter.prevent="handleSubmit">
    <slot name="title">
      <Title>
        <slot name="title">
          {{ title }}
        </slot>
        <template #desc>
          <span class="text-muted-foreground">
            <slot name="subTitle">
              {{ subTitle }}
            </slot>
          </span>
        </template>
      </Title>
    </slot>

    <Form />

    <VbenButton
      :class="{ 'cursor-wait': loading }"
      :loading="loading"
      aria-label="login"
      class="mt-2 w-full"
      @click="handleSubmit"
    >
      {{ submitButtonText }}
    </VbenButton>
  </div>
</template>
