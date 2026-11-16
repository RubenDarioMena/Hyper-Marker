(() => {
  'use strict';
  if (window.HyperMarker) { window.HyperMarker.destroy(); }
  const state = { mode:'VIEW', annotations:[], config:null, target:null, selected:false, pending:null, active:false, held:false,
    pointer:null, pointerType:'', start:null, startTime:0, suppressClick:false, tool:'pen', color:'#176b5b', width:3, points:[], accessible:false, wheelsEnabled:true, drawTarget:null, drawingId:null };
  const host = document.createElement('div');
  host.id = '__hypermarker_overlay';
  host.style.cssText = 'position:fixed;inset:0;z-index:2147483647;pointer-events:none;contain:layout style;';
  const shadow = host.attachShadow({mode:'open'});
  shadow.innerHTML = `<style>
    :host{all:initial}*{box-sizing:border-box}.highlight{position:fixed;background:rgba(255,185,55,.24);border:3px solid #b95700;outline:1px solid white;pointer-events:none;display:none}
    .pin{position:fixed;width:48px;height:48px;border-radius:50%;background:#176b5b;color:white;border:2px solid white;box-shadow:0 2px 7px #0005;transform:translate(-50%,-50%);pointer-events:auto;font:700 16px system-ui;min-height:48px;padding:0}
    .progress{position:fixed;width:96px;height:96px;border-radius:50%;display:none;border:4px solid white;box-shadow:0 2px 10px #0008;pointer-events:none;background:#00e676;color:#003b20;font:700 22px system-ui;text-align:center;line-height:88px}
    svg{position:fixed;inset:0;width:100%;height:100%;overflow:visible;pointer-events:none}
  </style><svg xmlns="http://www.w3.org/2000/svg"></svg><div class="highlight"></div><div class="pins"></div><div class="progress" role="progressbar" aria-label="Mantén presionado para dibujar"></div>`;
  document.documentElement.append(host);
  const highlight = shadow.querySelector('.highlight');
  const pins = shadow.querySelector('.pins');
  const progress=shadow.querySelector('.progress');
  let progressFrame=0;
  function stopProgress(){cancelAnimationFrame(progressFrame);progress.style.display='none';}
  function drawProgress(){
    if(state.mode!=='DRAW'||state.drawTarget||!state.start)return;
    const amount=Math.min(1,(performance.now()-state.startTime)/delay());
    Object.assign(progress.style,{display:'block',left:Math.max(0,Math.min(innerWidth-96,state.start[0]-48))+'px',top:Math.max(0,state.start[1]-125)+'px',background:`conic-gradient(#00e676 ${amount*360}deg,#34483e 0deg)`});
    progress.textContent=Math.ceil((1-amount)*delay()/100)/10+' s';
    progressFrame=requestAnimationFrame(drawProgress);
  }
  const svg = shadow.querySelector('svg');
  const css = document.createElement('style');
  css.textContent = 'html[data-hypermarker-mode="EDIT"] *,html[data-hypermarker-mode="DRAW"] *{-webkit-user-select:none!important;user-select:none!important}html[data-hypermarker-mode="DRAW"]{touch-action:none!important}';
  document.documentElement.append(css);
  const removals=[];
  const listen=(target,type,fn,options)=>{target.addEventListener(type,fn,options);removals.push(()=>target.removeEventListener(type,fn,options));};
  let request=0, renderQueued=false, lastUnresolved='';
  // Elementos con texto sustituido: el original se conserva para restaurarlo sin recargar.
  const applied=new Map();
  const pageKey=()=>location.pathname+location.search+(/^#(?:\/|!)/.test(location.hash)?location.hash:'');
  function send(type,payload={}) {
    if (!state.config) return;
    window.HyperMarkerBridge?.postMessage(JSON.stringify({...state.config,protocolVersion:1,requestId:String(++request),type,payload:{...payload,pageKey:pageKey()}}));
  }
  const snippet=el=>(el.textContent||'').replace(/\s+/g,' ').trim().slice(0,160);
  const snippetOf=el=>applied.get(el)?.snippet ?? snippet(el);
  const own=el=>el===host||el===css;
  const delay=()=>Math.max(500,Math.min(2000,state.config?.longPressMs||500));
  function selector(el) {
    if (el.id && document.querySelectorAll('#'+CSS.escape(el.id)).length===1) return '#'+CSS.escape(el.id);
    for (const key of ['data-anchor','data-testid']) {
      const value=el.getAttribute(key);
      if (value && value.length<120) {
        const candidate='['+key+'="'+CSS.escape(value)+'"]';
        if(document.querySelectorAll(candidate).length===1)return candidate;
      }
    }
    const parts=[];
    while(el && el!==document.documentElement && parts.length<30) {
      const tag=el.localName;
      const siblings=el.parentElement?Array.from(el.parentElement.children).filter(node=>node.localName===tag && !own(node)):[];
      parts.unshift(tag+':nth-of-type('+(siblings.indexOf(el)+1)+')');
      el=el.parentElement;
    }
    return 'html>'+parts.join('>');
  }
  function findTarget(x,y) {
    const elements=document.elementsFromPoint(x,y).filter(el=>!own(el)&&el!==document.documentElement&&el!==document.body);
    let el=elements.find(node=>{const r=node.getBoundingClientRect();return r.width>1&&r.height>1&&getComputedStyle(node).visibility!=='hidden';});
    if (!el) el=document.body;
    const surface=el.closest('canvas,svg,iframe');
    if(surface)el=surface;
    else {const interactive=el.closest('a,button,input,textarea,select');if(interactive)el=interactive;}
    if(!el)return null;
    const r=el.getBoundingClientRect();
    if(r.width<=0||r.height<=0)return null;
    return {el,anchor:{strategy:/^(canvas|svg|iframe|body)$/i.test(el.localName)?'SURFACE':'DOM',selector:selector(el),tag:el.localName,
      text:snippetOf(el),fallbackX:x+scrollX,fallbackY:y+scrollY,bounds:{width:r.width,height:r.height},precision:(getComputedStyle(el).transform!=='none'||(r.width>innerWidth*.9&&r.height>innerHeight*.9))?'BOUNDING_RECT':'ELEMENT',ancestors:Array.from((function*(n){let i=0;while(n&&i++<4){yield n.localName;n=n.parentElement;}})(el.parentElement)),
      relativeX:Math.max(0,Math.min(1,(x-r.left)/r.width)),relativeY:Math.max(0,Math.min(1,(y-r.top)/r.height)),status:'RESOLVED'},pageKey:pageKey()};
  }
  // En View se prefiere el bloque visible que contiene el punto, para que el zoom encuadre algo legible.
  function viewTarget(x,y) {
    const target=findTarget(x,y);
    if(!target)return null;
    let el=target.el;
    while(el.parentElement&&el.parentElement!==document.body&&/^inline/.test(getComputedStyle(el).display)&&!/^(img|canvas|svg|video|iframe)$/i.test(el.localName))el=el.parentElement;
    return {...target,el};
  }
  function showTarget(target) {
    state.target=target;
    if(!target){highlight.style.display='none';return;}
    const r=target.el.getBoundingClientRect();
    Object.assign(highlight.style,{borderColor:state.mode==='DRAW'?'#00e676':'#b95700',background:state.mode==='DRAW'?'rgba(0,230,118,.08)':'rgba(255,185,55,.24)',display:'block',left:r.left+'px',top:r.top+'px',width:r.width+'px',height:r.height+'px'});
  }
  function release() {
    stopProgress();clearTimeout(state.pending);state.pending=null;state.active=false;state.held=false;state.pointer=null;state.start=null;state.points=[];schedule();
  }
  function cancel() { release();state.selected=false;state.suppressClick=false;showTarget(state.drawTarget); }
  function selectAt(x,y) { showTarget(state.mode==='VIEW'?viewTarget(x,y):findTarget(x,y)); }
  function resolve(anchor) {
    try {
      const matches=document.querySelectorAll(anchor.selector);
      if(matches.length!==1)return null;
      const el=matches[0];
      if(el.localName!==anchor.tag || snippetOf(el)!==anchor.text)return null;
      const r=el.getBoundingClientRect();
      return r.width>0&&r.height>0?el:null;
    } catch {return null;}
  }
  const preformatted=el=>/^pre/.test(getComputedStyle(el).whiteSpace);
  const displayText=el=>preformatted(el)?el.textContent:el.textContent.replace(/\s+/g,' ').trim();
  function textInfo(el) {
    const entry=applied.get(el);
    const editable=!/^(input|textarea|select|option|canvas|svg|iframe|img|video|audio|body|html|script|style)$/i.test(el.localName)&&!el.isContentEditable&&
      (entry?entry.raw:el.textContent).length<=20000&&el.textContent.trim().length>0;
    if(!editable)return {editable:false};
    return {editable:true,text:displayText(el),original:entry?entry.display:displayText(el),raw:entry?entry.raw:el.textContent,formatted:entry?entry.formatted:el.children.length>0,overrideId:entry?.id||''};
  }
  function elementOf(annotation) {
    if(annotation.kind==='TEXT'){for(const [el,entry] of applied)if(entry.id===annotation.id)return el;return null;}
    return resolve(annotation.payload.anchor);
  }
  // Pines y textos del elemento, sus descendientes o contenedores no globales.
  function related(target) {
    const pinIds=[],textIds=[];
    for(const annotation of state.annotations){
      if(annotation.pageKey!==pageKey()||annotation.kind==='STROKE')continue;
      const el=elementOf(annotation);
      if(!el||!(target.contains(el)||(el!==document.body&&el!==document.documentElement&&el.contains(target))))continue;
      (annotation.kind==='PIN'?pinIds:textIds).push(annotation.id);
    }
    return {pins:pinIds,texts:textIds};
  }
  function restore(el) {
    const entry=applied.get(el);
    applied.delete(el);
    if(entry&&el.textContent===entry.text)el.replaceChildren(...entry.nodes);
  }
  function applyOverrides(list,unresolved) {
    const active=new Set();
    for(const annotation of list){
      const data=annotation.payload;
      const el=resolve(data.anchor);
      const entry=el&&applied.get(el);
      if(entry&&entry.id!==annotation.id){unresolved.push(annotation.id);continue;}
      if(entry){
        if(el.textContent!==data.text){
          if(el.textContent===entry.text||el.textContent===entry.raw){el.textContent=data.text;entry.text=data.text;}
          else{applied.delete(el);unresolved.push(annotation.id);continue;}
        }
        active.add(annotation.id);continue;
      }
      if(!el||el.textContent!==data.original){unresolved.push(annotation.id);continue;}
      applied.set(el,{id:annotation.id,nodes:Array.from(el.childNodes),snippet:snippet(el),display:displayText(el),raw:el.textContent,formatted:el.children.length>0,text:data.text});
      el.textContent=data.text;active.add(annotation.id);
    }
    for(const [el,entry] of Array.from(applied))if(!active.has(entry.id))restore(el);
  }
  const point=e=>{const r=state.drawTarget?.el.getBoundingClientRect();return r?[e.clientX-r.left-r.width/2,e.clientY-r.top-r.height/2]:[Math.max(0,e.clientX+scrollX),Math.max(0,e.clientY+scrollY)];};
  function path(points) {return points.map((p,i)=>(i?'L':'M')+p[0]+' '+p[1]).join(' ');}
  function makeStroke(data,id) {
    const element=document.createElementNS('http://www.w3.org/2000/svg','path');
    let points=data.points;
    if(data.coordinates==='ELEMENT_CENTER_CSS'){const el=resolve(data.anchor);if(!el)return null;const r=el.getBoundingClientRect();points=points.map(p=>[p[0]+r.left+r.width/2+scrollX,p[1]+r.top+r.height/2+scrollY]);}
    element.setAttribute('d',path(points));element.setAttribute('stroke',data.color);element.setAttribute('stroke-width',String(data.width));
    element.setAttribute('stroke-linecap','round');element.setAttribute('stroke-linejoin','round');element.setAttribute('fill','none');
    element.setAttribute('opacity',data.tool==='highlighter'?'.3':'1');
    if(state.mode==='DRAW'&&state.drawTarget&&state.tool==='eraser'&&id){element.style.pointerEvents='stroke';element.addEventListener('pointerdown',e=>{e.stopPropagation();e.preventDefault();send('stroke.erase',{id});});}
    return element;
  }
  function render() {
    renderQueued=false;
    svg.setAttribute('viewBox',`${scrollX} ${scrollY} ${innerWidth} ${innerHeight}`);
    svg.replaceChildren();pins.replaceChildren();
    const unresolved=[];
    const current=state.annotations.filter(a=>a.pageKey===pageKey());
    applyOverrides(current.filter(a=>a.kind==='TEXT'),unresolved);
    for(const annotation of current) {
      if(annotation.kind==='TEXT')continue;
      if(annotation.kind==='STROKE') {const stroke=makeStroke(annotation.payload,annotation.id);if(stroke)svg.append(stroke);else unresolved.push(annotation.id);continue;}
      const data=annotation.payload,el=resolve(data.anchor);
      if(!el){unresolved.push(annotation.id);continue;}
      const r=el.getBoundingClientRect();
      const marker=document.createElement('button');marker.className='pin';marker.textContent='•';marker.setAttribute('aria-label','Abrir nota: '+(data.title||'Pin'));
      marker.style.left=(r.left+r.width*data.anchor.relativeX)+'px';marker.style.top=(r.top+r.height*data.anchor.relativeY)+'px';
      marker.addEventListener('pointerdown',e=>e.stopPropagation());marker.addEventListener('click',e=>{e.stopPropagation();send('annotation.activated',{id:annotation.id});});pins.append(marker);
    }
    if(state.points.length)svg.append(makeStroke({anchor:state.drawTarget?.anchor,coordinates:'ELEMENT_CENTER_CSS',points:state.points,color:state.color,width:state.tool==='highlighter'?state.width*3:state.width,tool:state.tool}));
    const unresolvedKey=pageKey()+':'+unresolved.join(',');
    if(unresolvedKey!==lastUnresolved){lastUnresolved=unresolvedKey;send('anchor.unresolved',{ids:unresolved});}
    if(state.drawTarget&&!state.drawTarget.el.isConnected){state.drawTarget=null;cancel();send('drawing.cancelled');}
    if(state.drawTarget)showTarget(state.drawTarget);
    if((state.active||state.selected)&&state.target&&state.target.el.isConnected)showTarget(state.target);
  }
  function schedule() {if(!renderQueued){renderQueued=true;requestAnimationFrame(render);}}
  function stopContent(e) {if((state.mode!=='VIEW'||state.held)&&!e.composedPath().includes(host))e.stopImmediatePropagation();}
  const interactive=el=>el instanceof Element&&(!!el.closest('a,button,input,textarea,select,label,summary,video,audio,iframe,[role=button],[role=link],[onclick],[contenteditable]')||getComputedStyle(el).cursor==='pointer');
  const docRect=el=>{const r=el.getBoundingClientRect();return {x:r.left+scrollX,y:r.top+scrollY,width:r.width,height:r.height};};
  function selection(target) {
    return {anchor:target.anchor,text:textInfo(target.el),...related(target.el),
      boundary:target.anchor.strategy==='SURFACE'?'Ancla de superficie; no selecciona objetos dibujados.':target.anchor.precision==='BOUNDING_RECT'?'Región grande o transformada: el pin sigue el rectángulo visible del elemento.':'Ancla del elemento resaltado.'};
  }
  // Pulsación prolongada: View y Edit resaltan el elemento y permiten deslizar; Draw descarta el trazo iniciado.
  function hold() {
    clearTimeout(state.pending);state.pending=null;
    if(state.pointer===null)return;
    if(state.mode==='DRAW'&&!state.drawTarget){
      stopProgress();selectAt(state.start[0],state.start[1]);
      if(!state.target)return;
      state.drawTarget=state.target;state.drawingId=crypto.randomUUID?.()||('drawing-'+Date.now()+'-'+Math.random().toString(36).slice(2));state.held=true;state.suppressClick=true;
      highlight.animate([{background:'rgba(0,255,100,.5)'},{background:'rgba(0,230,118,.08)'}],{duration:350});
      send('drawing.started',{anchor:state.drawTarget.anchor});send('hold');return;
    }
    if(!state.wheelsEnabled){
      if(state.mode==='DRAW'){state.active=false;state.points=[];schedule();}
      return;
    }
    state.held=true;state.suppressClick=true;
    if(state.mode==='DRAW'){state.active=false;state.points=[];schedule();}
    else{state.active=true;selectAt(state.start[0],state.start[1]);}
    send('hold');
  }
  listen(window,'pointerdown',e=>{
    if(e.composedPath().includes(host))return;
    if(!e.isPrimary||state.pointer!==null){cancel();return;}
    if(e.pointerType==='mouse'&&e.button!==0)return;
    if(state.selected)cancel();
    stopContent(e);state.pointer=e.pointerId;state.pointerType=e.pointerType;state.start=[e.clientX,e.clientY];state.startTime=performance.now();state.suppressClick=false;
    if(state.mode==='DRAW') {
      e.preventDefault();
      if(!state.drawTarget){selectAt(e.clientX,e.clientY);state.pending=setTimeout(hold,delay());drawProgress();}
      else if(state.tool!=='eraser'){state.active=true;state.points=[point(e)];schedule();}
      return;
    }
    if(state.mode==='EDIT'&&state.accessible){selectAt(e.clientX,e.clientY);state.active=true;return;}
    state.pending=setTimeout(hold,delay());
  },{capture:true,passive:false});
  listen(window,'pointermove',e=>{
    if(e.pointerId!==state.pointer)return;
    const far=Math.hypot(e.clientX-state.start[0],e.clientY-state.start[1])>12;
    stopContent(e);
    if(state.mode==='DRAW'){
      e.preventDefault();
      if(far&&state.pending){clearTimeout(state.pending);state.pending=null;stopProgress();}
      if(state.active){if(state.points.length<20000)state.points.push(point(e));schedule();}
      return;
    }
    if(state.active){e.preventDefault();selectAt(e.clientX,e.clientY);}
    else if(far)cancel();
  },{capture:true,passive:false});
  listen(window,'touchmove',e=>{if((state.active||state.held)&&e.touches.length===1)e.preventDefault();else if(e.touches.length>1)cancel();},{capture:true,passive:false});
  listen(window,'pointerup',e=>{
    if(e.pointerId!==state.pointer)return;
    stopContent(e);
    const quick=!state.held&&e.timeStamp-state.startTime<350&&Math.hypot(e.clientX-state.start[0],e.clientY-state.start[1])<=12;
    if(state.mode==='VIEW'){
      if(state.held){
        e.preventDefault();state.selected=!!state.target;
        send('longpress',{x:e.clientX,y:e.clientY,target:state.target?{rect:docRect(state.target.el),tag:state.target.el.localName}:null,viewport:{width:innerWidth,height:innerHeight}});
        release();
      } else cancel();
      return;
    }
    if(state.mode==='EDIT'){
      if(state.active&&state.target){state.selected=true;send('target.selected',selection(state.target));release();return;}
      if(quick)send('tap');
    }
    if(state.mode==='DRAW'){
      if(!state.held&&state.drawTarget&&state.active&&state.points.length){
        if(state.points.length===1)state.points.push(state.points[0].slice());
        send('stroke.completed',{points:state.points,color:state.color,width:state.tool==='highlighter'?Math.min(50,state.width*3):state.width,tool:state.tool,coordinates:'ELEMENT_CENTER_CSS',drawingId:state.drawingId,anchor:state.drawTarget.anchor,viewport:{width:innerWidth,height:innerHeight,scale:visualViewport?.scale||1}});
      }
    }
    cancel();
  },{capture:true,passive:false});
  listen(window,'pointercancel',e=>{if(e.pointerId===state.pointer)cancel();},true);
  for(const type of ['click','dblclick','contextmenu','selectstart'])listen(window,type,e=>{
    if(e.composedPath().includes(host))return;
    if(state.mode==='VIEW'){
      if(!state.wheelsEnabled)return;
      // El menú contextual nativo de Chromium también marca una pulsación prolongada.
      if(type==='contextmenu'&&state.pending)hold();
      if(!state.held&&!state.suppressClick)return;
      if(type==='click')state.suppressClick=false;
    }
    e.preventDefault();e.stopImmediatePropagation();
  },{capture:true,passive:false});
  listen(window,'click',e=>{if(state.mode==='VIEW'&&!e.defaultPrevented&&!e.composedPath().includes(host)&&!interactive(e.target))send('tap');});
  listen(window,'scroll',schedule,{passive:true});listen(window,'resize',schedule);listen(window,'hashchange',schedule);listen(window,'popstate',schedule);
  if(window.visualViewport){listen(visualViewport,'resize',schedule);listen(visualViewport,'scroll',schedule);}
  const observer=new MutationObserver(mutations=>{if(mutations.some(m=>!own(m.target)&&!host.contains(m.target)))schedule();});
  observer.observe(document.body||document.documentElement,{subtree:true,childList:true,characterData:true,attributes:true,attributeFilter:['class','style','id']});
  let autoFrame=0,autoLast=0,autoSpeed=0;
  function autoscroll(time) {if(!autoSpeed)return;if(autoLast)window.scrollBy(0,autoSpeed*(time-autoLast)/1000);autoLast=time;autoFrame=requestAnimationFrame(autoscroll);}
  function stopAuto(){autoSpeed=0;autoLast=0;cancelAnimationFrame(autoFrame);}
  listen(document,'touchstart',stopAuto,{passive:true});
  // Respaldo de formularios: lo escrito o elegido se envía a Android y se restaura al recargar o recrear el visor.
  const forms={dirty:new Map(),timer:0,retry:0,restoring:false};
  const skipInput=/^(password|hidden|file|submit|button|reset|image)$/i;
  const valueSetter=el=>Object.getOwnPropertyDescriptor(({input:HTMLInputElement,textarea:HTMLTextAreaElement,select:HTMLSelectElement})[el.localName]?.prototype||{},'value')?.set;
  function formRoot(node){
    if(!(node instanceof Element)||own(node)||host.contains(node))return null;
    if(node.localName==='input')return skipInput.test(node.type)||/(cc-|one-time-code|password)/i.test(node.autocomplete||'')?null:node;
    if(node.localName==='textarea'||node.localName==='select')return node;
    return node.isContentEditable?node.closest('[contenteditable]:not([contenteditable="false"])'):null;
  }
  function fieldState(el){
    const k=selector(el).slice(0,4200);
    if(el.localName==='input'&&/^(checkbox|radio)$/i.test(el.type))return {k,c:el.checked};
    if(el.localName==='select'&&el.multiple)return {k,s:Array.from(el.selectedOptions).slice(0,500).map(o=>o.value.slice(0,1000))};
    if(/^(input|textarea|select)$/.test(el.localName))return {k,v:String(el.value).slice(0,20000)};
    return el.innerHTML.length<=20000?{k,h:el.innerHTML}:{k,v:(el.textContent||'').slice(0,20000)};
  }
  function sendForms(){
    clearTimeout(forms.timer);forms.timer=0;
    const fields=[];let size=0;
    for(const value of Array.from(forms.dirty.values()).reverse()){size+=JSON.stringify(value).length;if(size>450000||fields.length>=500)break;fields.unshift(value);}
    send('form.state',{fields});
  }
  function record(el){
    if(el.localName==='input'&&el.type==='radio'&&el.name){
      const scope=el.form||document;
      for(const radio of scope.querySelectorAll('input[type=radio]'))if(radio.name===el.name&&formRoot(radio)){const value=fieldState(radio);forms.dirty.set(value.k,value);}
    } else {const value=fieldState(el);forms.dirty.set(value.k,value);}
    clearTimeout(forms.timer);forms.timer=setTimeout(sendForms,400);
  }
  function onForm(e){if(forms.restoring)return;const el=formRoot(e.target);if(el)record(el);}
  listen(document,'input',onForm,true);listen(document,'change',onForm,true);
  listen(document,'reset',e=>setTimeout(()=>{if(e.target?.elements)for(const el of e.target.elements){const root=formRoot(el);if(root)record(root);}},0),true);
  const flushForms=()=>{if(forms.timer)sendForms();};
  listen(window,'pagehide',flushForms);listen(document,'visibilitychange',()=>{if(document.hidden)flushForms();});
  function fire(el){el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));}
  function applyField(field){
    let el;try{el=document.querySelector(field.k);}catch(_){return true;}
    el=el&&formRoot(el);
    if(!el)return false;
    if('c' in field){if(el.checked!==field.c){el.checked=!!field.c;fire(el);}}
    else if(Array.isArray(field.s)){if(el.options){for(const option of el.options)option.selected=field.s.includes(option.value);fire(el);}}
    else if(/^(input|textarea|select)$/.test(el.localName)){if(el.value!==field.v){const set=valueSetter(el);set?set.call(el,field.v):el.value=field.v;fire(el);}}
    else if('h' in field){if(el.innerHTML!==field.h){el.innerHTML=field.h;fire(el);}}
    else if(el.textContent!==field.v){el.textContent=field.v;fire(el);}
    forms.dirty.set(field.k,field);
    return true;
  }
  function restoreForms(fields){
    clearInterval(forms.retry);
    let pending=fields.filter(field=>field&&typeof field.k==='string');
    // Se conservan aunque su campo aún no exista, para no perderlos si se envía otro cambio antes.
    for(const field of pending)forms.dirty.set(field.k,field);
    const started=performance.now();
    const attempt=()=>{
      forms.restoring=true;
      try{pending=pending.filter(field=>!applyField(field));}finally{forms.restoring=false;}
      // Páginas que generan el formulario después de cargar: se reintenta unos segundos.
      if(!pending.length||performance.now()-started>15000){clearInterval(forms.retry);}
    };
    attempt();
    if(pending.length)forms.retry=setInterval(attempt,400);
  }
  window.HyperMarker={
    init(config){state.config=config;send('controller.ready');schedule();},
    command(type,payload){
      if(type==='mode'){if(payload.mode!==state.mode){state.drawTarget=null;}cancel();stopAuto();state.mode=payload.mode;state.accessible=!!payload.accessible;state.wheelsEnabled=payload.wheelsEnabled!==false;document.documentElement.dataset.hypermarkerMode=state.mode;}
      if(type==='annotations'){state.annotations=payload;lastUnresolved='';}
      if(type==='form.restore'){restoreForms(Array.isArray(payload.fields)?payload.fields:[]);}
      if(type==='tool'){state.tool=payload.tool;state.color=payload.color;state.width=payload.width;}
      if(type==='selectTarget'){
        const el=document.querySelector(payload.selector);if(el){el.scrollIntoView({block:'center'});const r=el.getBoundingClientRect();selectAt(r.left+r.width/2,r.top+r.height/2);
        if(state.target){const value=selection(state.target);send('target.selected',{...value,boundary:value.anchor.strategy==='SURFACE'?'Ancla de superficie':'Elemento elegido sin pulsación prolongada'});state.selected=true;}}
      }
      if(type==='cancel'){state.drawTarget=null;cancel();}
      if(type==='inspect'){const value=state.annotations.find(a=>a.id===payload.id);const el=value&&value.pageKey===pageKey()&&elementOf(value);if(el){el.scrollIntoView({block:'center'});showTarget({el});setTimeout(()=>{if(!state.selected&&!state.active)showTarget(null);},2000);}}
      if(type==='autoscroll'){stopAuto();autoSpeed=payload.speed||0;if(autoSpeed)autoFrame=requestAnimationFrame(autoscroll);}
      schedule();
    },
    exportDrawing(ids){
      render();
      const strokes=state.annotations.filter(a=>ids.includes(a.id)&&a.kind==='STROKE'&&a.pageKey===pageKey());
      let context=strokes.length===ids.length;
      const paths=strokes.map(a=>{const d=a.payload;let points=d.points;
        if(d.coordinates==='ELEMENT_CENTER_CSS'){const el=resolve(d.anchor);const r=el?.getBoundingClientRect();if(!r)context=false;
          points=points.map(p=>[p[0]+(r?r.left+r.width/2+scrollX:0),p[1]+(r?r.top+r.height/2+scrollY:0)]);}
        return {points,color:d.color,width:d.width,tool:d.tool};});
      host.style.visibility='hidden';
      return {context,paths};
    },
    accessibleTargets(){return Array.from(document.querySelectorAll('h1,h2,h3,p,li,a,button,canvas,svg,img,iframe,input')).filter(el=>{const r=el.getBoundingClientRect();return r.width>1&&r.height>1&&r.bottom>0&&r.top<innerHeight;}).slice(0,150).map(el=>({selector:selector(el),label:el.localName+' · '+(snippetOf(el)||el.getAttribute('alt')||'Superficie visual').slice(0,100)}));},
    destroy(){flushForms();clearInterval(forms.retry);cancel();stopAuto();observer.disconnect();removals.forEach(remove=>remove());for(const el of Array.from(applied.keys()))restore(el);host.remove();css.remove();delete document.documentElement.dataset.hypermarkerMode;delete window.HyperMarker;}
  };
})();
