# Gestión de notificaciones y Telegram

El módulo está en **Configuración del Sistema → Notificaciones y Telegram**
(`/admin/pruebas-notificaciones`). Usa la vista existente `CONFIGURACION` y sus
endpoints requieren el rol `ADMIN`. Hibernate crea las tablas `plantilla_telegram` y `configuracion_telegram`
con `ddl-auto=update`; no requiere un seeder ni SQL manual.

## Gestionar textos y envíos

Abrir la pestaña **Mensajes y envíos** como ADMIN:

1. Elegir el tipo de aviso (falta, atraso, abandono, señal perdida, etc.).
2. Editar el texto completo. Es texto plano; admite saltos de línea y emojis.
3. Elegir **Automático** o **Solo manual** y guardar. Cambiar o previsualizar un
   texto no envía mensajes. Las plantillas y sus modos se conservan al reiniciar.
4. Para un envío manual, completar nombre, área/turno, momento, dispositivo y
   descripción según corresponda. Generar la vista previa y pulsar **Enviar ahora
   al grupo**. Es un mensaje real, sin prefijo de prueba, dirigido al grupo activo.

Variables: `{nombre}`, `{contexto}`, `{momento}`, `{dispositivo}`, `{detalle}`.
`{detalle}` incorpora la descripción generada por el detector; para cambiarla,
reemplazar esa variable por el texto deseado. En los envíos manuales estos campos
se completan en pantalla. Las variables no conocidas se rechazan; los datos del
usuario no se vuelven a interpretar como variables ni como formato Markdown.

**Solo manual** bloquea los próximos envíos automáticos de ese tipo a Telegram.
No desactiva el detector, las incidencias ni las notificaciones internas de
SICOPE; tampoco crea una cola de pendientes. Para enviar un aviso hay que preparar
su mensaje manualmente. La recuperación de señal y la pérdida son tipos distintos:
configurar ambos si se desea que ninguno se envíe automáticamente.

Hasta guardar una personalización, cada tipo conserva una plantilla inicial y el
modo automático previo. El catálogo informativo muestra los canales disponibles,
no sustituye el modo seleccionado. `SALIDA_TEMPRANA` no tiene detector automático.
No se agregan envíos automáticos para permisos, justificaciones u otros avisos que
actualmente no usan Telegram; el envío personalizado continúa en la primera pestaña.

Las alertas reales y la simulación de señal perdida usan el mismo servicio de
plantillas. Un cambio de texto o modo se aplica sin reiniciar. Los mensajes que ya
fueron enviados no se editan retroactivamente. Si otro administrador cambia la
plantilla entre la vista previa y el envío manual, se exige previsualizar de nuevo.

## Primera prueba en producción

1. Publicar los cambios del frontend y backend, compilar con
   `mvn clean package -Pfrontend` y, solo tras `BUILD SUCCESS`, ejecutar
   `sudo systemctl restart control-medico`. El JAR generado debe coincidir con
   la ruta de `ExecStart` del servicio.
2. Ingresar como ADMIN y abrir el módulo, pestaña **Configuración y pruebas**. Si el menú no aparece, recargar la
   página y comprobar que el usuario tenga la vista `CONFIGURACION`.
3. Comprobar el ID de grupo en **Destino de Telegram**. Para cambiarlo, escribir
   el nuevo ID numérico (incluido el signo negativo) y pulsar **Guardar grupo**.
   El valor se aplica a las próximas alertas y pruebas sin reiniciar.
4. Elegir **Mensaje personalizado**, escribir un texto y pulsar **Enviar prueba
   al grupo**. Opcionalmente seleccionar un usuario de referencia.
5. Comprobar **Aceptado por Telegram**, el ID/título del grupo y el ID del mensaje;
   verificar el mensaje en Telegram. La aceptación no significa que fue leído.
6. Elegir **Simular señal perdida (4 minutos)** y enviar. Comparte la plantilla
   con la alerta real, pero lleva un prefijo de PRUEBA y datos simulados. No crea
   sesiones, faltas, asistencias, incidencias ni altera la deduplicación real.

Telegram se envía al grupo activo mostrado en el módulo. Elegir un usuario no envía un mensaje a
su Telegram privado. La casilla **Enviar también una notificación de prueba al
usuario en SICOPE** está desactivada por defecto. Al activarla se registra una
notificación personal `CUSTOM`, marcada como PRUEBA, y se usa el envío WS/push
existente. El resultado de SICOPE se informa separado del de Telegram; registrar
la notificación no garantiza recibir el push en el dispositivo.

## Resultados

- `CONFIRMADO`: Telegram respondió `ok: true` con chat y número de mensaje.
- `FALLIDO`: Telegram rechazó la solicitud o el mensaje no es válido.
- `DESHABILITADO`: faltan token/grupo o el canal está desactivado.
- `INCIERTO`: timeout, desconexión o respuesta sin confirmación fiable. Mirar el
  grupo antes de repetir: el mensaje pudo llegar. No hay reintento automático.
- SICOPE `REGISTRADO`: la notificación personal fue registrada. `NO_CONFIRMADO`:
  revisar el buzón antes de repetir; Telegram puede haber tenido éxito igualmente.

Si el navegador pierde la conexión, revisar ambos destinos antes de repetir.
Si el estado dice configurado pero falla el envío, revisar token, pertenencia del
bot al grupo, permisos y conectividad HTTPS del servidor hacia Telegram.

## Cambiar el grupo desde SICOPE

Solo ADMIN puede guardar el destino. El guardado valida que sea un ID numérico
negativo de grupo y no envía mensajes automáticamente; usar después la prueba
para comprobar que el bot pertenezca al grupo y tenga permiso de escribir.

El ID se guarda en `configuracion_telegram`, fila `id=1`, y tiene prioridad sobre
`sisu.telegram.chat-id` / `SISU_TELEGRAM_CHAT_ID`. Si no existe la fila, se utiliza
el valor del servidor como hasta ahora. La pantalla indica el origen del valor.
El token y el interruptor `SISU_TELEGRAM_ENABLED` siguen siendo del servidor.
Guardar un grupo no habilita un bot sin token ni un canal desactivado.

La configuración es global para las instancias que compartan esta base de datos.
Para mantener grupos independientes entre desarrollo y producción, utilizar bases
de datos separadas. Un envío ya iniciado conserva el destino que tomó al comenzar.
Los siguientes envíos leen el grupo guardado, incluso después de reiniciar.

## Configuración de producción

El servicio existente puede definir estas variables dentro de `[Service]`:

```ini
Environment="SISU_TELEGRAM_ENABLED=true"
Environment="SISU_TELEGRAM_BOT_TOKEN=REEMPLAZAR_EN_EL_SERVIDOR"
Environment="SISU_TELEGRAM_CHAT_ID=REEMPLAZAR_EN_EL_SERVIDOR"
```

Tras editar `/etc/systemd/system/control-medico.service`:

```bash
sudo systemctl daemon-reload
sudo systemctl restart control-medico
sudo journalctl -u control-medico --since "5 minutes ago" --no-pager
```

No guardar valores reales en el repositorio. El módulo no muestra ni permite
editar el token. Cambiar solamente estas variables no requiere recompilar.
Una vez guardado el grupo en SICOPE, cambiar el ID del servicio no reemplaza
el valor guardado: administrar el destino desde el módulo.

## Catálogo comprobado

| Aviso real | Telegram automático |
| --- | --- |
| GPS no activado al inicio | Sí |
| Señal perdida | Sí; plantilla compartida con esta prueba |
| Señal recuperada | Sí, después de una pérdida avisada |
| Falta: no se presentó | Sí |
| Atraso | Sí |
| Abandono de área | Sí |
| Regreso al área | Sí, después del abandono avisado |
| Ausencia por pérdida de señal | Sí |
| Cierre de sesión en turno | Sí |
| Solicitud de permiso nueva o modificada | No |
| Permiso observado, aprobado o rechazado | No |
| Justificación pendiente, aprobada o rechazada | No |
| Recordatorio y fin de turno | No |
| Jornada asignada o actualizada | No |
| Horario de interno asignado | No |
| Actividad asignada | No |
| GPS denegado y avisos personales de incidencia/ausencia | No; supervisión usa los avisos anteriores |
| Mensaje personalizado | No en el flujo habitual; este módulo permite probar Telegram |

La salida provisional de geocerca se muestra en monitoreo y no se envía a
Telegram. `SALIDA_TEMPRANA` tiene formato de mensaje, pero no se encontró un
detector que genere esa incidencia automáticamente. Hay métodos antiguos de
notificación de ausencia/salida que ya no son llamados por los detectores.
Los tiempos y condiciones operativas se describen en `monitoreo-gps-spec.md` §8.1.

## API y validación técnica

Gestión de mensajes, todos restringidos a ADMIN:

- `GET /api/v1/telegram/admin/plantillas`: catálogo y configuración efectiva.
- `PUT /api/v1/telegram/admin/plantillas/{tipo}`: `{texto, automatico}`.
- `POST /api/v1/telegram/admin/plantillas/previsualizar`: `{texto, valores}`.
- `POST /api/v1/telegram/admin/plantillas/{tipo}/enviar`:
  `{valores, textoEsperado}`; el texto debe coincidir con la vista previa de la
  plantilla guardada. Reporta la aceptación, rechazo o incertidumbre de Telegram.

Configuración y pruebas:

- `GET /api/v1/telegram/admin/estado`: configuración efectiva, origen (`SISTEMA`
  o `SERVIDOR`), disponibilidad del token e interruptor del servidor, sin token.
- `PUT /api/v1/telegram/admin/configuracion`: JSON `{ "chatId": "-1001234567890" }`.
  Persiste el grupo y devuelve el estado efectivo. Solo ADMIN.
- `POST /api/v1/telegram/admin/pruebas`: JSON con `tipo` (`PERSONALIZADO` o
  `SENAL_PERDIDA`), `idUsuario` opcional, `mensaje` (máximo 2000 caracteres) y
  `enviarEnSicope` (false por defecto). No acepta un destino Telegram arbitrario.
- `POST /api/v1/telegram/admin/test?mensaje=...`: conserva la prueba anterior;
  ahora devuelve el resultado confirmado en `data.telegram`.

Las pruebas unitarias usan respuestas simuladas del cliente HTTP y un contexto
mínimo de seguridad: no cargan Spring Boot, no conectan a BD y no envían mensajes.

```bash
mvn -Dtest=TelegramNotificationServiceImplTest,TelegramControllerTest,TelegramConfiguracionServiceImplTest,TelegramPlantillaServiceImplTest test
```

Para validar el detector real, además de la entrega, se necesita un turno y un
dispositivo de prueba controlados. Esta simulación comprueba plantilla y canal de
envío; no verifica la programación de turnos ni los umbrales del detector.
