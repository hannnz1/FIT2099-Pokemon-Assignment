import test from 'node:test';
import assert from 'node:assert/strict';
import {createMovementInput} from '../../agent/movement-input.mjs';
const fixture=()=>{const sent=[],timers=[];let resolve;let allowed=true;
 const c=createMovementInput({send:d=>{sent.push(d);return new Promise(r=>resolve=r);},canSend:()=>allowed,schedule:f=>{timers.push(f);return f;},cancel:f=>{const i=timers.indexOf(f);if(i>=0)timers.splice(i,1);}});
 return {c,sent,timers,settle:async()=>{resolve();await Promise.resolve();await Promise.resolve();},setAllowed:v=>allowed=v};};
test('one in-flight request and one latest turn intent; release cancels queued movement',async()=>{
 const f=fixture();f.c.press('N');f.c.press('E');f.c.press('S');assert.deepEqual(f.sent,['N']);
 await f.settle();f.timers.shift()();assert.deepEqual(f.sent,['N','S']);
 f.c.release('S');await f.settle();assert.equal(f.timers.length,0);assert.equal(f.sent.length,2);
});
test('held movement repeats after acknowledgement, clearing and loss of control stop repeats',async()=>{
 const f=fixture();f.c.press('N');await f.settle();assert.equal(f.timers.length,1);
 f.setAllowed(false);f.timers.shift()();assert.deepEqual(f.sent,['N']);
 f.setAllowed(true);f.c.press('W');f.c.clear();await f.settle();assert.equal(f.timers.length,0);
});
test('tap submits once; rejection does not create an automatic retry',async()=>{
 let count=0;const timers=[];const c=createMovementInput({send:()=>{count++;return Promise.reject(new Error('offline'));},schedule:f=>timers.push(f),cancel:()=>{}});
 c.tap('E');await Promise.resolve();await Promise.resolve();assert.equal(count,1);assert.equal(timers.length,0);
});

test('a new direction after releasing in-flight movement survives the old acknowledgement',async()=>{const f=fixture();f.c.press('N');f.c.release('N');f.c.press('E');assert.deepEqual(f.sent,['N']);await f.settle();assert.equal(f.timers.length,1);f.timers.shift()();assert.deepEqual(f.sent,['N','E']);f.c.release('E');await f.settle();assert.equal(f.timers.length,0);});
