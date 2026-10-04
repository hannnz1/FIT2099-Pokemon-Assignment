import test from 'node:test';
import assert from 'node:assert/strict';
const shell=await import('../../agent/growth-shell.mjs').catch(()=>null);
test('HUD distinguishes unconfirmed saves, storage errors and disconnected last-known data',()=>{
 assert.ok(shell,'growth shell is not implemented');
 const v={storage:'PERSISTENT',status:'IDLE',world:{turn:5,team:[]}};
 assert.equal(shell.shellStatus(v,true,false,false).save,'自动存档已同步');
 assert.equal(shell.shellStatus(v,true,true,false).save,'正在确认操作…');
 assert.equal(shell.shellStatus(v,true,false,true).save,'操作未确认');
 assert.equal(shell.shellStatus({...v,status:'STORAGE_ERROR'},true,false,false).save,'存档失败，请恢复');
 assert.equal(shell.shellStatus(v,false,false,false).save,'离线 · 显示上次进度');
});
test('growth bag lists only actual stored partners and reports its capture rule',()=>{
 assert.ok(shell,'growth shell is not implemented');
 const w={partner:{captureId:'a'},team:[{captureId:'a',name:'木守宫'},{captureId:'b',name:'水跃鱼'}]};
 assert.deepEqual(shell.bagPartners(w).map(p=>p.captureId),['b']);
 assert.deepEqual(shell.bagPartners({}),[]);
});
test('HUD follows authoritative turn and training progress including completion',()=>{
 assert.ok(shell,'growth shell is not implemented');
 const v={status:'RUNNING',targetLevel:20,world:{turn:9,partner:{level:18},team:[]}};
 assert.equal(shell.shellStatus(v,true,false,false).period,'夜晚');
 assert.equal(shell.shellStatus({...v,world:{...v.world,turn:10}},true,false,false).period,'白昼');
 assert.match(shell.shellStatus(v,true,false,false).task,/18.*20/);
 assert.match(shell.shellStatus({...v,status:'COMPLETED'},true,false,false).task,/完成/);
});
test('clean shell reveals battle actions only for living wild targets and retains critical feedback',()=>{
 assert.equal(shell.explorationControls(null).skills,false);
 assert.deepEqual(shell.explorationControls({state:'WILD',hp:10,targetAdjacent:false}),{skills:true,approach:true,capture:true});
 assert.equal(shell.explorationControls({state:'WILD',hp:10,targetAdjacent:true}).approach,false);
 assert.equal(shell.explorationControls({state:'CAPTURED',hp:10}).skills,false);
 assert.equal(shell.transientFeedback('成长模式已连接，进度以服务器存档为准'),true);
 assert.equal(shell.transientFeedback('技能回合已结算'),true);
 assert.equal(shell.transientFeedback('存档失败，请恢复'),false);
 assert.equal(shell.transientFeedback('捕捉未成功，目标进行了反击'),false);
});
test('navigation lists current, unlocked and locked regions with distinct reasons',()=>{
 const view={manualAllowed:true,status:'IDLE',world:{region:'lab',partner:{hp:20},canRest:true,regions:[{id:'lab',name:'研究室',unlocked:true},{id:'forest',name:'苔叶森林',unlocked:true},{id:'river',name:'浅溪河岸',unlocked:false}]}};
 const nav=shell.navigationPresentation(view,{online:true});
 assert.equal(nav.current,'研究室');
 assert.deepEqual(nav.regions.map(r=>[r.status,r.enabled]),[['当前',false],['已解锁',true],['未解锁',false]]);
 assert.match(nav.regions[2].reason,/前一区域/);
 assert.equal(nav.recovery.reason,'已在恢复点');
});
test('navigation explains connection, storage, pending actions and AI ownership',()=>{
 const view={manualAllowed:true,status:'IDLE',world:{region:'forest',partner:{hp:20},canRest:false,regions:[{id:'river',unlocked:true}]}};
 for(const [v,context,reason] of [[view,{online:false},'请先重新连接'],[{...view,status:'STORAGE_ERROR'},{online:true},'请先恢复存档'],[view,{online:true,pending:true},'正在核对上一条操作，请稍候'],[{...view,status:'RUNNING'},{online:true},'AI 正在培养，请先暂停或取消委托'],[view,{online:true,returning:true},'正在自动返程，请稍候']]){
  const nav=shell.navigationPresentation(v,context);
  assert.equal(nav.regions[0].enabled,false);assert.equal(nav.regions[0].reason,reason);assert.equal(nav.recovery.reason,reason);
 }
});
test('navigation allows a fainted partner to recover and explains missing partners',()=>{
 const view={manualAllowed:true,status:'IDLE',world:{region:'forest',partner:{hp:0},canRest:false,regions:[{id:'lab',unlocked:true}]}};
 assert.equal(shell.navigationPresentation(view,{online:true}).recovery.enabled,true);
 const missing=shell.navigationPresentation({...view,world:{...view.world,partner:null}},{online:true});
 assert.equal(missing.recovery.reason,'请先选择伙伴');assert.equal(missing.regions[0].enabled,false);
});
