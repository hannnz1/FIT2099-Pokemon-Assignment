import test from 'node:test';
import assert from 'node:assert/strict';
import { dialoguePresentation } from '../../agent/dialogue.mjs';
test('historic NPC claims preserve time and provenance instead of becoming current inventory',()=>{
 const view=dialoguePresentation({speakerId:'treecko',message:'请现场确认',historical:'true',turn:'1',memories:JSON.stringify([{x:2,y:1,quantity:3,occurredAt:0,source:'SELF_OBSERVATION'}])});
 assert.equal(view.name,'木守宫');assert.equal(view.historical,true);assert.match(view.clues[0],/第0回合/);assert.match(view.clues[0],/3个树果/);assert.match(view.clues[0],/亲眼观察/);
 assert.equal(view.coins,undefined);assert.equal(view.carriedBerry,undefined);
});
test('unknown speakers and malformed memories cannot create an NPC claim',()=>{
 assert.equal(dialoguePresentation({speakerId:'another-player',message:'secret'}),null);
 const view=dialoguePresentation({speakerId:'treecko',message:'没有有效线索',historical:'true',memories:'invalid'});assert.deepEqual(view.clues,[]);
 const forged=dialoguePresentation({speakerId:'treecko',message:'clue',historical:'true',memories:JSON.stringify([{x:2,y:1,quantity:3,occurredAt:0,source:'HIDDEN_WORLD_STATE'}])});assert.deepEqual(forged.clues,[]);
});
test('merchant replies are explicitly dated dialogue information, never a live shop projection',()=>{
 const view=dialoguePresentation({speakerId:'merchant',message:'现有4个树果',historical:'false',turn:'6'});
 assert.equal(view.asOfTurn,6);assert.equal(view.historical,false);assert.equal(view.merchantStock,undefined);
});
