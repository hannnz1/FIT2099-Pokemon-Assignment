// Presentation helpers only: canonical commands, fees and saves stay with each client.
export function questWorkspace(status){
 if(['COMPLETED','FAILED','CANCELLED'].includes(status))return 'result';
 if(status==='READY')return 'confirm';
 if(['PARSING','RUNNING','REPLANNING','WAITING_APPROVAL','PAUSED','PROVIDER_UNAVAILABLE'].includes(status))return 'running';
 return 'setup';
}
export function battleWorkspace(view){
 const status=view?.status??'IDLE',started=Boolean(view?.world?.ownTeam?.length);
 return {arena:started,preparation:!['RUNNING','PAUSED'].includes(status),outcome:started&&['COMPLETED','FAILED','CANCELLED'].includes(status)};
}
export function mountPlayerNavigation(doc=globalThis.document){
 const header=doc.querySelector('header');if(!header||header.querySelector('.player-mode-menu'))return;
 const menu=doc.createElement('details');menu.className='player-mode-menu';const summary=doc.createElement('summary');summary.textContent='玩法';menu.append(summary);
 const nav=doc.createElement('nav');nav.setAttribute('aria-label','玩法切换');
 for(const [path,name] of [['growth','伙伴冒险'],['training','战斗训练'],['duel','训练师对战'],['quest','AI 委托'],['evaluation','评测中心']]){
  const a=doc.createElement('a');a.href='/'+path+'/';a.textContent=name;if(doc.body.dataset.playerPage===path)a.setAttribute('aria-current','page');nav.append(a);
 }
 menu.append(nav);header.append(menu);
 doc.addEventListener('pointerdown',e=>{if(menu.open&&!menu.contains(e.target))menu.open=false;});
 doc.addEventListener('keydown',e=>{if(e.key==='Escape'&&menu.open){menu.open=false;summary.focus();}});
 // A menu owns focus; opening it must not also move a Pokémon.
 menu.addEventListener('keydown',e=>{if(e.key==='Escape'){menu.open=false;summary.focus();e.preventDefault();}e.stopPropagation();});
 return menu;
}
export function mountEvaluationWorkspace(doc=globalThis.document){
 const setup=doc.querySelector?.('.setup'),heading=setup?.querySelector?.('.heading');let previousBatch=null;
 if(!heading)return {updatePresets(){},update(){}};
 heading.classList.add('evaluation-workspace-summary');
 const toggle=doc.createElement('button');toggle.type='button';toggle.textContent='调整选项';toggle.hidden=true;heading.append(toggle);
 const expand=()=>{setup.classList.remove('collapsed');toggle.textContent='收起选项';toggle.setAttribute('aria-expanded','true');};
 toggle.onclick=()=>{const collapsed=setup.classList.toggle('collapsed');toggle.textContent=collapsed?'调整选项':'收起选项';toggle.setAttribute('aria-expanded',String(!collapsed));};
 const presets=doc.createElement('div');presets.className='evaluation-presets';presets.setAttribute('aria-label','测试预设');
 for(const [value,label] of [['quick','快速体验'],['stable','稳定性测试'],['custom','自定义']]){
  const b=doc.createElement('button');b.type='button';b.dataset.preset=value;b.textContent=label;b.onclick=()=>{
   const custom=doc.getElementById('custom-options');
   if(value!=='custom'){const radio=doc.querySelector(`[name=depth][value=${value}]`);radio.checked=true;radio.dispatchEvent(new Event('input',{bubbles:true}));}
   else{custom.checked=true;doc.getElementById('advanced').open=true;custom.dispatchEvent(new Event('input',{bubbles:true}));}
  };presets.append(b);
 }
 doc.getElementById('setup').prepend(presets);
 const oldAgain=doc.getElementById('test-again');oldAgain.addEventListener('click',expand);
 return {updatePresets(){const value=doc.getElementById('custom-options').checked?'custom':doc.querySelector('[name=depth]:checked')?.value;for(const b of presets.children)b.setAttribute('aria-pressed',String(b.dataset.preset===value));},update(batch){
  toggle.hidden=!batch;
  if(batch&&batch.id!==previousBatch){previousBatch=batch.id;setup.classList.add('collapsed');toggle.textContent='调整选项';toggle.setAttribute('aria-expanded','false');}
 }};
}
