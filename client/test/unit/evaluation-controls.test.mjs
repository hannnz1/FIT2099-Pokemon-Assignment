import test from 'node:test';
import assert from 'node:assert/strict';
import * as view from '../../agent/evaluation-view.mjs';
test('overview counts real terminal cases separately from successful completion',()=>{
  assert.equal(typeof view.summarizeBatches,'function');
  assert.deepEqual(view.summarizeBatches([{status:'RUNNING',cases:[{status:'COMPLETED'},{status:'FAILED'},{status:'RUNNING'}]},{status:'PAUSED',cases:[{status:'PAUSED'}]}]),{batches:2,active:2,cases:4,finished:2,completed:1});
  assert.deepEqual(view.summarizeBatches([]),{batches:0,active:0,cases:0,finished:0,completed:0});
});
test('replay navigation clamps invalid positions and stops at both boundaries',()=>{
  assert.equal(typeof view.replayPosition,'function');
  assert.deepEqual(view.replayPosition(4,99),{index:3,total:4,previous:true,next:false});
  assert.deepEqual(view.replayPosition(4,-1),{index:0,total:4,previous:false,next:true});
  assert.deepEqual(view.replayPosition(0,NaN),{index:0,total:0,previous:false,next:false});
});
test('request gate rejects earlier selection and overlapping poll responses',()=>{
  assert.equal(typeof view.requestGate,'function');
  const gate=view.requestGate();const first=gate.next();assert.equal(gate.current(first),true);
  const second=gate.next();assert.equal(gate.current(first),false);assert.equal(gate.current(second),true);
});
test('tables give narrow-screen cells escaped column labels',()=>{
  assert.match(view.table(['<模型>'],[['x']]),/data-label="&lt;模型&gt;"/);
});

test('page playback pauses on manual navigation and stale cases cannot replace current selection',async()=>{
  const {readFile}=await import('node:fs/promises');
  const html=await readFile(new URL('../../agent/evaluation.html',import.meta.url),'utf8');
  const nodes=new Map([...html.matchAll(/id="([^"]+)"/g)].map(match=>[match[1],{
    value:'',dataset:{},hidden:false,disabled:false,innerHTML:'',textContent:'',events:new Map(),attrs:{},
    addEventListener(name,handler){this.events.set(name,handler);},
    setAttribute(name,value){this.attrs[name]=value;},
    contains(element){return element===this;},querySelector(){return null;},querySelectorAll(){return [];},scrollIntoView(){}
  }]));
  for(const [id,value] of Object.entries({seeds:'11',repetitions:'1','replay-speed':'4',step:'0'}))nodes.get(id).value=value;
  const documentEvents=new Map(),windowEvents=new Map();
  const before={document:globalThis.document,window:globalThis.window,fetch:globalThis.fetch,matchMedia:globalThis.matchMedia};
  globalThis.document={hidden:false,activeElement:null,getElementById:id=>nodes.get(id),addEventListener:(name,handler)=>documentEvents.set(name,handler),querySelectorAll:()=>[]};
  globalThis.window={addEventListener:(name,handler)=>windowEvents.set(name,handler)};
  globalThis.matchMedia=()=>({matches:true});
  const batch={id:'batch-a',createdAt:0,status:'COMPLETED',cases:[]};
  const run=id=>({id,scenario:'forest',model:id,seed:11,trace:[0,1,2].map(turn=>({agentId:'partner',worldAfter:{turn}}))});
  let resolveOld,reportCount=0;
  globalThis.fetch=async path=>({ok:true,json:async()=>{
    if(path.endsWith('/session'))return {csrfToken:'test'};
    if(path.endsWith('/catalog'))return {models:['baseline'],scenarios:['forest']};
    if(path.endsWith('/batches'))return {revision:1,batches:[batch]};
    if(path.endsWith('/report'))return {comparison:[],cases:[{id:'current',scenario:'forest',model:'baseline',seed:11,repetition:1,status:'COMPLETED',metrics:{actionSteps:++reportCount,totalTokens:null}}]};
    if(path.includes('/runs/old/'))return new Promise(resolve=>{resolveOld=resolve;});
    return run('current');
  }});
  const click=(id,dataset)=>nodes.get(id).events.get('click')({target:{closest:selector=>selector==='[data-show]'&&dataset?.show?{dataset}:selector==='[data-run]'&&dataset?.run?{dataset}:null}});
  try{
    await import(new URL('../../agent/evaluation.mjs?control-test',import.meta.url));
    click('batches',{show:'batch-a'});
    await new Promise(resolve=>setImmediate(resolve));
    const previousCases=nodes.get('cases').innerHTML;
    globalThis.document.activeElement=nodes.get('cases');
    click('batches',{show:'batch-a'});
    await new Promise(resolve=>setImmediate(resolve));
    assert.equal(nodes.get('cases').innerHTML,previousCases);
    globalThis.document.activeElement=null;documentEvents.get('focusout')();
    await new Promise(resolve=>setImmediate(resolve));
    assert.notEqual(nodes.get('cases').innerHTML,previousCases);
    const old=click('cases',{run:'old'});
    await new Promise(resolve=>setImmediate(resolve));
    await click('cases',{run:'current'});
    resolveOld(run('old'));await old;
    assert.equal(nodes.get('replay-panel').hidden,false);
    assert.match(nodes.get('replay-title').textContent,/current/);
    assert.match(nodes.get('replay').innerHTML,/&quot;turn&quot;: 0/);
    click('replay-next');assert.equal(nodes.get('step').value,1);
    click('replay-play');assert.equal(nodes.get('replay-play').attrs['aria-pressed'],'true');
    nodes.get('replay').events.get('click')?.({target:{closest:selector=>selector==='summary'?{}:null}});
    assert.equal(nodes.get('replay-play').attrs['aria-pressed'],'false','opening details pauses playback');
    click('replay-play');
    click('replay-prev');assert.equal(nodes.get('step').value,0);
    assert.equal(nodes.get('replay-play').attrs['aria-pressed'],'false');
    click('replay-next');click('replay-play');
    await new Promise(resolve=>setTimeout(resolve,300));
    assert.equal(nodes.get('step').value,2);
    assert.equal(nodes.get('replay-play').attrs['aria-pressed'],'false');
    click('replay-play');assert.equal(nodes.get('step').value,0);
    click('batches',{show:'batch-b'});
    assert.equal(nodes.get('replay-panel').hidden,true);
    assert.equal(nodes.get('replay-play').attrs['aria-pressed'],'false');
    await new Promise(resolve=>setImmediate(resolve));
  }finally{
    windowEvents.get('pagehide')?.();
    Object.assign(globalThis,before);
  }
});
