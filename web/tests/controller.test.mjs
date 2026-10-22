import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { chromium } from '@playwright/test';
import { readFile } from 'node:fs/promises';
let browser;
before(async()=>{browser=await chromium.launch({executablePath:process.env.CHROME_PATH||(process.platform==='darwin'?'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome':chromium.executablePath()),headless:true});});
after(async()=>{await browser?.close();});
async function page(){
  const page=await browser.newPage({viewport:{width:600,height:700}});
  await page.setContent(`<style>body{margin:0;padding:20px}p{height:70px;background:#dfd;padding:10px}canvas{display:block;background:orange}</style><p id="a">Primer elemento estable</p><p id="b">Segundo elemento estable</p><button id="button" onclick="this.textContent='Funciona'">Interactuar</button><canvas id="canvas" width="300" height="100"></canvas><p id="rich">Texto con <b id="bold">formato</b> interno</p><div style="height:1800px"></div>`);
  await page.evaluate(()=>{window.events=[];window.HyperMarkerBridge={postMessage:value=>window.events.push(JSON.parse(value))};});
  await page.addScriptTag({content:await readFile('../app/src/main/assets/annotation/controller.js','utf8')});
  await page.evaluate(()=>HyperMarker.init({sessionId:'session',documentId:'doc',revision:1}));
  return page;
}
const highlight=p=>p.evaluate(()=>document.querySelector('#__hypermarker_overlay').shadowRoot.querySelector('.highlight').style.display);
const last=(p,type)=>p.evaluate(type=>events.filter(e=>e.type===type).at(-1),type);
const count=(p,type)=>p.evaluate(type=>events.filter(e=>e.type===type).length,type);
async function mode(page,mode){await page.evaluate(mode=>HyperMarker.command('mode',{mode}),mode);}
async function hold(page,x,y){await page.mouse.move(x,y);await page.mouse.down();await page.waitForTimeout(600);await page.mouse.up();}
async function pin(page,selector){
  const r=await page.locator(selector).boundingBox();await mode(page,'EDIT');
  await hold(page,r.x+30,r.y+30);
  return last(page,'target.selected');
}
test('long press highlights while held, follows movement and keeps the target selected for the cross menu',async()=>{
 const p=await page();await mode(p,'EDIT');const a=await p.locator('#a').boundingBox(),b=await p.locator('#b').boundingBox();
 await p.mouse.move(a.x+40,a.y+25);await p.mouse.down();await p.waitForTimeout(600);
 assert.equal(await highlight(p),'block');
 assert.equal(await count(p,'hold'),1);
 assert.equal(await count(p,'target.selected'),0);
 await p.mouse.move(b.x+40,b.y+25);await p.waitForTimeout(50);
 const top=await p.evaluate(()=>parseFloat(document.querySelector('#__hypermarker_overlay').shadowRoot.querySelector('.highlight').style.top));assert.equal(top,b.y);
 await p.mouse.up();const event=await last(p,'target.selected');assert.equal(event.payload.anchor.selector,'#b');
 assert.equal(event.payload.text.editable,true);assert.equal(event.payload.text.text,'Segundo elemento estable');
 assert.equal(await highlight(p),'block');
 await p.evaluate(()=>HyperMarker.command('cancel',{}));
 assert.equal(await highlight(p),'none');
 await mode(p,'VIEW');await p.evaluate(()=>document.getElementById('button').click());assert.equal(await p.locator('#button').textContent(),'Funciona');await p.close();
});
test('short tap in Edit toggles chrome without activating content, cancelled gesture selects nothing, View restores buttons',async()=>{
 const p=await page();await mode(p,'EDIT');await p.locator('#button').click();assert.equal(await p.locator('#button').textContent(),'Interactuar');
 assert.equal(await count(p,'tap'),1);
 await p.mouse.move(50,50);await p.mouse.down();await p.mouse.move(110,50);await p.waitForTimeout(550);await p.mouse.up();
 assert.equal(await count(p,'target.selected'),0);
 await mode(p,'VIEW');await p.locator('#button').click();assert.equal(await p.locator('#button').textContent(),'Funciona');await p.close();
});
test('View taps toggle chrome only outside interactive content and long press opens the cross on a block',async()=>{
 const p=await page();
 await p.locator('#button').click();assert.equal(await count(p,'tap'),0);
 await p.mouse.click(300,650);assert.equal(await count(p,'tap'),1);
 const bold=await p.locator('#bold').boundingBox();
 await hold(p,bold.x+5,bold.y+5);
 const event=await last(p,'longpress');const rich=await p.locator('#rich').boundingBox();
 assert.equal(event.payload.target.tag,'p');assert.equal(event.payload.target.rect.y,rich.y);assert.equal(event.payload.target.rect.height,rich.height);
 assert.equal(await highlight(p),'block');assert.equal(await count(p,'tap'),1);
 await p.locator('#button').evaluate(el=>el.textContent='Interactuar');const button=await p.locator('#button').boundingBox();
 await hold(p,button.x+10,button.y+10);
 assert.equal(await p.locator('#button').textContent(),'Interactuar');assert.equal(await count(p,'longpress'),2);
 await p.close();
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
 assert.deepEqual((await last(p,'anchor.unresolved')).payload.ids,['note']);await p.close();
});
test('text overrides apply without touching the source, keep pins anchored and restore the original nodes',async()=>{
 const p=await page();const selected=await pin(p,'#rich');const pageKey=selected.payload.pageKey;
 assert.equal(selected.payload.text.formatted,true);assert.equal(selected.payload.text.text,'Texto con formato interno');
 await p.evaluate(()=>{window.boldNode=document.getElementById('bold');});
 const text={id:'text',kind:'TEXT',pageKey,payload:{anchor:selected.payload.anchor,original:selected.payload.text.raw,text:'Texto reemplazado'}};
 const note={id:'note',kind:'PIN',pageKey,payload:{anchor:selected.payload.anchor,title:'Idea',body:''}};
 await p.evaluate(list=>HyperMarker.command('annotations',list),[text,note]);await p.waitForTimeout(80);
 assert.equal(await p.locator('#rich').textContent(),'Texto reemplazado');
 assert.equal(await p.evaluate(()=>document.querySelector('#__hypermarker_overlay').shadowRoot.querySelectorAll('.pin').length),1);
 assert.deepEqual((await last(p,'anchor.unresolved')).payload.ids,[]);
 await p.evaluate(()=>HyperMarker.command('cancel',{}));
 const rich=await p.locator('#rich').boundingBox();await hold(p,rich.x+200,rich.y+20);const again=await last(p,'target.selected');
 assert.equal(again.payload.text.overrideId,'text');assert.equal(again.payload.text.original,'Texto con formato interno');
 assert.deepEqual(again.payload.pins,['note']);assert.deepEqual(again.payload.texts,['text']);
 await p.evaluate(list=>HyperMarker.command('annotations',list),[note]);await p.waitForTimeout(80);
 assert.equal(await p.locator('#rich').textContent(),'Texto con formato interno');
 assert.equal(await p.evaluate(()=>document.getElementById('bold')===window.boldNode),true);await p.close();
});
test('canvas produces a surface anchor and accessible target selection works',async()=>{
 const p=await page();const event=await pin(p,'#canvas');assert.equal(event.payload.anchor.strategy,'SURFACE');assert.equal(event.payload.text.editable,false);
 const targets=await p.evaluate(()=>HyperMarker.accessibleTargets());assert.ok(targets.some(t=>t.selector==='#a'));
 await p.evaluate(()=>HyperMarker.command('selectTarget',{selector:'#a'}));assert.equal((await last(p,'target.selected')).payload.anchor.selector,'#a');await p.close();
});
test('drawing completes one transaction, finger taps and long presses do not draw, View stays interactive',async()=>{
 const p=await page();await mode(p,'DRAW');await p.mouse.move(50,50);await p.mouse.down();await p.mouse.move(180,100,{steps:8});await p.mouse.up();
 const event=await p.evaluate(()=>events.find(e=>e.type==='stroke.completed'));assert.equal(event.payload.coordinates,'PAGE_CSS');assert.ok(event.payload.points.length>2);
 await p.mouse.click(300,300);assert.equal(await count(p,'tap'),1);
 await hold(p,300,320);assert.equal(await count(p,'longpress'),1);
 assert.equal(await count(p,'stroke.completed'),1);
 await mode(p,'VIEW');await p.locator('#button').click();assert.equal(await p.locator('#button').textContent(),'Funciona');await p.close();
});
