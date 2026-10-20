import { build } from 'esbuild';
import { mkdir, copyFile } from 'node:fs/promises';
const destination = '../app/src/main/assets/editor';
await mkdir(destination, { recursive: true });
await build({ entryPoints: ['src/editor.js'], bundle: true, minify: true, target: 'chrome100',
  outfile: destination + '/editor.js', loader: { '.woff2':'dataurl', '.woff':'dataurl', '.ttf':'dataurl', '.svg':'dataurl' },
  legalComments:'linked' });
await copyFile('src/index.html', destination + '/index.html');
