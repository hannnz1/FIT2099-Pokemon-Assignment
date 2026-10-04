export const SPECIES = Object.freeze({Treecko:{name:'木守宫',texture:'grass',ability:'切开藤蔓'},Torchic:{name:'火稚鸡',texture:'fire',ability:'温暖与照明'},Mudkip:{name:'水跃鱼',texture:'water',ability:'引导水流'}});
export function gridToPixel({gridX=0,gridY=0},tileSize=64) { return {x:gridX*tileSize,y:gridY*tileSize}; }
export function toEntityView(entity) { const p=SPECIES[entity.speciesId];return {...entity,id:entity.entityId,...gridToPixel(entity),name:entity.name||p?.name||entity.speciesId||'调查员',texture:p?.texture||'player',placeholder:!!entity.speciesId}; }
export function toQuestView(snapshot) { const q=snapshot.quest||{};return {...q,title:q.title||'抵达栖流岛',objective:q.objective||'与调查员岚交谈，了解考核。',evidence:[...new Set(q.evidence||[])],rescued:new Set(q.rescuedIds||[]).size}; }
