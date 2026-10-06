import test from 'node:test';
import assert from 'node:assert/strict';
import {reconcileEntities} from '../../agent/scene-entities.mjs';
import {movementTransition} from '../../agent/motion-state.mjs';

test('entity changes preserve identity through evolution and remove only departed IDs',()=>{
 const a={id:'a',species:'MUDKIP',x:1,y:2,hp:20};
 assert.deepEqual(reconcileEntities([a],[{...a}]),{added:[],changed:[],removed:[]});
 const evolved={...a,species:'MARSHTOMP',x:2};
 const diff=reconcileEntities([a,{id:'b'}],[evolved,{id:'c'}]);
 assert.deepEqual(diff,{added:[{id:'c'}],changed:[evolved],removed:[{id:'b'}]});
});
test('motion accepts confirmed adjacent steps but snaps unknown paths, ignores stale turns',()=>{
 const a={region:'forest',turn:2,x:1,y:2};
 assert.equal(movementTransition(null,a),'SNAP');
 assert.equal(movementTransition(a,{...a,turn:3,x:2}),'STEP');
 assert.equal(movementTransition(a,{...a,turn:4,x:4}),'SNAP');
 assert.equal(movementTransition(a,{...a,turn:1,x:2}),'IGNORE');
 assert.equal(movementTransition(a,{...a,turn:2,x:2}),'IGNORE');
 assert.equal(movementTransition(a,{...a,turn:3,region:'river'}),'REGION_CHANGE');
 assert.equal(movementTransition(a,{...a,turn:3}),'IGNORE');
});
