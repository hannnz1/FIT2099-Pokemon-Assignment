// Presentation only: NPC statements never become position, inventory or quest state.
export function dialoguePresentation(data) {
  const names={treecko:'木守宫',merchant:'商人',professor:'博士'};
  if(!data||!Object.hasOwn(names,data.speakerId)||typeof data.message!=='string')return null;
  const view={name:names[data.speakerId],message:data.message.slice(0,1000),historical:data.historical==='true',clues:[]};
  const turn=Number(data.turn);view.asOfTurn=Number.isSafeInteger(turn)&&turn>=0?turn:null;
  if(view.historical){
    try{
      const claims=JSON.parse(data.memories);
      if(Array.isArray(claims))for(const c of claims.slice(0,3)){
        if(!c||![c.x,c.y,c.quantity,c.occurredAt].every(Number.isSafeInteger)||c.x<0||c.y<0||c.quantity<0||c.occurredAt<0||c.source!=='SELF_OBSERVATION')continue;
        view.clues.push(`第${c.occurredAt}回合 · 亲眼观察 · 坐标（${c.x},${c.y}） · 当时${c.quantity}个树果`);
      }
    }catch{/* no facts from malformed or unsupported memories */}
  }
  return view;
}
