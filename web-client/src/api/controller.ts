import type { Snapshot, ActionResult } from './types.ts';
export type InputState='READY'|'REQUESTING'|'ANIMATING'|'ERROR'|'ENDED';
export class ApiError extends Error {status:number;code:string;constructor(status:number,message:string,code=''){super(message);this.status=status;this.code=code;}}
export async function request(path:string, options:RequestInit={}):Promise<any> {
 const controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),8000);
 try {const response=await fetch(path,{...options,credentials:'same-origin',headers:{'Content-Type':'application/json'},signal:controller.signal});
  const body=response.status===204?null:await response.json();if(!response.ok)throw new ApiError(response.status,body.message??'请求失败',body.code);return body;
 }finally{clearTimeout(timeout);}
}
export class GameController {
 snapshot:Snapshot|null=null;state:InputState='REQUESTING';error='';onChange=()=>{};
 pending:{requestId:string;expectedRevision:number;actionId:string}|null=null;
 private transport:typeof request;private animate:(result:ActionResult)=>Promise<void>;
 constructor(transport:typeof request,animate:(result:ActionResult)=>Promise<void>){this.transport=transport;this.animate=animate;}
 private notify(){this.onChange();}
 async load(){this.state='REQUESTING';this.notify();try{this.snapshot=await this.transport('/api/games',{method:'POST',body:'{}'});this.pending=null;this.error='';this.state=this.snapshot?.phase==='ENDED'?'ENDED':'READY';}catch(e){this.state='ERROR';this.error=e instanceof Error?e.message:'无法连接服务器';}this.notify();}
 async refresh(){try{this.accept(await this.transport('/api/games/current'));this.pending=null;this.error='';this.state=this.snapshot?.phase==='ENDED'?'ENDED':'READY';}catch(e){this.error=e instanceof Error?e.message:'读取失败';this.state='ERROR';}this.notify();}
 accept(s:Snapshot){if(!this.snapshot||s.gameId!==this.snapshot.gameId||s.revision>=this.snapshot.revision)this.snapshot=s;}
 async act(actionId:string){if(this.state!=='READY')return;
  if(!this.snapshot)return;this.pending={requestId:crypto.randomUUID(),expectedRevision:this.snapshot.revision,actionId};await this.send();}
 async retry(){if(this.state==='ERROR'&&this.pending)await this.send();}
 private async send(){if(!this.pending||!this.snapshot)return;this.state='REQUESTING';this.error='';this.notify();
  try {const result:ActionResult=await this.transport(`/api/games/${this.snapshot.gameId}/actions`,{method:'POST',body:JSON.stringify(this.pending)});
   const current=this.snapshot;if(result.snapshot.revision>=current.revision){this.accept(result.snapshot);if(!result.replayed){this.state='ANIMATING';this.notify();await this.animate(result);}}
   this.pending=null;this.state=this.snapshot.phase==='ENDED'?'ENDED':'READY';
  }catch(e){this.state='ERROR';this.error=e instanceof Error?e.message:'网络错误';if(e instanceof ApiError&&e.status===409){await this.refresh();return;}if(e instanceof ApiError&&![429,500,502,503,504].includes(e.status))this.pending=null;}
  this.notify();
 }
 async restart(){if(this.state==='REQUESTING'||this.state==='ANIMATING')return;this.state='REQUESTING';this.notify();try{if(this.snapshot)await this.transport(`/api/games/${this.snapshot.gameId}`,{method:'DELETE'});this.snapshot=null;this.pending=null;await this.load();}catch(e){this.state='ERROR';this.error=e instanceof Error?e.message:'重新开始失败';this.notify();}}
}
