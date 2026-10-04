export const regionNames={lab:'研究室',forest:'苔叶森林',river:'浅溪河岸',mountain:'赤岩山道'};
const wildRegion=id=>['forest','river','mountain'].includes(id);
export const moveNames={BITE:'咬住',WING_ATTACK:'翅膀攻击',POISON_FANG:'毒牙',SUPERSONIC:'超音波',STUN_SPORE:'麻痹粉',SLEEP_POWDER:'睡眠粉',THUNDER_WAVE:'电磁波',SPARK:'电光',THUNDERBOLT:'十万伏特',SCREECH:'刺耳声',MEGA_DRAIN:'超级吸取',AGILITY:'高速移动',SLAM:'摔打',LEAF_BLADE:'叶刃',SAND_ATTACK:'泼沙',BULK_UP:'健美',SLASH:'劈开',FIRE_PUNCH:'火焰拳',BLAZE_KICK:'火焰踢',TAKE_DOWN:'猛撞',MUDDY_WATER:'浊流',POUND:'拍击',TACKLE:'撞击',SCRATCH:'抓',LEER:'瞪眼',GROWL:'叫声',ABSORB:'吸取',QUICK_ATTACK:'电光一闪',PURSUIT:'追打',FURY_CUTTER:'连斩',MUD_SLAP:'掷泥',WATER_GUN:'水枪',MUD_SHOT:'泥巴射击',EMBER:'火花',PECK:'啄',DOUBLE_KICK:'二连踢'};
export const reasonNames={PROVIDER_BUDGET_EXHAUSTED:'网站 AI 今日或本月体验额度已用完；可继续手动冒险，额度到期自动恢复',PROVIDER_REQUEST_REJECTED:'AI 请求超出网站允许范围',REGION_LOCKED:'请先完成前一区域的冒险调查',ADVENTURE_STARTED:'连续冒险已开始，森林调查完成后解锁河流',ADVENTURE_REWARDED:'调查奖励已领取',TRAINING_TASK_PENDING:'请先取消未完成的培养委托，再开启连续冒险',ADVENTURE_NEW_JOURNEY_REQUIRED:'连续冒险需在初次出发前启用；当前可继续自由探索',ADVENTURE_ALREADY_STARTED:'冒险已开始',TRAINING_REGION_RESTRICTED:"AI 仅能前往选定培养区域或研究室",UNKNOWN_REGION:"请选择有效的探索区域",TARGET_REGION_REQUIRED:"目标在其他区域，请先前往对应地图",NO_REGION_ROUTE:"区域之间没有通路",EXPEDITION_SELECTED:"探索区域已选定",RECOVERY_POINT_REQUIRED:"请先走到营地恢复点",GROWTH_RETURNED:"伙伴已返回成长研究室，可以继续培养",RECALL_BEFORE_TRAINING:"请先在原地图收回伙伴",CANCEL_QUEST_BEFORE_TRAINING:"请先取消原地图未结束的委托",GROWTH_CARRIED:"伙伴已带回原地图收藏；等级与技能已保留",COLLECTION_FULL:"收藏已满，请先整理收藏",STARTER_CHOSEN:'已选择初始伙伴',GROWTH_RESTED:'全队 HP 和 PP 已恢复',GROWTH_CAPTURED:'捕捉成功，已加入成长队伍',GROWTH_DEFEATED:'目标已击败，经验已结算',CAPTURE_FAILED:'捕捉未成功，目标进行了反击',PARTNER_FAINTED:'伙伴已倒下，请返回研究室恢复',EVOLVED:'进化完成，个体与经验保留',EVOLUTION_LEVEL_REQUIRED:'尚未达到当前形态的进化等级',MOVE_LEARNED:'已学习技能',MILESTONE_REWARDED:'里程碑奖励已领取',EXPEDITION_READY:'选定区域的新遭遇已准备',REGION_ENTERED:'已进入区域',ARRIVED:'已抵达',MOVED:'已移动一步',TARGET_ADJACENT:'已接近目标',OUT_OF_REACH:'请先接近目标',SKILL_USED:'技能回合已结算',LAB_REQUIRED:'请先返回研究室',NO_PP:'技能 PP 已用完，请恢复',PARTNER_EXHAUSTED:'伙伴已倒下，请返回研究室',PATH_BLOCKED:'该方向无法通行',ACCEPTED:'操作已接受',GOAL_COMPLETED:'AI 培养目标已达成',MODE_NOT_SELECTED:'请重新连接切回成长模式',STALE_REVISION:'状态已更新，请重试',PROVIDER_CONFIGURATION:'本机 AI 配置不可用',STORAGE_UNAVAILABLE:'存档失败，暂停操作；请恢复存档',CHOOSE_STARTER:'请先选择初始伙伴',REPLACE_MOVE_REQUIRED:'需要选择一个替换技能',REQUEST_PENDING:'正在处理上一条操作',DISCONNECTED:'连接中断，请重新连接',TARGET_UNAVAILABLE:'目标已结束，请选择其他目标',EXPEDITION_UNFINISHED:'本轮还有野生目标，请先完成探索',REWARD_ALREADY_CLAIMED:'奖励已领取',EVOLVED_LEVEL_REQUIRED:'进化等级不足'};
export function manualAllowed(view,connected,busy,pending=null){return Boolean(view&&view.manualAllowed&&connected&&!busy&&!pending);}
export function growthDirection(key,tag,allowed,editable=false){if(!allowed||editable||['INPUT','SELECT','TEXTAREA'].includes(tag?.toUpperCase()))return null;return ({ArrowUp:'N',ArrowDown:'S',ArrowLeft:'W',ArrowRight:'E',w:'N',s:'S',a:'W',d:'E'})[key]??({w:'N',a:'W',s:'S',d:'E'})[key?.toLowerCase()]??null;}
export function selectedTarget(world,id){return world.encounters?.find(e=>e.id===id&&e.state==='WILD'&&(!e.region||e.region===world.region))??null;}
export function skillEnabled(world,target,move){return Boolean(wildRegion(world.region)&&(!target?.region||target.region===world.region)&&world.partner?.hp>0&&target?.targetAdjacent&&move.pp>0);}
export function mapEntities(world){return [...(world.partner?[{...world.partner,id:world.partner.captureId,x:world.x,y:world.y,role:'partner'}]:[]),...(wildRegion(world.region)?world.encounters.filter(e=>e.state==='WILD'&&(!e.region||e.region===world.region)).map(e=>({...e,role:'wild'})):[])];}

export function hpFeedbackDelta(before,after){return before&&after&&before.captureId===after.captureId?after.hp-before.hp:0;}

const retainedTrainingStates=['RUNNING','REPLANNING','PAUSED','PROVIDER_UNAVAILABLE','STORAGE_ERROR'];
export function trainingConfig(view,draft){
 const retained=retainedTrainingStates.includes(view.status)&&view.targetIndividual===view.world?.partner?.captureId&&view.targetLevel>0&&wildRegion(view.targetRegion);
 return retained?{region:view.targetRegion,level:view.targetLevel,retained:true}:{...draft,retained:false};
}
export function growthTargetId(view,manualId){
 if(!['RUNNING','REPLANNING'].includes(view.status))return selectedTarget(view.world,manualId)?.id??null;
 // Only confirmed actions of this task may supply the AI's battle target.
 for(const row of [...(view.stepTrace??[])].reverse()){
  if(row.taskId!==view.taskId||!['move_to','use_skill'].includes(row.toolName)||!['SUCCESS','IN_PROGRESS'].includes(row.actionStatus))continue;
  return selectedTarget(view.world,row.arguments?.targetId)?.id??null;
 }
 return null;
}
export function growthTargetHint(view){
 if(['RUNNING','REPLANNING'].includes(view.status))return 'AI 正在导航或选择当前地图目标，无需再次点击；培养委托继续执行。';
 if(view.status==='PAUSED')return '委托已暂停，培养目标已保留；点击继续委托恢复。';
 if(view.status==='PROVIDER_UNAVAILABLE')return 'AI 连接暂不可用，培养目标已保留；恢复连接后继续委托。';
 return '请选择当前区域中的目标';
}

export function adventurePresentation(view,{online=true,busy=false,pending=false}={}){
 const w=view.world??{},a=w.adventure??{},lab=w.region==='lab';
 const blocked=!online?'请先重新连接':view.status==='STORAGE_ERROR'?'请先恢复存档':pending?'正在核对上一条操作，请稍候':busy?'正在处理操作，请稍候':['RUNNING','REPLANNING'].includes(view.status)?'AI 正在培养，请先暂停或取消委托':view.manualAllowed===false?'当前暂不可操作':null;
 let startReason=a.enabled?'连续冒险已开启，无需重复开启':blocked??(['PAUSED','PROVIDER_UNAVAILABLE'].includes(view.status)&&view.targetLevel>0?'请先取消未完成的培养委托':!lab?'回到研究室后开启':w.adventureAvailable===false?'当前暂不能开启，请更新游戏程序':'可开启，保留现有伙伴与成长');
 const start={label:a.enabled?'连续冒险已开启':'开始连续冒险（保留伙伴）',enabled:!a.enabled&&!blocked&&lab&&w.adventureAvailable!==false&&!(['PAUSED','PROVIDER_UNAVAILABLE'].includes(view.status)&&view.targetLevel>0),reason:startReason};
 const names={ADVENTURE_FOREST:'森林调查',ADVENTURE_RIVER:'河岸调查',ADVENTURE_MOUNTAIN:'山道调查',DEX_THREE:'收集3种伙伴'};
 const rewards=(a.quests??[]).map(q=>{
  let reason=q.claimed?'已领取':blocked;
  if(!reason&&!q.ready){if(q.id==='DEX_THREE')reason=`图鉴已收集 ${(a.caught??[]).length} / 3 种形态（包含进化）`;else if(!a.enabled)reason='请先开启连续冒险';else {const id=q.id.substring(10).toLowerCase(),region=w.regions?.find(r=>r.id===id);reason=region?.unlocked===false?'请先完成前一区域调查':region?.prepared?`本区还有 ${region.remaining} 个野生目标，全部捕捉或击败后完成`:'前往该区域，完成全部遭遇';}}
  if(!reason)reason=!lab?'条件已达成，回研究室领取':!w.partner?'请先选择出战伙伴':'条件已达成，可以领取';
  return {...q,label:names[q.id]+(q.claimed?' · 已领取':` +${q.reward} EXP`),enabled:!q.claimed&&!blocked&&lab&&!!w.partner&&q.ready,reason};
 });
 return {start,rewards,detail:a.enabled?(a.completed?'三地区调查已完成，未领的奖励可回研究室领取':'捕捉或击败本区全部野生目标，依次解锁河岸和山道；奖励回研究室领取'):'可用现有存档开启：保留伙伴、等级、技能和收藏；重新生成调查遭遇，从森林开始。请先回研究室，取消未完成的培养委托。'};
}

export function adventureNextStep(view,options={}){
 const w=view.world??{},a=w.adventure??{};
 if(!a.enabled)return null;
 const allowed=manualAllowed(view,options.online??true,options.busy??false,options.pending??false)&&!['RUNNING','REPLANNING'].includes(view.status);
 const reason=options.online===false?'请先重新连接':view.status==='STORAGE_ERROR'?'请先恢复存档':options.pending?'正在核对上一条操作，请稍候':options.busy?'正在处理操作，请稍候':['RUNNING','REPLANNING'].includes(view.status)?'AI 正在培养，请先暂停委托后手动移动':!w.partner?'请先选择出战伙伴':'当前暂不可操作';
 const id=['forest','river','mountain'].find(id=>!(a.cleared??[]).includes(id));
 if(a.completed||!id)return {reason,title:'三地区调查已完成',detail:'回研究室领取未领的调查奖励；你可以继续探索和培养伙伴。',action:w.region==='lab'?null:{kind:'TRAVEL',regionId:'lab',label:'返回研究室 · 走一步',enabled:allowed&&!!w.partner}};
 const region=w.regions?.find(r=>r.id===id),name=regionNames[id],detail=`下一步：${name}调查。`+(region?.prepared?`还剩 ${region.remaining} 个野生目标，捕捉或击败全部目标后解锁下一区域。`:'前往该区域，捕捉或击败全部野生目标。');
 const action=!w.partner?{kind:'PARTNER',label:'选择出战伙伴',enabled:allowed}:w.region===id?{kind:'TARGETS',label:'查看当前野生目标',enabled:allowed}:{kind:'TRAVEL',regionId:id,label:`前往${name} · 走一步`,enabled:allowed&&region?.unlocked!==false};
 return {reason,title:'连续冒险进行中',detail,action};
}

export function recoveryNeed(view){const p=view?.world?.partner;if(!p)return null;return p.hp<=0?'FAINTED':p.hp/p.maxHp<=.3?'LOW':null;}
export function createRecoveryRunner({step,onChange=()=>{}}){
 let running=false,inFlight=false,individual=null,suppressed=null,reason='',steps=0;
 const eligible=(v,o={})=>Boolean(v?.world?.partner&&v.manualAllowed&&o.online!==false&&!o.pending&&!o.busy&&!['RUNNING','REPLANNING','STORAGE_ERROR'].includes(v.status));
 const stop=why=>{running=false;suppressed=individual;reason=why;onChange();};
 const api={get running(){return running},get reason(){return reason},get steps(){return steps},
 update(v,o={}){const p=v?.world?.partner;if(p?.hp>0&&p.captureId===suppressed)suppressed=null;if(running&&(p?.captureId!==individual||o.online===false||o.pending||['RUNNING','REPLANNING','STORAGE_ERROR'].includes(v?.status)||v?.manualAllowed===false))stop('自动返程已停止，请确认连接、存档与委托状态');if(!running&&recoveryNeed(v)==='FAINTED'&&p.captureId!==suppressed&&eligible(v,o))api.start(v,o);},
 start(v,o={}){if(running||inFlight||!eligible(v,o))return false;individual=v.world.partner.captureId;running=true;reason='';steps=0;onChange();return true;},
 cancel(){if(running)stop('已取消自动返程，可手动移动或再次点击返回');},
 async tick(v,o={}){if(!running||inFlight)return;if(!eligible(v,o)||v.world.partner.captureId!==individual){stop('自动返程已停止，请确认连接、存档与委托状态');return;}if(steps>=256){stop('返程步数超出限制，请检查地图路线');return;}const action=v.world.canRest?'rest':'go_to_recovery';inFlight=true;try{await step(action);steps++;if(running&&action==='rest'){running=false;reason='全队已恢复，可以继续冒险';suppressed=null;}}catch(e){stop(e.message??'自动返程失败');}finally{inFlight=false;onChange();}}
 };return api;
}

export function respawnHint(world){const r=world?.regions?.find(r=>r.id===world.region);if(!r||world.region==='lab')return '';if(r.remaining>0)return `本区野生伙伴 ${r.remaining} / ${r.wildLimit??3} 只`;if(r.respawnMoves==null)return '进入区域后生成野生伙伴';if(r.respawnLimitReached)return '本区探索轮次已达上限';if(r.respawnBlocked)return '下一批已准备：请离开出生点，继续移动后生成';return `本区已清空 · 在本区再移动 ${r.respawnMoves} 回合刷新下一批 · 上限 ${r.wildLimit??3} 只`;}
