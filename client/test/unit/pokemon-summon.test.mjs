import test from 'node:test';
import assert from 'node:assert/strict';
import * as battle from '../../agent/battle-view.mjs';
test('summon controls require owned ball and unlocked authoritative collection control',()=>{
  assert.equal(typeof battle.summonPresentation,'function','summon presentation is missing');
  const view={collectionControlAllowed:true,collectionAvailable:true,collection:[{captureId:'a',species:'TREECKO',hp:85,maxHp:100,state:'IN_BALL'}],summoned:null};
  assert.equal(battle.summonPresentation(view).canSummon,true);
  for(const v of [{...view,collection:[]},{...view,collectionControlAllowed:false},{...view,collectionAvailable:false}])assert.equal(battle.summonPresentation(v).canSummon,false);
});
test('deployed individual cannot be summoned twice and recall uses its identity',()=>{
  assert.equal(typeof battle.summonPresentation,'function','summon presentation is missing');
  const view={collectionControlAllowed:true,collectionAvailable:true,collection:[{captureId:'a',species:'TREECKO',hp:85,maxHp:100,state:'DEPLOYED'}],summoned:{captureId:'a',x:30,y:10,hp:85,maxHp:100}};
  const result=battle.summonPresentation(view);assert.equal(result.canSummon,false);assert.equal(result.canRecall,true);assert.equal(result.recallId,'a');assert.match(result.detail,/30, 10/);assert.match(result.detail,/85 \/ 100/);
  assert.equal(battle.summonPresentation({...view,collectionControlAllowed:false}).canRecall,false);
});
test('original map marker distinguishes owned Treecko and follows actual deployed position',()=>{
  assert.equal(typeof battle.summonedMarker,'function','summon marker is missing');
  const view={summoned:{captureId:'a',x:30,y:10,hp:85,maxHp:100}};
  assert.equal(battle.summonedMarker(view,30,10).label,'我的木守宫');assert.equal(battle.summonedMarker(view,29,10),null);assert.equal(battle.summonedMarker({summoned:null},30,10),null);
});
