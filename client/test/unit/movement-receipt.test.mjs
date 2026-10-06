import test from 'node:test';
import assert from 'node:assert/strict';
import {failureRecovery} from '../../agent/failure-recovery.mjs';
import {shellStatus} from '../../agent/growth-shell.mjs';
const view={status:'IDLE',storage:'PERSISTENT',world:{turn:3}};
test('active movement receipt is processing, not an unconfirmed operation',()=>{
 assert.equal(failureRecovery(view,'growth',true,true,true).visible,false);
 assert.equal(shellStatus(view,true,true,true).save,'正在确认操作…');
});
test('unresolved receipt still exposes retry once request has stopped',()=>{
 const state=failureRecovery(view,'growth',true,false,true);
 assert.equal(state.visible,true);
 assert.deepEqual(state.actions.map(a=>a.command),['RETRY_PENDING']);
 assert.equal(shellStatus(view,true,false,true).save,'操作未确认');
});
test('transport failure retains reconnect and original receipt recovery',()=>{
 assert.deepEqual(failureRecovery(view,'growth',false,false,true).actions.map(a=>a.command),['RECONNECT']);
});
