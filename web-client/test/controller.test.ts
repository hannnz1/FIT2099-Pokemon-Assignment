import { test } from 'node:test';
import assert from 'node:assert/strict';
import { GameController } from '../src/api/controller.ts';
const snap=(revision=0)=>({gameId:'g',revision,turn:revision,phase:'READY',map:{version:'v'},availableActions:[]});
test('only one pending action; response loss retries the exact same request',async()=>{
 const calls:any[]=[];let attempt=0;
 const c=new GameController(async(path,options)=>{calls.push(JSON.parse(options.body));if(attempt++===0)throw Error('network');return {snapshot:snap(1),replayed:true,events:[]};},async()=>{});
 c.snapshot=snap() as any;c.state='READY';
 await c.act('wait');assert.equal(c.state,'ERROR');await c.act('other');assert.equal(calls.length,1);
 await c.retry();assert.deepEqual(calls[0],calls[1]);assert.equal(c.snapshot.revision,1);assert.equal(c.state,'READY');
});
test('stale snapshots cannot overwrite current state; replay skips animation',async()=>{
 let animations=0;const c=new GameController(async()=>({snapshot:snap(2),replayed:true,events:[]}),async()=>{animations++;});c.snapshot=snap(3) as any;c.state='READY';await c.act('wait');assert.equal(c.snapshot.revision,3);assert.equal(animations,0);
});
test('input is locked during request and animation',async()=>{
 let resolve:any;let calls=0;const c=new GameController(async()=>{calls++;return await new Promise(r=>resolve=r);},async()=>{});c.snapshot=snap() as any;c.state='READY';
 const p=c.act('wait');await c.act('wait');assert.equal(calls,1);resolve({snapshot:snap(1),replayed:false,events:[]});await p;assert.equal(c.state,'READY');
});
