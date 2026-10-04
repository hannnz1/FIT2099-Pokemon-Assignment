import test from 'node:test';
import assert from 'node:assert/strict';
import {fieldPresentation} from '../../agent/battle-view.mjs';
const view=()=>({collectionControlAllowed:true,wildActionAllowed:true,combatPartner:'owned',combatPartnerState:{species:'MUDKIP',x:31,y:10,hp:30,maxHp:38,level:16,experience:2535,growth:{},moves:[{id:'WATER_GUN',name:'水枪',pp:2,maxPp:25},{id:'TACKLE',name:'撞击',pp:0,maxPp:35}]},wild:[{id:'target',species:'TREECKO',x:32,y:11,hp:19,maxHp:19,state:'WILD'}]});
test('growth field controls show real PP and prevent legacy attack bypass',()=>{
 const p=fieldPresentation(view(),'target');assert.equal(p.canAttack,false);assert.match(p.detail,/Lv.16.*EXP 2535/);assert.equal(p.skills[0].enabled,true);assert.equal(p.skills[1].enabled,false);assert.match(p.skills[0].label,/2\/25 PP/);
});
test('skills reject unavailable targets, restrictions, zero HP and locked control',()=>{
 for(const change of [v=>v.battleForbidden=true,v=>v.constraints=['NO_ACTIVE_BATTLE'],v=>v.collectionControlAllowed=false,v=>v.wildActionAllowed=false,v=>v.combatPartnerState.hp=0,v=>v.combatPartnerState.x=29,v=>v.wild[0].state='DEFEATED']){const v=view();change(v);assert.ok(fieldPresentation(v,'target').skills.every(s=>!s.enabled));}
});
test('legacy partner keeps original attack controls and gets no invented skills',()=>{
 const v=view();delete v.combatPartnerState.growth;delete v.combatPartnerState.moves;assert.equal(fieldPresentation(v,'target').canAttack,true);assert.deepEqual(fieldPresentation(v,'target').skills,[]);
});
