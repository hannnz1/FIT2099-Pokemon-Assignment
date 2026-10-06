const reasons={PROVIDER_USAGE_UNKNOWN:"上次调用的费用尚未确认，已暂停新的付费调用",MODEL_QUEUE_FULL:"模型请求队列已满，请稍后重试",BATTLE_BUDGET_EXHAUSTED:"本局对战的调用或 Token 上限已达到",NAVIGATION_REQUIRES_HELP:"通路持续被占用或无法到达，委托已暂停；请检查通路和角色位置后继续。",NO_PROGRESS_REQUIRES_HELP:"伙伴反复观察或往返，但采集与交付没有进展；请检查资源、背包和交付目标后继续。",PROVIDER_BUDGET_EXHAUSTED:'网站 AI 今日或本月体验额度已用完；可继续手动冒险，额度到期自动恢复',PROVIDER_REQUEST_REJECTED:'AI 请求超出网站允许范围',LOOP_LIMIT:'AI 达到决策或连续失败上限',LOOP_ERROR:'AI 执行遇到内部错误，请查看记录',ACTION_STEP_LIMIT:'导航达到本次行动步数上限',OPERATION_TIMEOUT:'本次操作响应超时',QUEST_EXPIRED:'任务已超过截止回合',TASK_TIME_LIMIT:'委托达到本轮时间上限',STEP_LIMIT:'委托达到行动上限',DECISION_LIMIT:'委托达到决策上限',PARTNER_FAINTED:'伙伴已经倒下',PARTNER_EXHAUSTED:'伙伴 HP 不足',PROVIDER_TIMEOUT:'AI 响应超时',PROVIDER_RATE_LIMIT:'AI 请求频率受限',PROVIDER_QUOTA_EXHAUSTED:'AI 可用额度不足',PROVIDER_AUTHENTICATION:'本机 AI 配置需要检查',STORAGE_UNAVAILABLE:'存档尚未确认',AREA_RESTRICTED:'目标超出本轮允许范围'};
export function failureReason(view={}){const world=view.world??{};if(view.errorCode==='QUEST_EXPIRED'||world.questStatus==='EXPIRED')return `任务已到截止回合 ${world.deadlineTurn??world.turn??'—'}（当前 ${world.turn??'—'} 回合）。手动探索和 AI 行动共用本轮回合，请开始新一轮。`;const last=[...(view.trace??[])].reverse().find(r=>['FAILED','TIMED_OUT'].includes(r.status));return reasons[view.errorCode]??reasons[last?.code]??'本次委托未完成，请查看行动记录。';}
export function failureRecovery(view={},mode='quest',online=true,busy=false,pending=false){
 const status=view.status,world=view.world??{},reason=failureReason(view);
 let title='',detail='',actions=[];
 const action=(label,command,disabled=false)=>({label,command,disabled:busy||disabled});
 if(!online){title='连接中断';detail='重新连接后会核对最新状态；不会自动重置地图。';actions=[action('重新连接','RECONNECT')];}
 else if(pending&&busy){/* An active request has not failed; keep the recovery panel closed. */}
 else if(pending){title='上一条操作尚未确认';detail='先重试原请求并核对结果，避免重复提交或误重置。';actions=[action('重试未确认操作','RETRY_PENDING')];}
 else if(view.clientError==='REQUEST_LIMIT'){title='本地房间操作次数已达上限';detail=['PERSISTENT','FILE','POSTGRESQL'].includes(view.storage)?'先重启本地游戏服务，再重新连接；服务器会读取原有存档。当前房间不能继续提交游戏操作。':'当前为内存模式，重启服务会丢失未保存进度。请先查看记录；重启后才能重新体验。';actions=[action('重启服务后重新连接','RECONNECT'),action('查看行动记录','SHOW_TRACE')];}
 else if(status==='STORAGE_ERROR'){title='请先恢复存档';const storageReasons={IO_ERROR:'文件读写失败，请检查存档目录和磁盘空间。',DATABASE_ERROR:'数据库存储失败，请检查数据库连接。',TOO_LARGE:'存档体积超过限制。',CORRUPT:'存档校验失败。',INVALID_CHECKPOINT:'存档数据未通过校验。',CLOSED:'存档服务已关闭。'};detail='存档未确认，游戏操作暂时冻结；恢复后继续。'+(storageReasons[view.storageFailureCode]??'');actions=[action('恢复存档','RECOVER')];}
 else if(status==='PAUSED'&&['NAVIGATION_REQUIRES_HELP','NO_PROGRESS_REQUIRES_HELP'].includes(view.errorCode)){title='委托已自动暂停';detail='已保留背包与交付进度。可先手动移动或检查现场，处理后点击继续；也可取消委托改为手动操作。';actions=[action('继续委托','RESUME'),action('查看行动记录','SHOW_TRACE'),action('取消委托','CANCEL')];}
 else if(status==='PROVIDER_UNAVAILABLE'){title='AI 暂时无法继续';detail=reason+' 可取消后手动操作；已完成的动作会保留。';actions=[action('继续委托','RESUME'),action('取消委托','CANCEL')];}
 else if(status==='FAILED'){
  title='委托未完成 · 可以继续游戏';
  if(mode==='growth'){detail='成长队伍、经验和已完成进度保留。先恢复伙伴，再调整等级目标重新委托。';if(world.partner&&!world.canRest&&world.region!=='lab')actions.push(action('返回研究室 · 走一步','RETURN_LAB'));if(world.canRest&&world.partner)actions.push(action('恢复全队 HP / PP','REST'));if(world.partner?.hp>0&&view.aiAvailable&&world.partner.level<world.partner.levelCap)actions.push(action('调整目标并重新培养','GROWTH_CONFIG'));actions.push(action('查看培养记录','SHOW_TRACE'));}
  else{detail=mode==='training'?'重新训练会重置训练场，并保留已经收入收藏的伙伴。':'开始新一轮会重置当前委托地图和任务，保留已经收入收藏的伙伴。到期任务不能直接继续原回合。';actions=[action(mode==='training'?'重新训练（保留收藏）':'开始新一轮（保留收藏）','RESET'),action('查看失败记录','SHOW_TRACE')];}
 }
 if(["PAUSED","PROVIDER_UNAVAILABLE"].includes(status)&&view.resumeRemaining===0){actions=actions.filter(a=>a.command!=="RESUME");detail=`本房间已用完 ${view.resumeLimit??3} 次恢复机会，再点继续不会恢复。已完成的进度仍保留；可手动接管采集与交付，或取消委托后开始新一轮。`;}
 const category=failureCategory(view,online,pending);if(['website-budget','provider-quota'].includes(category))actions=actions.filter(a=>a.command!=='RESUME');return {visible:Boolean(title),title,reason,detail,actions,category,progress:failureProgress(view)};
}
export function mountFailureRecovery(host,onAction){host.className='failure-recovery';host.setAttribute('role','status');host.setAttribute('aria-live','polite');let key='';return {update(view,mode,online,busy,pending){const state=failureRecovery(view,mode,online,busy,pending),next=JSON.stringify(state);host.hidden=!state.visible;if(next===key){host.querySelectorAll('button').forEach((button,i)=>{button.disabled=state.actions[i].disabled;});return;}key=next;host.replaceChildren();if(!state.visible)return;const title=document.createElement('h2'),reason=document.createElement('p'),detail=document.createElement('p'),controls=document.createElement('div');title.textContent=state.title;reason.textContent=state.reason;detail.textContent=state.detail+(state.progress?' 已保留进度：'+state.progress:'');controls.className='controls';for(const item of state.actions){const b=document.createElement('button');b.type='button';b.textContent=item.label;b.disabled=item.disabled;b.addEventListener('click',()=>{if(!b.disabled)onAction(item.command);});controls.append(b);}host.append(title,reason,detail,controls);}};}

export function failureCategory(view={},online=true,pending=false){
 if(!online)return 'network';if(pending)return 'receipt-unknown';if(view.status==='STORAGE_ERROR')return 'storage';
 const code=view.errorCode??view.trace?.slice().reverse().find(r=>['FAILED','TIMED_OUT'].includes(r.status))?.code;
 if(['PROVIDER_BUDGET_EXHAUSTED','BATTLE_BUDGET_EXHAUSTED','PUBLIC_EVAL_DAILY_LIMIT'].includes(code))return 'website-budget';
 if(code==='PROVIDER_QUOTA_EXHAUSTED')return 'provider-quota';
 if(['PROVIDER_RATE_LIMIT','RATE_LIMIT','MODEL_QUEUE_FULL'].includes(code))return 'rate-limit';
 if(['PROVIDER_TIMEOUT','OPERATION_TIMEOUT'].includes(code))return 'timeout';
 if(['NO_PATH','NAVIGATION_REQUIRES_HELP'].includes(code))return 'navigation';
 if(['PARTNER_FAINTED','PARTNER_EXHAUSTED'].includes(code))return 'fainted';
 if(['TASK_TIME_LIMIT','STEP_LIMIT','DECISION_LIMIT','LOOP_LIMIT','ACTION_STEP_LIMIT','NO_PROGRESS_REQUIRES_HELP','WALL_TIME_LIMIT'].includes(code))return 'goal-limit';
 if(['PROVIDER_UNAVAILABLE','PROVIDER_CONFIGURATION','PROVIDER_AUTHENTICATION','PROVIDER_USAGE_UNKNOWN'].includes(code))return 'provider';
 return 'unknown';
}
export function failureProgress(view={}){
 const w=view.world??{},parts=[],q=view.sharedQuest;
 if(q&&Number.isFinite(Number(q.delivered)))parts.push('共享交付 '+q.delivered+' / '+q.required);
 else if(view.delivered!==undefined&&w.requiredBerry!==undefined)parts.push('已交付 '+view.delivered+' / '+w.requiredBerry);
 if(w.carriedBerry!==undefined)parts.push('伙伴背包 '+w.carriedBerry+' 个树果');
 if(w.partner&&view.targetLevel)parts.push('已培养 Lv.'+w.partner.level+' / 目标 Lv.'+view.targetLevel);
 const last=[...(view.trace??[])].reverse().find(e=>['SUCCESS','IN_PROGRESS'].includes(e.status));
 if(last)parts.push('最后成功行动：'+(({PICKED_UP:'采集',SHARED_DELIVERED:'交付',QUEST_COMPLETED:'交付完成',SKILL_USED:'使用技能',GROWTH_DEFEATED:'战斗获胜',MOVING:'移动',MOVED:'移动',REGION_ENTERED:'进入区域',GROWTH_RESTED:'恢复伙伴'})[last.code]??'已确认的行动'));
 return parts.join(' · ');
}
