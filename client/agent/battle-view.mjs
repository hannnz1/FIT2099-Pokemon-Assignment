import {growthLevelForExperience} from './pokemon-art.mjs';
import {pokemonNames} from './pokemon-art.mjs';
export const speciesName = species => pokemonNames[species] ?? '未知宝可梦';
const levelOf=p=>growthLevelForExperience(p.experience)??1;
export const names = { 'wild-treecko': '木守宫', 'wild-torchic': '火稚鸡' };
export function carryPresentation(view) {
  const collection = view.collection ?? [];
  return {
    canTransfer: Boolean(view.manualAllowed && view.collectionAvailable !== false && view.captured?.includes('wild-treecko') && collection.length < 20),
    label: collection.length ? collection.map(p => `${speciesName(p.species)}${p.growth ? ` Lv.${levelOf(p.growth)}` : ""} · ${p.hp} / ${p.maxHp} HP${p.state === 'DEPLOYED' ? (Number.isInteger(p.x)&&Number.isInteger(p.y)?` · 已放出（${p.x}, ${p.y}）`:' · 已放出（位置待恢复）') : ''}`).join('，') : '还没有带回的宝可梦'
  };
}
export function summonPresentation(view) {
  const unlocked = Boolean(view.collectionControlAllowed && view.collectionAvailable !== false);
  const options = (view.collection ?? []).map((p,i) => ({id:p.captureId,label:`${speciesName(p.species)} ${i+1}${p.growth ? ` Lv.${levelOf(p.growth)}` : ""} · ${p.hp} / ${p.maxHp} HP`,state:p.state})).filter(p => p.state !== 'DEPLOYED');
  const out = view.summoned;
  return {options,canSummon:unlocked && !out && options.length > 0,canRecall:unlocked && Boolean(out),recallId:out?.captureId,followId:out?.captureId,canFollow:unlocked && Boolean(out) && !out.following,canStopFollow:unlocked && Boolean(out?.following),
    detail:out ? `我的${speciesName(out.species ?? 'TREECKO')}在（${out.x}, ${out.y}） · ${out.hp} / ${out.maxHp} HP · ${out.following ? '跟随已开启' : '停留原地'}` : '选择精灵球和方向，将宝可梦放到水跃鱼旁边的空位。'};
}
export function summonedMarker(view,x,y) {
  return view.summoned?.x === x && view.summoned?.y === y ? {symbol:'◆',label:`我的${speciesName(view.summoned.species ?? 'TREECKO')}`} : null;
}
export const states = { IDLE: '等待任务', PARSING: '正在理解任务', READY: '请确认任务', RUNNING: '伙伴正在行动', REPLANNING: '伙伴正在调整行动', PAUSED: '已暂停，可以手动接手', COMPLETED: '任务完成', FAILED: '任务未完成', CANCELLED: '任务已取消', PROVIDER_UNAVAILABLE: 'AI 暂不可用，可继续重试或手动接手', ERROR: '任务解析失败', STORAGE_ERROR: '存档写入失败，行动已停止' };
export const codes = { SKILL_USED:'使用技能', PARTNER_FAINTED:'伙伴已倒下，请收回休整', USE_SKILL_REQUIRED:'成长伙伴请使用技能按钮', GROWTH_PARTNER_REQUIRED:'请切换为成长伙伴', MOVE_NOT_EQUIPPED:'该技能未装备', NO_PP:'技能PP已用完，请收回并休整', ARRIVED: '到达位置', MOVED: '移动一步', ATTACKED: '发动攻击', ATTACK_MISSED: '攻击未命中', TARGET_DEFEATED: '目标已击败', CAPTURED: '捕捉成功', GOAL_COMPLETED: '目标已完成', NOT_CAPTURABLE: '该目标目前不可捕捉', OUT_OF_REACH: '请先移动到目标相邻位置', TARGET_UNAVAILABLE: '目标已捕捉或已倒下', NO_ACTIVE_BATTLE: '任务约定禁止主动战斗', AI_CONTROLS_COMPANION: '请先暂停或取消 AI 任务', PROVIDER_INVALID_RESPONSE: '暂不支持这个任务或限制，请尝试单个捕捉/击败目标', CANCEL_BEFORE_RESET: '请先取消当前任务', STALE_REVISION: '状态已更新，请重试', ACTOR_UNAVAILABLE: '伙伴已倒下，请重新训练', STORAGE_UNAVAILABLE: '存档暂不可用，请恢复存档', LOOP_LIMIT: '达到本次行动上限', TASK_TIME_LIMIT: '达到本次时间上限' };
export function intentLabel(intent) { return `${intent.goal === 'CAPTURE' ? '捕捉' : '击败'}${names[intent.targetId] ?? intent.targetId}${intent.noBattle ? '；不主动战斗' : '；允许主动战斗'}`; }
export function arenaTiles(view) {
  const tiles = Array.from({ length: 27 }, () => ({ kind: '', label: '空地', symbol: '' }));
  for (const target of view.targets ?? []) if (target.state === 'WILD' && Number.isInteger(target.x) && Number.isInteger(target.y) && target.x >= 0 && target.x < 9 && target.y >= 0 && target.y < 3)
    tiles[target.y * 9 + target.x] = { kind: target.id === 'wild-treecko' ? 'treecko' : 'torchic', label: names[target.id], symbol: target.id === 'wild-treecko' ? '◆' : '▲' };
  const x = Number(view.world.x), y = Number(view.world.y);
  if (view.world.x !== undefined && view.world.y !== undefined && Number.isInteger(x) && Number.isInteger(y) && x >= 0 && x < 9 && y >= 0 && y < 3)
    tiles[y * 9 + x] = { kind: 'actor', label: '水跃鱼', symbol: '●' };
  return tiles;
}
export function resultLabel(event) {
  const label = event.code === 'COMBAT_OBSERVED' ? '观察训练场' : codes[event.code] ?? event.code;
  if (!event.data || event.data.damage === undefined) return label;
  return `${label} · 伤害 ${event.data.damage} · 反击 ${event.data.retaliationDamage ?? 0} · 伙伴生命 ${event.data.actorHp ?? '—'}${Number(event.data.experienceGained)>0?` · EXP +${event.data.experienceGained} · Lv.${event.data.levelAfter}`:''}`;
}

export function fieldPresentation(view,targetId) {
 const wild=view.wild??[],manage=Boolean(view.collectionControlAllowed&&view.collectionAvailable!==false),enabled=wild.length>0;
 const partner=view.combatPartnerState,selected=wild.find(t=>t.id===targetId),active=Boolean(manage&&view.wildActionAllowed),adjacent=Boolean(partner&&selected&&Math.max(Math.abs(partner.x-selected.x),Math.abs(partner.y-selected.y))===1);
 const partners=[{id:'mudkip',label:'委托伙伴水跃鱼'}];if(view.summoned)partners.push({id:view.summoned.captureId,label:`我的${speciesName(view.summoned.species)} · ${view.summoned.hp}/${view.summoned.maxHp} HP`});
 const stateName={WILD:'野生',CAPTURED:'精灵球中',TRANSFERRED:'已收藏',DEFEATED:'已击败'};
 return {partners,targets:wild.map(t=>({id:t.id,label:`${speciesName(t.species)} · ${t.hp}/${t.maxHp} HP · ${stateName[t.state]??t.state}`})),
  restOptions:[{id:'mudkip',label:'委托伙伴水跃鱼'},...(view.emergencyRest?[]:(view.collection??[])).filter(p=>p.state!=='DEPLOYED').map((p,i)=>({id:p.captureId,label:`${speciesName(p.species)} ${i+1}${p.growth ? ` Lv.${levelOf(p.growth)}` : ""} · ${p.hp}/${p.maxHp} HP`}))],
  canExplore:manage&&!enabled&&view.map?.mode==='ORIGINAL_V1',canSelect:manage&&enabled,canMove:active,canAttack:!partner?.growth&&partner?.hp>0&&active&&adjacent&&selected.state==='WILD'&&!view.battleForbidden&&!view.constraints?.includes('NO_ACTIVE_BATTLE'),
  skills:(partner?.moves??[]).map(m=>({id:m.id,label:`${m.name} · ${m.pp}/${m.maxPp} PP`,enabled:Boolean(active&&adjacent&&partner.hp>0&&selected?.state==='WILD'&&m.pp>0&&!view.battleForbidden&&!view.constraints?.includes('NO_ACTIVE_BATTLE'))})),
  canCapture:partner?.hp>0&&active&&adjacent&&selected.state==='WILD'&&['TREECKO','MUDKIP'].includes(selected.species),canStore:manage&&selected?.state==='CAPTURED'&&(view.collection??[]).length<20,canRest:manage&&Boolean(view.restAllowed),
  detail:enabled&&partner?`当前出战：${view.combatPartner==='mudkip'?'委托伙伴水跃鱼':`我的${speciesName(partner.species)}`} ${partner.level?`Lv.${partner.level} · EXP ${partner.experience}${partner.burned?" · 灼伤":""}`:""} · ${partner.hp}/${partner.maxHp} HP · （${partner.x}, ${partner.y}）`:'开启野外探索后，可以指挥出战伙伴挑战或捕捉野生宝可梦。',
  targetDetail:selected?`${speciesName(selected.species)} · ${selected.hp}/${selected.maxHp} HP · ${stateName[selected.state]}${selected.state==='WILD'?` · （${selected.x}, ${selected.y}）`:''}`:'尚未开启野外探索'};
}

export function questTaskPresentation(view){
 const executor=view.aiPartner&&view.aiPartner!=='mudkip'?`你的${speciesName(view.aiPartnerState?.species)}（收藏 ${view.aiPartner.slice(0,8)}，${view.aiPartnerState?.hp??'—'}/${view.aiPartnerState?.maxHp??100} HP），确认后自动召唤并停止跟随`:'水跃鱼';
 if(['SEQUENCE','MIXED'].includes(view.taskKind)){
  const steps=view.goalSteps??[],parts=steps.map(step=>questTaskPresentation({...view,taskKind:step.kind,targetId:step.targetId,goalSteps:undefined,status:'RUNNING'}));
  const completed=steps.map(step=>{if(step.kind==='COMPLETE_QUEST')return Number(view.delivered)===3;const target=(view.wild??[]).find(t=>t.id===step.targetId);return step.kind==='CAPTURE'?['CAPTURED','TRANSFERRED'].includes(target?.state):target?.state==='DEFEATED';});
  const count=completed.filter(Boolean).length,index=completed.findIndex(done=>!done),done=steps.length>=2&&index===-1;
  const mixed=view.taskKind==='MIXED',title=mixed?'混合委托':'顺序委托';
  const labels=parts.map(p=>p.label).join(' → '),deadline=view.customDeadline??view.world?.deadlineTurn??60,area=view.areaId==='ORCHARD_AREA'?'允许区域：仅果园。':view.areaId==='QUEST_AREA'?'允许区域：任务区域。':'';
  return {field:true,label:`${title} ${count}/${steps.length}`,confirmation:`${mixed?`树果阶段由水跃鱼执行，野外阶段由${executor}执行。`:`由${executor}执行。`}按顺序执行：${labels}。全程遵守所选限制，在第 ${deadline} 回合前完成。${area}确认后开启野外探索。`,progress:`阶段进度 ${count}/${steps.length}：${parts.map((p,i)=>`${completed[i]?'✓':i===index?'当前':'待执行'} ${i+1}. ${p.label}`).join('；')}。${done?'全部目标已达成':`第 ${index+1} 步 · ${parts[index]?.progress??'等待确认'}`}`,result:view.status==='COMPLETED'?(done?`${title}全部完成！捕获个体可选择目标后点击“收入收藏”。`:'正在核对各阶段实际状态。'):''};
 }

 const kind=view.taskKind??'COMPLETE_QUEST',field=['CAPTURE','DEFEAT'].includes(kind),deadline=view.customDeadline??view.world?.deadlineTurn??60;
 const area=view.areaId?`允许区域：${view.areaId==='QUEST_AREA'?'任务区域':'果园区域'}。`:'';
 if(!field&&view.cooperative){const q=view.sharedQuest??{},counts=q.contributions??{},actual=q.delivered??view.delivered??0;return {field:false,label:'协作树果委托',confirmation:`你和水跃鱼共同交付 ${q.required??3} 个树果，可分别采集并分批交给博士。请在第 ${deadline} 回合前完成。${area}双方共用限制和世界回合。`,progress:`共享交付：${actual}/${q.required??3} · 你 ${counts.PLAYER?.delivered??0} · 水跃鱼 ${counts.AGENT?.delivered??0}`,result:view.status==='COMPLETED'?(actual===(q.required??3)?`协作完成！博士实际收到 ${actual} 个树果。你交付 ${counts.PLAYER?.delivered??0}，水跃鱼交付 ${counts.AGENT?.delivered??0}。`:'正在核对实际交付。'):''};}
 if(!field)return {field:false,label:'树果委托',confirmation:`收集 ${view.world?.requiredBerry??3} 个树果，交给博士。请在第 ${deadline} 回合前完成。${area}`,progress:`树果交付：${view.delivered??0}/${view.world?.requiredBerry??3}`,result:view.status==='COMPLETED'?`委托完成！博士实际收到 ${view.delivered??0} 个树果。`:''};
 const target=(view.wild??[]).find(t=>t.id===view.targetId),species=({'field-treecko':'TREECKO','field-mudkip':'MUDKIP','field-torchic':'TORCHIC'})[view.targetId],name=speciesName(species),verb=kind==='CAPTURE'?'捕捉':'击败',label=`${verb}野生${name}`;
 const done=kind==='CAPTURE'?['CAPTURED','TRANSFERRED'].includes(target?.state):target?.state==='DEFEATED';
 const state=({WILD:'野生',CAPTURED:'已捕捉',TRANSFERRED:'已收藏',DEFEATED:'已击败'})[target?.state]??'确认后开启探索';
 return {field:true,label,confirmation:`由${executor}${verb}一只野生${name}。请在第 ${deadline} 回合前完成。${area}${(view.wild??[]).length?'':'确认后开启野外探索，不推进回合。'}`,progress:`当前目标：${name} · ${state}${Number.isFinite(target?.hp)?` · ${target.hp}/${target.maxHp??100} HP`:''} · ${done?'目标已达成':'等待实际动作达成'}`,result:view.status==='COMPLETED'?(done?`${verb}完成！野生${name}${kind==='CAPTURE'?'已进入精灵球，可点击“收入收藏”。':'已被击败。'}`:'正在核对实际目标状态。'):''};
}

export const fieldCodes={SKILL_USED:"使用技能",PARTNER_FAINTED:"伙伴已倒下，请收回休整",USE_SKILL_REQUIRED:"成长伙伴请使用技能按钮",GROWTH_PARTNER_REQUIRED:"请切换为成长伙伴",MOVE_NOT_EQUIPPED:"该技能未装备",NO_PP:"技能PP已用完，请收回并休整",BERRY_STAGE_COMPLETED:"树果交付阶段完成，继续下一阶段",RESOURCE_WAITED:"树果格被占用，等待一回合",RESOURCE_APPROACHED:"已靠近被占用的树果格",FIELD_OBSERVED:'查看野外实际状态',TARGET_ADJACENT:'已到达目标相邻格',TARGET_NOT_IN_TASK:'目标不属于当前确认的委托',NO_PATH:'当前没有可通行路线，请调整位置后继续',STAGE_OPERATION_NOT_ALLOWED:'当前阶段不允许此操作',INVALID_DEADLINE:'截止回合已到达，请取消当前确认并重新设置目标'};
