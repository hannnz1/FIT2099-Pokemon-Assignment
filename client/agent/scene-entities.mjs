// Pure identity reconciliation: a texture/form change never creates a new individual.
export function reconcileEntities(previous,next){
 const before=new Map(previous.map(e=>[e.id,e])),after=new Map(next.map(e=>[e.id,e]));
 return {added:next.filter(e=>!before.has(e.id)),changed:next.filter(e=>before.has(e.id)&&JSON.stringify(before.get(e.id))!==JSON.stringify(e)),removed:previous.filter(e=>!after.has(e.id))};
}
