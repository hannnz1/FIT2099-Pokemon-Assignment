import test from 'node:test';import assert from 'node:assert/strict';import * as effects from '../../agent/battle-effects.mjs';
const old={taskId:'a',trace:[]};const row={index:1,round:1,actions:[{side:1,type:'MOVE',moveId:'POUND',damage:4,eligible:true,outcome:'RESOLVED'},{side:0,type:'MOVE',moveId:'ABSORB',damage:0,eligible:true,outcome:'MISSED'},{side:0,type:'RESIDUAL',damage:2,status:'POISON'}]};
test('only new confirmed actions, actual order, no history replay or HP invented moves',()=>{
 assert.equal(typeof effects.battleEvents,'function');const next={taskId:'a',trace:[row]};assert.deepEqual(effects.battleEvents(null,next),[]);assert.deepEqual(effects.battleEvents(next,next),[]);assert.deepEqual(effects.battleEvents(old,{...next,taskId:'b'}),[]);
 const events=effects.battleEvents(old,next);assert.deepEqual(events.map(e=>e.kind),['move','miss','residual']);assert.deepEqual(events.map(e=>e.side),[1,0,0]);assert.deepEqual(effects.battleEvents(old,{...old,world:{hp:0}}),[]);
});
test('bounded queue preserves order, duplicate suppression and cancellation',async()=>{
 assert.equal(typeof effects.createEffectQueue,'function');let release;const seen=[];const q=effects.createEffectQueue({limit:2,play:(event,signal)=>{seen.push(event.id);return new Promise(r=>{release=r;signal.addEventListener('abort',r,{once:true});});}});
 q.enqueue([{id:'a'},{id:'b'},{id:'c'},{id:'d'}]);assert.equal(q.pending,2);release();await new Promise(r=>setImmediate(r));assert.deepEqual(seen,['a','c']);q.enqueue([{id:'a'}]);q.clear();await new Promise(r=>setImmediate(r));assert.equal(q.pending,0);assert.deepEqual(seen,['a','c']);
});
