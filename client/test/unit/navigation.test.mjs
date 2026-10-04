import test from 'node:test';
import assert from 'node:assert/strict';
import {nextMode} from '../../src/integration/navigation.js';
test('borrowing from dialog enters battle and capture enters return dialogue',()=>{
 assert.equal(nextMode('dialog',{encounterView:{}}),'battle');
 assert.equal(nextMode('battle',{dialog:{}}),'dialog');
 assert.equal(nextMode('dialog',{}),'world');
});
test('background state messages do not steal focus from inventory',()=>{
 assert.equal(nextMode('inventory',{encounterView:{}}),'inventory');
});
