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
test('compiled React fixture resolves relative assets and remains interactive',async()=>{
 const root=resolve('../fixtures/react-built');
 const server=createServer(async(req,res)=>{try{const relative=req.url.replace(/^\/nested\//,'');const file=resolve(root,relative);if(!file.startsWith(root+'/'))throw Error();res.setHeader('Content-Type',({'.js':'application/javascript','.css':'text/css','.html':'text/html'})[extname(file)]||'text/plain');res.end(await readFile(file));}catch{res.statusCode=404;res.end();}});
 await new Promise(done=>server.listen(0,'127.0.0.1',done));
 const browser=await chromium.launch({executablePath:process.env.CHROME_PATH||(process.platform==='darwin'?'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome':chromium.executablePath()),headless:true});
 try{const page=await browser.newPage();await page.goto('http://127.0.0.1:'+server.address().port+'/nested/index.html');await page.locator('#react-counter').click();assert.equal(await page.locator('#react-counter').textContent(),'Explorar · 1');}
 finally{await browser.close();await new Promise(done=>server.close(done));}
});
