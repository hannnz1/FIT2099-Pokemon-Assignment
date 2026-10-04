import {worldArtUrl} from './world-art.mjs';
import {mountFailureRecovery} from './failure-recovery.mjs';
import {mountJourney,mountTouch,filterPokemon} from './ui-journey.mjs';
import {pokemonCard,renderAgentPanel} from './game-ui.mjs';
import {controlHint,disabledHint,cooperationPresentation,aiPartnerSelection} from './rpg-view.mjs';
import {mountRpg} from './rpg-scene.mjs';
import { AgentClient } from './client.mjs';
import { dialoguePresentation } from './dialogue.mjs';
import { navigateMode } from './mode-navigation.mjs';
import { carryPresentation, summonPresentation, summonedMarker, fieldPresentation, speciesName, questTaskPresentation, fieldCodes } from './battle-view.mjs';
const client = new AgentClient(undefined,undefined,document.body.dataset.apiBase || '/api/agent');
const $ = id => document.getElementById(id);
const moveButton=direction=>client.view?.cooperative?$(`player-${direction.toLowerCase()}`):client.view?.wildActionAllowed?document.querySelector(`[data-wild-direction="${direction}"]`):$(`manual-${direction.toLowerCase()}`);
const rpg=mountRpg($('rpg-map'),{mode:'quest',canMove:()=>Boolean(moveButton('N')&&!moveButton('N').disabled),onMove:direction=>moveButton(direction)?.click(),onTarget:id=>{const el=$('wild-target');if([...el.options].some(o=>o.value===id)){el.value=id;el.dispatchEvent(new Event('change'));}}});
const journey=mountJourney($('journey-host'),'quest'),touch=mountTouch($('quest-touch'),direction=>moveButton(direction)?.click());
const recovery=mountFailureRecovery($('failure-recovery'),kind=>{if(kind==='RECONNECT')reconnect();else if(kind==='RETRY_PENDING')$('retry').click();else if(kind==='SHOW_TRACE'){const trace=$('trace');const details=trace.closest('details');if(details)details.open=true;trace.scrollIntoView({block:'center'});}else act(kind);});
let clientFailure='';
const states = { IDLE:'等待委托',PARSING:'正在理解委托',READY:'请确认目标',RUNNING:'伙伴正在行动',REPLANNING:'正在重新规划',WAITING_APPROVAL:'等待你的购买决定',PAUSED:'已暂停',COMPLETED:'已完成',FAILED:'任务失败',CANCELLED:'已取消',PROVIDER_UNAVAILABLE:'暂时无法思考',ERROR:'未能理解委托' };
const codes = {PROVIDER_BUDGET_EXHAUSTED:'网站 AI 今日或本月体验额度已用完；可继续手动冒险，额度到期自动恢复',PROVIDER_REQUEST_REJECTED:'AI 请求超出网站允许范围',MOVING:'正在沿小径前进',ARRIVED:'到达目的地',PICKED_UP:'已把树果放入背包',PURCHASED:'购买成功',QUEST_COMPLETED:'博士已收到树果',GOAL_COMPLETED:'委托完成',APPROVAL_REQUESTED:'请求你批准购买',APPROVED:'你已批准这笔购买',APPROVAL_DENIED:'你已拒绝购买',PROVIDER_TIMEOUT:'思考超时，可以稍后重试',PROVIDER_CONFIGURATION:'尚未配置可用的模型密钥',PROVIDER_UNAVAILABLE:'模型暂时不可用',PROVIDER_AUTHENTICATION:'模型密钥验证失败',PROVIDER_QUOTA_EXHAUSTED:'模型可用额度不足',PROVIDER_RATE_LIMIT:'模型请求过于频繁，请稍后重试',STALE_REVISION:'状态已变化，请查看最新状态后再操作',STALE_TASK:'这轮任务已结束，请操作当前任务',PARSE_LIMIT:'本房间已达到委托理解次数上限，重启服务可重新体验',RESUME_LIMIT:'本房间已达到继续次数上限，请取消任务',TASK_TIME_LIMIT:'已达到本轮行动时间上限',OPERATION_TIMEOUT:'本次思考超时，请选择继续或取消',DISCONNECTED:'连接中断，正在重新连接',REQUEST_PENDING:'刚才的操作还未确认',RESOURCE_NOT_AVAILABLE:'这里没有足够的树果，伙伴会重新规划',INSUFFICIENT_BERRIES:'树果不足，伙伴会重新规划',REQUEST_LIMIT:'本房间操作次数已达上限，请取消后重启服务',RATE_LIMIT:'操作过于频繁，请稍后重试',SERVER_BUSY:'本地服务忙，请稍后重试'};
const constraintNames={NO_SPENDING:'不花金币',NO_ACTIVE_BATTLE:'不主动战斗',AREA_RESTRICTED:'限制活动区域',DEADLINE:'指定截止回合'};
codes.AI_CONTROLS_COMPANION='请先暂停 AI，再自己操作水跃鱼';codes.QUEST_EXPIRED='已到截止时间，请开始新一轮';codes.AREA_RESTRICTED='目标超出允许区域';codes.STORAGE_UNAVAILABLE='存档未确认，操作已冻结，请重新读取存档';
Object.assign(codes,{COOP_REQUIRED:'请先在新一轮开启玩家协作',NEW_ROUND_REQUIRED:'请开始新一轮后开启协作',SHARED_BERRY_ONLY:'协作模式支持共同交付树果；野外委托请开始普通新一轮',PLAYER_PICKED_UP:'玩家采集了树果',PLAYER_WAITED:'玩家等待一回合',SHARED_DELIVERED:'已交付部分树果，共享任务继续',APPROVAL_FREEZE:'请先批准或拒绝购买，双方世界动作暂时等待',CONFIRM_BEFORE_PLAYER_ACTION:'请先确认或取消正在解析的委托'});
codes.MOVED='向目标前进一步';codes.OBSERVED='查看了脚边的环境';codes.INVENTORY='检查了自己的背包';codes.PROVIDER_UNSUPPORTED_TASK='请使用树果任务，或单个野生木守宫/水跃鱼捕捉、单个野生目标击败任务及四类约束';codes.PROVIDER_INVALID_RESPONSE='伙伴未能理解本次委托，请重试';codes.SESSION_REQUIRED='服务已重启，正在恢复会话';codes.PROVIDER_REQUEST_REJECTED='模型暂时无法处理本次请求';
codes.NPC_DIALOGUE='与 NPC 交流并取得信息';codes.WAITED='等待了一个行动回合';codes.QUEST_OBSERVED='查看了任务与当前环境';codes.OUT_OF_REACH='还没有靠近目标，伙伴会重新规划';
codes.CANCEL_BEFORE_SWITCH='请先完成任务确认或取消当前解析，再切换场景';codes.OTHER_MODE_ACTIVE='另一场景的 AI 正在行动，请返回该场景暂停后重试';
codes.MODE_NOT_SELECTED='操作权已切换到另一场景，请重新打开当前场景后操作';
let busy = false, notice = '', polling = false, renderedTrace = '', renderedDialogues = '', renderedCollection = '', renderedPartner = '',renderedFieldTask='';
function selectOptions(id,options){const el=$(id),key=JSON.stringify(options);if(el.dataset.options===key)return;el.dataset.options=key;const previous=el.value;el.replaceChildren(...options.map(p=>{const o=document.createElement('option');o.value=p.id;o.textContent=p.label;return o;}));if(options.some(p=>p.id===previous))el.value=previous;}
Object.assign(codes,{AI_PARTNER_SUMMONED:'AI 已召唤本次出战伙伴',OWNED_FIELD_ONLY:'收藏伙伴只支持捕捉或击败委托；协作树果请使用委托水跃鱼',OTHER_PARTNER_SUMMONED:'请先收回另一只已召唤的伙伴',AI_PARTNER_NOT_SUMMONED:'请重新召唤本次确认的收藏伙伴，再继续 AI'});
codes.NOT_OWNED='请选择自己收藏中的宝可梦';codes.ALREADY_SUMMONED='请先收回当前宝可梦，再召唤另一只';codes.NOT_SUMMONED='这只宝可梦当前没有放出';codes.NO_SUMMON_SPACE='这个方向没有可通行空位，请换个方向';codes.INVALID_DIRECTION='请选择东、南、西或北';codes.PATH_CHANGED='前方被挡住，请绕行或收回宝可梦';codes.POKEMON_SUMMONED='已把收藏宝可梦召唤到地图';codes.POKEMON_RECALLED='已将宝可梦收回精灵球';codes.FOLLOW_ENABLED='木守宫已开始跟随';codes.FOLLOW_DISABLED='收藏伙伴已停在原地';codes.FOLLOW_ENABLED='收藏伙伴已开始跟随';Object.assign(codes,{EXPLORATION_READY:'野外探索已开启',PARTNER_SELECTED:'已切换出战伙伴',WILD_STORED:'已将捕捉个体收入收藏',POKEMON_RESTED:'生命值已恢复',EXPLORATION_REQUIRED:'请先开启野外探索',ORIGINAL_MAP_REQUIRED:'请在原地图开启探索',NO_WILD_SPACE:'野生位置被占用，请收回伙伴或换位置后重试',LEADER_EXHAUSTED:'水跃鱼生命值不足以承担下一回合；暂停后选择委托水跃鱼并点击休整，即可获得濒危救助',PARTNER_EXHAUSTED:'伙伴生命值不足，请收回并到博士附近休整',RESEARCH_AREA_REQUIRED:'请先让水跃鱼靠近博士',RECALL_BEFORE_REST:'请先收回需要休整的宝可梦',NOT_CAPTURED:'请先捕捉这个目标',COLLECTION_FULL:'收藏背包已满',UNKNOWN_TARGET:'请选择野生目标，不能攻击任务NPC',TARGET_UNAVAILABLE:'这个目标已捕捉、收藏或击败',NOT_CAPTURABLE:'火稚鸡目前不可捕捉',OUT_OF_REACH:'请先指挥出战伙伴移动到目标相邻格',NO_ACTIVE_BATTLE:'当前委托约定不主动战斗',PATH_BLOCKED:'前方被占用或不能通行',ATTACKED:'攻击命中',ATTACK_MISSED:'攻击未命中',TARGET_DEFEATED:'目标已击败',CAPTURED:'捕捉成功'});
Object.assign(codes,fieldCodes);
const readable = code => codes[code] || (/fetch|abort|network/i.test(code)?'连接暂时中断，正在重新连接。':'本次操作未完成，请查看最新状态后重试。');
function text(id,value){$(id).textContent=value;}
function render() {
  $('mode-switch').setAttribute('aria-disabled',String(busy||!client.connected));
  const v=client.view;const online=client.connected;
  text('connection',online?'本地服务已连接':'连接中断');text('notice',notice || (online?'所有位置、物品与金币以游戏实际状态为准。':'正在重连；显示的是最后一次收到的状态。'));
  for(const el of document.querySelectorAll('button'))el.disabled=!online || busy||Boolean(client.pendingRequest);
  $('retry').hidden=!client.pendingRequest;$('retry').disabled=busy;
  recovery.update({...v,clientError:clientFailure},'quest',online,busy,Boolean(client.pendingRequest));if(!v)return;
  const npc=v.npcAgents;const npcLock=!online||busy||Boolean(client.pendingRequest);text('npc-status',npc?`角色状态 ${npc.status} · ${npc.pendingAgent?`正在思考：${npc.pendingAgent}`:'等待下一次行动'} · 消息 ${npc.messages?.length??0} 条`:'尚未开启；新一轮开始时可启用。');text('npc-records',JSON.stringify(npc?{messages:npc.messages,cognition:npc.cognition,trace:npc.trace,budget:npc.foundation.agents}:[],null,2));$('npc-cognition').replaceChildren(...(npc?.cognition??[]).map(row=>{const card=document.createElement('section');const title=document.createElement('h3');title.textContent=({torchic:'火稚鸡',professor:'博士',merchant:'商人'})[row.agentId]??row.agentId;card.append(title);const model=row.model;const entries=model?.reflection?[`模型推断（须核实）：${model.reflection.summary}`,`依据：${model.reflection.evidenceIds.join('、')}`,`早段：${model.plan.morning}`,`中段：${model.plan.afternoon}`,`晚段：${model.plan.evening}`,`阶段：${model.phase} · 下一步 ${model.plan.next}${model.requiresReplanning?" · 待重新规划":""}`,`自身观察 ${row.observations.length} 条 · 已压缩 ${row.compressed??0} 条`]:['正在准备观察与计划'];for(const entry of entries){const p=document.createElement('p');p.textContent=entry;card.append(p);}return card;}));$('npc-start').disabled=npcLock||Boolean(npc)||v.status!=='IDLE'||Number(v.world.turn)!==0||!v.aiAvailable;$('npc-pause').disabled=npcLock||npc?.status!=='RUNNING';$('npc-resume').disabled=npcLock||npc?.status!=='PAUSED'||['PARSING','READY','WAITING_APPROVAL','COMPLETED','FAILED'].includes(v.status);$('npc-cancel').disabled=npcLock||!npc||['COMPLETED','CANCELLED'].includes(npc.status);
  renderAgentPanel($('quest-agent-panel'),v,online,busy);
  journey.update(v);
  const s=v.status,w=v.world;const active=['RUNNING','REPLANNING','WAITING_APPROVAL'].includes(s);
  const field=fieldPresentation(v,$('wild-target').value);selectOptions('wild-target',field.targets);selectOptions('combat-partner',field.partners);selectOptions('rest-pokemon',field.restOptions);
  if(renderedPartner!==v.combatPartner){renderedPartner=v.combatPartner;$('combat-partner').value=v.combatPartner||'mudkip';}
  const fieldTaskKey=`${v.taskId}:${v.targetId??''}`;
  if(v.taskKind&&v.taskKind!=='COMPLETE_QUEST'&&renderedFieldTask!==fieldTaskKey&&field.targets.some(t=>t.id===v.targetId)){renderedFieldTask=fieldTaskKey;$('wild-target').value=v.targetId;}
  const taskView=questTaskPresentation(v);text('task-progress',taskView.progress);
  const coop=cooperationPresentation(v),coopLock=!online||busy||Boolean(client.pendingRequest);$('coop-controls').hidden=!coop.enabled;$('coop-start').hidden=coop.enabled;$('coop-start').disabled=coopLock||s!=='IDLE'||Number(w.turn)!==0;
  text('player-position',coop.position);text('shared-progress',coop.progress);text('coop-detail',coop.enabled?(v.playerAllowed?(['FAILED','CANCELLED'].includes(s)?'AI 已停止，你仍可继续完成共享树果任务。':'你可以直接行动，水跃鱼继续执行自己的任务。'):s==='WAITING_APPROVAL'?'等待你的购买决定，双方世界动作暂时冻结。':s==='PARSING'||s==='READY'?'请先确认或取消委托。':s==='COMPLETED'?'共享任务已完成，双方贡献已保存。':'请检查任务状态；暂停 AI 不会禁止独立玩家行动。'):'新一轮开始时开启协作。你将控制独立玩家角色，不必暂停水跃鱼。');
  for(const d of ['n','w','s','e'])$(`player-${d}`).disabled=coopLock||!coop.canMove;
  $('player-pickup').disabled=coopLock||!coop.canPickup;$('player-deliver').disabled=coopLock||!coop.canDeliver;
  const selectedResource=$('player-resource').value;$('player-resource').replaceChildren(...coop.resources.map(p=>{const el=document.createElement('option');el.value=`${p.x},${p.y}`;el.textContent=`（${p.x}, ${p.y}） · ${p.quantity} 个`;return el;}));if(coop.resources.some(p=>`${p.x},${p.y}`===selectedResource))$('player-resource').value=selectedResource;$('player-resource').disabled=coopLock||!coop.canPickup;
  $('contribution-ledger').replaceChildren(...coop.events.map(value=>{const row=document.createElement('li');row.textContent=value;return row;}));
  const currentField=fieldPresentation(v,$('wild-target').value),lock=!online||busy||Boolean(client.pendingRequest);
  $('wild-skills').replaceChildren(...currentField.skills.map(skill=>{const button=document.createElement('button');button.textContent=skill.label;button.disabled=lock||!skill.enabled;button.addEventListener('click',()=>act('WILD_ACTION',{action:'use_skill',arguments:{targetId:$('wild-target').value,moveId:skill.id}}));return button;}));
  text('partner-detail',field.detail);text('wild-detail',currentField.targetDetail);
  for(const [id,can] of [['explore',field.canExplore],['select-partner',field.canSelect],['wild-attack',currentField.canAttack],['wild-capture',currentField.canCapture],['wild-store',currentField.canStore],['rest-pokemon-button',field.canRest]])$(id).disabled=lock||!can;
  for(const button of document.querySelectorAll('[data-wild-direction]'))button.disabled=lock||!field.canMove;
  $('combat-partner').disabled=lock||!field.canSelect;$('wild-target').disabled=lock||!field.targets.length;$('rest-pokemon').disabled=lock||!field.canRest;
  text('collection',carryPresentation(v).label);
  const preview=$('collection-preview'),previewKey=JSON.stringify([v.collection??[],$('collection-search').value,$('collection-filter').value,$('collection-select').value,lock]);if(preview.dataset.key!==previewKey){preview.dataset.key=previewKey;const items=filterPokemon(v.collection??[],$('collection-search').value,$('collection-filter').value);preview.replaceChildren(...items.map(p=>{const card=pokemonCard(p,{compact:true,selected:p.captureId===$('collection-select').value});const choose=document.createElement('button');choose.textContent=p.captureId===$('collection-select').value?'当前选择':'选择伙伴';choose.disabled=lock||!v.collection?.length;choose.addEventListener('click',()=>{$('collection-select').value=p.captureId;preview.dataset.key='';render();});card.append(choose);return card;}));if(!items.length){const empty=document.createElement('p');empty.textContent='没有符合条件的收藏伙伴。';preview.append(empty);}}
  const summon=summonPresentation(v),collectionKey=JSON.stringify(summon.options);
  if(collectionKey!==renderedCollection){renderedCollection=collectionKey;const previous=$('collection-select').value;$('collection-select').replaceChildren(...summon.options.map(p=>{const option=document.createElement('option');option.value=p.id;option.textContent=p.label;return option;}));if(summon.options.some(p=>p.id===previous))$('collection-select').value=previous;}
  text('collection-detail',summon.detail);$('follow').disabled=!online||busy||Boolean(client.pendingRequest)||!summon.canFollow;$('stop-follow').disabled=!online||busy||Boolean(client.pendingRequest)||!summon.canStopFollow;$('summon').disabled=!online||busy||Boolean(client.pendingRequest)||!summon.canSummon;$('recall').disabled=!online||busy||Boolean(client.pendingRequest)||!summon.canRecall;
  $('collection-select').disabled=!online||busy||!summon.canSummon;$('summon-direction').disabled=!online||busy||!summon.canSummon;
  text('status',(states[s]||s)+(taskView.field?` · ${taskView.label}`:''));text('carried',w.carriedBerry);text('delivered',`${v.delivered}/${w.requiredBerry}`);text('coins',w.coins);text('turn',`${w.turn}/${w.deadlineTurn}`);const remaining=Math.max(0,Number(w.deadlineTurn)-Number(w.turn));text('quest-budget',remaining===0?'本轮截止回合已到，请开始新一轮（保留收藏）。':`本轮剩余 ${remaining} 个行动回合，手动探索和 AI 共用。`+(remaining<=20?'长距离委托建议先开始新一轮。':''));
  text('position',`伙伴位置（${w.x}, ${w.y}） · 脚边可见树果 ${w.visibleBerry} 个`);
  text('world-detail',`${v.map?.mode==='ORIGINAL_V1'?(v.cooperative?'原地图 · 玩家与 AI 协作':'原地图 · 单人委托与探索'):'紧凑测试地图'} · ${v.period==='NIGHT'?'夜晚':'白天'} · 第 ${v.nightStarts||60} 回合天黑。${v.storage==='POSTGRESQL'?'PostgreSQL 存档':v.storage==='FILE'?'本机文件存档':'内存场景'}${v.recovered?' · 已恢复存档，未完成 AI 任务需手动继续':''}。等待模型与审批不推进回合。`);
  for(const id of ['manual-n','manual-w','manual-s','manual-e','manual-pickup','manual-deliver','manual-wait'])$(id).disabled=!online||busy||Boolean(client.pendingRequest)||!v.manualAllowed;
  $('recover').hidden=s!=='STORAGE_ERROR';$('recover').disabled=!online||busy||s!=='STORAGE_ERROR';
  touch.update(direction=>Boolean(moveButton(direction)&&!moveButton(direction).disabled));
  recovery.update({...v,clientError:clientFailure},'quest',online,busy,Boolean(client.pendingRequest));
  text('manual-detail',controlHint(v,online,busy||Boolean(client.pendingRequest)));
  const metrics=v.metrics||{};text('metrics',`实际工具步骤 ${metrics.totalSteps||0} · 无效调用 ${metrics.invalidCalls||0} · 重新规划 ${metrics.replanCount||0} · 审批 ${metrics.approvalCount||0} · 平均模型响应 ${Math.round(metrics.averageLlmLatencyMs||0)} 毫秒`);
  text('audit',JSON.stringify(v.stepTrace||[],null,2));
  text('provider',v.aiAvailable?`伙伴的思考模型：${v.model}`:'请在启动服务的本机环境中配置模型密钥，再重启服务。');
  text('result',s==='COMPLETED'?taskView.result:v.errorCode?readable(v.errorCode):s==='PROVIDER_UNAVAILABLE'?'思考暂时中断，你可以继续或取消。':'');
  const aiChoice=aiPartnerSelection(v,$('ai-partner').value);$('ai-partner').replaceChildren(...aiChoice.options.map(p=>{const option=document.createElement('option');option.value=p.id;option.textContent=p.label;return option;}));$('ai-partner').value=aiChoice.selected;$('ai-partner').disabled=!online||busy||!aiChoice.editable;
  $('parse').disabled=!online||busy||!v.aiAvailable||!['IDLE','ERROR'].includes(s);
  $('goal').disabled=!online||busy||!['IDLE','ERROR'].includes(s);for(const button of document.querySelectorAll('[data-goal]'))button.disabled=$('goal').disabled;
  $('confirmation').hidden=s!=='READY';text('parsed-goal',taskView.confirmation);$('confirm').disabled=!online||busy||s!=='READY';
  $('constraints').replaceChildren(...(v.constraints||[]).map(c=>{const el=document.createElement('span');el.textContent=constraintNames[c]||c;return el;}));
  $('pause').disabled=!online||busy||!active;$('resume').disabled=!online||busy||!['PAUSED','PROVIDER_UNAVAILABLE'].includes(s);
  $('cancel').disabled=!online||busy||['COMPLETED','FAILED','CANCELLED','IDLE'].includes(s);
  $('reset').disabled=!online||busy||!['IDLE','ERROR','COMPLETED','FAILED','CANCELLED'].includes(s);
  $('approval').hidden=!v.approval;
  if(v.approval)text('approval-detail',`购买 ${v.approval.quantity} 个树果，共花费 ${v.approval.totalCost} 金币。当前金币：${w.coins}。`);
  for(const id of ['approve','deny'])$(id).disabled=!online||busy||s!=='WAITING_APPROVAL'||!v.approval;
  for(const button of document.querySelectorAll('button'))button.title=button.disabled?disabledHint(v,button.id,client.connected,busy||Boolean(client.pendingRequest)):'';
  const target=(v.wild??[]).find(t=>t.id===$('wild-target').value);
  if(!lock&&v.manualAllowed){
   if($('wild-capture').disabled)$('wild-capture').title=!target?'请先开启探索并选择目标。':target.state!=='WILD'?'目标已捕捉、收藏或击败。':target.species==='TORCHIC'?'火稚鸡暂不可捕捉。':'请开启探索，并移动到目标相邻格。';
   if($('wild-attack').disabled)$('wild-attack').title=v.combatPartnerState?.growth?'成长伙伴请使用技能按钮。':v.constraints?.includes('NO_ACTIVE_BATTLE')||v.battleForbidden?'当前委托禁止主动战斗。':'请选择野生目标，并移动到相邻格。';
   if($('wild-store').disabled)$('wild-store').title=(v.collection??[]).length>=20?'收藏已满（20个）。':'请先捕捉目标，再收入收藏。';
   if($('summon').disabled)$('summon').title=v.summoned?'请先收回当前伙伴。':'请先将捕捉个体收入收藏。';
  }
  const tiles=[];const landmarks={orchard:['❋','果园'],market:['▣','商店'],laboratory:['⌂','研究室'],alternative:['✧','林间'],treecko:['♧','交流处']};
  const width=v.map?.width||9,height=v.map?.height||3,large=width>11;
  const startX=large?Math.min(Math.max(Number(w.x)-5,0),width-11):0,startY=large?Math.min(Math.max(Number(w.y)-4,0),Math.max(0,height-9)):0;
  $('map').classList.toggle('original-map',large);
  for(let y=startY;y<Math.min(height,startY+(large?9:height));y++)for(let x=startX;x<Math.min(width,startX+(large?11:width));x++){
    const el=document.createElement('div');el.className='tile';el.setAttribute('aria-label',`坐标 ${x}, ${y}`);
    const ground=v.map?.terrain?.[y]?.[x];if(ground==='#'){el.classList.add('wall');el.textContent='■';}else if(ground==='~'){el.classList.add('water');el.textContent='≈';}else if(ground==='^'){el.classList.add('lava');el.textContent='♨';}
    const landmark=Object.entries(v.locations).find(([,p])=>p.x===x&&p.y===y);
    if(landmark){const [symbol,label]=landmarks[landmark[0]];el.textContent=symbol;const small=document.createElement('small');small.textContent=label;el.append(small);}
    const npc=Object.entries(v.npcs||{}).find(([,p])=>p.x===x&&p.y===y);
    if(npc){const names={treecko:'木守宫',merchant:'商人',professor:'博士'};el.textContent=npc[0]==='treecko'?'♧':'◈';const small=document.createElement('small');small.textContent=names[npc[0]];el.append(small);el.setAttribute('aria-label',`${names[npc[0]]}，坐标 ${x}, ${y}`);}
    const wild=(v.wild||[]).find(t=>t.state==='WILD'&&t.x===x&&t.y===y);if(wild){el.classList.add('wild-pokemon');el.textContent=wild.species==='TORCHIC'?'▲':'◇';const small=document.createElement('small');small.textContent=`野生${speciesName(wild.species)}`;el.append(small);el.setAttribute('aria-label',`野生${speciesName(wild.species)}，坐标 ${x}, ${y}`);}
    const owned=summonedMarker(v,x,y);if(owned){el.classList.add('owned-pokemon');el.textContent=owned.symbol;const small=document.createElement('small');small.textContent=owned.label;el.append(small);el.setAttribute('aria-label',`${owned.label}，坐标 ${x}, ${y}`);}
    if(v.cooperative&&v.player?.x===x&&v.player?.y===y){el.classList.add('owned-pokemon');el.textContent='@';const small=document.createElement('small');small.textContent='你 · 玩家';el.append(small);el.setAttribute('aria-label',`玩家，坐标 ${x}, ${y}`);}
    if(Number(w.x)===x&&Number(w.y)===y){el.classList.add('actor');el.textContent='●';const small=document.createElement('small');small.textContent='水跃鱼';el.append(small);el.setAttribute('aria-label',`水跃鱼，坐标 ${x}, ${y}`);}tiles.push(el);
  }$('map').replaceChildren(...tiles);rpg.update(v,{targetId:$('wild-target').value,online:client.connected,busy:busy||Boolean(client.pendingRequest)});
  const dialogueKey=`${v.taskId}:${JSON.stringify(v.dialogues||[])}`;
  if(dialogueKey!==renderedDialogues){
    renderedDialogues=dialogueKey;const cards=[];
    for(const data of v.dialogues||[]){const view=dialoguePresentation(data);if(!view)continue;const card=document.createElement('article');card.className='npc-dialogue';
      const name=document.createElement('strong');name.textContent=`${view.name} · ${view.historical?'历史记忆':'对话时规则信息'}${view.asOfTurn===null?'':` · 第${view.asOfTurn}回合`}`;const message=document.createElement('p');message.textContent=view.message;if(['professor','merchant'].includes(data.speakerId)){const img=document.createElement('img');img.src=worldArtUrl(data.speakerId);img.alt=view.name+'形象';img.className='npc-portrait';img.width=40;img.height=55;card.append(img);}card.append(name,message);
      for(const clue of view.clues){const row=document.createElement('p');row.className='footnote';row.textContent=clue;card.append(row);}cards.push(card);
    }
    if(!cards.length){const p=document.createElement('p');p.className='muted';p.textContent='伙伴还没有向 NPC 询问。';cards.push(p);}$('dialogues').replaceChildren(...cards);
  }
  const traceKey=`${v.taskId}:${JSON.stringify(v.trace)}`;
  if(traceKey!==renderedTrace){
    renderedTrace=traceKey;
    $('trace').replaceChildren(...(v.trace.length?v.trace.slice(-24):[{code:'尚未开始行动'}]).map((row,i)=>{const li=document.createElement('li');const n=document.createElement('time');n.textContent=String(Math.max(0,v.trace.length-24)+i+1).padStart(2,'0');const label=document.createElement('span');label.textContent=(codes[row.code]||row.code)+(row.data?.damage!==undefined?` · 伤害 ${row.data.damage} · 反击 ${row.data.retaliationDamage} · 伙伴生命 ${row.data.actorHp}${Number(row.data.experienceGained)>0?` · EXP +${row.data.experienceGained} · Lv.${row.data.levelAfter}`:''}`:'');li.append(n,label);return li;}));
    $('trace').scrollTop=$('trace').scrollHeight;
  }
}
async function act(command,params={}){
  if(busy)return;
  busy=true;notice='正在提交操作……';render();
  try{await client.command(command,params);notice='操作已接收，正在更新游戏状态。';await client.poll();notice='';}
  catch(error){if(error.message==='REQUEST_LIMIT')clientFailure=error.message;notice=readable(error.message);if(client.connected)try{await client.poll();}catch{}}
  finally{busy=false;render();}
}
$('mode-switch').addEventListener('click',async event=>{event.preventDefault();if(busy)return;busy=true;notice='正在安全切换场景……';render();try{await navigateMode(client,'/training/');}catch(error){if(error.message==='REQUEST_LIMIT')clientFailure=error.message;notice=readable(error.message);}finally{busy=false;render();}});
$('task-form').addEventListener('submit',e=>{e.preventDefault();act('PARSE',{text:$('goal').value,captureId:$('ai-partner').value});});
for(const [id,cmd]of Object.entries({confirm:'CONFIRM',pause:'PAUSE',resume:'RESUME',cancel:'CANCEL',reset:'RESET'}))$(id).addEventListener('click',()=>act(cmd));
for(const [id,cmd]of Object.entries({approve:'APPROVE',deny:'DENY'}))$(id).addEventListener('click',()=>{const proposalId=client.view?.approval?.proposalId;if(proposalId)act(cmd,{proposalId});});
for(const button of document.querySelectorAll('[data-goal]'))button.addEventListener('click',()=>{$('goal').value=button.dataset.goal;$('goal').focus();});
$('retry').addEventListener('click',async()=>{busy=true;render();try{await client.retry();await client.poll();notice='';}catch(error){if(error.message==='REQUEST_LIMIT')clientFailure=error.message;notice=readable(error.message);}finally{busy=false;render();}});
for(const [id,direction] of Object.entries({'manual-n':'N','manual-s':'S','manual-w':'W','manual-e':'E'}))$(id).addEventListener('click',()=>act('MANUAL',{action:'move',arguments:{direction}}));
$('manual-pickup').addEventListener('click',()=>act('MANUAL',{action:'pickup',arguments:{quantity:1}}));
for(const action of ['deliver','wait'])$(`manual-${action}`).addEventListener('click',()=>act('MANUAL',{action,arguments:{}}));
$('recover').addEventListener('click',()=>act('RECOVER'));
$('explore').addEventListener('click',()=>act('EXPLORE'));
$('select-partner').addEventListener('click',()=>act('PARTNER',{captureId:$('combat-partner').value}));
$('rest-pokemon-button').addEventListener('click',()=>act('REST',{captureId:$('rest-pokemon').value}));
$('wild-target').addEventListener('change',()=>render());
for(const button of document.querySelectorAll('[data-wild-direction]'))button.addEventListener('click',()=>act('WILD_ACTION',{action:'move',arguments:{direction:button.dataset.wildDirection}}));
for(const [id,action] of [['wild-attack','attack'],['wild-capture','capture']])$(id).addEventListener('click',()=>act('WILD_ACTION',{action,arguments:{targetId:$('wild-target').value}}));
$('wild-store').addEventListener('click',()=>act('STORE_WILD',{targetId:$('wild-target').value}));
$('summon').addEventListener('click',()=>{const captureId=$('collection-select').value;if(captureId)act('SUMMON',{captureId,direction:$('summon-direction').value});});
for(const [id,enabled] of [['follow',true],['stop-follow',false]])$(id).addEventListener('click',()=>{const captureId=client.view?.summoned?.captureId;if(captureId)act('FOLLOW',{captureId,enabled});});
$('recall').addEventListener('click',()=>{const captureId=client.view?.summoned?.captureId;if(captureId)act('RECALL',{captureId});});
async function reconnect(){if(busy)return;busy=true;render();try{await client.connect();clientFailure='';if(client.pendingRequest){await client.retry();await client.poll();}notice='';}catch(error){if(error.message==='REQUEST_LIMIT')clientFailure=error.message;notice=readable(error.message);}finally{busy=false;render();}}
async function poll(){if(polling)return;polling=true;const wasConnected=client.connected;try{if(client.roomId)await client.poll();else await client.connect();if(!wasConnected&&client.connected)notice='';}catch(error){if(error.message==='REQUEST_LIMIT')clientFailure=error.message;notice=readable(error.message);}finally{polling=false;render();setTimeout(poll,500);}}
render();poll();


$('coop-start').addEventListener('click',()=>act('COOP_START'));
for(const d of ['N','W','S','E'])$(`player-${d.toLowerCase()}`).addEventListener('click',()=>act('PLAYER_ACTION',{action:'move',arguments:{direction:d}}));
$('player-pickup').addEventListener('click',()=>{const [x,y]=$('player-resource').value.split(',').map(Number);if(Number.isInteger(x)&&Number.isInteger(y))act('PLAYER_ACTION',{action:'pickup',arguments:{x,y,quantity:1}});});
$('player-deliver').addEventListener('click',()=>act('PLAYER_ACTION',{action:'deliver',arguments:{}}));

for(const [id,command] of [['npc-start','NPC_START'],['npc-pause','NPC_PAUSE'],['npc-resume','NPC_RESUME'],['npc-cancel','NPC_CANCEL']])$(id).addEventListener('click',()=>act(command));

for(const id of ['collection-search','collection-filter','collection-select'])$(id).addEventListener(id==='collection-search'?'input':'change',()=>{$('collection-preview').dataset.key='';render();});
