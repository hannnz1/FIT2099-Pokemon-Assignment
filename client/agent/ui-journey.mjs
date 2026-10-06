import {pokemonNames,portraitUrl} from './pokemon-art.mjs';
const names={NORMAL:'一般',GRASS:'草',FIRE:'火',WATER:'水',ELECTRIC:'电',FLYING:'飞行',BUG:'虫',POISON:'毒',FIGHTING:'格斗',GROUND:'地面',DARK:'恶',PSYCHIC:'超能',ICE:'冰',ROCK:'岩石',GHOST:'幽灵',DRAGON:'龙',STEEL:'钢'};
export function skillPresentation(m={}){return {type:names[m.type]??'技能',tone:String(m.type??'NORMAL').toLowerCase(),detail:`${m.power===0?'变化技能':'威力 '+(m.power??'—')} · 命中 ${m.accuracy??'—'}% · PP ${m.pp??'—'} / ${m.maxPp??'—'}`};}
export function filterPokemon(list=[],query='',state='all'){const q=query.trim().toLowerCase();return list.filter(p=>(state==='all'||p.state===state)&&(!q||(pokemonNames[p.species]??p.name??'').toLowerCase().includes(q)));}
export function growthEvents(before,after){
 if(!before?.world||!after?.world||after.world.turn<before.world.turn||before.taskId!==after.taskId)return [];const a=before.world,b=after.world,out=[];
 for(const e of b.encounters??[]){const old=a.encounters?.find(x=>x.id===e.id);if(old?.state==='WILD'&&e.state==='CAPTURED')out.push({kind:'capture',species:e.species,title:'捕捉成功',detail:`${e.name??pokemonNames[e.species]??'新伙伴'}加入了成长队伍。`});}
 const p=a.partner,n=b.partner;if(!p||!n||p.captureId!==n.captureId)return out;
 if(n.level>p.level&&n.species===p.species)out.push({kind:'level',species:n.species,title:'伙伴升级',detail:`${pokemonNames[n.species]??n.name} Lv.${p.level} → Lv.${n.level}`});
 if(n.species!==p.species)out.push({kind:'evolution',species:n.species,title:'进化完成',detail:`${pokemonNames[p.species]} → ${pokemonNames[n.species]} · Lv.${p.level} → Lv.${n.level}`});
 for(const m of n.moves??[]){const old=p.moves?.find(x=>x.id===m.id);if(!old)out.push({kind:'learn',species:n.species,title:'学会新技能',detail:m.name??m.id});else if(m.pp<old.pp&&b.turn>a.turn){const s=skillPresentation(m);out.push({kind:'skill',tone:s.tone,title:m.name,detail:`${s.type}属性 · PP ${m.pp} / ${m.maxPp}`});}}
 return out;
}
export function journeySteps(view={},mode='growth'){
 const w=view.world??{},owned=mode==='growth'?Boolean(w.partner||w.team?.length||w.starterAvailable===false):Boolean(view.collection?.length);
 const captured=mode==='growth'?(w.encounters??[]).some(e=>e.state==='CAPTURED')||(w.milestones??[]).some(m=>m.id==='FIRST_CAPTURE'&&m.ready):Boolean(view.collection?.length)||(view.wild??[]).some(e=>e.state==='CAPTURED');
 return [{id:'starter',title:mode==='growth'?'选择第一位伙伴':'准备出战伙伴',done:owned,detail:mode==='growth'?'在「我的伙伴」选择木守宫、水跃鱼或火稚鸡；已有伙伴可直接探索。':'在「野外探索」开启探索并选择出战伙伴。',anchor:mode==='growth'?'partner-title':'field-title'},
 {id:'explore',title:'探索与选择目标',done:mode==='growth'?Boolean(w.partner&&w.region!=='lab'):Boolean(view.wildActionAllowed||Number(w.turn)>0),detail:'方向键或触控按钮每次移动一格。点击野生伙伴，接近到相邻格后才能行动。',anchor:mode==='growth'?'region-title':'field-title'},
 {id:'capture',title:'战斗、捕捉与收藏',done:captured,detail:mode==='growth'?'留意 HP 与 PP，选择技能削弱目标，再尝试捕捉；成功后加入成长队伍。':'选择目标、接近后尝试捕捉，再点击「收入收藏」。召唤与收回在收藏面板操作。',anchor:mode==='growth'?'target-title':'field-title'},
 {id:'delegate',title:'交给 AI 完成目标',done:view.status==='COMPLETED',detail:view.aiAvailable?(mode==='growth'?'设定区域与目标等级，点击委托；可随时暂停或取消。':'描述目标 → 理解委托 → 核对并确认 → 查看行动与结果。暂停后可手动接管。'):'AI 尚未配置，可以先手动完成探索和捕捉；配置后再体验委托。',anchor:mode==='growth'?'ai-title':'quest-agent-panel'}];
}
export function mountJourney(host,mode){
 const details=document.createElement('details');details.className='journey';details.open=mode!=='quest';
 const summary=document.createElement('summary');summary.textContent='第一次冒险 · 操作指南';const list=document.createElement('ol');details.append(summary,list);host.append(details);let key='';
 return {update(view){const steps=journeySteps(view,mode),next=JSON.stringify(steps);if(next===key)return;key=next;list.replaceChildren(...steps.map(s=>{const li=document.createElement('li');li.dataset.complete=String(s.done);const link=document.createElement('a');link.href='#'+s.anchor;link.textContent=(s.done?'✓ ':'')+s.title;const p=document.createElement('p');p.textContent=s.detail;li.append(link,p);return li;}));}};
}
export function mountFeedback(host){let timer;host.setAttribute('role','status');host.setAttribute('aria-live','polite');return {show(events){if(document.hidden||!events.length)return;clearTimeout(timer);host.replaceChildren(...events.slice(-5).map(e=>{const card=document.createElement('div');card.className='event-card event-'+e.kind;card.dataset.tone=e.tone??e.kind;if(e.species&&pokemonNames[e.species]){const img=document.createElement('img');img.src=portraitUrl(e.species);img.alt=pokemonNames[e.species];card.append(img);}const text=document.createElement('div'),title=document.createElement('strong'),detail=document.createElement('p');title.textContent=e.title;detail.textContent=e.detail;text.append(title,detail);card.append(text);return card;}));host.hidden=false;timer=setTimeout(()=>host.hidden=true,6500);},destroy(){clearTimeout(timer);}};}
export function mountTouch(host,onMove,movement){
 host.className='touch-controls';const label=document.createElement('span');label.textContent='触控移动';host.append(label);
 const buttons=['N','W','S','E'].map(d=>{const b=document.createElement('button');b.type='button';b.textContent={N:'↑',W:'←',S:'↓',E:'→'}[d];b.setAttribute('aria-label','向'+{N:'北',W:'西',S:'南',E:'东'}[d]+'移动');b.style.touchAction='none';
  let pressed=false;
  b.addEventListener('pointerdown',e=>{if(!movement||b.disabled)return;e.preventDefault();pressed=true;b.setPointerCapture?.(e.pointerId);movement.press(d);});
  for(const type of ['pointerup','pointercancel','lostpointercapture'])b.addEventListener(type,()=>{if(pressed){movement?.release(d);pressed=false;}});
  b.addEventListener('click',e=>{if(movement){if(e.detail===0)movement.tap(d);}else onMove(d);});host.append(b);return [d,b];});
 return {update(canMove){for(const [d,b]of buttons)b.disabled=!canMove(d);},destroy(){movement?.clear();host.replaceChildren();}};
}
