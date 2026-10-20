# Hyper-Marker

Aplicación Android nativa, offline-first, para organizar y usar proyectos HTML interactivos y notas Markdown, con pines y dibujos no destructivos. **Interactive Notes** es el nombre provisional usado en la especificación; **Hyper-Marker** identifica este repositorio. El nombre visible de la aplicación se decidirá durante la implementación.

## Estado actual

Este repositorio contiene la documentación de arranque. **Todavía no hay aplicación, proyecto Gradle, pruebas ejecutables ni APK.** La dirección de producto está aprobada en la [especificación original](Interactive_Notes_Android_Agent_Design.md); las decisiones técnicas adicionales se identifican como propuestas en [architecture.md](architecture.md).

## Empezar aquí

1. Leer [AGENTS.md](AGENTS.md) y la guía [Agent.md](Agent.md).
2. Leer la [especificación original](Interactive_Notes_Android_Agent_Design.md), autoridad del alcance funcional.
3. Revisar [architecture.md](architecture.md) y [docs/security.md](docs/security.md).
4. Consultar [progress.md](progress.md) para conocer el estado real y continuar el primer hito pendiente del [plan de implementación](docs/implementation-plan.md).
5. Usar [docs/testing.md](docs/testing.md) para validar entregables y registrar evidencia.

## Producto V1

- Biblioteca de carpetas anidadas y documentos: crear, renombrar, mover, duplicar, buscar y eliminar con confirmación.
- Importación de HTML/HTM, carpetas y ZIP mediante Storage Access Framework y recepción desde Compartir de Android; copia completa a almacenamiento privado.
- Lector WebView inmersivo con controles bajo demanda, JavaScript, recursos relativos, consola y desplazamiento automático ajustable.
- Notas `.md` con edición Source y Visual, autoguardado y undo/redo, preservando el texto fuente ante sintaxis no compatible.
- Pines y dibujos persistentes en una capa separada del contenido original.
- **En modo Pin, una pulsación prolongada de aproximadamente 500 ms resalta el elemento exacto mientras se mantiene el contacto; al deslizar cambia el resaltado y al soltar abre el editor.**

V1.1 agrega modificaciones no destructivas de texto renderizado y reconciliación avanzada de anclas. RAR, exportación, inspector avanzado y demás mejoras quedan fuera de V1.

## Arquitectura prevista

Kotlin y Jetpack Compose para la aplicación; Room para metadatos; archivos privados para proyectos importados, Markdown y datos de dibujo; WebView con acceso local HTTPS controlado y un controlador de anotaciones JS/CSS. Las páginas importadas son contenido no confiable. El acceso de red se habilita explícitamente por documento.

## Compilación e instalación

**Instrucciones futuras, no verificadas:** el hito H1 creará el wrapper de Gradle y documentará versiones fijadas de JDK, Gradle, Android Gradle Plugin, Kotlin, SDK y dependencias, además del `applicationId` y el mínimo de Android con su justificación. No se debe interpretar esta sección como una compilación realizada.

Una vez implementado H1, el flujo previsto desde la raíz es:

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

La ruta supone un módulo `app` sin flavors; deberá actualizarse si cambia la configuración. `local.properties` y SDK locales no se versionan. Las instrucciones de pruebas están en [docs/testing.md](docs/testing.md).

## Flujo de uso esperado

Desde la biblioteca, importar un archivo, carpeta o ZIP, o compartirlo desde otra aplicación. Se prefiere `index.html`; ante una selección ambigua se solicita una entrada al usuario. El lector abre la copia privada y permite cambiar entre View, Pin y Draw. Las notas Markdown se crean en la biblioteca. Las anotaciones se guardan sin modificar los archivos importados.

## Límites y compatibilidad

- React/Vite se importa como salida de producción compilada con rutas relativas; JSX/TSX sin compilar no se ejecuta.
- Un canvas/WebGL se ancla como superficie; no se identifican semánticamente objetos dibujados en su interior.
- Los dibujos no prometen seguir el reflujo del texto. Los pines dependen de que pueda resolverse su ancla; una resolución dudosa produce estado **Unanchored**.
- Offline significa que los archivos locales importados están disponibles sin conexión. Un documento que depende de recursos remotos puede quedar incompleto.
- WebView no constituye un sandbox perfecto. Las limitaciones reales se documentarán a partir de las pruebas.

No se ha elegido una licencia de distribución del proyecto. Las dependencias que se incorporen deberán registrar versión, licencia y avisos aplicables.
