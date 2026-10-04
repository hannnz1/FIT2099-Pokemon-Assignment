import {agentPresentation} from './game-ui.mjs';
import {pokemonNames,portraitUrl} from './pokemon-art.mjs';
export function shellStatus(view,online,busy,pending){
 const w=view?.world??{},agent=agentPresentation(view??{},online,busy);
 const save=!online?'离线 · 显示上次进度':view?.status==='STORAGE_ERROR'?'存档失败，请恢复':pending?'操作未确认':busy?'正在确认操作…':view?.storage==='PERSISTENT'?'自动存档已同步':'内存模式 · 关闭后不保留';
 return {save,period:Math.floor((w.turn??0)/5)%2?'夜晚':'白昼',turn:w.turn??0,task:view?.status==='IDLE'?'自由探索':agent.label+(view?.targetLevel&&w.partner?` · Lv.${w.partner.level} → ${view.targetLevel}`:''),teamCount:w.team?.length??0};
}
export function bagPartners(world){return (world.team??[]).filter(p=>p.captureId!==world.partner?.captureId);}
export function explorationControls(target){const available=Boolean(target?.state==='WILD'&&target.hp>0);return {skills:available,approach:available&&!target.targetAdjacent,capture:available};}
export function transientFeedback(text){return /^(成长模式已连接|已抵达|技能回合已结算|全队.*恢复|恢复完成|捕捉成功|已.*领取)/.test(text);}
const navigationNames={lab:'研究室',forest:'苔叶森林',river:'浅溪河岸',mountain:'赤岩山道'};
export function navigationPresentation(view,context={}){
 const w=view?.world??{},blocked=!view?'正在读取地图':context.online===false?'请先重新连接':view.status==='STORAGE_ERROR'?'请先恢复存档':context.pending?'正在核对上一条操作，请稍候':context.returning?'正在自动返程，请稍候':context.busy?'正在处理操作，请稍候':['RUNNING','REPLANNING','PARSING'].includes(view.status)?'AI 正在培养，请先暂停或取消委托':!w.partner?'请先选择伙伴':!view.manualAllowed?'当前暂不可操作':'';
 const regions=(w.regions??[]).map(r=>{const current=r.id===w.region,locked=r.unlocked===false,reason=current?'你正在这里':locked?'请先完成前一区域调查':blocked;return {id:r.id,name:r.name??navigationNames[r.id]??r.id,status:current?'当前':locked?'未解锁':'已解锁',reason,enabled:!reason};});
 const recoveryReason=blocked||(w.canRest?'已在恢复点':'');
 return {current:navigationNames[w.region]??w.region??'读取中',regions,recovery:{enabled:!recoveryReason,reason:recoveryReason}};
}
export function renderNavigation(host,presentation,onRegion,onRecovery){
 const doc=host.ownerDocument;
 const row=(name,status,label,enabled,reason,action,id)=>{const item=doc.createElement('div');item.className='navigation-row';const info=doc.createElement('div'),title=doc.createElement('strong'),badge=doc.createElement('span'),hint=doc.createElement('small'),button=doc.createElement('button');title.textContent=name;badge.textContent=status;badge.className='navigation-badge';hint.textContent=reason;hint.id='navigation-reason-'+id;hint.hidden=!reason;info.append(title,badge,hint);button.textContent=label;button.disabled=!enabled;button.setAttribute('aria-label',name+'：'+label);if(reason)button.setAttribute('aria-describedby',hint.id);button.addEventListener('click',action);item.append(info,button);return item;};
 const list=doc.createElement('div');list.className='navigation-list';
 for(const r of presentation.regions)list.append(row(r.name,r.status,'向入口走一步',r.enabled,r.reason,()=>onRegion(r.id),r.id));
 const camp=doc.createElement('section');camp.className='navigation-camp';const heading=doc.createElement('h3');heading.textContent='恢复点';camp.append(heading,row('营地','恢复全队','前往营地',presentation.recovery.enabled,presentation.recovery.reason,onRecovery,'camp'));
 host.replaceChildren(list,camp);
}
export function mountGrowthShell(doc=globalThis.document){
 const dialogs=[...doc.querySelectorAll('.player-dialog')];
 const open=id=>{const dialog=doc.getElementById(id);if(!dialog)return;for(const d of dialogs)if(d.open)d.close();dialog.showModal();};
 for(const trigger of doc.querySelectorAll('[data-open-panel]'))trigger.addEventListener('click',()=>open(trigger.dataset.openPanel));
 for(const dialog of dialogs){
  dialog.querySelector('[data-close-panel]').addEventListener('click',()=>dialog.close());
  dialog.addEventListener('close',()=>doc.getElementById('map').focus({preventScroll:true}));
 }
 doc.getElementById('show-touch').addEventListener('change',event=>doc.body.classList.toggle('show-touch',event.target.checked));
 doc.getElementById('journey-host').addEventListener('click',event=>{const link=event.target.closest('a');if(!link||!link.hash)return;const target=doc.getElementById(link.hash.slice(1));if(!target)return;event.preventDefault();const dialog=target.closest('.player-dialog');if(dialog)open(dialog.id);else{for(const d of dialogs)if(d.open)d.close();doc.getElementById('map').focus({preventScroll:true});}});
 return {open,isOpen:()=>dialogs.some(d=>d.open),attachMapInfo(){const legend=doc.querySelector('.map-boundary-legend');if(legend)doc.getElementById('map-info').append(legend);doc.getElementById('map-info').append(doc.getElementById('map-status'));},decoratePartner(card,p){
  const note=card.querySelector('.experience-note');if(note){const level=p.level,base=Math.max(0,Math.floor(6*level*level*level/5)-15*level*level+100*level-140),total=p.nextLevelExperience-base;const meter=doc.createElement('progress');meter.className='exp-meter';meter.max=Math.max(1,total);meter.value=total>0?Math.max(0,p.experience-base):1;meter.setAttribute('aria-label',note.textContent);meter.title=note.textContent;note.replaceWith(meter);return note;}
 },update(view,online,busy,pending){
  const status=shellStatus(view,online,busy,pending);
  doc.body.classList.toggle('connection-normal',online&&!pending);
  doc.getElementById('hud-period').textContent=(status.period==='白昼'?'☀ ':'☾ ')+status.period;
  doc.getElementById('hud-turn').textContent='第 '+status.turn+' 回合';
  const save=doc.getElementById('hud-save');save.textContent=status.save==='自动存档已同步'?'✓ 已保存':status.save;save.title=status.save;
  doc.getElementById('task-summary').textContent=status.task;
  doc.getElementById('dock-team-count').textContent=status.teamCount;
  const bag=doc.getElementById('bag-partners');bag.replaceChildren();
  for(const p of bagPartners(view?.world??{})){const row=doc.createElement('div');row.className='bag-partner';const img=doc.createElement('img');img.src=portraitUrl(p.species);img.alt=pokemonNames[p.species]??p.name;const text=doc.createElement('span');text.textContent=(pokemonNames[p.species]??p.name)+' · Lv.'+p.level+' · HP '+p.hp+'/'+p.maxHp;row.append(img,text);bag.append(row);}
  if(!bag.childElementCount)bag.textContent='还没有收在球中的其他伙伴。';
 }};
}
