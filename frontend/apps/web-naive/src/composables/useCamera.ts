import { Capacitor } from '@capacitor/core';

export function useCamera() {

  /**
   * Retorna un File listo para subir.
   * En APK: abre cámara o galería nativa.
   * En web: retorna null (el componente usa su file input normal).
   */
  async function capturarImagen(): Promise<File | null> {
    if (!Capacitor.isNativePlatform()) return null;

    try {
      const { Camera, CameraResultType, CameraSource } = await import('@capacitor/camera');

      const image = await Camera.getPhoto({
        quality          : 85,
        allowEditing     : false,
        resultType       : CameraResultType.Base64,
        source           : CameraSource.Prompt,   // pregunta: cámara o galería
        presentationStyle: 'popover',
      });

      if (!image.base64String) return null;

      // Convertir base64 → File
      const byteString   = atob(image.base64String);
      const arrayBuffer  = new ArrayBuffer(byteString.length);
      const uint8Array   = new Uint8Array(arrayBuffer);
      for (let i = 0; i < byteString.length; i++)
        uint8Array[i] = byteString.charCodeAt(i);

      const mimeType = `image/${image.format ?? 'jpeg'}`;
      const blob     = new Blob([arrayBuffer], { type: mimeType });
      return new File([blob], `foto_${Date.now()}.${image.format ?? 'jpg'}`, { type: mimeType });

    } catch (error: any) {
      if (error?.message?.includes('cancelled')) return null; // usuario canceló
      console.error('Camera error:', error);
      return null;
    }
  }

  return {
    isNative: Capacitor.isNativePlatform(),
    capturarImagen,
  };
}