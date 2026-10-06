import {createMovementInput} from './movement-input.mjs';
import {mountFailureRecovery} from './failure-recovery.mjs';
import {controlHint,disabledHint} from './rpg-view.mjs';
import {mountRpg} from './rpg-scene.mjs';
import {AgentClient} from './client.mjs';
import {names,states,codes,intentLabel,arenaTiles,resultLabel,carryPresentation} from './battle-view.mjs';
import {navigateMode} from './mode-navigation.mjs';
import {pokemonCard,renderAgentPanel} from './game-ui.mjs';

export function trainingPresentation(view={},targetId='wild-treecko') {
  const target=(view.targets??[]).find(t=>t.id===targetId),world=view.world??{};
  const adjacent=Boolean(target?.state==='WILD'&&Number.isFinite(Number(world.x))&&Number.isFinite(Number(world.y))&&Math.max(Math.abs(Number(world.x)-target.x),Math.abs(Number(world.y)-target.y))===1);
  const name=names[targetId]??'目标';
  let detail=!target?'连接游戏后查看目标。':target.state==='CAPTURED'?'木守宫已进入训练场精灵球，暂停或结束 AI 后点击「收入收藏」。':target.state==='TRANSFERRED'?'已收入收藏，前往研究室接回成长队伍，再培养和对战。':target.state==='DEFEATED'?'目标已击败，可重新训练开始下一次练习。':adjacent?`已与${name}相邻，可以${targetId==='wild-torchic'?'攻击；火稚鸡暂不可捕捉':'捕捉或攻击'}。`:`先接近${name}，抵达相邻格后再行动。`;
  if(Number(world.actorHp)<=0)detail='水跃鱼已倒下，暂停或结束 AI 后恢复训练伙伴；目标进度保留。';
  const confirmation=view.intent?`${intentLabel(view.intent)}。执行伙伴：水跃鱼。确认后按训练场规则行动，可随时暂停。`:'';
  return {adjacent,detail,confirmation};
}
export function trainingError(view={},failure=null,online=true) {
  if(view.errorCode)return codes[view.errorCode]??view.errorCode;
  if(failure?.disconnected&&online)return '';
  return failure&&(!view.revision||view.revision<=failure.revision)?failure.message:'';
}

export function trainingJourney(view={}) {
 const targets=view.targets??[],tree=targets.find(t=>t.id==='wild-treecko'),fire=targets.find(t=>t.id==='wild-torchic');
 const captured=tree?.state==='CAPTURED',owned=tree?.state==='TRANSFERRED',down=Number(view.world?.actorHp)<=0;
 if(down)return {title:'恢复训练伙伴',detail:'恢复后继续当前训练；已捕捉伙伴和野生目标进度保留。',action:'REST',label:'恢复训练伙伴',step:0};
 if(captured)return {title:'捕捉成功 · 收入收藏',detail:'先把木守宫收入收藏，再开始下一轮。未收藏的捕捉不会被重新训练清空。',action:'TRANSFER',label:'收入收藏',step:1};
 if(owned)return {title:'伙伴已收藏 · 前往研究室培养',detail:'打开队伍，在“接回成长队伍”中迁移为 Lv.5；培养等级与技能后进入训练师对战。',action:'GROWTH',label:'前往研究室培养',step:2};
 if(tree?.state==='DEFEATED'&&fire?.state==='DEFEATED')return {title:'本轮训练结束',detail:'本轮目标已全部击败。开始下一轮恢复训练伙伴并刷新目标，已有收藏保留。',action:'RESET',label:'开始下一轮',step:0};
 if(tree?.state==='DEFEATED')return {title:'木守宫已击败',detail:'已击败的伙伴无法捕捉。可继续挑战火稚鸡，或重新训练后尝试捕捉。',action:'RESET',label:'重新练习捕捉',step:0};
 const adjacent=trainingPresentation(view,'wild-treecko').adjacent;
 return {title:adjacent?'捕捉木守宫':'接近木守宫',detail:adjacent?'入门规则允许直接捕捉；无需先把它击倒。':'每次点击前进一格；地图方向键也可移动。',action:adjacent?'CAPTURE':'APPROACH',label:adjacent?'捕捉木守宫':'向木守宫走一步',step:0};
}

export function newbieGuide(view={},targetId='wild-treecko') {
 const t=view.tutorial??{},tree=view.targets?.find(p=>p.id==='wild-treecko');
 if(t.skipped)return {...trainingJourney(view),guided:false};
 const row=(step,title,detail,action,label,target='wild-treecko')=>({step,title,detail,action,label,target,guided:true});
 if(Number(view.world?.actorHp)<=0)return row(6,'恢复伙伴','点击恢复，保留本轮目标进度。','REST','恢复伙伴');
 if(tree?.state==='DEFEATED')return row(4,'目标已击败','已倒下的木守宫无法捕捉；重新训练后再试。','RESET','重新练习捕捉');
 if(!['CAPTURED','TRANSFERRED'].includes(tree?.state)){
  if((t.moves??0)<2)return row(1,'移动你的伙伴','用方向键 / WASD 或触控移动两格。','MOVE','用方向键移动');
  if(!t.selected)return row(2,'选择木守宫','点击地图上的木守宫，白圈表示当前目标。','SELECT','点击地图目标');
  if(!trainingPresentation(view,'wild-treecko').adjacent)return row(3,'靠近木守宫','移动到目标旁边；也可点击辅助按钮前进一步。','APPROACH','向目标走一步');
  return row(4,'捕捉木守宫','入门训练允许直接捕捉，无需先击倒。','CAPTURE','捕捉木守宫');
 }
 if(tree.state==='CAPTURED')return row(4,'保存捕捉成果','收入收藏后，伙伴才会保留到下一轮。','TRANSFER','收入收藏');
 if(!t.attacked){const fire=view.targets?.find(p=>p.id==='wild-torchic');if(fire?.state==='DEFEATED'&&t.rested)return row(7,'前往研究室培养','已有战斗目标已完成，接回收藏伙伴后练习技能与 PP。','GROWTH','进入伙伴冒险');if(fire?.state==='DEFEATED')return row(6,'恢复伙伴','目标已完成战斗练习，恢复后继续。','REST','恢复伙伴');return trainingPresentation(view,'wild-torchic').adjacent?row(5,'练习一次攻击','点击攻击，观察伤害、反击和伙伴 HP。','ATTACK','攻击火稚鸡','wild-torchic'):row(5,'接近战斗目标','火稚鸡仅供战斗练习，靠近后攻击一次。','APPROACH','向火稚鸡走一步','wild-torchic');}
 if(!t.rested)return row(6,'恢复你的伙伴','恢复保留目标进度，不刷新野生伙伴。','REST','恢复伙伴');
 return row(7,'前往研究室培养','接回 Lv.5 木守宫并选为出场伙伴；第 8 步在野外学习技能与 PP。','GROWTH','进入伙伴冒险');
}

if(typeof document!=='undefined') {
const client=new AgentClient(undefined,undefined,document.body.dataset.apiBase||'/api/agent'),$=id=>document.getElementById(id);
let busy=false,polling=false,failure=null,clientFailure='';
const menu=$('training-menu');$('touch-movement').append(document.querySelector('.directions'));$('open-menu').onclick=()=>menu.showModal();$('close-menu').onclick=()=>menu.close();$('show-training-touch').onchange=e=>document.body.classList.toggle('show-training-touch',e.target.checked);
const recovery=mountFailureRecovery($('failure-recovery'),kind=>{if(kind==='RECONNECT')$('reconnect').click();else if(kind==='RETRY_PENDING')run(()=>client.retry());else if(kind==='SHOW_TRACE')$('trace').scrollIntoView({block:'center'});else run(()=>client.command(kind));});
const movement=createMovementInput({send:direction=>run(()=>client.command('MANUAL',{action:'move',arguments:{direction}})),canSend:()=>!menu.open&&client.connected&&!busy&&!client.pendingRequest&&client.view?.manualAllowed});
const rpg=mountRpg($('rpg-map'),{mode:'training',canMove:()=>!menu.open&&Boolean(document.querySelector('[data-direction="N"]')&&!document.querySelector('[data-direction="N"]').disabled),onMove:direction=>movement.tap(direction),movement,onTarget:id=>{const el=$('target');if(!menu.open&&!el.disabled&&[...el.options].some(o=>o.value===id)){el.value=id;el.dispatchEvent(new Event('change'));}}});
codes.CANCEL_BEFORE_SWITCH='请先完成任务确认或取消当前解析，再切换场景';codes.OTHER_MODE_ACTIVE='另一场景的 AI 正在行动，请返回该场景暂停后重试';
codes.MODE_NOT_SELECTED='操作权已切换到另一场景，请重新打开当前场景后操作';
codes.COLLECT_BEFORE_RESET='请先将捕捉到的木守宫收入收藏，再开始下一轮';codes.TRAINING_RESTED='训练伙伴已恢复，目标进度保留';
codes.NOT_CAPTURED='请先捕捉木守宫，再收入收藏';codes.COLLECTION_FULL='收藏背包已满（20个），本次捕捉保留在训练场';
function render() {
  const view=client.view,blocked=busy||!client.connected||Boolean(client.pendingRequest);
  $('connection').textContent=client.connected?'已连接游戏':'连接已断开';$('reconnect').hidden=client.connected;
  for(const link of document.querySelectorAll('#mode-switch,.collection-link,.growth-link,.duel-link'))link.setAttribute('aria-disabled',String(blocked));
  renderAgentPanel($('agent-progress'),view??{},client.connected,busy||Boolean(client.pendingRequest));
  for(const el of document.querySelectorAll('button'))el.disabled=blocked||!view;
  $('goal').disabled=blocked||!view||!['IDLE','ERROR'].includes(view.status);
  $('target').disabled=blocked||!view;
  $('reconnect').disabled=busy;
  $('error').textContent=trainingError(view??{},failure,client.connected);
  recovery.update({...view,clientError:clientFailure},'training',client.connected,busy,Boolean(client.pendingRequest));if(!view)return;
  $('status').textContent=`${states[view.status]??view.status}${view.intent?` · ${intentLabel(view.intent)}`:''}`;
  $('provider').textContent=view.aiAvailable?`${view.provider === 'deepseek' ? 'DeepSeek' : view.provider === 'gemini' ? 'Gemini' : 'OpenAI'} · ${view.model}，理解任务后由你确认。`:'AI 尚未配置，可以先使用右侧按钮手动训练。';
  $('turn').textContent=`第 ${view.world.turn} 回合`;
  $('map').replaceChildren(...arenaTiles(view).map(tile=>{const el=document.createElement('div');el.className=`tile ${tile.kind}`;el.textContent=tile.symbol;el.title=tile.label;return el;}));
  const teaching=newbieGuide(view);const visibleTarget=teaching.guided&&teaching.step===5?'wild-torchic':$('target').value;
  rpg.update(view,{targetId:view.tutorial?.selected||view.tutorial?.skipped?visibleTarget:null,online:client.connected,busy:blocked});
  const actor=pokemonCard({species:'MUDKIP',hp:Number(view.world.actorHp),maxHp:1000},{compact:true});actor.querySelector('.condition').textContent=Number(view.world.actorHp)>0?'入门训练伙伴':'需要恢复';
  const cards=(view.targets??[]).map(t=>{const card=pokemonCard({species:t.id==='wild-treecko'?'TREECKO':'TORCHIC',hp:t.hp,maxHp:100},{selected:t.id===$('target').value,compact:true});card.querySelector('.condition').textContent=({WILD:'野生目标',CAPTURED:'训练场精灵球中',TRANSFERRED:'已收入收藏',DEFEATED:'已击败'})[t.state]??t.state;return card;});
  $('health').replaceChildren(actor);const selected=view.targets?.find(t=>t.id===visibleTarget);$('health').dataset.target=view.tutorial?.selected&&selected?.state==='WILD'?`${names[selected.id]} · HP ${selected.hp}/100`:'';
  $('inventory').textContent=view.captured.length?view.captured.map(id=>`${names[id]} × 1`).join('，'):'暂无捕捉 · 捕捉木守宫后可收入收藏';
  const guide=trainingPresentation(view,$('target').value);
  $('target-detail').textContent=guide.detail;
  $('confirmation').hidden=view.status!=='READY';$('intent').textContent=guide.confirmation;
  const journey=newbieGuide(view,$('target').value);$('journey-title').textContent=journey.title;$('journey-count').textContent=journey.guided?`${journey.step} / 8`:'自由训练';
  $('journey-detail').textContent=!view.manualAllowed?'AI 正在操作或等待确认，请在菜单中暂停或取消后接手。':journey.detail;
  $('journey-action').textContent=journey.label;
  $('journey-action').disabled=blocked||!view.manualAllowed||['MOVE','SELECT'].includes(journey.action)||(journey.action==='TRANSFER'&&!carryPresentation(view).canTransfer);
  $('skip-tutorial').hidden=Boolean(view.tutorial?.skipped);$('replay-tutorial').disabled=blocked;
  $('rest-training').disabled=blocked||!view.manualAllowed||Number(view.world.actorHp)>=1000;
  const carry=carryPresentation(view);$('collection').textContent=carry.label;
  $('collection-count').textContent=`${(view.collection??[]).length} / 20`;
  $('collection-preview').replaceChildren(...(view.collection??[]).map(p=>{const card=pokemonCard(p,{compact:true});const state=document.createElement('p');state.className='collection-state';state.textContent=p.state==='DEPLOYED'?'已在委托地图放出':'精灵球中 · 可在委托地图召唤';card.querySelector('.pokemon-info').append(state);return card;}));
  $('transfer').disabled=blocked||!carry.canTransfer;
  $('parse').disabled=blocked||!view.aiAvailable||!['IDLE','ERROR'].includes(view.status)||!$('goal').value.trim();
  for(const el of document.querySelectorAll('[data-goal]'))el.disabled=blocked||!['IDLE','ERROR'].includes(view.status);
  $('confirm').disabled=blocked||view.status!=='READY';
  $('pause').disabled=blocked||!['RUNNING','REPLANNING'].includes(view.status);
  $('resume').disabled=blocked||!['PAUSED','PROVIDER_UNAVAILABLE'].includes(view.status);
  $('reset').disabled=blocked||view.captured.length>0||!['IDLE','ERROR','COMPLETED','FAILED','CANCELLED'].includes(view.status);
  for(const el of document.querySelectorAll('[data-direction],#attack,#capture,#approach'))el.disabled=blocked||!view.manualAllowed;
  $('approach').disabled||=guide.adjacent||view.targets.find(t=>t.id===$('target').value)?.state!=='WILD';
  $('capture').disabled||=$('target').value==='wild-torchic'||view.targets.find(t=>t.id===$('target').value)?.state!=='WILD';
  $('attack').disabled||=view.targets.find(t=>t.id===$('target').value)?.state!=='WILD';
  $('manual-note').textContent=controlHint(view,client.connected,blocked);
  $('recover').hidden=view.status!=='STORAGE_ERROR';$('cancel').disabled=busy||!client.connected||['IDLE','COMPLETED','FAILED','CANCELLED'].includes(view.status);
  $('saved').textContent=`${view.storage==='MEMORY'?'内存训练':'单人训练存档'}${view.recovered?' · 已恢复，未完成任务保持暂停':''}`;
  for(const button of document.querySelectorAll('button'))button.title=button.disabled?disabledHint(view,button.id,client.connected,busy||Boolean(client.pendingRequest)):'';
  if(!blocked&&view.manualAllowed&&$('capture').disabled)$('capture').title=$('target').value==='wild-torchic'?'火稚鸡暂不可捕捉。':'目标已捕捉或已击败。';
  if(!blocked&&view.captured.length>0)$('reset').title='请先收入收藏，避免丢失本次捕捉';
  if(!blocked&&$('transfer').disabled)$('transfer').title=(view.collection??[]).length>=20?'收藏已满（20个）。':'请先捕捉木守宫，并暂停或结束 AI。';
  const metrics=view.metrics??{};$('metrics').textContent=`模型决策 ${metrics.decisionCount??0} 次`;
  const trace=(view.trace??[]).slice().reverse();$('trace-empty').hidden=trace.length>0;
  $('trace').replaceChildren(...trace.map(event=>{const el=document.createElement('li');el.textContent=resultLabel(event);return el;}));
}
async function run(action) {
  if(busy)return;busy=true;failure=null;render();
  try{await action();await client.poll();}catch(error){if(error.message==='REQUEST_LIMIT')clientFailure=error.message;failure={revision:client.view?.revision??0,message:codes[error.message]??error.message,disconnected:!client.connected};}
  finally{busy=false;render();}
}
$('goal-form').addEventListener('submit',event=>{event.preventDefault();if(!$('parse').disabled)run(()=>client.command('PARSE',{text:$('goal').value.trim()}));});
$('goal').addEventListener('input',()=>{$('parse').disabled=busy||!client.connected||Boolean(client.pendingRequest)||!client.view?.aiAvailable||!['IDLE','ERROR'].includes(client.view?.status)||!$('goal').value.trim();});
for(const button of document.querySelectorAll('[data-goal]'))button.addEventListener('click',()=>{if(button.disabled)return;$('goal').value=button.dataset.goal;render();$('goal').focus();});
for(const [id,command] of Object.entries({confirm:'CONFIRM',pause:'PAUSE',resume:'RESUME',cancel:'CANCEL',reset:'RESET',recover:'RECOVER'}))$(id).addEventListener('click',()=>run(()=>client.command(command)));
$('reconnect').addEventListener('click',()=>run(async()=>{await client.connect();clientFailure='';if(client.pendingRequest)await client.retry();}));
for(const el of document.querySelectorAll('[data-direction]'))el.addEventListener('click',()=>movement.tap(el.dataset.direction));
for(const action of ['approach','attack','capture'])$(action).addEventListener('click',()=>run(()=>client.command('MANUAL',{action,arguments:{targetId:$('target').value}})));
$('rest-training').addEventListener('click',()=>run(()=>client.command('REST')));
$('skip-tutorial').onclick=()=>run(()=>client.command('TUTORIAL',{action:'SKIP'}));
$('replay-tutorial').onclick=()=>run(async()=>{await client.command('TUTORIAL',{action:'REPLAY'});menu.close();});
$('journey-action').addEventListener('click',()=>{const j=newbieGuide(client.view);if(j.action==='GROWTH')return run(()=>leaveTraining('/growth/#training'));run(()=>j.action==='TRANSFER'?client.command('TRANSFER',{targetId:'wild-treecko'}):['REST','RESET'].includes(j.action)?client.command(j.action):client.command('MANUAL',{action:j.action.toLowerCase(),arguments:{targetId:j.target??'wild-treecko'}}));});
async function leaveTraining(href){const v=await client.poll();if(['PARSING','READY'].includes(v.status))throw new Error('CANCEL_BEFORE_SWITCH');if(['RUNNING','REPLANNING'].includes(v.status))await client.command('PAUSE');globalThis.location.assign(href);}
for(const link of document.querySelectorAll('.growth-link,.duel-link'))link.addEventListener('click',event=>{event.preventDefault();if(!busy&&client.connected&&!client.pendingRequest)run(()=>leaveTraining(link.getAttribute('href')));});
$('target').addEventListener('change',()=>{render();if($('target').value==='wild-treecko'&&!client.view?.tutorial?.selected)run(()=>client.command('TUTORIAL',{action:'SELECT'}));});
$('transfer').addEventListener('click',()=>run(()=>client.command('TRANSFER',{targetId:'wild-treecko'})));
for(const link of document.querySelectorAll('#mode-switch,.collection-link'))link.addEventListener('click',event=>{event.preventDefault();if(link.getAttribute('aria-disabled')!=='true')run(()=>navigateMode(client,'/quest/'));});
await run(()=>client.connect());
setInterval(async()=>{if(busy||polling||!client.roomId)return;polling=true;try{await client.poll();}catch(error){if(error.message==='REQUEST_LIMIT')clientFailure=error.message;failure={revision:client.view?.revision??0,message:codes[error.message]??error.message,disconnected:!client.connected};}finally{polling=false;render();}},750);
}


