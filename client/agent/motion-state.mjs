// Animate only an observed, confirmed adjacent step. Unknown paths must never cross walls.
export function movementTransition(previous,next){
 if(!previous)return 'SNAP';
 if(next.turn<previous.turn)return 'IGNORE';
 if(previous.region!==next.region)return 'REGION_CHANGE';
 if(next.turn===previous.turn)return 'IGNORE';
 const distance=Math.abs(next.x-previous.x)+Math.abs(next.y-previous.y);
 return distance===0?'IGNORE':distance===1?'STEP':'SNAP';
}
