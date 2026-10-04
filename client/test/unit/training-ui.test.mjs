import {test} from 'node:test';
import assert from 'node:assert/strict';
const training=await import('../../agent/battle.mjs');
const view={world:{x:1,y:1,actorHp:'950'},targets:[{id:'wild-treecko',hp:80,state:'WILD',x:2,y:1}],captured:[],collection:[],status:'IDLE',manualAllowed:true};
test('training target guidance uses actual adjacency and target state',()=>{
 assert.equal(typeof training.trainingPresentation,'function');
 assert.equal(training.trainingPresentation(view,'wild-treecko').adjacent,true);
 assert.match(training.trainingPresentation(view,'wild-treecko').detail,/相邻/);
 assert.match(training.trainingPresentation({...view,targets:[{...view.targets[0],x:7}]},'wild-treecko').detail,/接近/);
 assert.match(training.trainingPresentation({...view,targets:[{...view.targets[0],state:'CAPTURED'}],captured:['wild-treecko']},'wild-treecko').detail,/收藏/);
});
test('training confirmation keeps real target and no battle limit',()=>{
 assert.equal(typeof training.trainingPresentation,'function');
 assert.match(training.trainingPresentation({...view,intent:{goal:'CAPTURE',targetId:'wild-treecko',noBattle:true}},'wild-treecko').confirmation,/木守宫.*不主动战斗/);
 assert.match(training.trainingPresentation({...view,intent:{goal:'DEFEAT',targetId:'wild-torchic',noBattle:false}},'wild-treecko').confirmation,/火稚鸡.*允许主动战斗/);
});
test('training errors clear after a newer successful snapshot but retain failures in the same revision',()=>{
 assert.equal(typeof training.trainingError,'function');
 assert.equal(training.trainingError({revision:4,errorCode:null},{revision:3,message:'旧错误'}),'');
 assert.equal(training.trainingError({revision:3,errorCode:null},{revision:3,message:'本次失败'}),'本次失败');
 assert.match(training.trainingError({revision:4,errorCode:'OUT_OF_REACH'},null),/相邻/);
});

test('reconnection clears a transport failure even when snapshot revision is unchanged',()=>{
 const failure={revision:3,message:'连接失败',disconnected:true};
 assert.equal(training.trainingError({revision:3},failure,true),'');
 assert.equal(training.trainingError({revision:3},failure,false),'连接失败');
});
