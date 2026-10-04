import test from 'node:test';import assert from 'node:assert/strict';
import {agentPresentation,pokemonPresentation,growthGuide} from '../../agent/game-ui.mjs';
test('AI feedback distinguishes thinking, approval, pause and actual completion',()=>{
 for(const status of ['PARSING','RUNNING','REPLANNING'])assert.equal(agentPresentation({status,aiAvailable:true}).working,true);
 assert.equal(agentPresentation({status:'READY',aiAvailable:true}).working,false);
 assert.match(agentPresentation({status:'WAITING_APPROVAL',aiAvailable:true}).detail,/批准|拒绝/);
 assert.match(agentPresentation({status:'PAUSED',aiAvailable:true}).detail,/继续/);
 assert.match(agentPresentation({status:'COMPLETED',aiAvailable:true}).label,/完成/);
 assert.equal(agentPresentation({status:'RUNNING',aiAvailable:true},false).working,false);
 assert.match(agentPresentation({status:'IDLE',aiAvailable:false}).detail,/手动/);
});
test('progress reflects committed level or steps without simulated percentage',()=>{
 const a=agentPresentation({status:'RUNNING',aiAvailable:true,targetLevel:8,world:{partner:{level:6}},metrics:{totalSteps:3}});
 assert.match(a.detail,/6.*8/);assert.equal(a.steps,3);assert.equal('percent' in a,false);
 assert.equal(agentPresentation({status:'CANCELLED',aiAvailable:true}).phase,'stopped');
});
test('partner cards preserve real HP and never invent legacy levels',()=>{
 const p=pokemonPresentation({species:'TREECKO',hp:4,maxHp:19,level:5,experience:135,nextLevelExperience:179});assert.equal(p.name,'木守宫');assert.equal(p.hpText,'4 / 19');assert.equal(p.health,'low');assert.equal(p.experienceRemaining,44);
 assert.equal(pokemonPresentation({species:'MUDKIP',hp:10,maxHp:100}).level,null);
 assert.equal(pokemonPresentation({species:'TORCHIC',hp:0,maxHp:20}).condition,'需要恢复');
});
test('next step prioritizes offline, storage and fainted partner over battle',()=>{
 const v={status:'IDLE',world:{region:'forest',partner:{hp:0},starterAvailable:true}};
 assert.match(growthGuide(v).detail,/恢复/);assert.match(growthGuide(v,false).detail,/连接/);
 assert.match(growthGuide({...v,status:'STORAGE_ERROR'}).detail,/存档/);
 assert.match(growthGuide({status:'IDLE',world:{region:'lab',starterAvailable:true}}).detail,/选择/);
 assert.match(growthGuide({status:'IDLE',world:{region:'forest',partner:{hp:19}}},true, {targetAdjacent:false}).detail,/接近/);
});
test('exported growth collection shows actual level while legacy stays unknown',()=>{assert.equal(pokemonPresentation({species:'TREECKO',hp:19,maxHp:19,growth:{species:'TREECKO',experience:135,hp:19,pp:{POUND:35}}}).level,5);assert.equal(pokemonPresentation({species:'MUDKIP',hp:40,maxHp:100}).level,null);});
test('failed quest panel explains the actual expired deadline instead of generic advice',()=>{
 const p=agentPresentation({status:'FAILED',errorCode:'QUEST_EXPIRED',world:{turn:'60',deadlineTurn:'60',questStatus:'EXPIRED'}});
 assert.match(p.detail,/60/);assert.match(p.detail,/截止|到期/);
});
