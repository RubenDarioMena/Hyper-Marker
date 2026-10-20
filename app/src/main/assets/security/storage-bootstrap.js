(() => {
  // El origen opaco impide acceso a otros frames y al storage del perfil.
  // Esta implementación mantiene las APIs comunes durante la sesión del visor.
  function memoryStorage() {
    const values = Object.create(null);
    const api = {
      getItem(key) { key=String(key);return Object.hasOwn(values,key)?values[key]:null; },
      setItem(key,value) { key=String(key);value=String(value);if(key.length+value.length>524288)throw new DOMException('Límite de almacenamiento de sesión','QuotaExceededError');values[key]=value; },
      removeItem(key) { delete values[String(key)]; },
      clear() { for(const key of Object.keys(values))delete values[key]; },
      key(index) { return Object.keys(values)[Number(index)]??null; },
      get length() { return Object.keys(values).length; }
    };
    return new Proxy(api,{get(target,key){return key in target?Reflect.get(target,key):values[key];},set(_target,key,value){api.setItem(key,value);return true;},deleteProperty(_target,key){api.removeItem(key);return true;},ownKeys(){return Object.keys(values);},getOwnPropertyDescriptor(_target,key){if(Object.hasOwn(values,key))return{value:values[key],enumerable:true,configurable:true,writable:true};}});
  }
  for(const name of ['localStorage','sessionStorage']) {
    try{Object.defineProperty(globalThis,name,{value:memoryStorage(),writable:false,configurable:false});}catch(_){}
  }
})();
