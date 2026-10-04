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
  let detail=!target?'连接游戏后查看目标。':target.state==='CAPTURED'?'木守宫已进入训练场精灵球，暂停或结束 AI 后点击「收入收藏」。':target.state==='TRANSFERRED'?'已收入收藏，可前往 AI 委托地图召唤。':target.state==='DEFEATED'?'目标已击败，可重新训练开始下一次练习。':adjacent?`已与${name}相邻，可以${targetId==='wild-torchic'?'攻击；火稚鸡暂不可捕捉':'捕捉或攻击'}。`:`先接近${name}，抵达相邻格后再行动。`;
  if(Number(world.actorHp)<=0)detail='水跃鱼已倒下，结束当前任务后重新训练。';
  const confirmation=view.intent?`${intentLabel(view.intent)}。执行伙伴：水跃鱼。确认后按训练场规则行动，可随时暂停。`:'';
  return {adjacent,detail,confirmation};
}
export function trainingError(view={},failure=null,online=true) {
  if(view.errorCode)return codes[view.errorCode]??view.errorCode;
  if(failure?.disconnected&&online)return '';
  return failure&&(!view.revision||view.revision<=failure.revision)?failure.message:'';
}

if(typeof document!=='undefined') {
const client=new AgentClient(undefined,undefined,document.body.dataset.apiBase||'/api/agent'),$=id=>document.getElementById(id);
let busy=false,polling=false,failure=null,clientFailure='';
const recovery=mountFailureRecovery($('failure-recovery'),kind=>{if(kind==='RECONNECT')$('reconnect').click();else if(kind==='RETRY_PENDING')run(()=>client.retry());else if(kind==='SHOW_TRACE')$('trace').scrollIntoView({block:'center'});else run(()=>client.command(kind));});
const rpg=mountRpg($('rpg-map'),{mode:'training',canMove:()=>Boolean(document.querySelector('[data-direction="N"]')&&!document.querySelector('[data-direction="N"]').disabled),onMove:direction=>document.querySelector(`[data-direction="${direction}"]`)?.click(),onTarget:id=>{const el=$('target');if(!el.disabled&&[...el.options].some(o=>o.value===id)){el.value=id;el.dispatchEvent(new Event('change'));}}});
codes.CANCEL_BEFORE_SWITCH='请先完成任务确认或取消当前解析，再切换场景';codes.OTHER_MODE_ACTIVE='另一场景的 AI 正在行动，请返回该场景暂停后重试';
codes.MODE_NOT_SELECTED='操作权已切换到另一场景，请重新打开当前场景后操作';
codes.NOT_CAPTURED='请先捕捉木守宫，再收入收藏';codes.COLLECTION_FULL='收藏背包已满（20个），本次捕捉保留在训练场';
function render() {
  const view=client.view,blocked=busy||!client.connected||Boolean(client.pendingRequest);
  $('connection').textContent=client.connected?'已连接游戏':'连接已断开';$('reconnect').hidden=client.connected;
  for(const link of document.querySelectorAll('#mode-switch,.collection-link'))link.setAttribute('aria-disabled',String(blocked));
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
  rpg.update(view,{targetId:$('target').value,online:client.connected,busy:blocked});
  const actor=pokemonCard({species:'MUDKIP',hp:Number(view.world.actorHp),maxHp:1000},{compact:true});actor.querySelector('.condition').textContent=Number(view.world.actorHp)>0?'训练伙伴':'需要重新训练';
  const cards=(view.targets??[]).map(t=>{const card=pokemonCard({species:t.id==='wild-treecko'?'TREECKO':'TORCHIC',hp:t.hp,maxHp:100},{selected:t.id===$('target').value,compact:true});card.querySelector('.condition').textContent=({WILD:'野生目标',CAPTURED:'训练场精灵球中',TRANSFERRED:'已收入收藏',DEFEATED:'已击败'})[t.state]??t.state;return card;});
  $('health').replaceChildren(actor,...cards);
  $('inventory').textContent=view.captured.length?view.captured.map(id=>`${names[id]} × 1`).join('，'):'暂无捕捉 · 捕捉木守宫后可收入收藏';
  const guide=trainingPresentation(view,$('target').value);
  $('target-detail').textContent=guide.detail;
  $('confirmation').hidden=view.status!=='READY';$('intent').textContent=guide.confirmation;
  const carry=carryPresentation(view);$('collection').textContent=carry.label;
  $('collection-count').textContent=`${(view.collection??[]).length} / 20`;
  $('collection-preview').replaceChildren(...(view.collection??[]).map(p=>{const card=pokemonCard(p,{compact:true});const state=document.createElement('p');state.className='collection-state';state.textContent=p.state==='DEPLOYED'?'已在委托地图放出':'精灵球中 · 可在委托地图召唤';card.querySelector('.pokemon-info').append(state);return card;}));
  $('transfer').disabled=blocked||!carry.canTransfer;
  $('parse').disabled=blocked||!view.aiAvailable||!['IDLE','ERROR'].includes(view.status)||!$('goal').value.trim();
  for(const el of document.querySelectorAll('[data-goal]'))el.disabled=blocked||!['IDLE','ERROR'].includes(view.status);
  $('confirm').disabled=blocked||view.status!=='READY';
  $('pause').disabled=blocked||!['RUNNING','REPLANNING'].includes(view.status);
  $('resume').disabled=blocked||!['PAUSED','PROVIDER_UNAVAILABLE'].includes(view.status);
  $('reset').disabled=blocked||!['IDLE','ERROR','COMPLETED','FAILED','CANCELLED'].includes(view.status);
  for(const el of document.querySelectorAll('[data-direction],#attack,#capture'))el.disabled=blocked||!view.manualAllowed;
  $('capture').disabled||=$('target').value==='wild-torchic'||view.targets.find(t=>t.id===$('target').value)?.state!=='WILD';
  $('attack').disabled||=view.targets.find(t=>t.id===$('target').value)?.state!=='WILD';
  $('manual-note').textContent=controlHint(view,client.connected,blocked);
  $('recover').hidden=view.status!=='STORAGE_ERROR';$('cancel').disabled=busy||!client.connected||['IDLE','COMPLETED','FAILED','CANCELLED'].includes(view.status);
  $('saved').textContent=`${view.storage==='MEMORY'?'内存训练':'单人训练存档'}${view.recovered?' · 已恢复，未完成任务保持暂停':''}`;
  for(const button of document.querySelectorAll('button'))button.title=button.disabled?disabledHint(view,button.id,client.connected,busy||Boolean(client.pendingRequest)):'';
  if(!blocked&&view.manualAllowed&&$('capture').disabled)$('capture').title=$('target').value==='wild-torchic'?'火稚鸡暂不可捕捉。':'目标已捕捉或已击败。';
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
for(const el of document.querySelectorAll('[data-direction]'))el.addEventListener('click',()=>run(()=>client.command('MANUAL',{action:'move',arguments:{direction:el.dataset.direction}})));
for(const action of ['attack','capture'])$(action).addEventListener('click',()=>run(()=>client.command('MANUAL',{action,arguments:{targetId:$('target').value}})));
$('target').addEventListener('change',render);
$('transfer').addEventListener('click',()=>run(()=>client.command('TRANSFER',{targetId:'wild-treecko'})));
for(const link of document.querySelectorAll('#mode-switch,.collection-link'))link.addEventListener('click',event=>{event.preventDefault();if(link.getAttribute('aria-disabled')!=='true')run(()=>navigateMode(client,'/quest/'));});
await run(()=>client.connect());
setInterval(async()=>{if(busy||polling||!client.roomId)return;polling=true;try{await client.poll();}catch(error){if(error.message==='REQUEST_LIMIT')clientFailure=error.message;failure={revision:client.view?.revision??0,message:codes[error.message]??error.message,disconnected:!client.connected};}finally{polling=false;render();}},750);
}
