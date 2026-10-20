import { readFile, writeFile, mkdir, readdir } from 'node:fs/promises';
import { resolve } from 'node:path';
const lock=JSON.parse(await readFile('package-lock.json','utf8'));
let output='Hyper-Marker — componentes JavaScript distribuidos con el editor local\nGenerado con web/licenses.mjs. Versiones fijadas en package-lock.json.\n\n';
for(const [location,item] of Object.entries(lock.packages).sort(([a],[b])=>a.localeCompare(b))){
  if(!location||item.dev)continue;
  const path=resolve(location),pkg=JSON.parse(await readFile(path+'/package.json','utf8'));
  const names=await readdir(path);
  const licenses=names.filter(name=>/^(license|licence|copying|notice)(\.|$)/i.test(name));
  output+='='.repeat(76)+'\n'+pkg.name+' @ '+pkg.version+' · '+(pkg.license||'Ver texto de licencia')+'\n';
  if(!licenses.length) { output+='\n'+await readFile('license-overrides/'+pkg.name.replaceAll('/','_')+'.txt','utf8')+'\n'; }
  for(const file of licenses)output+='\n'+file+'\n'+await readFile(path+'/'+file,'utf8')+'\n';
}
await mkdir('../app/src/main/assets/licenses',{recursive:true});
await writeFile('../app/src/main/assets/licenses/WEB.txt',output);
