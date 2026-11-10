# AutoScan Hn - Gestor de Gastos Vehiculares con OCR
<img width="1024" height="1024" alt="LOGO" src="https://github.com/user-attachments/assets/a4177805-3172-4ae9-aabe-b93f53e96b2f" />

**Estudiantes:**
* Fabricio Antonio Robelo Beltrand - 202410080063
* Gustavo Antonio Reyes Hernández - 202410050149

---

## 📱 ¿Qué es AutoScan Hn?

**AutoScan Hn** es una aplicación móvil nativa para Android diseñada para digitalizar, procesar y administrar los gastos vehiculares (combustible y mantenimiento) de los conductores hondureños. 

El problema principal que resuelve es la pérdida de tiempo y la desorganización al tener que anotar manualmente los gastos de las facturas físicas. La aplicación utiliza **Inteligencia Artificial (Edge AI)** para escanear la factura con la cámara del celular, extraer automáticamente los datos clave (total, litros, fecha, nombre del comercio) y guardarlos en una base de datos local.



### 🎯 Objetivo General
Desarrollar una solución tecnológica móvil que automatice el registro de gastos vehiculares mediante el Reconocimiento Óptico de Caracteres (OCR), permitiendo a los usuarios llevar un control financiero preciso de sus vehículos sin necesidad de conexión a internet, garantizando la privacidad de sus datos.

**Implementación** 
AutoScan Hn está desarrollada para dispositivos Android 13 en adelante (API 33 como versión mínima). Es compatible con versiones posteriores, incluyendo Android 14, 15 y 16. La aplicación utiliza Google ML Kit para reconocimiento OCR y escaneo de documentos, CameraX para procesamiento mediante cámara y SQLite para almacenamiento local.

### 🛠️ Tecnologías Utilizadas
* **Lenguaje:** Java (Android Nativo)
* **Base de Datos:** SQLite (Local)
* **Inteligencia Artificial:** Google ML Kit (Text Recognition & Document Scanner)
* **Cámara:** AndroidX CameraX
* **Diseño UI:** Material Design 3

---

## ⚙️ Requisitos Técnicos y Versiones (Ingeniero)

### 1. Versiones declaradas en el proyecto (`build.gradle.kts`)
* **Versión de la App:** `versionName = "1.0"` y `versionCode = 1`.
* **Versión mínima de Android (Min SDK):** 33 (Android 13 en adelante). La app no instalará en celulares con versiones anteriores.
* **Versión de compilación (Compile SDK):** 37.

### 2. ¿Qué versión de Android Studio se necesita?
* **Versión Requerida:** **Android Studio Ladybug (2024.2.1)** o superior.
* **Java (JDK):** Requiere **JDK 11** o superior. Android Studio Ladybug ya incluye el JDK 17, el cual es 100% compatible.

### 3. ¿Dónde se ve la versión de la App una vez instalada?
Si el ingeniero quiere ver la versión directamente en el celular:
* Ve a los **Ajustes (Settings)** del celular Android.
* Entra a **Aplicaciones**.
* Busca **AutoScanHn**.
* Toca en **Información de la aplicación**. Ahí verá el número de versión (1.0).

### 4. ¿Dónde se configura en Android Studio?
* **El nombre de la app** se configura en el archivo `res/values/strings.xml` (en la etiqueta `app_name`).
* **El ícono de la app** está en `res/mipmap/`.
* **La versión y el SDK** se configuran en el archivo `build.gradle.kts` (Nivel de Módulo: `app`).

---

## 🚀 Características Principales

1. **Escáner Inteligente:** Utiliza la cámara de Google ML Kit para recortar los bordes de la factura y mejorar la calidad de la imagen.
2. **Lectura OCR:** Extrae texto de las facturas sin necesidad de internet. El motor `ReceiptParser` procesa el texto y autocompleta el formulario.
3. **Segunda Oportunidad:** Si el OCR no lee un campo la primera vez, el usuario puede presionar el botón "Recargar" para que el sistema intente una búsqueda más profunda de los datos faltantes.
4. **Validaciones Inteligentes:** Los campos obligatorios (Total, Fecha, Litros, Repuesto) se pintan de rojo si están vacíos, evitando que se guarden datos erróneos.
5. **Historial Completo:** Permite buscar, filtrar por categoría, ver la foto original, editar o eliminar los gastos guardados.

---

## 📂 Estructura del Proyecto (Arquitectura)

El código está dividido de la siguiente manera:

* **`com.autoscann.hn` (Lógica):**
  * `MainActivity.java`: Menú principal y accesos rápidos.
  * `BaseReciboActivity.java`: Clase abstracta que maneja la cámara, galería y OCR para todas las pantallas.
  * `NuevoGasolinaActivity.java` y `NuevoMantenimientoActivity.java`: Formularios y validaciones específicas.
  * `LivePreviewActivity.java`: Cámara en tiempo real (CameraX).
  * `ReceiptParser.java`: El "cerebro" matemático que usa Expresiones Regulares para limpiar el texto y encontrar los montos.
  * `DatabaseHelper.java`: Gestión de la base de datos SQLite.
  * `ReciboAdapter.java` y `RegistrosActivity.java`: Lista visual del historial.
  * `OcrUtil.java` e `ImagenUtil.java`: Utilidades de procesamiento de imágenes y texto.

* **`res/` (Vistas):**
  * **`layout/`**: Archivos `.xml` con la interfaz visual.
  * **`values/`**: `colors.xml` (paleta de colores), `themes.xml` (modo claro/oscuro), `dimens.xml` (medidas).

---

## 🛠️ Cómo compilar y ejecutar este proyecto

1. Descarga e instala **Android Studio Ladybug (2024.2.1)** o superior.
2. Abre Android Studio y selecciona **"Open"** (Abrir).
3. Busca la carpeta del proyecto (`ProyectoGraficaVisual`) y ábrela.
4. Espera a que Gradle sincronice las dependencias (requiere conexión a internet la primera vez para descargar las librerías de ML Kit y CameraX).
5. Conecta un dispositivo Android (Android 13 o superior) o usa un emulador con Google Play Services.
6. Presiona el botón verde de **"Run" (Play)** en la parte superior.

*(Nota: Asegúrate de conceder los permisos de cámara cuando la aplicación los solicite al usar el modo "Live").*
