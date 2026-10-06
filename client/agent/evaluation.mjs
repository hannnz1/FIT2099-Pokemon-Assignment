import {historyActions,scenarioNames,states,escapeHtml as esc,numeric,parseSeeds,table,comparisonView,replayView,summarizeBatches,replayPosition,requestGate,evaluationConfig,resultSummary,simpleResultsView,simpleReplayView} from './evaluation-view.mjs';
import {mountEvaluationWorkspace} from './player-frame.mjs';
const workspace=mountEvaluationWorkspace();
const $=id=>document.getElementById(id);
let lastAutoScroll=null,publicPaidLimits=null;let csrf='',snapshot={revision:0,batches:[]},selected=null,replay=null,replayBatch=null,busy=false,playing=false,playTimer=null,polling=false,pollTimer=null,ready=false;
const snapshotGate=requestGate(),reportGate=requestGate(),replayGate=requestGate(),pendingHtml=new Map();
async function api(path,body){
  const response=await fetch('/api/evaluation/'+path,{method:body?'POST':'GET',headers:body?{'Content-Type':'application/json','X-CSRF-Token':csrf}:{},body:body?JSON.stringify(body):undefined});
  const data=await response.json();
  if(!response.ok)throw Error(data.reasonCode??'请求失败');
  return data;
}
const friendlyErrors={PAID_CONFIRMATION_REQUIRED:'请先确认真实模型调用',PUBLIC_EVAL_LIMIT:'真实测试超过每批案例、决策或时限上限',PUBLIC_EVAL_DAILY_LIMIT:'当前会话今日真实测试额度已用完，请明天再试或选择免费基线',BATCH_LIMIT:'未归档测试已达到 8 批，请先归档一个已结束批次',BATCH_NOT_TERMINAL:'请先停止测试，再归档或删除',HISTORY_SETTLEMENT_LIMIT:'旧调用的费用尚未结算，请稍后清理'};
function notice(text){$('notice').textContent=text;}
// Defer replacing an interactive region until the focused control is released.
function stableHtml(id,html){
  const element=$(id);
  if(element.dataset.rendered===html){pendingHtml.delete(id);return;}
  const focused=element.contains(document.activeElement);
  const scene=id==='scene-results',active=document.activeElement;
  const openKeys=scene?[...element.querySelectorAll('details[open]')].map(d=>d.dataset.key):[];
  const focusKey=scene&&focused?active?.closest?.('details[data-key]')?.dataset.key:null;
  const focusRun=scene&&focused?active?.dataset?.run:null;
  if(focused&&!['run-controls','batches','scene-results'].includes(id)){pendingHtml.set(id,html);return;}
  const scrollLeft=element.querySelector('.table-wrap')?.scrollLeft??0;
  element.innerHTML=html;element.dataset.rendered=html;pendingHtml.delete(id);if(focused&&['run-controls','batches'].includes(id))element.querySelector('button')?.focus({preventScroll:true});
  if(scene){
    const details=[...element.querySelectorAll('details[data-key]')];
    for(const detail of details)if(openKeys.includes(detail.dataset.key))detail.open=true;
    if(focused){const detail=details.find(d=>d.dataset.key===focusKey);const control=focusRun?[...element.querySelectorAll('[data-run]')].find(b=>b.dataset.run===focusRun):detail?.querySelector('summary');control?.focus({preventScroll:true});}
  }
  const tableRegion=element.querySelector('.table-wrap');if(tableRegion)tableRegion.scrollLeft=scrollLeft;
}
document.addEventListener('focusout',()=>queueMicrotask(()=>{for(const [id,html] of pendingHtml)stableHtml(id,html);}));
function setBusy(value){
  busy=value;$('create').disabled=value||!ready;
  $('create').textContent=value?'正在保存…':'开始测试';
  $('recover').disabled=value;
  document.querySelectorAll('[data-control]').forEach(button=>{button.disabled=value;});
}
async function command(command,params){
  if(busy)return;
  const ticket=snapshotGate.next();reportGate.next();setBusy(true);notice('正在保存操作…');
  try{
    const latest=await api('batches');
    const result=await api('commands',{requestId:crypto.randomUUID(),expectedRevision:latest.revision,command,params});
    if(!snapshotGate.current(ticket))return;
    snapshot=result;if(selected&&!result.batches.some(b=>b.id===selected)){selected=null;clearReplay();clearResults();$('detail').hidden=true;}if(command==='CREATE'){const created=result.batches.find(b=>!latest.batches.some(old=>old.id===b.id));if(created){selected=created.id;clearReplay();clearResults();lastAutoScroll=null;}}render();await showReport();notice(command==='CREATE'?'测试已开始，结果会自动更新。':'操作已保存');
  }catch(error){notice(`操作失败：${friendlyErrors[error.message]??error.message}`);}
  finally{setBusy(false);updatePlan();}
}
function choices(id,values,labels){$(id).innerHTML=values.map((value,index)=>`<label><input type="checkbox" value="${esc(value)}" ${id==='scenarios'||value==='baseline'||id==='models'&&!values.includes('baseline')&&index===0?'checked':''}> ${esc(labels[value]??(value==='baseline'?'规则基线（免费）':value==='deepseek-flash'?'DeepSeek Flash（真实 AI）':value))}</label>`).join('');}
function checked(id){return [...$(id).querySelectorAll('input:checked')].map(input=>input.value);}
function configuration(){return evaluationConfig({models:checked('models'),scenarios:checked('scenarios'),starter:$('starter').value,seeds:$('seeds').value,repetitions:$('repetitions').value,decisions:$('decisions').value,seconds:$('seconds').value,prices:$('prices').value,paidConfirmed:$('paid-confirmed').checked},$('custom-options').checked?'custom':document.querySelector('[name=depth]:checked').value,publicPaidLimits);}
function updatePlan(){
 workspace.updatePresets();
 const paid=checked('models').some(m=>m!=='baseline');$('paid-warning').hidden=!paid;
 let valid=false;try{const config=configuration(),free=config.params.models.every(m=>m==='baseline');$('batch-plan').textContent='共 '+config.count+' 个案例 · '+(free?'免费规则基线':'真实模型，可能产生费用')+($('custom-options').checked?' · 自定义参数':'');valid=true;}catch(e){$('batch-plan').textContent=e.message;}
 $('create').disabled=busy||!ready||!valid;
}
$('setup').addEventListener('input',event=>{if(event.target.id!=='paid-confirmed')$('paid-confirmed').checked=false;if(event.target.name==='depth')$('custom-options').checked=false;if(event.target.name==='starter-choice')$('starter').value=event.target.value;if(['seeds','repetitions','decisions','seconds','prices'].includes(event.target.id))$('custom-options').checked=true;updatePlan();});
for(const [id,value] of [['select-all',true],['select-none',false]])$(id).onclick=()=>{for(const input of $('scenarios').querySelectorAll('input'))input.checked=value;updatePlan();};
$('setup').addEventListener('submit',event=>{event.preventDefault();try{command('CREATE',configuration().params);}catch(e){notice('请检查选项：'+e.message);}});
$('test-again').onclick=()=>{$('setup').closest('.setup').classList.remove('collapsed');$('create').scrollIntoView({block:'center',behavior:'auto'});$('create').focus({preventScroll:true});};
$('run-controls').addEventListener('click',event=>{const b=event.target.closest('[data-control]');if(b)command(b.dataset.control,{batchId:b.dataset.id});});
function render(){
 if(snapshot.publicPaidRemaining!=null)$('paid-quota').textContent='当前浏览器会话今日还可创建 '+snapshot.publicPaidRemaining+' 个真实案例';
 if(!selected&&snapshot.batches.length)selected=snapshot.batches[snapshot.batches.length-1].id;
 const current=snapshot.batches.find(b=>b.id===selected);workspace.update(current);const counts=resultSummary(current?.cases??[]),ongoing=current&&['RUNNING','QUEUED','PAUSED'].includes(current.status);$('run-progress').hidden=!ongoing;if(ongoing){$('progress-title').textContent=current.status==='PAUSED'?'测试已暂停':'测试进行中';$('progress-count').textContent='已结束 '+(counts.total-counts.pending)+' / '+counts.total+' 个案例';$('current-progress').max=counts.total||1;$('current-progress').value=counts.total-counts.pending;const row=current.cases.find(c=>c.status==='RUNNING')??current.cases.find(c=>['QUEUED','PAUSED'].includes(c.status));$('current-scenario').textContent=row?'当前：'+(scenarioNames[row.scenario]??row.scenario)+' · '+(states[row.status]??row.status):'正在汇总结果';stableHtml('run-controls',`<button data-control="${current.status==='PAUSED'?'RESUME':'PAUSE'}" data-id="${esc(current.id)}" ${busy?'disabled':''}>${current.status==='PAUSED'?'继续测试':'暂停'}</button><button class="secondary" data-control="CANCEL" data-id="${esc(current.id)}" ${busy?'disabled':''}>停止测试</button>`);}

  $('recover').hidden=snapshot.status!=='STORAGE_ERROR';
  const summary=summarizeBatches(snapshot.batches);
  for(const key of ['batches','active','completed'])$(`overview-${key}`).textContent=numeric(summary[key]);
  $('overview-finished').textContent=`${summary.finished} / ${summary.cases}`;
  stableHtml('batches',snapshot.batches.length?snapshot.batches.map(batch=>{
    const finished=summarizeBatches([batch]).finished;
    return `<article class="batch${batch.id===selected?' selected':''}"><div class="batch-top"><div><b>${esc(new Date(batch.createdAt).toLocaleString('zh-CN'))}</b><br><small>${esc(batch.id)}</small></div><span class="badge">${esc(states[batch.status]??batch.status)}</span></div><progress class="progress" value="${finished}" max="${Math.max(1,batch.cases.length)}" aria-label="已结束案例"></progress><small>${finished} / ${batch.cases.length} 案例已结束</small><div class="actions"><button data-show="${esc(batch.id)}" aria-pressed="${batch.id===selected}">${batch.id===selected?'当前结果':'查看结果'}</button>${['RUNNING','QUEUED'].includes(batch.status)?`<button data-control="PAUSE" data-id="${esc(batch.id)}" ${busy?'disabled':''}>暂停</button>`:batch.status==='PAUSED'?`<button data-control="RESUME" data-id="${esc(batch.id)}" ${busy?'disabled':''}>继续</button>`:''}${historyActions(batch).map(action=>`<button data-history="${action}" data-id="${esc(batch.id)}" ${busy?'disabled':''}>${action==='ARCHIVE'?'归档':'删除'}</button>`).join('')}${batch.archived?'<span class="badge">已归档</span>':''}${['RUNNING','QUEUED','PAUSED'].includes(batch.status)?`<button data-control="CANCEL" data-id="${esc(batch.id)}" ${busy?'disabled':''}>取消</button>`:''}</div></article>`;
  }).join(''):'<div class="empty"><strong>还没有评测批次</strong><br>选择模型与场景，加入队列后即可查看进度和比较结果。</div>');
}
function stopPlayback(){playing=false;clearTimeout(playTimer);playTimer=null;$('replay-play').textContent='播放';$('replay-play').setAttribute('aria-pressed','false');}
function clearResults(){for(const id of ['scene-results','result-summary','cases','comparison']){pendingHtml.delete(id);$(id).innerHTML='';delete $(id).dataset.rendered;}$('result-title').textContent='正在读取结果…';$('report-title').textContent='请稍候，正在读取所选测试。';}
function clearReplay(){
  stopPlayback();replayGate.next();replay=null;replayBatch=null;$('replay-panel').hidden=true;
}
$('batches').addEventListener('click',event=>{
  const show=event.target.closest('[data-show]'),control=event.target.closest('[data-control]'),history=event.target.closest('[data-history]');
  if(history){const action=history.dataset.history;if(action!=='DELETE'||confirm('删除这一批测试记录及回放？已消耗额度不会返还。'))command(action,{batchId:history.dataset.id});return;}
  if(show){
    if(selected!==show.dataset.show){selected=show.dataset.show;clearReplay();clearResults();reportGate.next();pendingHtml.delete('cases');pendingHtml.delete('comparison');$('detail').hidden=false;stableHtml('comparison','<p class="empty">正在读取模型比较…</p>');stableHtml('cases','');}
    render();showReport();
  }
  if(control)command(control.dataset.control,{batchId:control.dataset.id});
});
async function showReport(){
  if(!selected)return;
  const batchId=selected,ticket=reportGate.next();
  $('detail').setAttribute('aria-busy','true');
  try{
    const report=await api(`batches/${encodeURIComponent(batchId)}/report`);
    if(selected!==batchId||!reportGate.current(ticket))return;
    $('detail').hidden=false;
    const batch=snapshot.batches.find(row=>row.id===batchId);
    const counts=resultSummary(report.cases),ended=counts.pending===0;const heading=ended?'测试结束':'当前测试结果';$('result-title').textContent=heading;
    stableHtml('result-summary',`<strong>${counts.passed} / ${counts.total} 项目标达成</strong><span>${counts.failed} 项未达成${counts.cancelled?' · '+counts.cancelled+' 项已停止':''}${counts.pending?' · '+counts.pending+' 项待完成':''}</span><small>成长等场景按目标完成计；对战按获胜计。${ended?'':'尚未结束，不代表最终结果。'}</small>`);
    stableHtml('scene-results',simpleResultsView(report));
    if(ended&&lastAutoScroll!==batchId){lastAutoScroll=batchId;$('detail').scrollIntoView({block:'start',behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'instant':'smooth'});}
    $('report-title').textContent=`${batch?new Date(batch.createdAt).toLocaleString('zh-CN'):batchId} · ${report.cases.length} 个案例`;
    stableHtml('comparison',report.comparison.length?comparisonView(report.comparison):'<p class="empty">暂无模型统计，案例运行后将在这里显示。</p>');
    stableHtml('cases',report.cases.length?table(['场景','Seed / 重复','模型','状态','动作 / Token','详情'],report.cases.map(row=>[esc(scenarioNames[row.scenario]??row.scenario),`${row.seed} / ${row.repetition}`,esc(row.model),`<span class="badge">${esc(states[row.status]??row.status)}</span>`,`${numeric(row.metrics.actionSteps)} / ${numeric(row.metrics.totalTokens)}`,`<button data-run="${esc(row.id)}" data-batch="${esc(batchId)}" aria-label="回放 ${esc(scenarioNames[row.scenario]??row.scenario)} · ${esc(row.model)} · Seed ${esc(row.seed)}">回放</button>`])):'<p class="empty">此批次暂无案例。</p>');
  }catch(error){if(selected===batchId&&reportGate.current(ticket))notice(`结果读取失败：${friendlyErrors[error.message]??error.message}`);}
  finally{if(reportGate.current(ticket))$('detail').setAttribute('aria-busy','false');}
}
$('detail').addEventListener('click',async event=>{
  const button=event.target.closest('[data-run]');if(!button||!selected||button.dataset.batch&&button.dataset.batch!==selected)return;
  clearReplay();const batchId=selected,runId=button.dataset.run,ticket=replayGate.next();
  notice('正在读取案例回放…');
  try{
    const result=await api(`batches/${encodeURIComponent(batchId)}/runs/${encodeURIComponent(runId)}/replay`);
    if(selected!==batchId||!replayGate.current(ticket))return;
    replay=result;replayBatch=batchId;$('replay-panel').hidden=false;
    $('replay-title').textContent=`${scenarioNames[replay.scenario]??replay.scenario} · ${replay.model} · Seed ${replay.seed}`;
    $('step').max=Math.max(0,(replay.trace??[]).length-1);$('step').value=0;drawReplay();notice('已读取真实案例回放');
    $('replay-panel').scrollIntoView({behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'instant':'smooth',block:'start'});
  }catch(error){if(replayGate.current(ticket))notice(`回放读取失败：${friendlyErrors[error.message]??error.message}`);}
});
function drawReplay(){
  if(!replay)return;
  const position=replayPosition((replay.trace??[]).length,$('step').value);
  $('step').value=position.index;$('step').disabled=position.total===0;
  $('step').setAttribute('aria-valuetext',`第 ${position.total?position.index+1:0} 步，共 ${position.total} 步`);
  $('step-number').textContent=`${position.total?position.index+1:0} / ${position.total}`;
  $('replay-prev').disabled=!position.previous;$('replay-next').disabled=!position.next;$('replay-play').disabled=position.total<2;
  $('replay').innerHTML=simpleReplayView(replay,position.index);
  if(!position.next)stopPlayback();
}
function moveReplay(delta){if(!replay)return;$('step').value=replayPosition((replay.trace??[]).length,Number($('step').value)+delta).index;drawReplay();}
function schedulePlayback(){
  clearTimeout(playTimer);
  if(!playing)return;
  playTimer=setTimeout(()=>{if(!playing)return;moveReplay(1);schedulePlayback();},1000/Number($('replay-speed').value));
}
$('replay').addEventListener('click',event=>{if(event.target.closest?.('summary'))stopPlayback();});
$('replay-prev').addEventListener('click',()=>{stopPlayback();moveReplay(-1);});
$('replay-next').addEventListener('click',()=>{stopPlayback();moveReplay(1);});
$('step').addEventListener('input',()=>{stopPlayback();drawReplay();});
$('replay-play').addEventListener('click',()=>{
  if(playing){stopPlayback();return;}
  if(!replay||(replay.trace??[]).length<2)return;
  if(!replayPosition(replay.trace.length,$('step').value).next){$('step').value=0;drawReplay();}
  playing=true;$('replay-play').textContent='暂停';$('replay-play').setAttribute('aria-pressed','true');schedulePlayback();
});
$('replay-speed').addEventListener('change',schedulePlayback);
function download(path,name){const a=document.createElement('a');a.href='/api/evaluation/'+path;a.download=name;a.click();}
$('export').addEventListener('click',()=>{if(selected)download(`batches/${encodeURIComponent(selected)}/download`,'v4-batch-report.json');});
$('export-run').addEventListener('click',()=>{if(replay)download(`batches/${encodeURIComponent(replayBatch)}/runs/${encodeURIComponent(replay.id)}/download`,'v4-case-replay.json');});
$('recover').addEventListener('click',()=>command('RECOVER',{}));
async function poll(){
  if(polling)return;polling=true;clearTimeout(pollTimer);
  try{
    if(busy||document.hidden)return;
    const ticket=snapshotGate.next(),result=await api('batches');
    if(busy||!snapshotGate.current(ticket))return;
    snapshot=result;render();if(selected)await showReport();
  }catch(error){notice(`连接暂不可用：${friendlyErrors[error.message]??error.message}；正在自动重试`);}
  finally{polling=false;pollTimer=setTimeout(poll,1500);}
}
document.addEventListener('visibilitychange',()=>{if(document.hidden)stopPlayback();else if(ready)poll();});
window.addEventListener('pagehide',()=>{stopPlayback();clearTimeout(pollTimer);snapshotGate.next();reportGate.next();replayGate.next();});
try{
  csrf=(await api('session')).csrfToken;const catalog=await api('catalog');
  publicPaidLimits=catalog.publicPaidLimits;choices('models',catalog.models,{});$('model-options').hidden=catalog.models.length===1&&catalog.models[0]==='baseline';$('provider-note').textContent=catalog.models.length===1&&catalog.models[0]==='baseline'?'免费规则基线 · 不调用真实模型':'可选真实模型';choices('scenarios',catalog.scenarios,scenarioNames);updatePlan();
  snapshot=await api('batches');ready=true;render();setBusy(false);updatePlan();if(selected)await showReport();notice('已连接 · 测试结果独立保存');pollTimer=setTimeout(poll,1500);
}catch(error){notice(`连接失败：${friendlyErrors[error.message]??error.message}。请检查服务后刷新页面重试。`);$('create').disabled=true;}


$('history-cleanup')?.addEventListener('click',async()=>{
 const ended=snapshot.batches.filter(b=>historyActions(b).includes('DELETE'));
 if(!ended.length){notice('暂无可清理的已结束记录。');return;}
 if(!confirm('删除 '+ended.length+' 批已结束测试及其回放？运行和暂停批次保留；已消耗额度不会返还。'))return;
 for(const batch of ended){await command('DELETE',{batchId:batch.id});if(snapshot.batches.some(b=>b.id===batch.id))break;}
});
