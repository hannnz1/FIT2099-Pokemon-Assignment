import test from 'node:test';
import assert from 'node:assert/strict';
import {carryPresentation,summonPresentation} from '../../agent/battle-view.mjs';
import {projectRpg} from '../../agent/rpg-view.mjs';
test('evolved collection shows correct species level and real max HP',()=>{
 const view={collectionControlAllowed:true,collection:[{captureId:'11111111-1111-1111-1111-111111111111',species:'SWAMPERT',hp:125,maxHp:136,growth:{experience:56660},state:'IN_BALL'}]};
 assert.match(carryPresentation(view).label,/巨沼怪 Lv.40/);assert.match(carryPresentation(view).label,/125 \/ 136 HP/);assert.match(summonPresentation(view).options[0].label,/Lv.40/);
});
test('evolved summoned sprite uses evolved species and actual HP',()=>{
 const view={world:{x:'29',y:'10'},map:{width:59,height:13},summoned:{captureId:'owned',species:'SWAMPERT',x:30,y:10,hp:125,maxHp:136},locations:{},tiles:[]};
 const out=projectRpg(view).entities.find(e=>e.id==='owned');assert.equal(out.species,'SWAMPERT');assert.equal(out.name,'我的巨沼怪');assert.equal(out.maxHp,136);
});
