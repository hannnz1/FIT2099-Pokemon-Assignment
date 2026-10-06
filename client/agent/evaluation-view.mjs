import {failureReason,failureCategory} from './failure-recovery.mjs';
export const scenarioNames={forest:'森林成长',shared:'共享任务',competition:'资源竞争',information:'信息转发',fault:'故障恢复',battle:'训练师对战'};
export const states={NAVIGATION_REQUIRES_HELP:'导航受阻 · 保护停止',NO_PROGRESS_REQUIRES_HELP:'进度停滞 · 保护停止',QUEUED:'排队中',RUNNING:'运行中',PAUSED:'已暂停',COMPLETED:'完成',COMPLETED_WITH_FAILURES:'结束 · 有失败',CANCELLED:'已取消',FAILED:'失败',PROVIDER_UNAVAILABLE:'模型不可用',WALL_TIME_LIMIT:'超过时限'};
export const escapeHtml=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export const percent=value=>value==null?'—':`${(value*100).toFixed(1)}%`;
export const numeric=value=>value==null?'未知':Number(value).toLocaleString('zh-CN',{maximumFractionDigits:2});
export function parseSeeds(text){const values=text.split(',').map(s=>s.trim());if(values.some(v=>!/^[-]?\d+$/.test(v)))throw Error('Seed 必须为整数');const seeds=values.map(Number);if(seeds.some(v=>!Number.isSafeInteger(v))||seeds.length>10||new Set(seeds).size!==seeds.length)throw Error('Seed 需为不重复的安全整数，最多 10 个');return seeds;}
export function table(headers,rows){return `<div class="table-wrap" tabindex="0" role="region" aria-label="评测数据表格，可左右滚动"><table><thead><tr>${headers.map(h=>`<th scope="col">${escapeHtml(h)}</th>`).join('')}</tr></thead><tbody>${rows.map(row=>`<tr>${row.map((cell,index)=>`<td data-label="${escapeHtml(headers[index])}">${cell}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`;}
export function comparisonView(rows){return table(['模型','完成 / 结束','完成率 · 95% 区间','对战胜率','平均动作','拒绝动作率（含注入）','调用延迟 ms','Token','估算 USD','故障恢复'],rows.map(row=>[escapeHtml(row.model),`${row.completed} / ${row.finished}`,`${percent(row.completionRate)}${(row.completion95Wilson??[]).length?`<br><small>${row.completion95Wilson.map(percent).join(' – ')}</small>`:''}`,row.battleCases?`${row.battleWins} / ${row.battleCases} · ${percent(row.battleWinRate)}`:'—',numeric(row.averageActionSteps),percent(row.invalidActionRate),numeric(row.averageProviderLatencyMs),numeric(row.totalTokens),row.cost==null?'未知':row.cost.toFixed(6),percent(row.recoverySuccessRate)]));}
export function replayView(run,index){const trace=run.trace??[],row=trace[index];if(!row)return '<p>尚无已提交的动作。</p>';return `<div class="facts"><div class="fact"><b>${escapeHtml(row.agentId)}</b><br>${escapeHtml(row.toolName??'任务状态')}<br><span class="badge">${escapeHtml(row.actionStatus)} · ${escapeHtml(row.actionResult)}</span><br><small>动作来源：${row.executionSource==='ENGINE_DELIVERY_PRIORITY'?'引擎交付规则':row.executionSource==='SCHEDULER'?'任务调度':'模型或动作续步'}</small></div><div class="fact"><b>模型</b><br>${escapeHtml(run.model)}<br>Seed ${escapeHtml(run.seed)} · ${escapeHtml(scenarioNames[run.scenario])}</div></div><h3>工具参数</h3><pre>${escapeHtml(JSON.stringify(row.arguments,null,2))}</pre><h3>动作后的游戏状态</h3><pre>${escapeHtml(JSON.stringify(row.worldAfter,null,2))}</pre><details><summary>动作结果与世界指纹</summary><pre>${escapeHtml(JSON.stringify({result:row.resultData,fingerprint:row.worldFingerprint},null,2))}</pre></details>`;}

export function summarizeBatches(batches=[]){
  const cases=batches.flatMap(batch=>batch.cases??[]);
  return {batches:batches.length,active:batches.filter(batch=>['QUEUED','RUNNING','PAUSED'].includes(batch.status)).length,cases:cases.length,finished:cases.filter(row=>!['QUEUED','RUNNING','PAUSED'].includes(row.status)).length,completed:cases.filter(row=>row.status==='COMPLETED').length};
}
export function replayPosition(total,index){
  const position=Math.max(0,Math.min(Math.max(0,total-1),Number.isFinite(Number(index))?Math.trunc(Number(index)):0));
  return {index:position,total,previous:total>0&&position>0,next:total>0&&position<total-1};
}
export function requestGate(){
  let version=0;
  return {next:()=>++version,current:ticket=>ticket===version};
}


// Simple user choices map onto the existing bounded evaluation API.
export function evaluationConfig(draft,mode='quick',limits=null){
 const models=draft.models??[],scenarios=draft.scenarios??[];
 if(!models.length||!scenarios.length)throw Error('请至少选择一项测试内容和一种模型');
 const custom=mode==='custom',seeds=custom?parseSeeds(draft.seeds):mode==='stable'?[11,23,37]:[11];
 const paid=models.some(m=>m!=='baseline');
 const repetitions=custom?Number(draft.repetitions):1,decisions=custom?Number(draft.decisions):paid&&limits?.maxDecisions?limits.maxDecisions:100,seconds=custom?Number(draft.seconds):180;
 for(const [name,value,min,max] of [['重复次数',repetitions,1,5],['决策上限',decisions,1,100],['时限',seconds,1,600]])if(!Number.isInteger(value)||value<min||value>max)throw Error(name+'必须是 '+min+'–'+max+' 范围内的整数');
 const count=models.length*scenarios.length*seeds.length*repetitions;if(count>24)throw Error('共 '+count+' 个案例，超过 24 个上限，请减少测试组合');
 if(paid&&limits?.maxPaidCases){const paidCount=models.filter(m=>m!=='baseline').length*scenarios.length*seeds.length*repetitions;if(paidCount>limits.maxPaidCases)throw Error('公开 DeepSeek 测试每批最多 '+limits.maxPaidCases+' 个真实案例，请减少场景或次数');if(decisions>limits.maxDecisions||seconds>limits.maxSeconds)throw Error('真实案例最多 '+limits.maxDecisions+' 次决策、'+limits.maxSeconds+' 秒');if(!draft.paidConfirmed)throw Error('请先确认真实模型调用及费用说明');}
 return {count,params:{models,scenarios,seeds,repetitions,starter:draft.starter,decisions,wallMillis:seconds*1000,prices:custom?JSON.parse(draft.prices||'{}'):{},...(paid&&limits?.maxPaidCases?{paidConfirmed:true}:{})}};
}
export function caseOutcome(row){
 if(['QUEUED','RUNNING','PAUSED'].includes(row.status))return {kind:'pending',label:states[row.status]};
 if(row.status==='CANCELLED')return {kind:'cancelled',label:'已停止'};
 if(row.status==='COMPLETED'){
  if(row.scenario==='battle')return row.metrics?.battleWin===1?{kind:'passed',label:'获胜'}:{kind:'failed',label:row.metrics?.battleDraw===1?'平局':'未获胜'};
  return {kind:'passed',label:'通过'};
 }
 return {kind:'failed',label:states[row.status]??'未完成'};
}
export function resultSummary(rows=[]){const out={total:rows.length,passed:0,failed:0,cancelled:0,pending:0};for(const row of rows)out[caseOutcome(row).kind]++;return out;}
export function scenarioResults(rows=[]){
 const groups=new Map();for(const row of rows){const key=row.scenario+'|'+row.model;if(!groups.has(key))groups.set(key,{scenario:row.scenario,model:row.model,rows:[]});groups.get(key).rows.push(row);}
 return [...groups.values()].map(g=>({...g,...resultSummary(g.rows),failures:g.rows.filter(r=>caseOutcome(r).kind==='failed')}));
}
export function caseExplanation(row){
 const outcome=caseOutcome(row);
 if(outcome.kind==='pending')return outcome.label+'，尚未产生最终结果。';
 if(outcome.kind==='cancelled')return '测试已停止，未完成案例不计为通过或失败。';
 if(row.status==='COMPLETED')return row.scenario==='battle'?outcome.kind==='passed'?'已击败固定规则对手。':outcome.label+'；对战已结束，但未取得胜利。':'已完成该场景的预设目标。';
 if(row.errorCode&&failureCategory({errorCode:row.errorCode})!=='unknown')return failureReason({errorCode:row.errorCode});
 const reasons={WALL_TIME_LIMIT:'超过案例时限'+(row.budgets?.wallMillis?'（'+row.budgets.wallMillis/1000+' 秒）':'')+'，目标尚未完成。',NAVIGATION_REQUIRES_HELP:'导航受阻，系统保护停止；请查看最后行动与地图状态。',NO_PROGRESS_REQUIRES_HELP:'连续行动未推进目标，系统保护停止。',PROVIDER_UNAVAILABLE:'模型服务不可用，测试未完成。',ACTION_RECORD_LIMIT:'行动记录达到上限，测试停止。'};
 return reasons[row.status]??'目标未完成；具体原因待排查，请查看行动记录。';
}
export function simpleResultsView(report){
 return scenarioResults(report.cases??[]).map(g=>{const tone=g.pending?'pending':g.failed?'failed':g.cancelled?'cancelled':'passed';return `<article class="scene-result" data-tone="${tone}"><div class="result-heading"><strong>${escapeHtml(scenarioNames[g.scenario]??g.scenario)}</strong><span class="badge">${g.passed}/${g.total} ${g.scenario==='battle'?'获胜':'通过'}</span></div><small>${escapeHtml(g.model==='baseline'?'免费规则基线':g.model)}${g.pending?' · '+g.pending+' 项待完成':''}${g.cancelled?' · '+g.cancelled+' 项已停止':''}</small><p>${escapeHtml(g.pending?'测试尚未结束，结果会持续更新。':g.failed?caseExplanation(g.failures[0]):g.cancelled?'部分案例已停止，结果不完整。':g.scenario==='battle'?'已击败固定规则对手。':'已完成该场景的预设目标。')}</p><details data-key="${escapeHtml(g.scenario+'|'+g.model)}"><summary>${g.failed?'查看失败详情与回放':'查看各次结果与回放'}</summary>${g.rows.map(row=>`<div class="case-row"><div><strong>Seed ${escapeHtml(row.seed)} · 第 ${escapeHtml(row.repetition)} 次</strong><span>${escapeHtml(caseOutcome(row).label)}</span><small>${escapeHtml(caseExplanation(row))}</small></div><button data-run="${escapeHtml(row.id)}" data-batch="${escapeHtml(report.id??'')}">${caseOutcome(row).kind==='failed'?'查看详情':'回放'}</button></div>`).join('')}</details></article>`;}).join('');
}
export function simpleReplayView(run,index){
 const row=(run.trace??[])[index],tools={move:'移动',move_to:'前往目标',go_to_region:'前往区域',go_to_recovery:'前往营地',pickup:'采集资源',deliver:'交付资源',use_skill:'使用技能',rest:'恢复伙伴',battle_move:'使用对战技能',battle_switch:'切换出战伙伴',send_message:'发送观察信息',relay_message:'转发信息',eval_fault:'注入测试故障'};
 return `<div class="replay-summary"><strong>${escapeHtml(caseExplanation(run))}</strong>${row?`<p>第 ${index+1} 步：${escapeHtml(tools[row.toolName]??row.toolName??'任务状态更新')}</p><p>行动结果：${escapeHtml(row.actionStatus??'未知')} · ${escapeHtml(row.actionResult??'未提供结果代码')}</p>`:'<p>暂无已提交行动，无法据此推断失败根因。</p>'}</div><details><summary>查看工具参数与技术记录</summary>${replayView(run,index)}</details>`;
}

export function historyActions(batch){return ["COMPLETED","COMPLETED_WITH_FAILURES","CANCELLED"].includes(batch.status)?[...(!batch.archived?["ARCHIVE"]:[]),"DELETE"]:[];}
