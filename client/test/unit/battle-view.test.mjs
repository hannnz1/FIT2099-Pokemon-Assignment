import { test } from 'node:test';
import assert from 'node:assert/strict';
import { arenaTiles, intentLabel, resultLabel } from '../../agent/battle-view.mjs';
test('captured or defeated targets disappear from the arena; actual companion position remains', () => {
  const tiles = arenaTiles({ world:{x:'2',y:'1'},targets:[{id:'wild-treecko',state:'CAPTURED',x:2,y:1},{id:'wild-torchic',state:'DEFEATED',x:6,y:1}] });
  assert.equal(tiles.length,27); assert.equal(tiles[11].kind,'actor'); assert.equal(tiles.filter(t => t.kind === 'torchic').length,0);
});
test('a defeated companion without position is not rendered at origin', () => { assert.equal(arenaTiles({world:{actorHp:'0'},targets:[]}).filter(t=>t.kind==='actor').length,0); });
test('confirmation preserves goal and no battle; damage feedback uses real result', () => {
  assert.equal(intentLabel({goal:'CAPTURE',targetId:'wild-treecko',noBattle:true}),'捕捉木守宫；不主动战斗');
  assert.match(resultLabel({code:'ATTACKED',data:{damage:'25',retaliationDamage:'10',actorHp:'990'}}),/25.*10.*990/);
});
