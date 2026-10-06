import test from 'node:test';
import assert from 'node:assert/strict';
import * as view from '../../agent/evaluation-view.mjs';
test('history actions require terminal state and archived rows keep export access',()=>{
 assert.equal(typeof view.historyActions,'function');
 for(const status of ['RUNNING','QUEUED','PAUSED'])assert.deepEqual(view.historyActions({status}),[]);
 assert.deepEqual(view.historyActions({status:'COMPLETED'}),['ARCHIVE','DELETE']);
 assert.deepEqual(view.historyActions({status:'CANCELLED',archived:true}),['DELETE']);
});
