# Guía de despliegue — local y producción

> Producción vive en **`http://virtual.uap.edu.bo:7050`** (HTTP plano, sin TLS).
> Comprobado el 2026-09-07: la SPA responde 200, `/ws/info` responde y
> `/api/v1/auth/login` contesta. El backend ya está levantado ahí.

Este documento cubre cuatro escenarios distintos. Elige el tuyo antes de copiar comandos:

| Escenario | Backend | Frontend | Para qué |
|---|---|---|---|
| **A** | local | dev server local | desarrollar backend + frontend a la vez |
| **B** | producción | dev server local | probar la UI contra datos reales sin recompilar nada |
| **C** | producción | jar que sirve la SPA | desplegar la web |
| **D** | producción | APK Capacitor | el teléfono del personal |

---

## 0. Requisitos y trampas del entorno

| Herramienta | Versión requerida | Nota |
|---|---|---|
| JDK | **17 o 21** | ver aviso abajo |
| Maven | 3.8+ | |
| Node | ^20.19 \|\| ^22.18 \|\| ^24 | |
| pnpm | 10.33 (lo fija `packageManager`) | pnpm 11 se auto-degrada solo |
| PostgreSQL | el de `application.properties` | |
| Android SDK | plataforma 36 + build-tools 36 | sólo para el escenario D, ver §6 |

### ⚠ El JDK 25 rompe la compilación del backend

En esta máquina `JAVA_HOME` apunta a `~/.jdk/jdk-25.0.2`. Con ese JDK el build
falla con ~40 errores del tipo `cannot find symbol: method getNombre()`: Lombok no
llega a generar los getters. Con el JDK 21 que ya está instalado compila limpio.

```bash
# Verificado: EXIT=0
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
mvn clean compile
```

Ponlo en tu `~/.bashrc` o antepónlo a cada comando `mvn`. No es un problema del
código: el `pom.xml` compila con `--release 17` y Lombok simplemente no corre bajo
el JDK 25.

### El registro de npm apunta a un espejo chino

`frontend/.npmrc` fija `registry=https://registry.npmmirror.com`. Si `pnpm install`
se queda colgado o falla al descargar, cámbialo a `https://registry.npmjs.org`.

---

## 1. Escenario A — todo local

### Backend

**No hay perfil activo por defecto.** Los perfiles `kevin` y `jhowill` que
mencionan los docs están en `.gitignore` y **no existen en este árbol**: sólo hay
`application.properties`. Es decir, un `mvn spring-boot:run` pelado arranca en el
puerto **7050** y se conecta a la base de **producción**
(`virtual.uap.edu.bo:5432/bdControlPersonaSisu`).

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
mvn spring-boot:run
```

> ⚠ `spring.jpa.hibernate.ddl-auto=update` y no hay migraciones SQL. Un backend
> local apuntando a la base de producción **migra el esquema real** en cuanto
> toques una entidad. Si vas a tocar entidades, crea antes un
> `src/main/resources/application-local.properties` con tu propia base y arranca
> con `-Dspring-boot.run.profiles=local`.

### Frontend

```bash
cd frontend
pnpm bootstrap          # sólo la primera vez
pnpm dev:naive          # http://localhost:5888
```

Para apuntar al backend local, en `apps/web-naive/.env.development.local`:

```dotenv
VITE_GLOB_API_URL=
VITE_PROXY_TARGET=http://127.0.0.1:7050
VITE_NITRO_MOCK=false
```

---

## 2. Escenario B — tu dev server contra el backend de producción

Es lo que pediste. Ya está configurado en
`frontend/apps/web-naive/.env.development.local` (ese fichero **no se commitea**,
`*.local` está en `.gitignore`):

```dotenv
VITE_GLOB_API_URL=
VITE_PROXY_TARGET=http://virtual.uap.edu.bo:7050
VITE_NITRO_MOCK=false
```

```bash
cd frontend
pnpm dev:naive          # http://localhost:5888 → proxy → producción
```

`VITE_GLOB_API_URL` se deja **vacío** a propósito: así `/api` y `/ws` salen por el
proxy de Vite y el navegador nunca hace una petición cross-origin, con lo que no
dependes de CORS. Si le pusieras la URL absoluta también funcionaría, porque
`SecurityConfig` ya permite el patrón `http://localhost:*`, pero el proxy es más
limpio.

### Arreglo aplicado: `VITE_PROXY_TARGET` no funcionaba

La línea `VITE_PROXY_TARGET=http://172.16.21.5:8085` de `.env.development` **no
tenía ningún efecto**. Vite no vuelca los ficheros `.env` en `process.env`, y
`vite.config.ts` leía justo `process.env.VITE_PROXY_TARGET`, que siempre venía
`undefined`. El proxy caía siempre al fallback `http://127.0.0.1:7050`.

`vite.config.ts` ahora lee los `.env` a mano, con la misma detección de `--mode`
que usa `internal/vite-config`, y con esta prioridad:

```
shell > .env.<mode>.local > .env.<mode> > .env.local > .env > http://127.0.0.1:7050
```

Se resuelve sin importar nada de `vite` **a propósito**. `vite.config.ts` queda
fuera del `include` de `tsconfig.json`, así que el editor lo abre en un proyecto
inferido con resolución node10, y ahí los tipos de `vite` no se encuentran. Un
`import` de `vite` daría un error falso en el editor aunque el build funcione. Con
`node:fs`, `node:path` y `node:process` el fichero valida limpio bajo las dos
resoluciones.

> Si ves `El parámetro 'config' tiene un tipo 'any' implícitamente` o errores de
> módulos no encontrados en este fichero, casi siempre es que falta `pnpm install`.
> Sin `node_modules`, `@vben/vite-config` no resuelve tipos y todo cae a `any`.

También puedes seguir usando la vía de shell, que siempre gana:

```bash
VITE_PROXY_TARGET=http://virtual.uap.edu.bo:7050 pnpm dev:naive
```

Comprobado con el dev server levantado: `POST http://localhost:5888/api/v1/auth/login`
devuelve la misma respuesta que producción, y `http://localhost:5888/ws/info`
contesta con `"websocket":true`.

### Dos avisos para este escenario

- **Escribes en producción.** Fichajes, sesiones de monitoreo, fixes GPS,
  incidencias y notificaciones caen en la base real. Usa un usuario de pruebas.
- **Sesión única.** Si entras con el mismo usuario que tiene el APK, uno de los dos
  se cae con `SESION_DESPLAZADA`. Sólo `ROLE_ADMIN` y `ROLE_SUPERVISOR` están
  exentos (`sisu.sesion-unica.roles-exentos`).

El servidor mock de Nitro (`VITE_NITRO_MOCK`) es inofensivo aquí: arranca un Nitro
en el puerto 5320 al que **nadie enruta**, porque el proxy manda `/api` al backend
real. Apagarlo sólo te ahorra un proceso.

---

## 3. Escenario C — build de producción de la web

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
mvn clean package -Pfrontend
```

El perfil `frontend` hace `pnpm install --frozen-lockfile` + `pnpm build:naive` y
copia `frontend/apps/web-naive/dist` a `target/classes/static`, de modo que el jar
sirve la SPA y la API por el mismo puerto.

> ⚠ **Borra `apps/web-naive/.env.production.local` antes de este build.** Ese
> fichero fija la URL absoluta para el APK, y si está presente el build web también
> la hornea. La web servida por el propio backend debe quedar en *same-origin*
> (`VITE_GLOB_API_URL` vacío), que es exactamente como está hoy producción:
>
> ```
> $ curl http://virtual.uap.edu.bo:7050/_app.config.js
> window._VBEN_ADMIN_PRO_APP_CONF_={"VITE_GLOB_API_URL":""};
> ```

### 3.1 Qué tiene que estar en el servidor y dónde

El jar resultante es `target/controlMedico-0.0.1-SNAPSHOT.jar` y lleva la SPA dentro,
en `BOOT-INF/classes/static/`. No hay script de despliegue en el repo: se copia el
jar al servidor y se arranca con `java -jar`.

#### a) `serviceAccountKey.json` — la credencial de FCM

Es lo único que bloquea de verdad las notificaciones con la app cerrada.

**Dónde va:** `src/main/resources/serviceAccountKey.json`, en la máquina donde
compilas el jar, **antes** de `mvn package`. `FcmService` lo lee con
`ClassPathResource`, así que tiene que viajar **dentro** del jar. Dejarlo suelto al
lado del jar en el servidor no sirve.

**Cómo conseguirlo:** Firebase Console, proyecto `sistemacontrolpersonal-4b25c`,
Configuración del proyecto, pestaña Cuentas de servicio, Generar nueva clave
privada. Descarga un JSON, renómbralo a `serviceAccountKey.json`.

Tiene que ser el **mismo proyecto Firebase** que el `google-services.json` del APK.
Comprueba que el `project_id` coincide:

```bash
grep project_id src/main/resources/serviceAccountKey.json
grep project_id frontend/apps/web-naive/android/app/google-services.json
# ambos: sistemacontrolpersonal-4b25c
```

Verificación tras arrancar, en el log:

```
FCM habilitado (Firebase Admin inicializado).             ← bien
FCM deshabilitado: no se encontró serviceAccountKey.json  ← no llegan push
```

Está en `.gitignore` a propósito. Es una credencial que permite enviar push a todos
los dispositivos: no la commitees ni la pases por chat.

#### b) `sisu.storage.path` — ruta de archivos

Vale `C:/sisu-files`, una ruta de Windows. Si el servidor es Linux, la subida de
archivos falla. Sobreescríbela sin tocar el jar:

```bash
java -jar controlMedico-0.0.1-SNAPSHOT.jar --sisu.storage.path=/var/sisu-files
```

O crea `application-prod.properties` **al lado del jar** y arranca con
`--spring.profiles.active=prod`. Spring Boot lee automáticamente los `.properties`
del directorio de trabajo y del subdirectorio `config/`, y esos valores ganan sobre
los del jar. Es la vía recomendada para todo lo de esta sección salvo la credencial
de FCM.

#### c) Secretos que hoy son valores de desarrollo

| Propiedad | Valor actual | Problema |
|---|---|---|
| `sisu.jwt.secret` | *(ver `application.properties`)* | el propio valor dice, en su texto, que no es para producción |
| `spring.datasource.password` | *(ver `application.properties`)* | commiteada en el repo |
| `spring.mail.password` | contraseña de aplicación de Gmail | commiteada en el repo |

Quien tenga el repositorio puede firmar tokens válidos con ese secreto JWT.
Cámbialo en el servidor vía `application-prod.properties` o variable de entorno
`SISU_JWT_SECRET`, y rota la contraseña de correo.

#### d) Opcionales que hoy están apagados

- **Claves VAPID** (`vapid.public-key`, `vapid.private-key`). Sin ellas el Web Push
  del navegador queda desactivado. El backend imprime un par generado en el log al
  arrancar para que lo copies. **No afecta al APK**, que usa FCM.
- **Duración de la sesión** (`SISU_JWT_EXPIRATION`, en milisegundos). Por defecto
  30 días, para que el APK del personal no se deslogueé solo. Antes eran 8 h y
  aparecían deslogueados a mitad de turno; peor aún, el servicio nativo de GPS usa
  el mismo token, así que al vencer dejaba de reportar y el servidor lo contaba
  como "sin señal". **No hay refresh token**: `/api/v1/auth/refresh` es un stub que
  devuelve 401 a propósito, así que la única defensa contra un token robado es que
  caduque. Como el sistema va por HTTP sin TLS, acortar este valor es la palanca
  para reducir esa ventana:
  ```bash
  export SISU_JWT_EXPIRATION=604800000   # 7 días
  ```

- **Telegram**, por variables de entorno antes de arrancar el jar:
  ```bash
  export SISU_TELEGRAM_BOT_TOKEN=xxxxx:yyyyy
  export SISU_TELEGRAM_CHAT_ID=-100xxxxxxxxxx
  ```
  Si faltan, el canal se apaga solo y el resto sigue funcionando.
- **SMB** (`app.ipServidor=localhost`). Sólo si usáis el servidor de archivos
  compartido.

#### e) El sistema va por HTTP plano en una IP pública

`virtual.uap.edu.bo` resuelve a `190.129.216.210` y el puerto 7050 responde sin TLS.
Eso significa que las contraseñas del login, los tokens JWT y las coordenadas GPS
del personal viajan en claro por internet. Para un sistema con datos de ubicación de
personas es la deuda más seria que tiene el despliegue.

Si ponéis TLS delante, hay que tocar tres sitios más:

1. `capacitor.config.ts`: `androidScheme: 'https'`.
2. `SecurityConfig.corsConfigurationSource`: añadir `https://virtual.uap.edu.bo:*`.
3. `WebSocketConfig.registerStompEndpoints`: la lista actual sólo tiene
   `http://virtual.uap.edu.bo:*`, hay que añadir la variante `https`.

#### f) Una sola instancia

El broker STOMP es en memoria (`enableSimpleBroker`) y los `@Scheduled` de monitoreo
y ausencias corren en cada instancia. Levantar un segundo nodo duplicaría los
trabajos programados y partiría las suscripciones WebSocket. Antes de escalar hay
que mover el broker a RabbitMQ o ActiveMQ.

### 3.2 Nota sobre `mvn package`

El único test que existe es un `@SpringBootTest` de carga de contexto, sin
`src/test/resources` propio. Levanta la aplicación entera con `application.properties`
de `main`, así que **necesita la base de datos accesible** para pasar. Si compilas
sin acceso a la base:

```bash
mvn clean package -Pfrontend -DskipTests
```

---

## 4. Escenario D — APK apuntando a producción

### 4.1 La URL absoluta es obligatoria

Ya está puesta en `frontend/apps/web-naive/.env.production.local` (no se commitea):

```dotenv
VITE_GLOB_API_URL=http://virtual.uap.edu.bo:7050
```

El APK carga sus assets desde `http://localhost` dentro del WebView, así que una
ruta relativa resolvería contra el propio WebView. Sin esta variable se rompen
**tres** cosas, no una:

| Consumidor | Dónde | Qué pasa si está vacía |
|---|---|---|
| `requestClient` | `api/request.ts` | ninguna llamada REST funciona |
| SockJS | `composables/useWebSocket.ts` | sin tiempo real |
| **Servicio nativo GPS** | el patch de background-geolocation | descarta el POST en silencio |

El tercero es el que más cuesta detectar. En el patch:

```java
if (backendUrl == null || authToken == null) return;   // sin config no se puede subir
...
URL url = new URL(backendUrl + "/api/v1/monitoreo/ubicacion");
```

`setUploadConfig` sólo asigna `backendUrl` si la URL **no viene vacía**. Con el
`.env.production` actual (`VITE_GLOB_API_URL=`), el servicio en segundo plano nunca
postea y el personal aparece como "sin señal" aunque la app esté corriendo.

### 4.2 Secuencia de build

Verificada de principio a fin en esta máquina: `BUILD SUCCESSFUL in 29s`, con
`SICOPE-1.0-debug.apk` de 13 MB conteniendo la URL de producción.

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

cd frontend
pnpm bootstrap                       # aplica el patch de background-geolocation
pnpm build:capacitor                 # lee .env.production + .env.production.local

cd apps/web-naive
npx cap sync android                 # copia dist/ y reescribe capacitor.settings.gradle

cd android
./gradlew assembleDebug              # → app/build/outputs/apk/debug/SICOPE-1.0-debug.apk
```

**Nunca saltes `npx cap sync android`.** Sin él el APK se compila con los assets web
de la vez anterior, y eso ya ha roto el GPS en silencio antes.

Gradle 8.14.3 tampoco corre bajo el JDK 25, así que el mismo `JAVA_HOME` del backend
sirve para el APK.

Dos ficheros que hicieron falta y no vienen en el repo, ambos en `.gitignore`:

- `android/local.properties` con `sdk.dir=/home/<usuario>/Android/Sdk`. Alternativa
  equivalente: exportar `ANDROID_HOME`.
- Permiso de ejecución en `gradlew`, que git guarda como `100644`:
  ```bash
  chmod +x frontend/apps/web-naive/android/gradlew
  ```

Comprobación de que la URL quedó horneada, primero en el proyecto y después dentro
del APK ya empaquetado:

```bash
grep -o 'VITE_GLOB_API_URL[^,]*' \
  apps/web-naive/android/app/src/main/assets/public/_app.config.js

unzip -p android/app/build/outputs/apk/debug/SICOPE-1.0-debug.apk \
  assets/public/_app.config.js
```

Ambos deben imprimir `http://virtual.uap.edu.bo:7050`, no una cadena vacía.

### 4.3 Rebuild

Cada vez que toques algo del frontend, los dos primeros pasos son obligatorios. Si
sólo tocaste ficheros de `android/`, salta directo a Gradle o a Android Studio.

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

cd frontend
pnpm build:capacitor
cd apps/web-naive
npx cap sync android
```

Y después, o bien Android Studio con **Build → Build Bundle(s) / APK(s) → Build
APK(s)**, o bien por consola:

```bash
cd android && ./gradlew assembleDebug
```

Si algo se queda raro, borrado completo antes de repetir la secuencia:

```bash
cd frontend/apps/web-naive
rm -rf dist dist.zip android/app/src/main/assets/public
cd android && ./gradlew clean
```

No hace falta repetir `pnpm bootstrap` salvo que cambien las dependencias o el
patch nativo.

### 4.4 Firma del APK

`app/build.gradle` **no define ningún `signingConfig`**. `assembleRelease` produce
un APK sin firmar que Android se niega a instalar. Para repartir el APK tienes dos
opciones:

- usar `assembleDebug` (firmado con la clave de debug, suficiente para instalación
  manual dentro del hospital), o
- crear un keystore y añadir el `signingConfigs.release` correspondiente.

Además `versionCode` sigue en `1` y `versionName` en `1.0`. Súbelos en cada entrega
o los teléfonos no distinguirán una versión de otra.

---

## 5. Checklist: qué hace falta para que el APK funcione en segundo plano

### Notificaciones con la app cerrada (FCM)

| # | Requisito | Estado |
|---|---|---|
| 1 | `android/app/google-services.json` para `com.uap.sisu` | ✅ presente, proyecto `sistemacontrolpersonal-4b25c` |
| 2 | Plugin `com.google.gms.google-services` aplicado | ✅ condicional en `app/build.gradle` |
| 3 | `serviceAccountKey.json` **en el backend de producción** | ❓ **verificar en el servidor, ver §3.1a** |
| 4 | Permiso `POST_NOTIFICATIONS` en el manifest | ✅ |
| 5 | Canal `sisu_alertas` + `res/raw/notificacion_nutria.mp3` | ✅ |
| 6 | Icono `res/drawable-nodpi/ic_notif.png` | ✅ |
| 7 | Token FCM registrado vía `POST /api/v1/push/subscribe` | ✅ automático tras login |

El punto 3 es el único bloqueante real y no se ve desde el repo. Si falta, el
backend arranca igual y todo parece correcto: simplemente no llega ningún push.
Búscalo en el log de arranque del servidor:

```
FCM habilitado (Firebase Admin inicializado).          ← bien
FCM deshabilitado: no se encontró serviceAccountKey.json  ← no llegan push
```

El canal de sonido tiene una trampa de Android: **si el canal ya existe, el sistema
ignora el nuevo sonido**. Cambiar `notificacion_nutria` exige un id de canal nuevo o
reinstalar la app.

### GPS en segundo plano

| # | Requisito | Estado |
|---|---|---|
| 1 | `VITE_GLOB_API_URL` absoluta en el build | ✅ configurado ahora |
| 2 | Tráfico HTTP plano permitido hacia `virtual.uap.edu.bo` | ✅ ver 5.1 |
| 3 | Permisos de ubicación en segundo plano y servicio en primer plano | ✅ en el manifest |
| 4 | Patch nativo aplicado (`pnpm bootstrap` lo hace) | ✅ |
| 5 | Usuario concede **"Permitir todo el tiempo"** | ⚠ paso manual |
| 6 | App excluida de la optimización de batería | ⚠ paso manual |

#### 5.1 Tráfico en claro

`AndroidManifest.xml` ya trae `android:usesCleartextTraffic="true"` y
`network_security_config.xml` tiene un `base-config` con
`cleartextTrafficPermitted="true"`, así que HTTP hacia producción **ya funcionaba**.
He añadido el dominio de forma explícita para que quede documentado y no se rompa si
alguien endurece el `base-config` más adelante:

```xml
<domain includeSubdomains="true">virtual.uap.edu.bo</domain>
```

#### 5.2 Los dos pasos manuales en cada teléfono

Ningún cambio de código los evita, y son la causa habitual de que el GPS "se muera"
a mitad de turno:

1. **Ubicación → Permitir todo el tiempo.** Android nunca concede
   `ACCESS_BACKGROUND_LOCATION` en el primer diálogo. El usuario debe entrar en
   Ajustes → Apps → SICOPE → Permisos → Ubicación y elegir "Permitir todo el
   tiempo". La app tiene un botón que abre esa pantalla
   (`BackgroundGeolocation.openSettings()`).
2. **Batería → Sin restricciones.** Xiaomi/MIUI, Transsion/XOS, Huawei, Samsung y
   Oppo matan servicios en primer plano de forma agresiva. Hay que poner SICOPE en
   "Sin restricciones" y, en las capas chinas, activar además el autoarranque.

> El manifest **no** pide `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, y no lo he
> añadido: sin código que lance el intent correspondiente el permiso no haría nada,
> y añadir permisos sin uso complica la publicación. Si quieres el diálogo directo
> en vez del paso manual, hay que añadir el permiso **y** el intent.

#### 5.3 Límite conocido: el JWT del servicio nativo caduca a las 8 h

`sisu.jwt.expiration=28800000` son 8 horas. El servicio nativo recibe el token una
sola vez, cuando `addWatcher()` arranca el vigía. El plugin parcheado expone
`updateUploadConfig()` para refrescarlo en caliente, pero **nadie lo llama** en el
código actual. En un turno que pase de 8 horas sin reabrir la app, los POST nativos
empiezan a recibir 401 y las posiciones dejan de registrarse en el servidor.

Mitigación de hoy: que el personal abra la app al menos una vez cada 8 horas.
Arreglo real: llamar a `updateUploadConfig({ authToken })` cuando el token rote.

---

## 6. Checklist local para generar el APK

Todo lo de esta lista está **verificado en esta máquina**: el APK se compiló con
`BUILD SUCCESSFUL in 29s` y salió `SICOPE-1.0-debug.apk` de 13 MB.

| Pieza | Requisito | Estado aquí |
|---|---|---|
| JDK para Gradle | 17 o 21 | ✅ `/usr/lib/jvm/java-21-openjdk-amd64` |
| Android SDK | plataforma `android-36` | ✅ instalada |
| Build tools | 36.0.0 | ✅ instalada |
| `platform-tools` / `adb` | para instalar por USB | ✅ 1.0.41 |
| `ANDROID_HOME` o `local.properties` | Gradle necesita saber dónde está el SDK | ✅ creado |
| `gradlew` ejecutable | git lo guarda como `100644` | ✅ `chmod +x` aplicado |
| Node y pnpm | Node 24, pnpm 10.33 | ✅ pnpm se auto-degrada solo |
| `node_modules` con el patch | `pnpm bootstrap` lo aplica | ✅ patch presente |
| `google-services.json` | `android/app/`, paquete `com.uap.sisu` | ✅ presente |
| `.env.production.local` | URL absoluta de producción | ✅ creado |
| Keystore de debug | `~/.android/debug.keystore` | ✅ presente |

Lo que **no** hace falta en local: `serviceAccountKey.json`, las claves VAPID, el
token de Telegram y el acceso a la base de datos. Todo eso es del backend, y el
backend ya corre en producción. Para generar el APK sólo necesitas el frontend.

### Comandos de una sola vez

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
echo "sdk.dir=$HOME/Android/Sdk" > frontend/apps/web-naive/android/local.properties
chmod +x frontend/apps/web-naive/android/gradlew
```

Mete los dos `export` en tu `~/.bashrc` para no repetirlos.

### Instalar el APK en un teléfono

```bash
~/Android/Sdk/platform-tools/adb install -r \
  frontend/apps/web-naive/android/app/build/outputs/apk/debug/SICOPE-1.0-debug.apk
```

Si vas a repartirlo por USB o por archivo, recuerda que `versionCode` sigue en `1`:
Android no distinguirá una entrega de la siguiente. Súbelo en `app/build.gradle`
antes de cada reparto.

---

## 7. Qué se sube a producción y qué no

### 7.1 Regla general

El servidor sólo necesita **el jar**. Nada de lo que hay en `frontend/android/` llega
al servidor, y nada de lo que hay en el jar llega al APK. Son dos entregas
independientes:

| Entrega | Qué se genera | Dónde va |
|---|---|---|
| Servidor | `target/controlMedico-0.0.1-SNAPSHOT.jar` | la máquina que sirve el 7050 |
| Teléfonos | `SICOPE-1.0-debug.apk` | instalación manual o reparto interno |

Un cambio sólo del APK **no requiere tocar el servidor**. Un cambio sólo del backend
**no requiere regenerar el APK**, salvo que cambie el contrato de la API.

### 7.2 Ficheros que NUNCA se suben tal cual

| Fichero | Por qué |
|---|---|
| `.env.production.local` | fija la URL absoluta; hornearía esa URL en la web servida por el jar |
| `.env.development.local` | override de tu máquina |
| `android/local.properties` | ruta del SDK de tu máquina |
| `android/capacitor.settings.gradle` | lo regenera `cap sync` con el hash del store de pnpm, distinto en cada máquina |
| `serviceAccountKey.json` | credencial de envío push; va en el servidor, nunca en git ni por chat |

### 7.3 `spring.datasource.url` merece cuidado aparte

El valor actual en el árbol es `jdbc:postgresql://virtual.uap.edu.bo:5432/...`. **Ese
valor no debe subirse al servidor.** El servidor de producción está en la misma red
que la base, así que apuntarlo al dominio público lo obliga a salir a internet y
volver a entrar por el NAT para hablar con una base que tiene al lado.

Topología comprobada desde la red interna:

| Destino | Puerto 7050 | Puerto 5432 |
|---|---|---|
| `172.16.21.5` | abierto | — |
| `172.16.21.12` | cerrado | abierto |
| `virtual.uap.edu.bo` = `190.129.216.210` | abierto | **abierto** |

`172.16.21.5:7050` y `virtual.uap.edu.bo:7050` devuelven exactamente el mismo bundle,
mismo hash MD5, así que el dominio público es el NAT del backend interno.

En el servidor, la línea correcta es la LAN:

```properties
spring.datasource.url=jdbc:postgresql://172.16.21.12:5432/bdControlPersonaSisu
```

Es el valor que está commiteado en git. El cambio al dominio público es útil sólo
para desarrollar desde fuera de la red.

### 7.4 🔴 La base de datos está expuesta a internet

El puerto 5432 de `190.129.216.210` acepta conexiones desde internet. Comprobado
enviando un `SSLRequest` del protocolo PostgreSQL: responde `S`, es un PostgreSQL
real escuchando de cara al público.

La contraseña de esa base está commiteada en `application.properties`. Cualquiera con
acceso al repositorio puede conectarse desde cualquier parte del mundo y leer o
modificar los datos del personal del hospital, incluidas las coordenadas GPS.

Acciones, por orden de urgencia:

1. Cerrar el 5432 en el firewall perimetral. La base sólo tiene que ser accesible
   desde `172.16.21.x`. Si alguien necesita acceso remoto, que sea por VPN.
2. Rotar la contraseña de `postgres` y sacarla del repositorio.
3. Purgar la credencial del historial de git, o asumir que está comprometida.

Mientras el 5432 siga abierto, cerrar el resto de agujeros no cambia gran cosa.

---

## 8. Referencia de variables

### Frontend — cuál gana

Vite y el plugin `extra-app-config` leen, en este orden y ganando el último:

```
.env  →  .env.local  →  .env.<mode>  →  .env.<mode>.local
```

`<mode>` es `development` con `pnpm dev:naive` y `production` con `pnpm build:naive`
y `pnpm build:capacitor` (ambos usan `--mode production`).

| Variable | Dev (escenario B) | Web prod (C) | APK (D) |
|---|---|---|---|
| `VITE_GLOB_API_URL` | vacío | vacío | `http://virtual.uap.edu.bo:7050` |
| `VITE_PROXY_TARGET` | `http://virtual.uap.edu.bo:7050` | n/a | n/a |
| `VITE_NITRO_MOCK` | `false` | n/a | n/a |

`VITE_GLOB_API_URL` acaba en `dist/_app.config.js` como
`window._VBEN_ADMIN_PRO_APP_CONF_`. Se puede editar ese fichero después del build
sin recompilar, pero para el APK tendrías que editarlo dentro de
`android/app/src/main/assets/public/` y el siguiente `cap sync` lo pisaría.

### Backend

| Propiedad | Valor actual | Nota |
|---|---|---|
| `server.port` | 7050 | |
| `spring.datasource.url` | `virtual.uap.edu.bo:5432` | modificado, sin commitear |
| `spring.jpa.hibernate.ddl-auto` | `update` | Hibernate manda en el esquema |
| `sisu.jwt.expiration` | 28800000 (8 h) | ver 5.3 |
| `sisu.storage.path` | `C:/sisu-files` | ruta Windows, revisar en el servidor |
| `sisu.sesion-unica.roles-exentos` | `ROLE_ADMIN,ROLE_SUPERVISOR` | |
| `SISU_TELEGRAM_BOT_TOKEN` / `_CHAT_ID` | por entorno | sin ellas el canal se apaga solo |

### Orígenes ya permitidos

`SecurityConfig.corsConfigurationSource` (sólo `/api/v1/**`) y
`WebSocketConfig.registerStompEndpoints` ya incluyen `http://virtual.uap.edu.bo:*`,
`http://localhost:*`, `http://172.16.21.5:*`, `http://192.168.*.*:*` y los orígenes
de Capacitor (`http://localhost`, `capacitor://localhost`). **No hace falta tocar
nada** para los escenarios B, C y D. Un host de despliegue nuevo sí habría que
añadirlo en los dos sitios.

---

## 9. Verificación rápida de producción

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://virtual.uap.edu.bo:7050/
curl -s http://virtual.uap.edu.bo:7050/ws/info
curl -s http://virtual.uap.edu.bo:7050/_app.config.js
```

Esperado: `200`, un JSON con `"websocket":true`, y el `_app.config.js` con
`VITE_GLOB_API_URL` vacío.
