import test from 'node:test';import assert from 'node:assert/strict';
import {manualAllowed,hpFeedbackDelta} from '../../agent/growth-view.mjs';
import {journeySteps,filterPokemon} from '../../agent/ui-journey.mjs';
test('pending ACK locks movement until the same request is retried',()=>{assert.equal(manualAllowed({manualAllowed:true},true,false,{requestId:'same'}),false);assert.equal(manualAllowed({manualAllowed:true},true,false,null),true);});
test('partner switch is never damage; same individual damage remains visible',()=>{assert.equal(hpFeedbackDelta({captureId:'a',hp:19},{captureId:'b',hp:30}),0);assert.equal(hpFeedbackDelta({captureId:'a',hp:19},{captureId:'a',hp:17}),-2);});
test('deployed filter uses server state; quest tutorial reads actual wild list',()=>{assert.equal(filterPokemon([{species:'TREECKO',state:'DEPLOYED'}],'','DEPLOYED').length,1);assert.equal(journeySteps({world:{turn:3},wild:[{state:'CAPTURED'}]},'quest').find(s=>s.id==='capture').done,true);});
