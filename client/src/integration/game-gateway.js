export class GameGateway {
  constructor({fetchFn=globalThis.fetch.bind(globalThis),socketFactory=url=>new WebSocket(url),origin=globalThis.location?.origin||'http://localhost'}={}) {
    this.fetchFn=fetchFn; this.socketFactory=socketFactory; this.origin=origin;
    this.listeners=new Set();this.requests=new Map();this.generation=0;
  }
  async request(path,body) {
    const response=await this.fetchFn(path,{method:body?'POST':'GET',credentials:'same-origin',headers:{'Content-Type':'application/json'},...(body?{body}:{}),signal:AbortSignal.timeout(10000)});
    let data;try{data=await response.json();}catch{throw Error('Java 服务未就绪：接口没有返回 JSON');}
    if(!response.ok){const error=Error(data.message||`服务请求失败（${response.status}）`);error.definitive=[400,401,403,404,405,409,410,422].includes(response.status);throw error;}return data;
  }
  getSession(){return this.request('/api/v1/session');}
  openRoom(options){return this.request('/api/v1/rooms',JSON.stringify(options));}
  getSnapshot(roomId=this.roomId){return this.request(`/api/v1/rooms/${encodeURIComponent(roomId)}/snapshot`);}
  async send(command){
    if(!this.roomId)throw Error('尚未进入房间');
    const body=JSON.stringify(command), old=this.requests.get(command.requestId);
    if(!command.requestId)throw Error('缺少请求标识');
    if(old && old!==body)throw Error('同一请求的载荷不可改变');
    this.requests.set(command.requestId,body);
    return this.request(`/api/v1/rooms/${encodeURIComponent(this.roomId)}/commands`,body);
  }
  subscribe(fn){this.listeners.add(fn);return()=>this.listeners.delete(fn);}
  emit(message){this.listeners.forEach(fn=>fn(message));}
  async connect(roomId){
    this.disconnect(); this.roomId=roomId; const generation=this.generation;
    const url=new URL(`/ws/v1/rooms/${encodeURIComponent(roomId)}`,this.origin);url.protocol=url.protocol==='https:'?'wss:':'ws:';
    const ws=this.socketFactory(url.href);this.socket=ws;
    let ready=false;const buffer=[];
    ws.onmessage=event=>{if(generation!==this.generation)return;try{const msg=JSON.parse(event.data);if(ready)this.emit(msg);else buffer.push(msg);}catch{this.emit({type:'ERROR',message:'服务消息格式错误'});}};
    await new Promise((resolve,reject)=>{
      const timer=setTimeout(()=>{reject(Error('连接超时'));ws.close();},10000);
      ws.onopen=()=>{clearTimeout(timer);resolve();};ws.onerror=()=>{clearTimeout(timer);reject(Error('无法连接 Java 实时服务'));};
      ws.onclose=()=>{clearTimeout(timer);reject(Error('连接已关闭'));if(generation===this.generation){this.generation++;this.emit({type:'DISCONNECTED'});}};
    });
    try {
      const snapshot=await this.getSnapshot(roomId);
      if(generation!==this.generation)throw Error('连接已被替换');
      if(snapshot.roomId!==roomId)throw Error('房间快照不匹配');
      this.emit({type:'SNAPSHOT',snapshot});ready=true;
      buffer.forEach(message=>this.emit(message));return snapshot;
    }catch(error){if(generation===this.generation)this.disconnect();throw error;}
  }
  disconnect(){this.generation++;this.socket?.close();this.socket=undefined;}
}
