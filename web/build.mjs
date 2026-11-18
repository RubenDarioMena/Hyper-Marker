import { build } from 'esbuild';
import { mkdir, copyFile } from 'node:fs/promises';
const destination = '../app/src/main/assets/editor';
await mkdir(destination, { recursive: true });
await build({ entryPoints: ['src/editor.js'], bundle: true, minify: true, target: 'chrome100',
  outfile: destination + '/editor.js', loader: { '.woff2':'dataurl', '.woff':'dataurl', '.ttf':'dataurl', '.svg':'dataurl' },
  legalComments:'linked' });
await copyFile('src/index.html', destination + '/index.html');

// Visor Mermaid: el bundle oficial `mermaid.min.js` se copia sin cambios; el visor propio incrusta la fuente Recursive.
const diagrams = '../app/src/main/assets/mermaid';
await mkdir(diagrams, { recursive: true });
await build({ entryPoints: ['src/mermaid-viewer.js'], bundle: true, minify: true, target: 'chrome100', format: 'iife',
  outfile: diagrams + '/viewer.js', loader: { '.woff2':'dataurl' }, legalComments:'linked' });
await copyFile('node_modules/mermaid/dist/mermaid.min.js', diagrams + '/mermaid.min.js');
await copyFile('src/mermaid.html', diagrams + '/index.html');
