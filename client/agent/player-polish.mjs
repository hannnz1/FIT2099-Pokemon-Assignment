// Presentation only: no game rules, saves or model requests.
export function targetPanelState(target,practice,context={}){
 if(context.world&&!context.world.partner)return {active:false,hint:context.world.starterAvailable===false?'打开队伍，接回同行伙伴':'先选择与你同行的伙伴'};
 const active=Boolean(practice||target?.state==='WILD'&&target.hp>0);
 return {active,hint:practice?'练习不消耗真实 HP / PP':active&&!target.targetAdjacent?'靠近目标后可使用技能或捕捉':active?'选择技能，或尝试捕捉':'点击地图上的野生伙伴选择目标'};
}
export function cameraViewport(width){return width<=600?{width:448,height:448}:{width:832,height:576};}
export function parseTrainingRequest(text,partner,regions,current){
 const fail=reason=>({ok:false,reason});
 if(!partner)return fail('请先选择出场伙伴');
 if(/捕捉|采集|收集|交付|购买|对战|树果/.test(text))return fail('这里仅支持区域与目标等级的培养。其他任务请前往 AI 委托地图。');
 const tokens=text.match(/\d+/g)??[];
 if(tokens.length!==1||/[+\-−.]/.test(text))return fail('请只填写一个整数目标等级，例如：培养到 10 级');
 const allowed=/苔叶森林|浅溪河岸|赤岩山道|森林|河岸|山道|目标等级|目标|等级|帮我|培养|升级|训练|伙伴|精灵|请|把|将|在|去|练|升|到|至|级|\d+|[\s：:，,。]/g;
 let rest=text;if(partner.name)rest=rest.replaceAll(partner.name,'');
 if(rest.replace(allowed,'')||!/(培养|升级|训练|练|升|目标|级)/.test(text))return fail('这里只支持区域与目标等级的培养，请分别提交其他任务。');
 const match=[null,tokens[0]];
 const level=Number(match[1]);if(level<=partner.level||level>partner.levelCap)return fail('目标等级需要高于当前等级，且不超过 '+partner.levelCap+' 级');
 const names={forest:'苔叶森林',river:'浅溪河岸',mountain:'赤岩山道'};
 const region=Object.keys(names).find(id=>text.includes(names[id])||text.includes({forest:'森林',river:'河岸',mountain:'山道'}[id]))||current;
 if(!regions.some(r=>r.id===region&&r.unlocked!==false&&r.id!=='lab'))return fail('该区域尚未解锁，请先完成前一区域调查');
 return {ok:true,level,region};
}
export function mountPlayerPolish(doc,shell,getView,confirm){
 const $=id=>doc.getElementById(id);let preview=null,previewPartner=null;const invalidate=()=>{preview=null;$('ai-confirm').hidden=true;$('ai-preview-result').textContent='目标已变更，请重新预览后确认。';};$('training-region').addEventListener('change',invalidate);
 $('goal-detail-toggle').onclick=()=>{const expanded=$('training-onboarding-detail').hidden;$('training-onboarding-detail').hidden=!expanded;$('goal-detail-toggle').setAttribute('aria-expanded',String(expanded));};
 $('target-reopen').onclick=()=>doc.body.classList.add('target-open');
 $('target-collapse').onclick=()=>{doc.body.classList.remove('target-open');$('map').focus({preventScroll:true});};
 $('ai-preview').onclick=()=>{const v=getView();previewPartner=v?.world?.partner?.captureId;preview=parseTrainingRequest($('ai-request').value,v?.world?.partner,v?.world?.regions??[],$('training-region').value);$('ai-preview-result').textContent=preview.ok?'确认培养：'+({forest:'苔叶森林',river:'浅溪河岸',mountain:'赤岩山道'})[preview.region]+' · Lv.'+v.world.partner.level+' → '+preview.level+'。确认后才开始调用 AI。':preview.reason;$('ai-confirm').hidden=!preview.ok;};
 $('ai-request').oninput=()=>{preview=null;$('ai-confirm').hidden=true;$('ai-preview-result').textContent='';};
 $('ai-confirm').onclick=()=>{if(!preview?.ok)return;const v=getView(),fresh=parseTrainingRequest($('ai-request').value,v?.world?.partner,v?.world?.regions??[],$('training-region').value);if(previewPartner!==v?.world?.partner?.captureId||fresh.region!==preview.region||fresh.level!==preview.level){invalidate();return;}if(!fresh.ok){$('ai-preview-result').textContent=fresh.reason;$('ai-confirm').hidden=true;return;}confirm(fresh);};
 let reduced=globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches??false;
 try{const stored=localStorage.getItem('pokemon-reduced-motion');if(stored!==null)reduced=stored==='true';}catch{}
 $('reduce-motion').checked=reduced;doc.body.classList.toggle('reduce-motion',reduced);
 $('reduce-motion').onchange=()=>{doc.body.classList.toggle('reduce-motion',$('reduce-motion').checked);try{localStorage.setItem('pokemon-reduced-motion',String($('reduce-motion').checked));}catch{}};
 let selectedKey='';return {openTarget(){doc.body.classList.add('target-open');},update(v,target){
 if(preview&&previewPartner!==v.world?.partner?.captureId)invalidate();
 const practice=v.firstAdventure?.stage==='PRACTICE',state=targetPanelState(target,practice,v),key=practice?'practice':state.active?target.id:'';
 if(key!==selectedKey){doc.body.classList.toggle('target-open',state.active);selectedKey=key;}
 $('target-reopen').hidden=!state.active;doc.body.classList.toggle('has-target',state.active);doc.querySelector('.target-window').classList.toggle('empty-target',!state.active);$('target-empty').hidden=state.active;$('target-empty').textContent=state.hint;$('target-context').textContent=state.hint;$('target-context').hidden=!state.active;
 if(state.active&&!practice&&!target.targetAdjacent){$('skills').hidden=true;const capture=$('capture').querySelectorAll('button');if(capture.length>1)capture[capture.length-1].hidden=true;}
 doc.body.classList.toggle('story-active',!$('training-onboarding').hidden);
 $('ai-confirm').disabled=!v.aiAvailable||!v.manualAllowed||['RUNNING','REPLANNING'].includes(v.status);
 $('ai-preview').disabled=!v.world?.partner;
 for(const [index,b] of [...$('ai').querySelectorAll('button')].entries())if(index>0)b.hidden=b.disabled;
 if(!$('ai-status').textContent)$('ai-status').textContent=!v.aiAvailable?'AI 当前不可用，可继续手动探索。':!v.world?.partner?'请先选择伙伴，再确认培养目标。':'';
 }};
}
