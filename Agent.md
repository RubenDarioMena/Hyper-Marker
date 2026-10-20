# Guía de arranque y relevo para cualquier agente

## Objetivo

Implementar V1 de Hyper-Marker según [Interactive_Notes_Android_Agent_Design.md](Interactive_Notes_Android_Agent_Design.md). La entrega futura incluye aplicación Android funcional, pruebas, contenido de ejemplo y un APK debug reproducible cuando el entorno permita verificarlo. **La entrega actual es únicamente documental.**

## Lectura y autoridad

Leer, en orden: [AGENTS.md](AGENTS.md), este documento, la especificación original, [progress.md](progress.md), [architecture.md](architecture.md), [plan de implementación](docs/implementation-plan.md), [seguridad](docs/security.md) y [pruebas](docs/testing.md).

Las instrucciones explícitas del usuario gobiernan el trabajo. Para alcance de producto, la especificación original prevalece sobre estas guías derivadas. Arquitectura, seguridad y plan desarrollan esa especificación; sus propuestas no son decisiones aprobadas ni funcionalidades terminadas. Si se detecta una contradicción, documentarla y resolverla antes de implementar el comportamiento afectado. No cambiar silenciosamente requisitos obligatorios.

## Antes de escribir código

1. Revisar `git status`, rama, remotos, últimos commits y archivos existentes. Preservar cambios del usuario.
2. Identificar el primer hito incompleto en `progress.md`; comprobar sus dependencias en el plan.
3. Comprobar si existen Gradle wrapper, JDK, Android SDK y emulador/dispositivo. Si no existen, registrar la limitación. No ejecutar comandos de build inexistentes como prueba de la aplicación.
4. En H1, seleccionar y fijar versiones compatibles con documentación oficial vigente. Registrar mínimo SDK, target/compile SDK, JDK, Kotlin, AGP, Gradle, Compose, Room, AndroidX WebKit, editor Markdown y sus licencias donde corresponda. No inventar compatibilidad ni usar versiones dinámicas `+`.
5. Definir un cambio acotado que deje un comportamiento verificable y seleccionar sus casos de prueba antes de implementarlo.

## Cómo implementar

- Seguir UI → casos de uso/servicios → repositorios y almacenamiento. Usar inicialmente paquetes por responsabilidad en un módulo `app`; extraer módulos sólo cuando haya necesidad demostrable.
- Mantener estado de pantalla y persistencia fuera de composables; evitar que WebView o sus callbacks sean la fuente única de datos durables.
- Trabajar por hitos: biblioteca y persistencia, importación segura, lector, Markdown, pines, dibujos, endurecimiento y APK.
- Para el gesto obligatorio de Pin, demostrar primero el ciclo mantener → resaltar → deslizar → actualizar objetivo → soltar → editar → guardar/cancelar. No sustituirlo por coordenadas fijas o una pulsación que sólo abra un diálogo.
- Guardar el Markdown fuente como autoridad; Visual debe permitir editar, no ser sólo una vista previa. Nunca convertir contenido no compatible con pérdida silenciosa.
- Documentar los límites de canvas, DOM dinámico, orígenes web, dibujos y acceso de red. Si una API no permite hacer cumplir una política prevista, resolver la política antes de entregar la función.
- Evitar complejidad anticipada: no implementar sincronización, RAR, edición de texto V1.1 ni inspector avanzado como sustitutos de funciones V1 pendientes.

## Validación y cierre de cada cambio

1. Ejecutar las comprobaciones relevantes de [docs/testing.md](docs/testing.md), incluyendo regresiones en límites de seguridad cuando se modifiquen importación, WebView o bridge.
2. Revisar el diff, archivos generados y posibles secretos. No versionar APK, SDK, caches ni rutas locales.
3. Actualizar `progress.md` con lo hecho, lo no hecho, comandos y resultados reales, bloqueos y próximo paso. Actualizar arquitectura si cambia una decisión.
4. Hacer commits coherentes cuando formen parte del encargo; subir a la rama/remoto autorizados. No forzar el historial para resolver una divergencia.
5. Entregar un resumen verificable: comportamiento, archivos relevantes, pruebas realizadas, limitaciones y estado del commit. No confundir un build exitoso con aceptación funcional.

## Relevo mínimo

Al terminar una sesión, otro agente debe poder responder leyendo `progress.md`: ¿qué funciona?, ¿qué evidencia existe?, ¿qué sigue?, ¿qué decisión está pendiente?, ¿qué entorno se utilizó? Mantener estos documentos en español; conservar identificadores de código y términos de API en su idioma original.
