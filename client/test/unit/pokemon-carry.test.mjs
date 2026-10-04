import test from 'node:test';
import assert from 'node:assert/strict';
import * as battle from '../../agent/battle-view.mjs';
test('carry button needs a real capture, idle control and available collection',()=>{
  assert.equal(typeof battle.carryPresentation,'function','carry presentation is missing');
  const view={manualAllowed:true,collectionAvailable:true,captured:['wild-treecko'],collection:[]};
  assert.equal(battle.carryPresentation(view).canTransfer,true);
  for(const blocked of [{...view,captured:[]},{...view,manualAllowed:false},{...view,collectionAvailable:false},{...view,collection:Array.from({length:20},()=>({species:'TREECKO',hp:100,maxHp:100}))}])assert.equal(battle.carryPresentation(blocked).canTransfer,false);
});
test('original-map collection shows actual species HP and multiple individuals',()=>{
  assert.equal(typeof battle.carryPresentation,'function','carry presentation is missing');
  assert.equal(battle.carryPresentation({collection:[]}).label,'还没有带回的宝可梦');
  assert.equal(battle.carryPresentation({collection:[{species:'TREECKO',hp:60,maxHp:100},{species:'TREECKO',hp:100,maxHp:100}]}).label,'木守宫 · 60 / 100 HP，木守宫 · 100 / 100 HP');
});
