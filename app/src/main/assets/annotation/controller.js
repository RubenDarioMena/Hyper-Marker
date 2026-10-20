(() => {
  'use strict';
  if (window.HyperMarker) { window.HyperMarker.destroy(); }
  const original = { mode:'VIEW', annotations:[], config:null, target:null, pending:null, active:false,
    pointer:null, start:null, tool:'pen', color:'#176b5b', width:3, points:[], accessible:false };
  const state = original;
  const host = document.createElement('div');
  host.id = '__hypermarker_overlay';
  host.style.cssText = 'position:fixed;inset:0;z-index:2147483647;pointer-events:none;contain:layout style;';
  const shadow = host.attachShadow({mode:'open'});
  shadow.innerHTML = `<style>
    :host{all:initial}*{box-sizing:border-box}.highlight{position:fixed;background:rgba(255,185,55,.24);border:3px solid #b95700;outline:1px solid white;pointer-events:none;display:none}
    .pin{position:fixed;width:48px;height:48px;border-radius:50%;background:#176b5b;color:white;border:2px solid white;box-shadow:0 2px 7px #0005;transform:translate(-50%,-50%);pointer-events:auto;font:700 16px system-ui;min-height:48px;padding:0}
    svg{position:fixed;inset:0;width:100%;height:100%;overflow:visible;pointer-events:none}
  </style><svg xmlns="http://www.w3.org/2000/svg"></svg><div class="highlight"></div><div class="pins"></div>`;
  document.documentElement.append(host);
  const highlight = shadow.querySelector('.highlight');
  const pins = shadow.querySelector('.pins');
  const svg = shadow.querySelector('svg');
  const css = document.createElement('style');
  css.textContent = 'html[data-hypermarker-mode="PIN"] *,html[data-hypermarker-mode="DRAW"] *{-webkit-user-select:none!important;user-select:none!important}html[data-hypermarker-mode="DRAW"]{touch-action:none!important}';
  document.documentElement.append(css);
  const removals=[];
  const listen=(target,type,fn,options)=>{target.addEventListener(type,fn,options);removals.push(()=>target.removeEventListener(type,fn,options));};
  let request=0, renderQueued=false, lastUnresolved='';
  const pageKey=()=>location.pathname+location.search+(/^#(?:\/|!)/.test(location.hash)?location.hash:'');
  function send(type,payload={}) {
    if (!state.config) return;
    window.HyperMarkerBridge?.postMessage(JSON.stringify({...state.config,protocolVersion:1,requestId:String(++request),type,payload:{...payload,pageKey:pageKey()}}));
  }
  const snippet=el=>(el.textContent||'').replace(/\s+/g,' ').trim().slice(0,160);
  const own=el=>el===host||el===css;
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
      text:snippet(el),fallbackX:x+scrollX,fallbackY:y+scrollY,bounds:{width:r.width,height:r.height},precision:(getComputedStyle(el).transform!=='none'||(r.width>innerWidth*.9&&r.height>innerHeight*.9))?'BOUNDING_RECT':'ELEMENT',ancestors:Array.from((function*(n){let i=0;while(n&&i++<4){yield n.localName;n=n.parentElement;}})(el.parentElement)),
      relativeX:Math.max(0,Math.min(1,(x-r.left)/r.width)),relativeY:Math.max(0,Math.min(1,(y-r.top)/r.height)),status:'RESOLVED'},pageKey:pageKey()};
  }
  function showTarget(target) {
    state.target=target;
    if(!target){highlight.style.display='none';return;}
    const r=target.el.getBoundingClientRect();
    Object.assign(highlight.style,{display:'block',left:r.left+'px',top:r.top+'px',width:r.width+'px',height:r.height+'px'});
  }
  function cancel() {
    clearTimeout(state.pending);state.pending=null;state.active=false;state.pointer=null;state.start=null;state.points=[];showTarget(null);schedule();
  }
  function selectAt(x,y) { showTarget(findTarget(x,y)); }
  function resolve(anchor) {
    try {
      const matches=document.querySelectorAll(anchor.selector);
      if(matches.length!==1)return null;
      const el=matches[0];
      if(el.localName!==anchor.tag || snippet(el)!==anchor.text)return null;
      const r=el.getBoundingClientRect();
      return r.width>0&&r.height>0?el:null;
    } catch {return null;}
  }
  const point=e=>[Math.max(0,e.clientX+scrollX),Math.max(0,e.clientY+scrollY)];
  function path(points) {return points.map((p,i)=>(i?'L':'M')+p[0]+' '+p[1]).join(' ');}
  function makeStroke(data,id) {
    const element=document.createElementNS('http://www.w3.org/2000/svg','path');
    element.setAttribute('d',path(data.points));element.setAttribute('stroke',data.color);element.setAttribute('stroke-width',String(data.width));
    element.setAttribute('stroke-linecap','round');element.setAttribute('stroke-linejoin','round');element.setAttribute('fill','none');
    element.setAttribute('opacity',data.tool==='highlighter'?'.3':'1');
    if(state.mode==='DRAW'&&state.tool==='eraser'&&id){element.style.pointerEvents='stroke';element.addEventListener('pointerdown',e=>{e.stopPropagation();e.preventDefault();send('stroke.erase',{id});});}
    return element;
  }
  function render() {
    renderQueued=false;
    svg.setAttribute('viewBox',`${scrollX} ${scrollY} ${innerWidth} ${innerHeight}`);
    svg.replaceChildren();pins.replaceChildren();
    const unresolved=[];
    for(const annotation of state.annotations.filter(a=>a.pageKey===pageKey())) {
      if(annotation.kind==='STROKE') {svg.append(makeStroke(annotation.payload,annotation.id));continue;}
      const data=annotation.payload,el=resolve(data.anchor);
      if(!el){unresolved.push(annotation.id);continue;}
      const r=el.getBoundingClientRect();
      const marker=document.createElement('button');marker.className='pin';marker.textContent='•';marker.setAttribute('aria-label','Abrir nota: '+(data.title||'Pin'));
      marker.style.left=(r.left+r.width*data.anchor.relativeX)+'px';marker.style.top=(r.top+r.height*data.anchor.relativeY)+'px';
      marker.addEventListener('pointerdown',e=>e.stopPropagation());marker.addEventListener('click',e=>{e.stopPropagation();send('annotation.activated',{id:annotation.id});});pins.append(marker);
    }
    if(state.points.length)svg.append(makeStroke({points:state.points,color:state.color,width:state.tool==='highlighter'?state.width*3:state.width,tool:state.tool}));
    const unresolvedKey=pageKey()+':'+unresolved.join(',');
    if(unresolvedKey!==lastUnresolved){lastUnresolved=unresolvedKey;send('anchor.unresolved',{ids:unresolved});}
    if(state.active&&state.target)showTarget(state.target);
  }
  function schedule() {if(!renderQueued){renderQueued=true;requestAnimationFrame(render);}}
  function stopContent(e) {if(state.mode!=='VIEW'&&!e.composedPath().includes(host))e.stopImmediatePropagation();}
  listen(window,'pointerdown',e=>{
    if(state.mode==='VIEW'||e.composedPath().includes(host))return;
    if(!e.isPrimary||state.pointer!==null){cancel();return;}
    stopContent(e);state.pointer=e.pointerId;state.start=[e.clientX,e.clientY];
    if(state.mode==='DRAW') {
      e.preventDefault();if(state.tool==='eraser')return;
      state.active=true;state.points=[point(e)];schedule();return;
    }
    if(state.accessible){selectAt(e.clientX,e.clientY);state.active=true;return;}
    state.pending=setTimeout(()=>{state.active=true;selectAt(state.start[0],state.start[1]);},Math.max(500,Math.min(2000,state.config?.longPressMs||500)));
  },{capture:true,passive:false});
  listen(window,'pointermove',e=>{
    if(state.mode==='VIEW'||e.pointerId!==state.pointer)return;
    stopContent(e);
    if(state.mode==='DRAW'&&state.active){e.preventDefault();if(state.points.length<20000)state.points.push(point(e));schedule();return;}
    if(state.active){e.preventDefault();selectAt(e.clientX,e.clientY);}
    else if(state.start&&Math.hypot(e.clientX-state.start[0],e.clientY-state.start[1])>12)cancel();
  },{capture:true,passive:false});
  listen(window,'touchmove',e=>{if(state.active&&e.touches.length===1)e.preventDefault();else if(e.touches.length>1)cancel();},{capture:true,passive:false});
  listen(window,'pointerup',e=>{
    if(e.pointerId!==state.pointer)return;
    stopContent(e);
    if(state.mode==='PIN'&&state.active&&state.target)send('target.selected',{anchor:state.target.anchor,boundary:state.target.anchor.strategy==='SURFACE'?'Ancla de superficie; no selecciona objetos dibujados.':state.target.anchor.precision==='BOUNDING_RECT'?'Región grande o transformada: el pin sigue el rectángulo visible del elemento.':'Ancla del elemento resaltado.'});
    if(state.mode==='DRAW'&&state.active&&state.points.length)send('stroke.completed',{points:state.points,color:state.color,width:state.tool==='highlighter'?Math.min(50,state.width*3):state.width,tool:state.tool,coordinates:'PAGE_CSS',viewport:{width:innerWidth,height:innerHeight,scale:visualViewport?.scale||1}});
    cancel();
  },{capture:true,passive:false});
  listen(window,'pointercancel',cancel,true);
  for(const type of ['click','dblclick','contextmenu','selectstart'])listen(window,type,e=>{if(state.mode!=='VIEW'&&!e.composedPath().includes(host)){e.preventDefault();e.stopImmediatePropagation();}},{capture:true,passive:false});
  listen(window,'scroll',schedule,{passive:true});listen(window,'resize',schedule);listen(window,'hashchange',schedule);listen(window,'popstate',schedule);
  if(window.visualViewport){listen(visualViewport,'resize',schedule);listen(visualViewport,'scroll',schedule);}
  const observer=new MutationObserver(mutations=>{if(mutations.some(m=>!own(m.target)&&!host.contains(m.target)))schedule();});
  observer.observe(document.body||document.documentElement,{subtree:true,childList:true,characterData:true,attributes:true,attributeFilter:['class','style','id']});
  let autoFrame=0,autoLast=0,autoSpeed=0;
  function autoscroll(time) {if(!autoSpeed)return;if(autoLast)window.scrollBy(0,autoSpeed*(time-autoLast)/1000);autoLast=time;autoFrame=requestAnimationFrame(autoscroll);}
  function stopAuto(){autoSpeed=0;autoLast=0;cancelAnimationFrame(autoFrame);}
  listen(document,'touchstart',stopAuto,{passive:true});
  window.HyperMarker={
    init(config){state.config=config;send('controller.ready');schedule();},
    command(type,payload){
      if(type==='mode'){cancel();stopAuto();state.mode=payload.mode;state.accessible=!!payload.accessible;document.documentElement.dataset.hypermarkerMode=state.mode;}
      if(type==='annotations'){state.annotations=payload;lastUnresolved='';}
      if(type==='tool'){state.tool=payload.tool;state.color=payload.color;state.width=payload.width;}
      if(type==='selectTarget'){
        const el=document.querySelector(payload.selector);if(el){el.scrollIntoView({block:'center'});const r=el.getBoundingClientRect();selectAt(r.left+r.width/2,r.top+r.height/2);
        if(state.target){send('target.selected',{anchor:state.target.anchor,boundary:state.target.anchor.strategy==='SURFACE'?'Ancla de superficie':'Elemento elegido sin pulsación prolongada'});showTarget(null);}}
      }
      if(type==='cancel')cancel();
      if(type==='inspect'){const value=state.annotations.find(a=>a.id===payload.id);const el=value&&value.pageKey===pageKey()&&resolve(value.payload.anchor);if(el){el.scrollIntoView({block:'center'});showTarget({el});setTimeout(()=>showTarget(null),2000);}}
      if(type==='autoscroll'){stopAuto();autoSpeed=payload.speed||0;if(autoSpeed)autoFrame=requestAnimationFrame(autoscroll);}
      schedule();
    },
    accessibleTargets(){return Array.from(document.querySelectorAll('h1,h2,h3,p,li,a,button,canvas,svg,img,iframe,input')).filter(el=>{const r=el.getBoundingClientRect();return r.width>1&&r.height>1&&r.bottom>0&&r.top<innerHeight;}).slice(0,150).map(el=>({selector:selector(el),label:el.localName+' · '+(snippet(el)||el.getAttribute('alt')||'Superficie visual').slice(0,100)}));},
    destroy(){cancel();stopAuto();observer.disconnect();removals.forEach(remove=>remove());host.remove();css.remove();delete document.documentElement.dataset.hypermarkerMode;delete window.HyperMarker;}
  };
})();
