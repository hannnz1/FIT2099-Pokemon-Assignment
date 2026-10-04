export const scenarioNames={forest:'森林成长',shared:'共享任务',competition:'资源竞争',information:'信息转发',fault:'故障恢复'};
export const states={QUEUED:'排队中',RUNNING:'运行中',PAUSED:'已暂停',COMPLETED:'完成',COMPLETED_WITH_FAILURES:'结束 · 有失败',CANCELLED:'已取消',FAILED:'失败',PROVIDER_UNAVAILABLE:'模型不可用',WALL_TIME_LIMIT:'超过时限'};
export const escapeHtml=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export const percent=value=>value==null?'—':`${(value*100).toFixed(1)}%`;
export const numeric=value=>value==null?'未知':Number(value).toLocaleString('zh-CN',{maximumFractionDigits:2});
export function parseSeeds(text){const values=text.split(',').map(s=>s.trim());if(values.some(v=>!/^[-]?\d+$/.test(v)))throw Error('Seed 必须为整数');const seeds=values.map(Number);if(seeds.some(v=>!Number.isSafeInteger(v))||seeds.length>10||new Set(seeds).size!==seeds.length)throw Error('Seed 需为不重复的安全整数，最多 10 个');return seeds;}
export function table(headers,rows){return `<div class="table-wrap" tabindex="0" role="region" aria-label="评测数据表格，可左右滚动"><table><thead><tr>${headers.map(h=>`<th scope="col">${escapeHtml(h)}</th>`).join('')}</tr></thead><tbody>${rows.map(row=>`<tr>${row.map((cell,index)=>`<td data-label="${escapeHtml(headers[index])}">${cell}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`;}
export function comparisonView(rows){return table(['模型','完成 / 结束','完成率 · 95% 区间','平均动作','拒绝动作率（含注入）','调用延迟 ms','Token','估算 USD','故障恢复'],rows.map(row=>[escapeHtml(row.model),`${row.completed} / ${row.finished}`,`${percent(row.completionRate)}${(row.completion95Wilson??[]).length?`<br><small>${row.completion95Wilson.map(percent).join(' – ')}</small>`:''}`,numeric(row.averageActionSteps),percent(row.invalidActionRate),numeric(row.averageProviderLatencyMs),numeric(row.totalTokens),row.cost==null?'未知':row.cost.toFixed(6),percent(row.recoverySuccessRate)]));}
export function replayView(run,index){const trace=run.trace??[],row=trace[index];if(!row)return '<p>尚无已提交的动作。</p>';return `<div class="facts"><div class="fact"><b>${escapeHtml(row.agentId)}</b><br>${escapeHtml(row.toolName??'任务状态')}<br><span class="badge">${escapeHtml(row.actionStatus)} · ${escapeHtml(row.actionResult)}</span></div><div class="fact"><b>模型</b><br>${escapeHtml(run.model)}<br>Seed ${escapeHtml(run.seed)} · ${escapeHtml(scenarioNames[run.scenario])}</div></div><h3>工具参数</h3><pre>${escapeHtml(JSON.stringify(row.arguments,null,2))}</pre><h3>动作后的游戏状态</h3><pre>${escapeHtml(JSON.stringify(row.worldAfter,null,2))}</pre><details><summary>动作结果与世界指纹</summary><pre>${escapeHtml(JSON.stringify({result:row.resultData,fingerprint:row.worldFingerprint},null,2))}</pre></details>`;}

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
