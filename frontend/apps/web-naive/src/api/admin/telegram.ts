import { requestClient } from '#/api/request';

export interface TelegramEstado {
  habilitado: boolean;
  chatId: null | string;
  origen: 'SERVIDOR' | 'SISTEMA';
  botConfigurado: boolean;
  activadoEnServidor: boolean;
}

export interface TelegramPruebaRequest {
  tipo: 'PERSONALIZADO' | 'SENAL_PERDIDA';
  idUsuario: null | number;
  mensaje: string;
  enviarEnSicope: boolean;
}

export interface TelegramPruebaResultado {
  telegram: {
    chatId: null | string;
    chatTitulo: null | string;
    detalle: string;
    estado: 'CONFIRMADO' | 'DESHABILITADO' | 'FALLIDO' | 'INCIERTO';
    messageId: null | number;
  };
  texto: string;
  sicope: 'NO_CONFIRMADO' | 'NO_SOLICITADO' | 'REGISTRADO';
  idNotificacion: null | number;
}

export const getTelegramEstado = () =>
  requestClient.get<TelegramEstado>('/api/v1/telegram/admin/estado');

export const guardarTelegramConfiguracion = (chatId: string) =>
  requestClient.put<TelegramEstado>('/api/v1/telegram/admin/configuracion', {
    chatId,
  });

export const enviarPruebaTelegram = (data: TelegramPruebaRequest) =>
  requestClient.post<TelegramPruebaResultado>(
    '/api/v1/telegram/admin/pruebas',
    data,
  );

export interface TelegramPlantilla {
  tipo: string;
  nombre: string;
  texto: string;
  automatico: boolean;
}

export type TelegramValores = Record<
  'contexto' | 'detalle' | 'dispositivo' | 'momento' | 'nombre',
  string
>;

export const getTelegramPlantillas = () =>
  requestClient.get<TelegramPlantilla[]>('/api/v1/telegram/admin/plantillas');

export const guardarTelegramPlantilla = (
  tipo: string,
  texto: string,
  automatico: boolean,
) =>
  requestClient.put<TelegramPlantilla>(
    `/api/v1/telegram/admin/plantillas/${tipo}`,
    { texto, automatico },
  );

export const previsualizarTelegram = (
  texto: string,
  valores: TelegramValores,
) =>
  requestClient.post<string>(
    '/api/v1/telegram/admin/plantillas/previsualizar',
    { texto, valores },
  );

export const enviarTelegramManual = (tipo: string, valores: TelegramValores, textoEsperado: string) =>
  requestClient.post<TelegramPruebaResultado['telegram']>(
    `/api/v1/telegram/admin/plantillas/${tipo}/enviar`,
    { valores, textoEsperado },
  );
