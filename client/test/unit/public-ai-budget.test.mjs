import test from 'node:test';
import assert from 'node:assert/strict';
import {failureRecovery} from '../../agent/failure-recovery.mjs';
import {agentPresentation} from '../../agent/game-ui.mjs';
test('public AI exhausted allowance explains the cause and preserves cancellation',()=>{
 const v={status:'PROVIDER_UNAVAILABLE',errorCode:'PROVIDER_BUDGET_EXHAUSTED'};
 assert.match(agentPresentation(v).detail,/额度/);
 const recovery=failureRecovery(v,'growth');
 assert.match(recovery.detail,/手动/);
 assert.ok(recovery.actions.some(a=>a.command==='CANCEL'&&!a.disabled));
});
