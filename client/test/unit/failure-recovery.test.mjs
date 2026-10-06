import test from 'node:test';import assert from 'node:assert/strict';
import {failureRecovery,mountFailureRecovery} from '../../agent/failure-recovery.mjs';
test('failed quest exposes explicit fresh round and accurately describes map reset',()=>{const r=failureRecovery({status:'FAILED',errorCode:'QUEST_EXPIRED',world:{turn:'60'}},'quest');assert.equal(r.visible,true);assert.match(r.detail,/收藏|地图/);assert.equal(r.actions[0].command,'RESET');assert.equal(r.actions[0].disabled,false);});
test('waiting for a receipt never permits reset, recovery keeps same request',()=>{const r=failureRecovery({status:'FAILED'},'quest',true,false,true);assert.equal(r.actions[0].command,'RETRY_PENDING');assert.equal(r.actions.some(a=>a.command==='RESET'&&!a.disabled),false);});
test('growth failure preserves progress and offers actual rest or return rather than reset',()=>{const r=failureRecovery({status:'FAILED',aiAvailable:true,world:{region:'forest',partner:{hp:0,level:8,levelCap:40},canRest:false}},'growth');assert.equal(r.actions[0].command,'RETURN_LAB');assert.equal(r.actions.some(a=>a.command==='RESET'),false);assert.match(r.detail,/保留/);});
test('offline and storage failure require reconnection or recovery before gameplay',()=>{assert.equal(failureRecovery({status:'FAILED'},'training',false).actions[0].command,'RECONNECT');assert.equal(failureRecovery({status:'STORAGE_ERROR'},'quest').actions[0].command,'RECOVER');assert.equal(failureRecovery({status:'RUNNING'},'quest').visible,false);});
test('provider outage gives resume cancel choices; unknown reason is safe text',()=>{const r=failureRecovery({status:'PROVIDER_UNAVAILABLE'},'quest');assert.deepEqual(r.actions.map(a=>a.command),['RESUME','CANCEL']);assert.match(failureRecovery({status:'FAILED',errorCode:'<script>'},'quest').reason,/未完成/);});

test('repeated render restores retry permission after page-wide button disabling',()=>{
 const previous=globalThis.document;
 const element=()=>({children:[],setAttribute(){},replaceChildren(){this.children=[];},append(...children){this.children.push(...children);},addEventListener(){},querySelectorAll(){return this.children.flatMap(c=>c.children??[]).filter(c=>c.type==='button');}});
 globalThis.document={createElement:element};
 try{const host=element(),mounted=mountFailureRecovery(host,()=>{});mounted.update({status:'FAILED'},'quest',true,false,true);const button=host.querySelectorAll('button')[0];assert.equal(button.disabled,false);button.disabled=true;mounted.update({status:'FAILED'},'quest',true,false,true);assert.equal(button.disabled,false);mounted.update({status:'FAILED'},'quest',true,true,true);assert.equal(host.hidden,true);assert.equal(host.querySelectorAll('button').length,0);}
 finally{globalThis.document=previous;}
});

test('exhausted room avoids offering a reset that the budget would reject',()=>{const r=failureRecovery({status:'FAILED',clientError:'REQUEST_LIMIT',storage:'PERSISTENT'});assert.equal(r.actions.some(a=>a.command==='RESET'),false);assert.match(r.detail,/重启/);const memory=failureRecovery({clientError:'REQUEST_LIMIT',storage:'MEMORY'});assert.match(memory.detail,/丢失/);});

test('quest FILE and POSTGRESQL snapshots give persistent restart guidance',()=>{for(const storage of ['FILE','POSTGRESQL']){const r=failureRecovery({clientError:'REQUEST_LIMIT',storage});assert.match(r.detail,/读取原有存档/);assert.doesNotMatch(r.detail,/内存模式/);}});
test('legacy failures recover the reason from the terminal trace',()=>{assert.match(failureRecovery({status:'FAILED',trace:[{code:'LOOP_LIMIT',status:'FAILED'}]}).reason,/上限/);});

test('budget stop never offers paid resume, keeps actual delivery progress',()=>{
 const r=failureRecovery({status:'PROVIDER_UNAVAILABLE',errorCode:'PROVIDER_BUDGET_EXHAUSTED',delivered:2,world:{requiredBerry:3,carriedBerry:1}},'quest');
 assert.equal(r.category,'website-budget');assert.equal(r.actions.some(a=>a.command==='RESUME'),false);assert.match(r.progress,/2.*3/);assert.match(r.progress,/1/);
});
test('provider quota and timeout remain distinct; unknown errors are not raw output',()=>{
 const quota=failureRecovery({status:'PROVIDER_UNAVAILABLE',errorCode:'PROVIDER_QUOTA_EXHAUSTED'});assert.equal(quota.category,'provider-quota');assert.equal(quota.actions.some(a=>a.command==='RESUME'),false);
 assert.equal(failureRecovery({status:'PROVIDER_UNAVAILABLE',errorCode:'PROVIDER_TIMEOUT'}).category,'timeout');assert.equal(failureRecovery({status:'FAILED',errorCode:'secret/raw/path'}).category,'unknown');
});

test('exhausted resume allowance explains the limit and keeps manual progress',()=>{const r=failureRecovery({status:'PAUSED',errorCode:'NO_PROGRESS_REQUIRES_HELP',resumeRemaining:0,resumeLimit:3,manualAllowed:true,delivered:0,world:{requiredBerry:3,carriedBerry:0}});assert.equal(r.actions.some(a=>a.command==='RESUME'),false);assert.match(r.detail,/3/);assert.match(r.detail,/手动/);assert.equal(r.actions.some(a=>a.command==='CANCEL'),true);});
