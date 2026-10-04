import test from 'node:test';
import assert from 'node:assert/strict';
import { navigateMode } from '../../agent/mode-navigation.mjs';
test('switch waits for authoritative pause acknowledgement before navigating',async()=>{
  const calls=[];const client={connected:true,pendingRequest:null,poll:async()=>({status:'RUNNING'}),command:async(kind,params)=>{calls.push([kind,params]);}};
  await navigateMode(client,'/training/',href=>calls.push(href));assert.deepEqual(calls,[['SWITCH_MODE',{mode:'training'}],'/training/']);
});
test('failed pause or unconfirmed task cannot silently leave active work',async()=>{
  const client={connected:true,pendingRequest:null,poll:async()=>({status:'RUNNING'}),command:async()=>{throw new Error('STALE_REVISION');}};
  let moved=false;await assert.rejects(navigateMode(client,'/training/',()=>{moved=true;}),/STALE_REVISION/);assert.equal(moved,false);
  client.poll=async()=>({status:'READY'});await assert.rejects(navigateMode(client,'/training/',()=>{moved=true;}),/CANCEL_BEFORE_SWITCH/);assert.equal(moved,false);
});
test('navigation is local and disconnected or ambiguous requests remain blocked',async()=>{
  const client={connected:false};await assert.rejects(navigateMode(client,'/quest/'),/DISCONNECTED/);
  client.connected=true;client.pendingRequest={};await assert.rejects(navigateMode(client,'/quest/'),/REQUEST_PENDING/);
  await assert.rejects(navigateMode(client,'https://example.com'),/INVALID_MODE/);
});
