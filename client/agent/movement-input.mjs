export function createMovementInput({send,canSend=()=>true,schedule=f=>setTimeout(f,160),cancel=clearTimeout}){
 let held=null,inFlight=false,queued=null,timer=null;
 const stopTimer=()=>{if(timer!==null)cancel(timer);timer=null;};
 const clear=()=>{held=null;queued=null;stopTimer();};
 const submit=direction=>{
  if(!direction||inFlight||!canSend()){if(!canSend())clear();return;}
  stopTimer();inFlight=true;
  let result;try{result=send(direction);}catch{inFlight=false;clear();return;}
  Promise.resolve(result).then(()=>{
   inFlight=false;
   if(!canSend()){clear();return;}
   const next=queued??held;queued=null;
   if(next)timer=schedule(()=>{timer=null;submit(held?held:next);});
  },()=>{inFlight=false;clear();});
 };
 return {press(direction){if(held===direction)return;held=direction;if(inFlight)queued=direction;else submit(direction);},release(direction){if(held!==direction)return;clear();},tap(direction){if(inFlight){queued=direction;return;}submit(direction);},clear,destroy:clear,get state(){return {heldDirection:held,inFlight,queuedDirection:queued};}};
}
