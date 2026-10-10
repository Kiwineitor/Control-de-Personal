# 🏥 Sistema de Control Médico

Sistema integral de gestión y monitoreo médico. Este proyecto está construido con una arquitectura Full-Stack, utilizando **Spring Boot** para el Backend y **Vue 3** (con arquitectura Turborepo/Vben) para el Frontend.

---

## 🛠️ Requisitos Previos

Antes de levantar el proyecto, asegúrate de tener instalado:
* **Java 17** o superior.
* **Maven** (o usar el wrapper incluido `mvnw`).
* **Node.js** (versión 18 o superior).
* **Base de datos PostgreSQL** configurada localmente.

---

## 🚀 Guía de Instalación y Ejecución

El proyecto está dividido en dos partes independientes que deben ejecutarse en terminales o ventanas separadas.

### 1. Backend (Spring Boot)

El backend se gestiona desde la raíz del proyecto. 

1. Abre la carpeta principal del proyecto (`controlMedico`) en **Visual Studio Code** (o tu IDE Java preferido como IntelliJ/Eclipse).
2. Configura las credenciales de tu base de datos:
   * Ve a `src/main/resources/`.
   * Abre tu archivo de propiedades activo (ej. `application.properties`).
   * Actualiza los valores de conexión:
     ```properties
     spring.datasource.url=jdbc:postgresql://localhost:5432/tu_base_de_datos
     spring.datasource.username=tu_usuario
     spring.datasource.password=tu_contraseña
     ```
3. Levanta el servidor ejecutando la clase principal `ControlMedicoApplication.java` desde tu IDE, o usa la terminal en la raíz del proyecto:
   ```bash
   ./mvnw spring-boot:run

### 2. Frontend (Vue 3 / Turborepo)

1. El frontend utiliza pnpm como gestor de paquetes.
Habilitar Corepack (Solo la primera vez):
    Abre una terminal y ejecuta este comando para asegurar la compatibilidad del gestor de paquetes:

    npm i -g corepack
    corepack enable
Instalar Dependencias (¡Importante!):
La instalación debe hacerse en la raíz de la carpeta frontend para que el monorepo enlace todos los paquetes internos correctamente.
Abre una terminal y navega a la carpeta frontend:
Bash

cd frontend
pnpm install


Levantar el Servidor de Desarrollo:
Una vez instaladas las dependencias, navega a la aplicación principal y levanta el entorno:
Bash

cd apps/web-naive
pnpm dev

Abre tu navegador en la URL que te indique la terminal (usualmente http://localhost:5173 o similar) para ver la aplicación funcionando.

📁 Estructura General

    /src: Contiene todo el código fuente del Backend (Controladores, Servicios, Seguridad, Modelos).

    /frontend: Contiene el monorepo del Frontend.

        /frontend/apps/web-naive: Aplicación web principal en Vue.

        /frontend/packages: Componentes y utilidades compartidas del entorno Vben.
