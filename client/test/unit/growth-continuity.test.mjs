import test from 'node:test';import assert from 'node:assert/strict';
import * as growth from '../../agent/growth-view.mjs';
import {agentPresentation} from '../../agent/game-ui.mjs';
const world=(region='river')=>({region,expeditionRegion:'mountain',partner:{captureId:'partner',level:7,levelCap:40},encounters:[{id:region+'-1-0',region,state:'WILD',name:'目标'}]});
const view=(overrides={})=>({taskId:'task',status:'RUNNING',targetIndividual:'partner',targetRegion:'mountain',targetLevel:12,world:world(),stepTrace:[],...overrides});
test('cross-map training configuration keeps the original task rather than current level plus one',()=>{
 assert.deepEqual(growth.trainingConfig(view(),{region:'forest',level:8}),{region:'mountain',level:12,retained:true});
 assert.deepEqual(growth.trainingConfig(view({status:'PAUSED'}),{region:'forest',level:8}),{region:'mountain',level:12,retained:true});
 assert.deepEqual(growth.trainingConfig(view({status:'COMPLETED'}),{region:'forest',level:8}),{region:'forest',level:8,retained:false});
});
test('AI target follows its confirmed current-task action without a player click',()=>{
 const step={taskId:'task',toolName:'move_to',arguments:{targetId:'river-1-0'},actionStatus:'IN_PROGRESS'};
 assert.equal(growth.growthTargetId(view({stepTrace:[step]}),'forest-1-0'),'river-1-0');
 assert.equal(growth.growthTargetId(view({stepTrace:[{...step,taskId:'old'}]}),'river-1-0'),null);
 assert.equal(growth.growthTargetId(view({stepTrace:[{...step,actionStatus:'REJECTED'}]}),null),null);
 assert.equal(growth.growthTargetId(view({world:world('mountain'),stepTrace:[step]}),null),null);
 assert.equal(growth.growthTargetId(view({status:'IDLE'}),'river-1-0'),'river-1-0');
});
test('after map transition awaiting AI explicitly says no reselection is needed and retains region',()=>{
 const text=growth.growthTargetHint(view());assert.match(text,/无需.*点击/);assert.match(agentPresentation(view()).detail,/赤岩山道/);
 assert.match(growth.growthTargetHint(view({status:'PAUSED'})),/暂停/);
});
