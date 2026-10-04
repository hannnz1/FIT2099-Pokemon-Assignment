import {scenarioNames,states,escapeHtml as esc,numeric,parseSeeds,table,comparisonView,replayView,summarizeBatches,replayPosition,requestGate} from './evaluation-view.mjs';
const $=id=>document.getElementById(id);
let csrf='',snapshot={revision:0,batches:[]},selected=null,replay=null,replayBatch=null,busy=false,playing=false,playTimer=null,polling=false,pollTimer=null,ready=false;
const snapshotGate=requestGate(),reportGate=requestGate(),replayGate=requestGate(),pendingHtml=new Map();
async function api(path,body){
  const response=await fetch('/api/evaluation/'+path,{method:body?'POST':'GET',headers:body?{'Content-Type':'application/json','X-CSRF-Token':csrf}:{},body:body?JSON.stringify(body):undefined});
  const data=await response.json();
  if(!response.ok)throw Error(data.reasonCode??'请求失败');
  return data;
}
function notice(text){$('notice').textContent=text;}
// Defer replacing an interactive region until the focused control is released.
function stableHtml(id,html){
  const element=$(id);
  if(element.dataset.rendered===html){pendingHtml.delete(id);return;}
  if(element.contains(document.activeElement)){pendingHtml.set(id,html);return;}
  const scrollLeft=element.querySelector('.table-wrap')?.scrollLeft??0;
  element.innerHTML=html;element.dataset.rendered=html;pendingHtml.delete(id);
  const tableRegion=element.querySelector('.table-wrap');if(tableRegion)tableRegion.scrollLeft=scrollLeft;
}
document.addEventListener('focusout',()=>queueMicrotask(()=>{for(const [id,html] of pendingHtml)stableHtml(id,html);}));
function setBusy(value){
  busy=value;$('create').disabled=value||!ready;
  $('create').textContent=value?'正在保存…':'加入队列';
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
    snapshot=result;render();await showReport();notice('操作已保存');
  }catch(error){notice(`操作失败：${error.message}`);}
  finally{setBusy(false);}
}
function choices(id,values,labels){$(id).innerHTML=values.map((value,index)=>`<label><input type="checkbox" value="${esc(value)}" ${index===0?'checked':''}> ${esc(labels[value]??(value==='baseline'?'规则基线（不调用 AI）':value))}</label>`).join('');}
function checked(id){return [...$(id).querySelectorAll('input:checked')].map(input=>input.value);}
function updatePlan(){
  try{const count=checked('models').length*checked('scenarios').length*parseSeeds($('seeds').value).length*Number($('repetitions').value);$('batch-plan').textContent=`本批 ${numeric(count)} 个案例 · 最多 24 个${count>24?'，请减少配置组合':''}`;}
  catch(error){$('batch-plan').textContent=error.message;}
}
$('setup').addEventListener('input',updatePlan);
$('setup').addEventListener('submit',event=>{
  event.preventDefault();
  try{
    const params={models:checked('models'),scenarios:checked('scenarios'),seeds:parseSeeds($('seeds').value),repetitions:Number($('repetitions').value),starter:$('starter').value,decisions:Number($('decisions').value),wallMillis:Number($('seconds').value)*1000,prices:JSON.parse($('prices').value||'{}')};
    if(!params.models.length||!params.scenarios.length||params.models.length*params.scenarios.length*params.seeds.length*params.repetitions>24)throw Error('请选择模型与场景；每批最多 24 个案例');
    command('CREATE',params);
  }catch(error){notice(`请检查配置：${error.message}`);}
});
function render(){
  $('recover').hidden=snapshot.status!=='STORAGE_ERROR';
  const summary=summarizeBatches(snapshot.batches);
  for(const key of ['batches','active','completed'])$(`overview-${key}`).textContent=numeric(summary[key]);
  $('overview-finished').textContent=`${summary.finished} / ${summary.cases}`;
  stableHtml('batches',snapshot.batches.length?snapshot.batches.map(batch=>{
    const finished=summarizeBatches([batch]).finished;
    return `<article class="batch${batch.id===selected?' selected':''}"><div class="batch-top"><div><b>${esc(new Date(batch.createdAt).toLocaleString('zh-CN'))}</b><br><small>${esc(batch.id)}</small></div><span class="badge">${esc(states[batch.status]??batch.status)}</span></div><progress class="progress" value="${finished}" max="${Math.max(1,batch.cases.length)}" aria-label="已结束案例"></progress><small>${finished} / ${batch.cases.length} 案例已结束</small><div class="actions"><button data-show="${esc(batch.id)}" aria-pressed="${batch.id===selected}">${batch.id===selected?'当前结果':'查看结果'}</button>${['RUNNING','QUEUED'].includes(batch.status)?`<button data-control="PAUSE" data-id="${esc(batch.id)}" ${busy?'disabled':''}>暂停</button>`:batch.status==='PAUSED'?`<button data-control="RESUME" data-id="${esc(batch.id)}" ${busy?'disabled':''}>继续</button>`:''}${['RUNNING','QUEUED','PAUSED'].includes(batch.status)?`<button data-control="CANCEL" data-id="${esc(batch.id)}" ${busy?'disabled':''}>取消</button>`:''}</div></article>`;
  }).join(''):'<div class="empty"><strong>还没有评测批次</strong><br>选择模型与场景，加入队列后即可查看进度和比较结果。</div>');
}
function stopPlayback(){playing=false;clearTimeout(playTimer);playTimer=null;$('replay-play').textContent='播放';$('replay-play').setAttribute('aria-pressed','false');}
function clearReplay(){
  stopPlayback();replayGate.next();replay=null;replayBatch=null;$('replay-panel').hidden=true;
}
$('batches').addEventListener('click',event=>{
  const show=event.target.closest('[data-show]'),control=event.target.closest('[data-control]');
  if(show){
    if(selected!==show.dataset.show){selected=show.dataset.show;clearReplay();reportGate.next();pendingHtml.delete('cases');pendingHtml.delete('comparison');$('detail').hidden=false;stableHtml('comparison','<p class="empty">正在读取模型比较…</p>');stableHtml('cases','');}
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
    $('report-title').textContent=`${batch?new Date(batch.createdAt).toLocaleString('zh-CN'):batchId} · ${report.cases.length} 个案例`;
    stableHtml('comparison',report.comparison.length?comparisonView(report.comparison):'<p class="empty">暂无模型统计，案例运行后将在这里显示。</p>');
    stableHtml('cases',report.cases.length?table(['场景','Seed / 重复','模型','状态','动作 / Token','详情'],report.cases.map(row=>[esc(scenarioNames[row.scenario]??row.scenario),`${row.seed} / ${row.repetition}`,esc(row.model),`<span class="badge">${esc(states[row.status]??row.status)}</span>`,`${numeric(row.metrics.actionSteps)} / ${numeric(row.metrics.totalTokens)}`,`<button data-run="${esc(row.id)}" aria-label="回放 ${esc(scenarioNames[row.scenario]??row.scenario)} · ${esc(row.model)} · Seed ${esc(row.seed)}">回放</button>`])):'<p class="empty">此批次暂无案例。</p>');
  }catch(error){if(selected===batchId&&reportGate.current(ticket))notice(`结果读取失败：${error.message}`);}
  finally{if(reportGate.current(ticket))$('detail').setAttribute('aria-busy','false');}
}
$('cases').addEventListener('click',async event=>{
  const button=event.target.closest('[data-run]');if(!button||!selected)return;
  clearReplay();const batchId=selected,runId=button.dataset.run,ticket=replayGate.next();
  notice('正在读取案例回放…');
  try{
    const result=await api(`batches/${encodeURIComponent(batchId)}/runs/${encodeURIComponent(runId)}/replay`);
    if(selected!==batchId||!replayGate.current(ticket))return;
    replay=result;replayBatch=batchId;$('replay-panel').hidden=false;
    $('replay-title').textContent=`${scenarioNames[replay.scenario]??replay.scenario} · ${replay.model} · Seed ${replay.seed}`;
    $('step').max=Math.max(0,(replay.trace??[]).length-1);$('step').value=0;drawReplay();notice('已读取真实案例回放');
    $('replay-panel').scrollIntoView({behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'instant':'smooth',block:'start'});
  }catch(error){if(replayGate.current(ticket))notice(`回放读取失败：${error.message}`);}
});
function drawReplay(){
  if(!replay)return;
  const position=replayPosition((replay.trace??[]).length,$('step').value);
  $('step').value=position.index;$('step').disabled=position.total===0;
  $('step').setAttribute('aria-valuetext',`第 ${position.total?position.index+1:0} 步，共 ${position.total} 步`);
  $('step-number').textContent=`${position.total?position.index+1:0} / ${position.total}`;
  $('replay-prev').disabled=!position.previous;$('replay-next').disabled=!position.next;$('replay-play').disabled=position.total<2;
  $('replay').innerHTML=replayView(replay,position.index);
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
  }catch(error){notice(`连接暂不可用：${error.message}；正在自动重试`);}
  finally{polling=false;pollTimer=setTimeout(poll,1500);}
}
document.addEventListener('visibilitychange',()=>{if(document.hidden)stopPlayback();else if(ready)poll();});
window.addEventListener('pagehide',()=>{stopPlayback();clearTimeout(pollTimer);snapshotGate.next();reportGate.next();replayGate.next();});
try{
  csrf=(await api('session')).csrfToken;const catalog=await api('catalog');
  choices('models',catalog.models,{});choices('scenarios',catalog.scenarios,scenarioNames);updatePlan();
  snapshot=await api('batches');ready=true;render();setBusy(false);notice('已连接 · 单批最多 24 个案例');pollTimer=setTimeout(poll,1500);
}catch(error){notice(`连接失败：${error.message}。请检查服务后刷新页面重试。`);$('create').disabled=true;}
