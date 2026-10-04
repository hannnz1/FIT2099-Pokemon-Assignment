// Read-only presentation of authoritative Java snapshots; no local game rules.
import {pokemonNames as names} from './pokemon-art.mjs';
const number=v=>v===undefined||v===null||v===''?null:Number.isFinite(Number(v))?Number(v):null;
const position=p=>Number.isInteger(number(p?.x))&&Number.isInteger(number(p?.y));
export function projectRpg(view,mode='quest'){
 const training=mode==='training',w=view.world??{},width=training?9:view.map?.width??9,height=training?3:view.map?.height??3;
 const tiles=[];for(let y=0;y<height;y++)for(let x=0;x<width;x++){const c=view.map?.terrain?.[y]?.[x]??'.';tiles.push({x,y,kind:({'#':'wall','_':'floor','~':'water','^':'lava',',':'hay'})[c]??'grass'});}
 const entities=[],add=e=>{if(position(e)&&e.x>=0&&e.x<width&&e.y>=0&&e.y<height)entities.push(e);};
 const leader=view.leaderState??(view.combatPartner==='mudkip'?view.combatPartnerState:null);
 if(position(w))add({id:'leader',species:'MUDKIP',role:'leader',name:'水跃鱼',x:number(w.x),y:number(w.y),hp:training?number(w.actorHp):number(leader?.hp),maxHp:training?1000:number(leader?.maxHp)});
 if(!training)for(const [id,p]of Object.entries(view.npcs??{}))if(position(p))add({id:`npc-${id}`,role:'npc',species:id==='treecko'?'TREECKO':id==='torchic'?'TORCHIC':null,name:({treecko:'木守宫 · NPC',torchic:'火稚鸡 · Agent',professor:'博士',merchant:'商人'})[id]??id,x:number(p.x),y:number(p.y)});
 for(const p of training?view.targets??[]:view.wild??[])if(p.state==='WILD'&&position(p)){const species=p.species??(p.id==='wild-treecko'?'TREECKO':'TORCHIC');add({id:p.id,role:'wild',species,name:`野生${names[species]??'宝可梦'}`,x:number(p.x),y:number(p.y),hp:number(p.hp),maxHp:number(p.maxHp)??100});}
 if(!training&&view.cooperative&&position(view.player))add({id:'player',role:'player',species:null,name:'你 · 玩家',x:number(view.player.x),y:number(view.player.y),hp:number(view.player.hp),maxHp:number(view.player.maxHp)});
 const out=view.summoned;if(!training&&out&&position(out))add({id:out.captureId,role:'owned',species:out.species??'TREECKO',name:`我的${names[out.species??'TREECKO']}`,x:number(out.x),y:number(out.y),hp:number(out.hp),maxHp:number(out.maxHp)});
 const anchor=(view.cooperative?entities.find(e=>e.id==='player'):null)||entities.find(e=>e.id===view.combatPartner)||entities.find(e=>e.id==='leader');
 const cols=Math.min(width,13),rows=Math.min(height,9),startX=anchor?Math.max(0,Math.min(width-cols,anchor.x-Math.floor(cols/2))):0,startY=anchor?Math.max(0,Math.min(height-rows,anchor.y-Math.floor(rows/2))):0;
 return {width,height,tiles,entities,cols,rows,startX,startY,mode,period:view.period??'DAY',turn:number(w.turn),locations:training?{}:view.locations??{},taskId:view.taskId,lastAction:view.trace?.at(-1)??null,targets:(training?view.targets??[]:view.wild??[]).map(t=>({id:t.id,state:t.state,hp:number(t.hp)}))};
}
export function targetAt(p,x,y){return p.entities.find(e=>e.role==='wild'&&e.x===x&&e.y===y)?.id??null;}
export function keyboardDirection(key,tag,enabled,editable=false){if(!enabled||editable||['INPUT','TEXTAREA','SELECT'].includes(tag?.toUpperCase()))return null;return ({ArrowUp:'N',ArrowDown:'S',ArrowLeft:'W',ArrowRight:'E',w:'N',a:'W',s:'S',d:'E'})[key]??({w:'N',a:'W',s:'S',d:'E'})[key?.toLowerCase()]??null;}
export function bindMovementKeys({document:doc=globalThis.document,onMove,canMove,isReady=()=>true,directionForKey=keyboardDirection}){
 const handler=event=>{
  if(event.defaultPrevented||event.ctrlKey||event.metaKey||event.altKey||event.shiftKey||doc.querySelector?.('dialog[open], [role="dialog"][aria-modal="true"]'))return;
  const focused=doc.activeElement,direction=directionForKey(event.key,focused?.tagName,true,focused?.isContentEditable);
  if(!direction)return;
  // Consume movement keys even while a server command is pending; otherwise
  // held arrows scroll the page during each brief input lock.
  event.preventDefault();
  if(isReady()&&canMove()&&!event.repeat)onMove(direction);
 };
 doc.addEventListener('keydown',handler);return ()=>doc.removeEventListener('keydown',handler);
}

// Effects require a newer authoritative world turn in the same task.
export function rpgFeedback(previous,current){
 if(!previous||previous.taskId!==current.taskId||current.turn===null||previous.turn===null||current.turn<=previous.turn)return [];
 const events=[];
 for(const e of current.entities){const old=previous.entities.find(p=>p.id===e.id);if(!old)continue;
  if(Math.abs(e.x-old.x)+Math.abs(e.y-old.y)===1)events.push({kind:'move',id:e.id,x:old.x,y:old.y});
  if(Number.isFinite(old.hp)&&Number.isFinite(e.hp)&&e.hp<old.hp)events.push({kind:'damage',id:e.id,x:e.x,y:e.y,amount:old.hp-e.hp});
 }
 for(const old of previous.entities.filter(e=>e.role==='wild')){const t=current.targets.find(t=>t.id===old.id),before=previous.targets.find(t=>t.id===old.id);
  if(before?.state!=='WILD'||!t||!['CAPTURED','DEFEATED'].includes(t.state))continue;
  if(t.state==='DEFEATED'&&Number.isFinite(old.hp)&&Number.isFinite(t.hp)&&t.hp<old.hp)events.push({kind:'damage',id:old.id,x:old.x,y:old.y,amount:old.hp-t.hp});
  events.push({kind:t.state==='CAPTURED'?'capture':'defeat',id:old.id,x:old.x,y:old.y});
 }
 const action=current.lastAction;
 if(action?.code==='ATTACK_MISSED'&&JSON.stringify(action)!==JSON.stringify(previous.lastAction)){const target=previous.entities.find(e=>e.id===action.data?.targetId&&e.role==='wild');if(target)events.push({kind:'miss',id:target.id,x:target.x,y:target.y});}
 return events;
}
export function controlHint(view,online=true,busy=false){
 if(!online)return '连接中断，重新连接后才能操作。';
 if(busy)return '操作正在确认，请等待实际状态更新。';
 if(view.status==='STORAGE_ERROR')return '存档未确认，请先恢复存档。';
 if(['COMPLETED','FAILED','CANCELLED'].includes(view.status)&&!view.manualAllowed)return '本轮已结束，请点击新一轮（训练场：重新训练）继续。';
 if(['PARSING','READY'].includes(view.status))return '请先完成任务确认，或取消当前任务后手动操作。';
 if(!view.manualAllowed)return 'AI 正在控制伙伴，请先暂停或取消任务。';
 return '现在由你指挥。每次有效移动、攻击或捕捉推进一个回合。';
}
export function guideSteps(view,mode){
 const stored=(view.collection??[]).length>0;
 if(mode==='training')return [{label:'选择木守宫，移动到相邻格（初始位置向东一步）。',done:(view.targets??[]).some(t=>t.id==='wild-treecko'&&(t.state!=='WILD'||Math.max(Math.abs(Number(view.world?.x)-t.x),Math.abs(Number(view.world?.y)-t.y))===1))},{label:'点击捕捉；也可输入“捕捉木守宫，不要主动战斗”，确认后交给 AI。',done:(view.targets??[]).some(t=>['CAPTURED','TRANSFERRED'].includes(t.state))},{label:'暂停或结束 AI，点击“带回原地图”。',done:stored},{label:'返回原地图，在宝可梦背包中召唤伙伴。',done:Boolean(view.summoned)}];
 if(view.cooperative){const q=view.sharedQuest??{},c=q.contributions??{};return [{label:'方向键/WASD控制你自己，靠近树果后点击玩家采集；水跃鱼拥有独立背包。',done:Number(c.PLAYER?.picked??0)>0},{label:'输入共同收集3个树果的委托，确认后与水跃鱼同时行动。',done:Boolean(view.intent)},{label:'双方靠近博士可分批交付；查看共享进度和贡献账本，合计3个完成。',done:Number(q.delivered??0)>=Number(q.required??3)}];}
 const wild=view.wild??[];
 return [{label:'点击“开启野外探索”；点击地图上的野生目标选择它。',done:wild.length>0},{label:'移动到目标相邻格，再点击“野外捕捉”（火稚鸡暂不可捕捉）。',done:wild.some(t=>['CAPTURED','TRANSFERRED'].includes(t.state))},{label:'点击“收入收藏”，在背包中选择该个体。',done:stored},{label:'选择空位方向召唤，可开启跟随，或切换为出战伙伴。',done:Boolean(view.summoned)}];
}

export function disabledHint(view,id,online=true,busy=false){
 if(!online||busy||!view.manualAllowed)return controlHint(view,online,busy);
 if(id==='parse'&&['COMPLETED','FAILED','CANCELLED'].includes(view.status))return '本轮已结束，请点击新一轮（训练场：重新训练）再解析任务。';
 if(id==='parse'&&!view.aiAvailable)return '尚未配置可用模型；仍可手动试玩。';
 return ({pause:'当前没有正在行动的 AI 任务。',resume:'只有暂停或模型暂不可用的任务才能继续。',confirm:'请先解析任务并确认目标。',parse:'当前任务尚未结束，请先取消或开始新一轮。',reset:'请先暂停并取消当前任务。',attack:'请选择仍在地图上的野生目标。',explore:'原地图才能开启探索；本轮已经开启时无需重复开启。','rest-pokemon-button':'请靠近博士；收藏个体需要先收回，濒危水跃鱼可获得远程救助。',recall:'当前没有放出的收藏伙伴。',follow:'请先召唤伙伴；已经跟随时无需重复开启。','stop-follow':'当前没有正在跟随的伙伴。'})[id]??'此操作当前不可用，请查看目标状态、试玩步骤与本轮任务。';
}

export function cooperationPresentation(view){
 const enabled=Boolean(view.cooperative),player=view.player??{},q=view.sharedQuest??{},totals=q.contributions??{},resources=player.resources??[],allowed=enabled&&Boolean(view.playerAllowed);
 return {enabled,resources,canMove:allowed,canPickup:allowed&&resources.length>0&&Number(player.carriedBerry??0)<10,canDeliver:allowed&&Boolean(player.nearProfessor)&&Number(player.carriedBerry??0)>0,position:enabled?`你的位置（${player.x}, ${player.y}） · 背包树果 ${player.carriedBerry??0} 个`:'开启协作后，你将以独立玩家角色与水跃鱼一起收集树果。',progress:enabled?`共享任务 ${q.delivered??0}/${q.required??3} · 你交付 ${totals.PLAYER?.delivered??0} · 水跃鱼交付 ${totals.AGENT?.delivered??0}`:'',events:(q.ledger??[]).map(e=>`${e.sequence}. ${e.actor==='PLAYER'?'你':'水跃鱼'} · ${({PICKUP:'采集',PURCHASE:'购买',DELIVER:'交付'})[e.action]??e.action} ${e.quantity} 个树果 · 第${e.turn}回合时`)};
}

export function aiPartnerSelection(view,previous='mudkip'){
 const options=[{id:'mudkip',label:'委托伙伴水跃鱼'},...(view.collection??[]).map(p=>({id:p.captureId,label:`我的${names[p.species]??p.species} · ${p.hp}/${p.maxHp??100} HP · ${p.captureId.slice(0,8)}`}))];
 const editable=['IDLE','ERROR'].includes(view.status)&&!view.cooperative;
 const selected=view.cooperative?'mudkip':editable&&options.some(p=>p.id===previous)?previous:view.aiPartner??'mudkip';
 return {options,selected,editable};
}
