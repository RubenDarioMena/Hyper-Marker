# Fixtures sintéticos de Hyper-Marker

Todos los ejemplos de notas/HTML y generadores de esta carpeta fueron creados para este proyecto. No contienen datos personales. Las bibliotecas de terceros conservan sus propias licencias.

- `standalone/index.html`: ejemplo interactivo completo incluido también como `app/src/main/assets/demo.html`.
- `project/`: HTML con páginas y recursos CSS/JS/SVG relativos.
- `bundle/`: ZIP simple, con directorio envolvente y con selección de entrada ambigua.
- `dynamic/` y `surface/`: objetivos que desaparecen/cambian y ancla de canvas.
- `markdown/`: corpus compatible y texto que debe permanecer en Source.
- `react-src/`: fuente React; `react-built/`: salida Vite de producción con `base: './'`. Sólo la salida compilada se importa en Android.
- `malicious/`: ZIP pequeños que prueban traversal, rutas absolutas, symlinks y colisiones. No abrirlos con extractores generales; se usan contra el importador controlado y en directorios temporales.

Regenerar ZIP desde la raíz:

```sh
python3 scripts/generate-fixtures.py
```

Regenerar React con Node 22.23.1 o compatible, desde `web/`:

```sh
npm ci
node fixtures.mjs
```

React 19.3.1 y Vite 8.3.4 están fijados en `web/package-lock.json`. Las salidas compiladas se versionan para que las pruebas instrumentadas y la compilación Android no requieran Node. Los límites de tamaño se comprueban con valores pequeños inyectados en tests; no hay bombas ZIP grandes.
