import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile, access } from 'node:fs/promises';
import { resolve, extname, sep } from 'node:path';
import { chromium } from '@playwright/test';
const root=resolve('../app/build/feedback-export-fixture');
let available=true;try{await access(root+'/review.html');}catch{available=false;}
test('exported ZIP review renders pins and SVG, preserves originals and opens complete PNG captures',{skip:!available},async()=>{
 const server=createServer(async(req,res)=>{try{const path=resolve(root,'.'+decodeURIComponent(new URL(req.url,'http://local').pathname));if(!path.startsWith(root+sep))throw Error('outside');const bytes=await readFile(path);res.setHeader('Content-Type',({'.html':'text/html; charset=utf-8','.js':'application/javascript','.png':'image/png','.css':'text/css'})[extname(path)]||'application/octet-stream');res.end(bytes);}catch{res.statusCode=404;res.end();}});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const browser=await chromium.launch({executablePath:process.env.CHROME_PATH||chromium.executablePath(),headless:true});
 try{
  const p=await browser.newPage();const base=`http://127.0.0.1:${server.address().port}`;
  const errors=[];p.on('pageerror',e=>errors.push(e.message));
  await p.goto(base+'/review.html');assert.equal(await p.locator('img').count(),2);
  assert.ok(await p.locator('img').evaluateAll(imgs=>imgs.every(i=>i.complete&&i.naturalWidth>0)));
  assert.equal(await p.locator('img[src=x]').count(),0);
  await p.goto(base+'/review/content/index.html#title');await p.waitForTimeout(200);
  assert.equal(await p.evaluate(()=>window.original),true);
  assert.equal(await p.evaluate(()=>document.querySelector('#__hypermarker_overlay').shadowRoot.querySelectorAll('path').length),3);
  const pin=p.locator('#__hypermarker_overlay').locator('.pin');await pin.click();
  assert.match(await p.locator('dialog').textContent(),/Revisar este elemento/);
  assert.equal(await p.locator('dialog img').count(),0);assert.deepEqual(errors,[]);
  await p.goto(base+'/original/index.html');assert.equal(await p.locator('#__hypermarker_overlay').count(),0);
 }finally{await browser.close();await new Promise(r=>server.close(r));}
});
