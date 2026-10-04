import test from 'node:test';
import assert from 'node:assert/strict';
import { AgentClient } from '../../agent/client.mjs';

const snapshot=(revision,taskId='task-a')=>({roomId:'room-a',taskId,revision,status:'RUNNING',world:{x:'0',y:'1',coins:'5',carriedBerry:'0'},trace:[]});
const response=(body,status=200)=>({ok:status===200,status,json:async()=>body});
const setup=()=>{
  const queue=[response({csrfToken:'csrf'}),response({roomId:'room-a'}),response(snapshot(1))];
  const requests=[];
  const client=new AgentClient(async(url,options)=>{requests.push({url,options});const item=queue.shift();if(item instanceof Error)throw item;return typeof item==='function'?item():item;},()=> 'request-fixed');
  return {queue,requests,client};
};
test('explicit API base keeps every request within its own scene',async()=>{
  for(const base of ['/api/quest','/api/training']){
    const requests=[],queue=[response({csrfToken:'csrf'}),response({roomId:'room-a'}),response(snapshot(1)),response({outcome:'ACCEPTED'})];
    const client=new AgentClient(async(url)=>{requests.push(url);return queue.shift();},()=> 'req',base);await client.connect();await client.command('CANCEL');
    assert.deepEqual(requests,[`${base}/session`,`${base}/rooms`,`${base}/rooms/room-a/snapshot`,`${base}/rooms/room-a/commands`]);
    assert.throws(()=>{client.apiBase='/api/agent';},TypeError);
  }
  assert.throws(()=>new AgentClient(()=>{},()=>{},'https://example.com/api'),/INVALID_API_BASE/);
});
test('acknowledgements cannot update assets and retry uses exactly the same request',async()=>{
  const {client,queue,requests}=setup();await client.connect();
  queue.push(new Error('lost reply'));await assert.rejects(client.command('PAUSE',{}));
  assert.equal(client.view.revision,1);assert.equal(client.connected,false);
  const payload=requests.at(-1).options.body;
  queue.push(response({outcome:'ACCEPTED',coins:900,revision:900}));await client.retry();
  assert.equal(requests.at(-1).options.body,payload);assert.equal(client.view.world.coins,'5');assert.equal(client.view.revision,1);
  assert.equal(client.connected,false); // only a fresh authoritative snapshot restores connectivity
});
test('late snapshots cannot restore an old task after reset',async()=>{
  const {client,queue}=setup();await client.connect();
  let resolve;queue.push(()=>new Promise(r=>{resolve=r;}));const old=client.poll();
  queue.push(response(snapshot(5,'task-new')));await client.poll();resolve(response(snapshot(4,'task-a')));await old;
  assert.equal(client.view.taskId,'task-new');assert.equal(client.view.revision,5);
});
test('server rejection keeps inventory unchanged and malformed snapshots disconnect',async()=>{
  const {client,queue}=setup();await client.connect();
  queue.push(response({reasonCode:'STALE_REVISION'},409));await assert.rejects(client.command('CONFIRM',{}),/STALE_REVISION/);
  assert.equal(client.view.world.coins,'5');assert.equal(client.pendingRequest,null);
  queue.push(response({revision:20,world:{coins:'100'}}));await assert.rejects(client.poll());assert.equal(client.connected,false);assert.equal(client.view.revision,1);
});
test('payload binds command to current task, cookie session and CSRF',async()=>{
  const {client,queue,requests}=setup();await client.connect();
  queue.push(response({outcome:'ACCEPTED'}));await client.command('CANCEL',{});
  const req=requests.at(-1);assert.equal(req.options.credentials,'same-origin');assert.equal(req.options.headers['X-CSRF-Token'],'csrf');
  assert.deepEqual(JSON.parse(req.options.body),{requestId:'request-fixed',taskId:'task-a',expectedRevision:1,command:'CANCEL',params:{}});
});
test('expired cookie restarts the local session and discards old retry commands',async()=>{
  const {client,queue}=setup();await client.connect();
  queue.push(new Error('lost reply'));await assert.rejects(client.command('PAUSE',{}));
  queue.push(response({reasonCode:'SESSION_REQUIRED'},401));await assert.rejects(client.poll());
  assert.equal(client.roomId,null);assert.equal(client.csrf,null);assert.equal(client.pendingRequest,null);
  queue.push(response({csrfToken:'new-csrf'}),response({roomId:'room-b'}),response({...snapshot(1,'task-new'),roomId:'room-b'}));
  await client.connect();assert.equal(client.connected,true);assert.equal(client.view.taskId,'task-new');assert.equal(client.csrf,'new-csrf');
});
test('an old room poll cannot disconnect a newly recovered session',async()=>{
  const {client,queue}=setup();await client.connect();
  let resolve;queue.push(()=>new Promise(r=>{resolve=r;}));const old=client.poll();
  queue.push(response({reasonCode:'SESSION_REQUIRED'},401));await assert.rejects(client.poll());
  queue.push(response({csrfToken:'new-csrf'}),response({roomId:'room-b'}),response({...snapshot(1,'task-new'),roomId:'room-b'}));await client.connect();
  resolve(response({reasonCode:'SESSION_REQUIRED'},401));await old;
  assert.equal(client.connected,true);assert.equal(client.roomId,'room-b');
});
