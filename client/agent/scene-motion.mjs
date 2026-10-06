import {movementTransition} from './motion-state.mjs';
export const reducedMotion=()=>Boolean(globalThis.document?.body?.classList.contains('reduce-motion')||globalThis.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches);
export function moveVisual(scene,record,old,next,tile=64,offset={x:0,y:0}){
 const kind=movementTransition(old,next),x=(next.x+.5)*tile+offset.x,y=(next.y+.5)*tile+offset.y;
 if(kind==='IGNORE')return;
 if(record.motion){record.motion.stop();record.motion=null;}
 if(kind==='STEP'&&!reducedMotion()&&!document.hidden){
  record.motion=scene.tweens.add({targets:record.group,x,y,duration:160,ease:'Linear',onComplete:()=>{record.motion=null;}});
 }else record.group.setPosition(x,y);
}
