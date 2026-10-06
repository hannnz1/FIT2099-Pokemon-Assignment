import test from 'node:test';
import assert from 'node:assert/strict';
import {targetPanelState,cameraViewport,parseTrainingRequest} from '../../agent/player-polish.mjs';
test('empty and resolved targets collapse; practice stays actionable',()=>{
 assert.equal(targetPanelState(null,false).active,false);
 assert.equal(targetPanelState({state:'DEFEATED',hp:0},false).active,false);
 assert.equal(targetPanelState(null,true).active,true);
 assert.equal(targetPanelState({state:'WILD',hp:10,targetAdjacent:false},false).hint,'靠近目标后可使用技能或捕捉');
});
test('mobile camera shows fewer cells instead of shrinking desktop map',()=>{
 assert.deepEqual(cameraViewport(390),{width:448,height:448});
 assert.deepEqual(cameraViewport(1280),{width:832,height:576});
});
test('local goal preview validates level and unlocked region without model calls',()=>{
 const p={level:5,levelCap:40},regions=[{id:'forest',unlocked:true},{id:'river',unlocked:false}];
 assert.deepEqual(parseTrainingRequest('在苔叶森林培养到10级',p,regions,'forest'),{ok:true,level:10,region:'forest'});
 assert.equal(parseTrainingRequest('在浅溪河岸练到12级',p,regions,'forest').ok,false);
 assert.equal(parseTrainingRequest('升到50级',p,regions,'forest').ok,false);
 assert.equal(parseTrainingRequest('收集树果并交付',p,regions,'forest').ok,false);
 assert.equal(parseTrainingRequest('培养到10级并捕捉木守宫',p,regions,'forest').ok,false);
});

test('malformed numbers and additional intent never silently become a training goal',()=>{
 const p={level:5,levelCap:40},regions=[{id:'forest',unlocked:true}];
 for(const request of ['培养到-10级','培养到10.5级','培养到01000级','卖出伙伴并培养到10级','不要培养到10级','培养到10级再到20级'])assert.equal(parseTrainingRequest(request,p,regions,'forest').ok,false,request);
});
