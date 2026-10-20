import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { chromium } from '@playwright/test';
import { readFile } from 'node:fs/promises';
let browser;
before(async()=>{browser=await chromium.launch({executablePath:process.env.CHROME_PATH||(process.platform==='darwin'?'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome':chromium.executablePath()),headless:true});});
after(async()=>{await browser?.close();});
async function page(){
  const page=await browser.newPage({viewport:{width:600,height:700}});
  await page.setContent(`<style>body{margin:0;padding:20px}p{height:70px;background:#dfd;padding:10px}canvas{display:block;background:orange}</style><p id="a">Primer elemento estable</p><p id="b">Segundo elemento estable</p><button id="button" onclick="this.textContent='Funciona'">Interactuar</button><canvas id="canvas" width="300" height="100"></canvas><div style="height:1800px"></div>`);
  await page.evaluate(()=>{window.events=[];window.HyperMarkerBridge={postMessage:value=>window.events.push(JSON.parse(value))};});
  await page.addScriptTag({content:await readFile('../app/src/main/assets/annotation/controller.js','utf8')});
  await page.evaluate(()=>HyperMarker.init({sessionId:'session',documentId:'doc',revision:1}));
  return page;
}
async function mode(page,mode){await page.evaluate(mode=>HyperMarker.command('mode',{mode}),mode);}
async function pin(page,selector){
  const r=await page.locator(selector).boundingBox();await mode(page,'PIN');
  await page.mouse.move(r.x+30,r.y+30);await page.mouse.down();await page.waitForTimeout(600);await page.mouse.up();
  return page.evaluate(()=>events.filter(e=>e.type==='target.selected').at(-1));
}
test('long press highlights while held, follows movement and commits only on release',async()=>{
 const p=await page();await mode(p,'PIN');const a=await p.locator('#a').boundingBox(),b=await p.locator('#b').boundingBox();
 await p.mouse.move(a.x+40,a.y+25);await p.mouse.down();await p.waitForTimeout(600);
 assert.equal(await p.evaluate(()=>document.querySelector('#__hypermarker_overlay').shadowRoot.querySelector('.highlight').style.display),'block');
 assert.equal(await p.evaluate(()=>events.filter(e=>e.type==='target.selected').length),0);
 await p.mouse.move(b.x+40,b.y+25);await p.waitForTimeout(50);
 const top=await p.evaluate(()=>parseFloat(document.querySelector('#__hypermarker_overlay').shadowRoot.querySelector('.highlight').style.top));assert.equal(top,b.y);
 await p.mouse.up();const event=await p.evaluate(()=>events.filter(e=>e.type==='target.selected').at(-1));assert.equal(event.payload.anchor.selector,'#b');
 assert.equal(await p.evaluate(()=>document.querySelector('#__hypermarker_overlay').shadowRoot.querySelector('.highlight').style.display),'none');await p.close();
});
test('short tap and cancelled pending gesture create no pin, View restores buttons',async()=>{
 const p=await page();await mode(p,'PIN');await p.locator('#button').click();assert.equal(await p.locator('#button').textContent(),'Interactuar');
 await p.mouse.move(50,50);await p.mouse.down();await p.mouse.move(110,50);await p.waitForTimeout(550);await p.mouse.up();
 assert.equal(await p.evaluate(()=>events.filter(e=>e.type==='target.selected').length),0);
 await mode(p,'VIEW');await p.locator('#button').click();assert.equal(await p.locator('#button').textContent(),'Funciona');await p.close();
});
test('pin follows scroll/resize and a reused or missing target becomes unanchored',async()=>{
 const p=await page();const event=await pin(p,'#b');const pageKey=event.payload.pageKey;
 await p.evaluate(({anchor,pageKey})=>HyperMarker.command('annotations',[{id:'note',kind:'PIN',pageKey,payload:{anchor,title:'Idea',body:'**Nota**'}}]),{anchor:event.payload.anchor,pageKey});
 await mode(p,'VIEW');await p.waitForTimeout(60);
 assert.equal(await p.evaluate(()=>document.querySelector('#__hypermarker_overlay').shadowRoot.querySelectorAll('.pin').length),1);
 await p.evaluate(()=>scrollTo(0,100));await p.waitForTimeout(60);
 const geometry=await p.evaluate(()=>{const p=document.querySelector('#__hypermarker_overlay').shadowRoot.querySelector('.pin');return {top:parseFloat(p.style.top),rect:document.getElementById('b').getBoundingClientRect().top};});
 assert.ok(Math.abs(geometry.top-(geometry.rect+30))<1);
 await p.setViewportSize({width:450,height:700});await p.waitForTimeout(60);
 await p.locator('#b').evaluate(el=>el.textContent='Otra identidad');await p.waitForTimeout(70);
 assert.equal(await p.evaluate(()=>document.querySelector('#__hypermarker_overlay').shadowRoot.querySelectorAll('.pin').length),0);
 assert.deepEqual(await p.evaluate(()=>events.filter(e=>e.type==='anchor.unresolved').at(-1).payload.ids),['note']);await p.close();
});
test('canvas produces a surface anchor and accessible target selection works',async()=>{
 const p=await page();const event=await pin(p,'#canvas');assert.equal(event.payload.anchor.strategy,'SURFACE');
 const targets=await p.evaluate(()=>HyperMarker.accessibleTargets());assert.ok(targets.some(t=>t.selector==='#a'));
 await p.evaluate(()=>HyperMarker.command('selectTarget',{selector:'#a'}));assert.equal(await p.evaluate(()=>events.filter(e=>e.type==='target.selected').at(-1).payload.anchor.selector),'#a');await p.close();
});
test('drawing completes one transaction and does not block View',async()=>{
 const p=await page();await mode(p,'DRAW');await p.mouse.move(50,50);await p.mouse.down();await p.mouse.move(180,100,{steps:8});await p.mouse.up();
 const event=await p.evaluate(()=>events.find(e=>e.type==='stroke.completed'));assert.equal(event.payload.coordinates,'PAGE_CSS');assert.ok(event.payload.points.length>2);
 await mode(p,'VIEW');await p.locator('#button').click();assert.equal(await p.locator('#button').textContent(),'Funciona');await p.close();
});
