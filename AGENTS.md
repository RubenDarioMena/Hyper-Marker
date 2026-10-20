# Instrucciones para agentes

Estas instrucciones aplican a todo el repositorio. El punto de entrada operativo es [Agent.md](Agent.md); léelo antes de implementar. Este archivo existe para herramientas que descubren automáticamente `AGENTS.md`. Mantener una sola guía operativa en `Agent.md`, sin copiarla aquí.

## Reglas del proyecto

- Respetar la [especificación original](Interactive_Notes_Android_Agent_Design.md). No editarla para justificar un recorte de alcance; registrar propuestas y cambios autorizados por separado.
- Consultar [progress.md](progress.md) antes de trabajar y actualizarlo al finalizar con cambios, validación, limitaciones y siguiente paso concreto.
- Implementar Android nativo con Kotlin y Compose. No sustituirlo por una web, Expo u otra plataforma sin una decisión explícita del usuario.
- Completar V1 antes de iniciar V1.1. La selección Pin por pulsación prolongada con resaltado visible y actualizado en vivo es obligatoria.
- Conservar los originales importados. Anotaciones, notas y futuras sustituciones de texto son datos separados.
- Seguir [architecture.md](architecture.md), [docs/security.md](docs/security.md), el [plan](docs/implementation-plan.md) y la [matriz de pruebas](docs/testing.md). Distinguir propuestas técnicas de decisiones verificadas.
- Mantener el acceso a archivos limitado al proyecto abierto y la red deshabilitada por defecto por documento. Ningún mensaje de una página concede acceso general a Android o al sistema de archivos.
- No afirmar que una función, prueba o APK existe o funciona sin evidencia. Si falta SDK, dispositivo o capacidad de validación, registrar lo pendiente sin marcar el hito como terminado.
- No borrar ni sobrescribir cambios ajenos. Inspeccionar el estado Git antes de modificarlo y revisar el diff antes de crear commits.
