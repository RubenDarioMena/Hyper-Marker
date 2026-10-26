import {test} from 'node:test';
import assert from 'node:assert/strict';
import {chromium} from '@playwright/test';
import {createServer} from 'node:http';
import {readFile} from 'node:fs/promises';
import {resolve,extname} from 'node:path';
test('bundled Milkdown renders and edits Markdown without remote requests',async()=>{
 const root=resolve('../app/src/main/assets/editor');
 const server=createServer(async(req,res)=>{try{const file=resolve(root,'.'+req.url);if(!file.startsWith(root+'/'))throw Error();const body=await readFile(file);res.setHeader('Content-Type',({'.js':'application/javascript','.css':'text/css','.html':'text/html'})[extname(file)]||'text/plain');res.end(body);}catch{res.statusCode=404;res.end();}});
 await new Promise(done=>server.listen(0,'127.0.0.1',done));const origin='http://127.0.0.1:'+server.address().port;
 const browser=await chromium.launch({executablePath:process.env.CHROME_PATH||(process.platform==='darwin'?'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome':chromium.executablePath()),headless:true});
 try{const page=await browser.newPage();const remote=[];page.on('request',request=>{if(!request.url().startsWith(origin)&&!request.url().startsWith('data:'))remote.push(request.url());});
 await page.addInitScript(()=>{window.events=[];window.MarkdownBridge={postMessage:value=>events.push(JSON.parse(value))};});await page.goto(origin+'/index.html');
 await page.evaluate(()=>startEditor('# Título\n\n- Uno\n- Dos\n\n**Fuerte** y *énfasis* y [enlace](https://example.com).','test'));
 for(let i=0;i<50&&!await page.evaluate(()=>events.some(e=>e.type==='loaded'));i++)await new Promise(r=>setTimeout(r,50));assert.ok(await page.evaluate(()=>events.some(e=>e.type==='loaded')));assert.equal(await page.locator('h1').textContent(),'Título');assert.equal(await page.locator('.ProseMirror li').count(),2);
 await page.locator('[contenteditable=true]').click();await page.keyboard.press('ControlOrMeta+End');await page.keyboard.press('Enter');await page.keyboard.type('Una idea nueva');
 for(let i=0;i<50&&!await page.evaluate(()=>events.some(e=>e.type==='change'&&e.payload.source.includes('Una idea nueva')));i++)await new Promise(r=>setTimeout(r,50));
 const markdown=await page.evaluate(()=>editorMarkdown());assert.match(markdown,/Título/);assert.match(markdown,/\*\*Fuerte\*\*/);assert.match(markdown,/Una idea nueva/);assert.deepEqual(remote,[]);
 }finally{await browser.close();await new Promise(done=>server.close(done));}
});
test('toolbar commands format the current selection, insert tables and switch the sheet theme',async()=>{
 const root=resolve('../app/src/main/assets/editor');
 const server=createServer(async(req,res)=>{try{const file=resolve(root,'.'+req.url);if(!file.startsWith(root+'/'))throw Error();const body=await readFile(file);res.setHeader('Content-Type',({'.js':'application/javascript','.css':'text/css','.html':'text/html'})[extname(file)]||'text/plain');res.end(body);}catch{res.statusCode=404;res.end();}});
 await new Promise(done=>server.listen(0,'127.0.0.1',done));const origin='http://127.0.0.1:'+server.address().port;
 const browser=await chromium.launch({executablePath:process.env.CHROME_PATH||(process.platform==='darwin'?'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome':chromium.executablePath()),headless:true});
 try{const page=await browser.newPage();
 await page.addInitScript(()=>{window.events=[];window.MarkdownBridge={postMessage:value=>events.push(JSON.parse(value))};});await page.goto(origin+'/index.html');
 await page.evaluate(()=>startEditor('Una idea clave\n\nPrimero\n\n| A | B |\n| --- | --- |\n| 1 | 2 |\n','test'));
 for(let i=0;i<50&&!await page.evaluate(()=>events.some(e=>e.type==='loaded'));i++)await new Promise(r=>setTimeout(r,50));
 const select=(text)=>page.evaluate(text=>{const node=[...document.querySelectorAll('.ProseMirror p')].find(p=>p.textContent.includes(text)).firstChild;const start=node.textContent.indexOf(text);const range=document.createRange();range.setStart(node,start);range.setEnd(node,start+text.length);const s=getSelection();s.removeAllRanges();s.addRange(range);document.querySelector('.ProseMirror').focus();},text);
 await select('clave');await page.waitForTimeout(80);
 assert.equal(await page.evaluate(()=>editorCommand('bold')),true);
 assert.match(await page.evaluate(()=>editorMarkdown()),/Una idea \*\*clave\*\*/);
 await select('Primero');await page.waitForTimeout(80);
 await page.evaluate(()=>editorCommand('bullets'));
 assert.match(await page.evaluate(()=>editorMarkdown()),/^\* Primero|^- Primero/m);
 await page.evaluate(()=>editorCommand('heading',2));
 await page.evaluate(()=>editorCommand('table'));
 const markdown=await page.evaluate(()=>editorMarkdown());
 assert.equal((markdown.match(/^\|/gm)||[]).length>=6,true);assert.match(markdown,/\| A +\| B +\|/);
 await page.evaluate(()=>setEditorStyle({dark:true,font:'serif'}));
 assert.equal(await page.evaluate(()=>getComputedStyle(document.querySelector('.milkdown')).backgroundColor),'rgb(18, 18, 18)');
 assert.equal(await page.evaluate(()=>document.documentElement.dataset.font),'serif');
 }finally{await browser.close();await new Promise(done=>server.close(done));}
});
test('editor snapshot restores the same relative reading position',async()=>{
 const root=resolve('../app/src/main/assets/editor');
 const server=createServer(async(req,res)=>{try{const file=resolve(root,'.'+req.url);if(!file.startsWith(root+'/'))throw Error();const body=await readFile(file);res.setHeader('Content-Type',({'.js':'application/javascript','.css':'text/css','.html':'text/html'})[extname(file)]||'text/plain');res.end(body);}catch{res.statusCode=404;res.end();}});
 await new Promise(done=>server.listen(0,'127.0.0.1',done));
 const browser=await chromium.launch({executablePath:process.env.CHROME_PATH||(process.platform==='darwin'?'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome':chromium.executablePath()),headless:true});
 try{const page=await browser.newPage({viewport:{width:390,height:640}});
 await page.addInitScript(()=>{window.events=[];window.MarkdownBridge={postMessage:value=>events.push(JSON.parse(value))};});await page.goto('http://127.0.0.1:'+server.address().port+'/index.html');
 const source=Array.from({length:80},(_,i)=>`## Sección ${i}\n\nContenido de la sección ${i}.`).join('\n\n');
 await page.evaluate(value=>startEditor(value,'test'),source);
 for(let i=0;i<50&&!await page.evaluate(()=>events.some(e=>e.type==='loaded'));i++)await new Promise(r=>setTimeout(r,50));
 await page.evaluate(()=>editorSetScrollFraction(.6));await page.waitForTimeout(100);
 const snapshot=JSON.parse(await page.evaluate(()=>editorSnapshot()));
 assert.equal(snapshot.source.trimEnd(),source);assert.ok(Math.abs(snapshot.fraction-.6)<.03,snapshot.fraction);
 await page.evaluate(()=>editorSetScrollFraction(0));await page.waitForTimeout(100);
 assert.ok(JSON.parse(await page.evaluate(()=>editorSnapshot())).fraction<.01);
 }finally{await browser.close();await new Promise(done=>server.close(done));}
});
test('compiled React fixture resolves relative assets and remains interactive',async()=>{
 const root=resolve('../fixtures/react-built');
 const server=createServer(async(req,res)=>{try{const relative=req.url.replace(/^\/nested\//,'');const file=resolve(root,relative);if(!file.startsWith(root+'/'))throw Error();res.setHeader('Content-Type',({'.js':'application/javascript','.css':'text/css','.html':'text/html'})[extname(file)]||'text/plain');res.end(await readFile(file));}catch{res.statusCode=404;res.end();}});
 await new Promise(done=>server.listen(0,'127.0.0.1',done));
 const browser=await chromium.launch({executablePath:process.env.CHROME_PATH||(process.platform==='darwin'?'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome':chromium.executablePath()),headless:true});
 try{const page=await browser.newPage();await page.goto('http://127.0.0.1:'+server.address().port+'/nested/index.html');await page.locator('#react-counter').click();assert.equal(await page.locator('#react-counter').textContent(),'Explorar · 1');}
 finally{await browser.close();await new Promise(done=>server.close(done));}
});
