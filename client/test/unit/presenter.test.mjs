import test from 'node:test';
import assert from 'node:assert/strict';
import { BattlePresenter } from '../../src/integration/battle-presenter.js';
test('confirmed events play once and cancelling prevents queued old events',async()=>{
 const seen=[];let resume;const p=new BattlePresenter(async e=>{seen.push(e.eventId);await new Promise(r=>resume=r);});
 const task=p.play([{eventId:'a'},{eventId:'b'}]);p.cancel();resume();await task;assert.deepEqual(seen,['a']);
 const next=p.play([{eventId:'a'},{eventId:'c'}]);resume();await next;assert.deepEqual(seen,['a','c']);
});
