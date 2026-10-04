import test from 'node:test';
import assert from 'node:assert/strict';
import {GameGateway} from '../../src/integration/game-gateway.js';
import {ClientState} from '../../src/integration/client-state.js';
import {SessionController,canSubmit} from '../../src/integration/session-controller.js';
const snapshot=(seq=1,roomId='r',sessionId='s')=>({protocolVersion:1,mapVersion:1,mapId:'chapter1',roomId,sessionId,seq,entities:[],selfAssets:{},quest:{},roomStatus:'ACTIVE'});
const deferred=()=>{let resolve;const promise=new Promise(r=>resolve=r);return {resolve,promise};};
const tick=()=>new Promise(r=>setImmediate(r));
class Transport {
 constructor(){this.listeners=new Set();this.roomId='r';this.sent=[];}
 subscribe(fn){this.listeners.add(fn);return()=>this.listeners.delete(fn);}
 emit(e){this.listeners.forEach(fn=>fn(e));}
 async connect(room){this.roomId=room;this.emit({type:'SNAPSHOT',snapshot:snapshot(1,room,room==='r'?'s':'new')});}
 async send(cmd){this.sent.push(cmd);if(this.sent.length===1)throw Error('response lost');return {requestId:cmd.requestId,outcome:'APPLIED'};}
}
test('socket close during snapshot fetch cannot produce a successful connection',async()=>{
 const fetchResult=deferred();let socket;const messages=[];
 const g=new GameGateway({socketFactory:()=>socket={close(){}},fetchFn:()=>fetchResult.promise});g.subscribe(e=>messages.push(e.type));
 const connection=g.connect('r');socket.onopen();await tick();socket.onclose();fetchResult.resolve({ok:true,json:async()=>snapshot()});
 await assert.rejects(connection);assert.deepEqual(messages,['DISCONNECTED']);
});
test('same-room reconnect preserves unacknowledged command payload',async()=>{
 const g=new Transport(),s=new SessionController(g,new ClientState());await s.connect('r');
 const cmd={requestId:'original',action:'CAPTURE'};await assert.rejects(s.send(cmd));
 await s.connect('r');assert.deepEqual(s.pending,cmd);await s.send(s.pending);assert.deepEqual(g.sent,[cmd,cmd]);assert.equal(s.pending,undefined);
});
test('late resync from previous room cannot seed the new state',async()=>{
 const g=new Transport(),state=new ClientState(),s=new SessionController(g,state);await s.connect('r');
 const d=deferred();g.getSnapshot=()=>d.promise;
 g.emit({type:'STATE',sessionId:'s',seq:3,eventId:'gap',snapshot:snapshot(3)});
 await s.connect('other');d.resolve(snapshot(5));await tick();assert.equal(state.getView().roomId,'other');
});
test('events received during resync are buffered and applied after snapshot',async()=>{
 const g=new Transport(),state=new ClientState(),s=new SessionController(g,state);await s.connect('r');
 const d=deferred();g.getSnapshot=()=>d.promise;
 g.emit({type:'STATE',sessionId:'s',seq:3,eventId:'three',snapshot:snapshot(3)});
 g.emit({type:'STATE',sessionId:'s',seq:4,eventId:'four',snapshot:snapshot(4)});
 d.resolve(snapshot(2));await tick();assert.equal(state.getView().seq,4);
});
test('all panels reject disabled, pending, disconnected and frozen actions',()=>{
 const base={connected:true,busy:false,pending:undefined};const view={roomStatus:'ACTIVE'};
 assert.equal(canSubmit(base,view,{enabled:false}),false);
 assert.equal(canSubmit({...base,pending:{}},view,{}),false);
 assert.equal(canSubmit({...base,connected:false},view,{}),false);
 assert.equal(canSubmit(base,{roomStatus:'FROZEN'},{}),false);
 assert.equal(canSubmit(base,view,{enabled:true}),true);
});
test('definitive 403 rejection unlocks input instead of trapping pending forever',async()=>{
 const gateway=new Transport(),session=new SessionController(gateway,new ClientState());await session.connect('r');
 const http=new GameGateway({fetchFn:async()=>({ok:false,status:403,json:async()=>({message:'无权操作'})})});http.roomId='r';gateway.send=cmd=>http.send(cmd);
 await assert.rejects(session.send({requestId:'denied',action:'CAPTURE'}),/无权/);assert.equal(session.pending,undefined);
});
