import test from 'node:test';
import assert from 'node:assert/strict';
import {summonPresentation,carryPresentation} from '../../agent/battle-view.mjs';
const deployed={captureId:'a',x:29,y:9,hp:85,maxHp:100,following:false};
const view={collectionControlAllowed:true,collectionAvailable:true,collection:[],summoned:deployed};
test('follow controls require actual deployment and authoritative management control',()=>{
 const p=summonPresentation(view);assert.equal(p.canFollow,true);assert.equal(p.canStopFollow,false);assert.equal(p.followId,'a');
 for(const v of [{...view,summoned:null},{...view,collectionControlAllowed:false},{...view,collectionAvailable:false}])assert.equal(summonPresentation(v).canFollow,false);
});
test('following presentation exposes stop control and restored actual location',()=>{
 const p=summonPresentation({...view,summoned:{...deployed,following:true}});assert.equal(p.canFollow,false);assert.equal(p.canStopFollow,true);assert.match(p.detail,/跟随已开启/);assert.match(p.detail,/29, 9/);assert.match(p.detail,/85 \/ 100/);
 assert.equal(summonPresentation({...view,collectionControlAllowed:false,summoned:{...deployed,following:true}}).canStopFollow,false);
});
test('unavailable deployment projection never shows undefined spawn coordinates as actual location',()=>{
 assert.equal(carryPresentation({collection:[{species:'TREECKO',hp:85,maxHp:100,state:'DEPLOYED'}]}).label,'木守宫 · 85 / 100 HP · 已放出（位置待恢复）');
});
