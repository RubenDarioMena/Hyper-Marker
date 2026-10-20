import React from 'react';
import {createRoot} from 'react-dom/client';
import './style.css';
function App(){const [count,setCount]=React.useState(0);return <main><p>PROYECTO REACT COMPILADO</p><h1 id="react-title">Un documento vivo</h1><p id="react-anchor">Este bloque conserva un ancla estable.</p><button id="react-counter" onClick={()=>setCount(count+1)}>Explorar · {count}</button></main>}
createRoot(document.getElementById('root')).render(<App/>);
