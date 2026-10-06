// Confirmed trace events only; presentation never creates game actions.
export function battleEvents(previous,next){
 if(!previous||previous.taskId!==next?.taskId)return [];
 const before=previous.trace??[],seen=new Set(before.map(r=>r.index??JSON.stringify(r))),out=[];
 const highest=Math.max(0,...before.map(r=>Number(r.index)||0));
 for(const row of next.trace??[]){const key=row.index??JSON.stringify(row);if(seen.has(key)||row.index&&row.index<=highest)continue;
  let actions=row.actions;
  if(!actions&&row.data?.combatEvents){try{actions=JSON.parse(row.data.combatEvents);}catch{continue;}}
  if(!Array.isArray(actions))continue;
  actions.forEach((action,i)=>{const kind=action.type==='SWITCH'?'switch':action.type==='RESIDUAL'?'residual':action.outcome==='STATUS_PREVENTED'?'prevented':action.outcome==='MISSED'?'miss':action.eligible===false?'skipped':'move';out.push({...action,kind,id:String(next.taskId)+':'+String(key)+':'+i});});
  for(const side of [0,1]){const name=side?'right':'left';if(row.before?.[name]?.hp>0&&row.after?.[name]?.hp===0)out.push({kind:'faint',side,id:String(next.taskId)+':'+String(key)+':faint:'+side});}
 }
 return out;
}
export function createEffectQueue({play,limit=8}){
 let pending=[],active=null;const seen=new Set();
 const drain=async()=>{if(active||!pending.length)return;const event=pending.shift(),controller=new AbortController();active=controller;
  try{await play(event,controller.signal);}catch{}finally{if(active===controller)active=null;drain();}
 };
 return {enqueue(events){for(const event of events){if(seen.has(event.id))continue;seen.add(event.id);while(seen.size>256)seen.delete(seen.values().next().value);pending.push(event);while(pending.length>limit)pending.shift();drain();}},clear(){pending=[];active?.abort();},get pending(){return pending.length;}};
}
export async function animateElement(element,frames,options,signal){
 if(!element||signal?.aborted||!element.animate)return;
 const animation=element.animate(frames,options),cancel=()=>animation.cancel();signal?.addEventListener('abort',cancel,{once:true});
 try{await animation.finished;}catch{}finally{signal?.removeEventListener('abort',cancel);animation.cancel();}
}
