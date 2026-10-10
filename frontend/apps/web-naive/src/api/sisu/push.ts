import { requestClient } from '#/api/request';

export interface PushSubscripcionRequest {
  endpoint:  string;
  p256dh:    string;
  auth:      string;
  userAgent?: string;
  /** "WEBPUSH" (navegador) o "FCM" (app nativa Capacitor). */
  platform?: string;
}

export interface VapidPublicKeyResponse {
  publicKey: string;
  enabled:   boolean;
}

export async function getPushPublicKey(): Promise<VapidPublicKeyResponse> {
  return requestClient.get<VapidPublicKeyResponse>('/api/v1/push/public-key');
}

export async function subscribePush(data: PushSubscripcionRequest): Promise<void> {
  return requestClient.post('/api/v1/push/subscribe', data);
}

export async function unsubscribePush(endpoint: string): Promise<void> {
  return requestClient.delete('/api/v1/push/subscribe', { params: { endpoint } });
}
