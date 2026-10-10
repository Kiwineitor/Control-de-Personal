// apps/web-naive/src/api/core/user.ts
import type { UserInfo } from '@vben/types';
import { requestClient } from '#/api/request';

export async function getUserInfoApi() {
  return requestClient.get<UserInfo>('/api/v1/user/info'); // ← agregar /api/v1
}