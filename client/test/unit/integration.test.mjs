import test from 'node:test';
import assert from 'node:assert/strict';
import { ClientState } from '../../src/integration/client-state.js';
import { loadPreferences, savePreferences } from '../../src/utils/preferences-store.js';
import { toEntityView, gridToPixel, toQuestView } from '../../src/integration/scene-adapter.js';
import { GameGateway } from '../../src/integration/game-gateway.js';
const snap = (seq=10) => ({protocolVersion:1,environment:'test',worldId:'w',roomId:'r',sessionId:'s',seq,selfPlayerId:'p',mapId:'chapter1',mapVersion:1,roomStatus:'ACTIVE',entities:[],selfAssets:{party:[],inventory:[]},quest:{revision:1,evidence:[],rescuedIds:[]}});
test('state ignores old events and requests resync on missing sequence',()=>{
 const s=new ClientState(); assert.equal(s.applySnapshot(snap()),true);
 assert.equal(s.applyEvent({sessionId:'s',seq:9,eventId:'old',snapshot:snap(9)}),'IGNORED');
 assert.equal(s.applyEvent({sessionId:'s',seq:11,eventId:'one',snapshot:snap(11)}),'APPLIED');
 assert.equal(s.applyEvent({sessionId:'s',seq:13,eventId:'gap',snapshot:snap(13)}),'RESYNC');
 assert.equal(s.getView().seq,11);
});
test('old sessions, duplicate events and stale snapshots cannot overwrite current state',()=>{
 const s=new ClientState(); s.applySnapshot(snap());
 assert.equal(s.applyEvent({sessionId:'old',seq:11,eventId:'x',snapshot:snap(11)}),'IGNORED');
 const e={sessionId:'s',seq:11,eventId:'x',snapshot:snap(11)};
 assert.equal(s.applyEvent(e),'APPLIED'); assert.equal(s.applyEvent(e),'IGNORED');
 assert.equal(s.applySnapshot(snap(8)),false);
});
test('views are immutable and unsubscribed listeners do not fire',()=>{
 const s=new ClientState(); let calls=0; const off=s.subscribe(()=>calls++); s.applySnapshot(snap()); off(); s.applySnapshot(snap(11));
 assert.equal(calls,1); assert.throws(()=>s.getView().selfAssets.party.push({}));
});
test('unsupported map or protocol never enters state',()=>{
 for(const patch of [{mapVersion:999},{protocolVersion:9}]) { const s=new ClientState(); assert.throws(()=>s.applySnapshot({...snap(),...patch}),/版本/); }
});
test('legacy localStorage assets cannot become preferences or game state',()=>{
 const values=new Map([['MONSTER_TAMER_DATA',JSON.stringify({monsters:{inParty:[1]},money:999})]]);
 const storage={getItem:k=>values.get(k),setItem:(k,v)=>values.set(k,v)};
 assert.equal(loadPreferences(storage).volume,0.5);
 savePreferences({volume:0.8,textSpeed:30,party:['forged']},storage);
 assert.deepEqual(loadPreferences(storage),{volume:0.8,textSpeed:30});
 values.set('ECHO_PREFERENCES','{broken'); assert.equal(loadPreferences(storage).volume,0.5);
});
test('entity identity and grid mapping do not merge same-species companions',()=>{
 assert.deepEqual(gridToPixel({gridX:2,gridY:3}),{x:128,y:192});
 assert.notEqual(toEntityView({entityId:'a',speciesId:'Treecko'}).id,toEntityView({entityId:'b',speciesId:'Treecko'}).id);
 assert.equal(toEntityView({entityId:'x',speciesId:'unknown'}).placeholder,true);
});
test('quest projection never invents evidence or double counts rescued babies',()=>{
 assert.equal(toQuestView(snap()).evidence.length,0);
 const q=toQuestView({...snap(),quest:{evidence:['E11'],rescuedIds:['baby1','baby1']}});
 assert.equal(q.rescued,1); assert.deepEqual(q.evidence,['E11']);
});
test('transport retries use identical command payload and request ID',async()=>{
 const bodies=[]; const gateway=new GameGateway({fetchFn:async(url,options)=>{bodies.push(options.body);if(bodies.length===1)throw Error('lost');return {ok:true,json:async()=>({requestId:'one',outcome:'APPLIED'})};}});
 gateway.roomId='r';const command={requestId:'one',worldId:'w',action:'CAPTURE',targetId:'t',expectedRevision:{attempt:1}};
 await assert.rejects(gateway.send(command)); await gateway.send(command);
 assert.equal(bodies[0],bodies[1]);
 await assert.rejects(gateway.send({...command,targetId:'other'}),/载荷/);
});
test('HTTP rejection is surfaced and is never a local success',async()=>{
 const g=new GameGateway({fetchFn:async()=>({ok:false,status:403,json:async()=>({message:'无权操作'})})});g.roomId='r';
 await assert.rejects(g.send({requestId:'x',action:'CAPTURE'}),/无权/);
});
