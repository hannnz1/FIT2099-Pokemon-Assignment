import {failureReason} from './failure-recovery.mjs';
const regionNames={lab:'研究室',forest:'苔叶森林',river:'浅溪河岸',mountain:'赤岩山道'};
import {portraitUrl,pokemonNames,growthLevelForExperience} from './pokemon-art.mjs';
const active=['RUNNING','REPLANNING','PARSING'];
export function agentPresentation(view={},online=true,busy=false){
 const status=view.status??'IDLE';let label,detail,tone='neutral',phase='ready';
 const states={IDLE:['等待委托','选择目标后，把任务交给伙伴。'],PARSING:['正在理解任务','伙伴正在理解你的要求，请稍候。'],READY:['请确认目标','检查目标与限制，确认后伙伴才会出发。'],RUNNING:['伙伴正在行动','伙伴正在观察与行动，你可以随时暂停。'],REPLANNING:['正在调整计划','伙伴遇到了变化，正在重新选择下一步行动。'],WAITING_APPROVAL:['需要你的决定','请批准或拒绝购买；等待期间不会推进游戏回合。'],PAUSED:['委托已暂停','可以继续委托，也可以自己操作伙伴。'],COMPLETED:['委托已完成','目标已达成，可以查看结果与行动记录。'],FAILED:['本次委托未完成','查看行动记录和原因，休整后再尝试。'],CANCELLED:['委托已取消','已保留实际游戏进度。'],PROVIDER_UNAVAILABLE:['AI 暂时不可用','可以稍后继续，或取消后手动操作。'],ERROR:['需要重新描述任务','请调整任务描述后重新提交。'],STORAGE_ERROR:['存档需要恢复','请先恢复存档，确认后再继续操作。']};
 [label,detail]=states[status]??['状态待确认','请重新连接以确认当前任务。'];
 if(status==='FAILED'||status==='PROVIDER_UNAVAILABLE')detail=failureReason(view);
 if(status==='PARSING')phase='understand';else if(status==='READY')phase='confirm';else if(['RUNNING','REPLANNING','WAITING_APPROVAL','PAUSED','PROVIDER_UNAVAILABLE'].includes(status))phase='execute';else if(status==='COMPLETED')phase='complete';else if(['FAILED','CANCELLED','ERROR','STORAGE_ERROR'].includes(status))phase='stopped';
 if(status==='COMPLETED')tone='success';else if(['WAITING_APPROVAL','PROVIDER_UNAVAILABLE','FAILED','ERROR','STORAGE_ERROR'].includes(status))tone='warning';else if(active.includes(status))tone='active';
 if(status==='IDLE'&&!view.aiAvailable)detail='AI 尚未配置，可先手动探索、战斗和培养伙伴。';
 const partner=view.world?.partner;
 if(partner&&view.targetLevel&&phase==='execute')detail=`当前 Lv.${partner.level} → 目标 Lv.${view.targetLevel}。${view.targetRegion?`培养区域：${regionNames[view.targetRegion]??view.targetRegion}。`:""}${detail}`;
 const steps=view.metrics?.totalSteps??view.metrics?.actionSteps??null;
 if(!online){label='连接已中断';detail='正在重新连接；当前显示上次收到的进度。';tone='warning';}
 else if(busy){detail='正在确认你的操作，请稍候。';}
 return {label,detail,tone,phase,working:online&&!busy&&active.includes(status),steps};
}
export function pokemonPresentation(source={}){
 const p={...source,...(source.growth??{})},hp=Number(p.hp??0),maxHp=Number(p.maxHp??0),level=Number.isFinite(p.level)?p.level:growthLevelForExperience(p.experience);
 return {name:pokemonNames[p.species]??p.name??'伙伴',species:p.species,hp,maxHp,hpText:`${hp} / ${maxHp}`,health:hp<=0?'empty':maxHp>0&&hp/maxHp<=.3?'low':maxHp>0&&hp/maxHp<=.6?'medium':'healthy',level,condition:hp<=0?'需要恢复':({POISON:'中毒',PARALYSIS:'麻痹',SLEEP:'睡眠',BURN:'灼伤'})[p.traits?.status]??(p.burned?'灼伤':'状态良好'),experienceRemaining:Number.isFinite(p.experience)&&Number.isFinite(p.nextLevelExperience)?Math.max(0,p.nextLevelExperience-p.experience):null};
}
export function growthGuide(view={},online=true,target=null){
 const w=view.world??{},p=w.partner;
 if(!online)return {title:'恢复连接',detail:'请重新连接，确认最新进度后继续。'};
 if(view.status==='STORAGE_ERROR')return {title:'确认存档',detail:'存档暂未确认，请先恢复存档。'};
 if(active.includes(view.status)||view.status==='PAUSED')return {title:agentPresentation(view).label,detail:agentPresentation(view).detail};
 if(!p)return {title:w.starterAvailable===false?'接回伙伴':'选择你的第一位伙伴',detail:w.starterAvailable===false?'伙伴在委托地图收藏中，可返回研究室培养。':'在右侧选择木守宫、水跃鱼或火稚鸡，再前往森林。'};
 if(p.hp<=0)return {title:'先让伙伴恢复',detail:'前往恢复点或返回研究室，使用全队恢复。'};
 if(w.region==='lab')return {title:'准备出发',detail:'前往森林探索；也可以在这里学习技能或领取奖励；达到进化等级后会自动进化。'};
 if(target)return {title:target.targetAdjacent?'选择技能或捕捉':'接近选中的目标',detail:target.targetAdjacent?'在右侧选择技能，或尝试捕捉；留意伙伴的 HP 与 PP。':'点击接近目标，每次前进一步，抵达相邻格后再行动。'};
 return {title:'探索当前区域',detail:'点击地图上的野生伙伴选择目标；方向键或 WASD 可移动。'};
}
const el=(tag,cls,text)=>{const n=document.createElement(tag);if(cls)n.className=cls;if(text!==undefined)n.textContent=text;return n;};
export function pokemonCard(source,{selected=false,compact=false}={}){
 const p=pokemonPresentation(source),card=el('article','pokemon-card'+(selected?' selected':'')+(compact?' compact':''));
 card.dataset.health=p.health;const img=el('img','pokemon-portrait');img.src=portraitUrl(p.species);img.alt=p.name;img.width=112;img.height=112;
 const info=el('div','pokemon-info'),heading=el('div','pokemon-heading');heading.append(el('strong','',p.name),el('span','level-badge',p.level===null?'伙伴':`Lv.${p.level}`));info.append(heading,el('span','condition',p.condition));
 const hp=el('div','meter-label');hp.append(el('span','','HP'),el('span','',p.hpText));const bar=el('progress','hp-meter');bar.max=Math.max(1,p.maxHp);bar.value=Math.max(0,p.hp);bar.setAttribute('aria-label',p.name+' HP '+p.hpText);info.append(hp,bar);
 if(!compact&&p.experienceRemaining!==null)info.append(el('p','experience-note',p.experienceRemaining>0?`再获得 ${p.experienceRemaining} EXP 到下一级`:'已达到本阶段经验目标'));
 card.append(img,info);return card;
}
export function renderAgentPanel(host,view,online,busy){
 const p=agentPresentation(view,online,busy);host.dataset.tone=p.tone;host.setAttribute('aria-busy',String(p.working));
 const title=host.querySelector('[data-agent-title]'),detail=host.querySelector('[data-agent-detail]');title.textContent=p.label;detail.textContent=p.detail;
 for(const step of host.querySelectorAll('[data-phase]')){const order=['understand','confirm','execute','complete'],i=order.indexOf(step.dataset.phase),current=order.indexOf(p.phase);step.dataset.current=String(i===current);step.dataset.done=String(current>=0&&i<current);if(i===current)step.setAttribute('aria-current','step');else step.removeAttribute('aria-current');}
}
