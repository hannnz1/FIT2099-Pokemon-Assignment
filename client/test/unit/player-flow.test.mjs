import test from 'node:test';
import assert from 'node:assert/strict';
import {targetPanelState} from '../../agent/player-polish.mjs';
import {questWorkspace,battleWorkspace} from '../../agent/player-frame.mjs';
test('new player receives a partner choice instruction before target selection',()=>{
 assert.equal(targetPanelState(null,false,{world:{region:'lab',starterAvailable:true}}).hint,'先选择与你同行的伙伴');
 assert.equal(targetPanelState(null,false,{world:{region:'lab',starterAvailable:false}}).hint,'打开队伍，接回同行伙伴');
});
test('quest interruption preserves execution workspace and completed tasks use results',()=>{
 for(const status of ['RUNNING','PAUSED','WAITING_APPROVAL','PROVIDER_UNAVAILABLE'])assert.equal(questWorkspace(status),'running');
 assert.equal(questWorkspace('READY'),'confirm');
 for(const status of ['COMPLETED','FAILED','CANCELLED'])assert.equal(questWorkspace(status),'result');
 assert.equal(questWorkspace('ERROR'),'setup');
});
test('battle only shows an arena and outcome after an actual team exists',()=>{
 assert.equal(battleWorkspace({status:'IDLE',world:{ownTeam:[]}}).arena,false);
 assert.equal(battleWorkspace({status:'COMPLETED',world:{ownTeam:[]}}).outcome,false);
 const world={ownTeam:[{name:'水跃鱼'}]};
 assert.deepEqual(battleWorkspace({status:'RUNNING',world}),{arena:true,preparation:false,outcome:false});
 assert.equal(battleWorkspace({status:'CANCELLED',world}).outcome,true);
});
