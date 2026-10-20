import { build } from 'vite';
import { resolve } from 'node:path';
await build({root:resolve('../fixtures/react-src'),base:'./',build:{outDir:resolve('../fixtures/react-built'),emptyOutDir:true},resolve:{alias:{react:resolve('node_modules/react'), 'react-dom':resolve('node_modules/react-dom')}}});

const {readFile,writeFile}=await import('node:fs/promises');
let notices='Fixture React compilado — licencias de terceros\n\n';
for(const name of ['react','react-dom','scheduler']){notices+=name+'\n'+await readFile('node_modules/'+name+'/LICENSE','utf8')+'\n';}
await writeFile('../fixtures/react-built/THIRD_PARTY_NOTICES.txt',notices);
