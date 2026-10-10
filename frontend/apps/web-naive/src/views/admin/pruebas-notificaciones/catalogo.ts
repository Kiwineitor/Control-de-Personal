/** Canales comprobados en NotificacionServiceImpl y los detectores de monitoreo. */
export const catalogoNotificaciones = [
  {
    nombre: 'GPS no activado al iniciar turno',
    telegram: true,
    detalle: 'El turno comenzó y todavía no hay ubicaciones.',
  },
  {
    nombre: 'Señal perdida',
    telegram: true,
    detalle: 'Dejó de reportar ubicación. No implica que salió del área.',
  },
  {
    nombre: 'Señal recuperada',
    telegram: true,
    detalle: 'Vuelve a reportar después de una pérdida avisada.',
  },
  {
    nombre: 'Falta: no se presentó',
    telegram: true,
    detalle: 'No hubo ubicaciones desde el inicio del turno.',
  },
  {
    nombre: 'Atraso',
    telegram: true,
    detalle: 'La primera llegada supera la tolerancia configurada.',
  },
  {
    nombre: 'Abandono de área',
    telegram: true,
    detalle: 'Permanece fuera del área más allá del umbral.',
  },
  {
    nombre: 'Regreso al área',
    telegram: true,
    detalle: 'Regresa después de un abandono ya notificado.',
  },
  {
    nombre: 'Ausencia por pérdida de señal',
    telegram: true,
    detalle: 'El corte de señal supera el umbral de ausencia.',
  },
  {
    nombre: 'Cierre de sesión en turno',
    telegram: true,
    detalle: 'Cierra sesión durante el turno.',
  },
  {
    nombre: 'Permisos: solicitud nueva o modificada',
    telegram: false,
    detalle: 'Avisos internos a los responsables.',
  },
  {
    nombre: 'Permisos: observado, aprobado o rechazado',
    telegram: false,
    detalle: 'Avisos al solicitante en SICOPE.',
  },
  {
    nombre: 'Justificación pendiente, aprobada o rechazada',
    telegram: false,
    detalle: 'Avisos a revisores o al funcionario en SICOPE.',
  },
  {
    nombre: 'Recordatorio y fin de turno',
    telegram: false,
    detalle: 'Avisos al funcionario en SICOPE y push según disponibilidad.',
  },
  {
    nombre: 'Jornada asignada o actualizada',
    telegram: false,
    detalle: 'Avisos al personal asignado.',
  },
  {
    nombre: 'Horario de interno o actividad asignada',
    telegram: false,
    detalle: 'Avisos al destinatario en SICOPE.',
  },
  {
    nombre: 'GPS denegado y avisos personales de incidencia/ausencia',
    telegram: false,
    detalle:
      'Avisos al funcionario; Telegram utiliza los avisos de supervisión anteriores.',
  },
  {
    nombre: 'Mensaje personalizado',
    telegram: false,
    detalle:
      'Normalmente solo SICOPE. Este módulo permite probar también el grupo.',
  },
];
