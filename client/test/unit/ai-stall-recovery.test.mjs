import test from 'node:test';
import assert from 'node:assert/strict';
import {agentPresentation} from '../../agent/game-ui.mjs';
import {failureRecovery} from '../../agent/failure-recovery.mjs';
test('automatic pause explains path or progress problem and offers resume without resetting world',()=>{
 for(const errorCode of ['NAVIGATION_REQUIRES_HELP','NO_PROGRESS_REQUIRES_HELP']){
  const view={status:'PAUSED',errorCode};const p=agentPresentation(view);
  assert.equal(p.tone,'warning');assert.equal(p.working,false);assert.match(p.detail,/通路|进展/);
  const recovery=failureRecovery(view);
  assert.equal(recovery.visible,true);assert.match(recovery.detail,/保留背包与交付进度/);
  assert.deepEqual(recovery.actions.map(a=>a.command),['RESUME','SHOW_TRACE','CANCEL']);
 }
});
test('ordinary manual pause stays quiet while pending request and disconnection override recovery',()=>{
 const view={status:'PAUSED',errorCode:'NO_PROGRESS_REQUIRES_HELP'};
 assert.equal(failureRecovery({status:'PAUSED'}).visible,false);
 assert.deepEqual(failureRecovery(view,'quest',false).actions.map(a=>a.command),['RECONNECT']);
 assert.deepEqual(failureRecovery(view,'quest',true,false,true).actions.map(a=>a.command),['RETRY_PENDING']);
 assert.ok(failureRecovery(view,'quest',true,true).actions.every(a=>a.disabled));
});
test('storage recovery displays only finite failure categories without echoing raw errors',()=>{
 const io=failureRecovery({status:'STORAGE_ERROR',storageFailureCode:'IO_ERROR'},'growth');
 assert.match(io.detail,/文件读写/);assert.deepEqual(io.actions.map(a=>a.command),['RECOVER']);
 const unknown=failureRecovery({status:'STORAGE_ERROR',storageFailureCode:'private-path-and-key'},'growth');
 assert.doesNotMatch(unknown.detail,/private-path-and-key/);
});
