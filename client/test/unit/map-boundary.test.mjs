import test from 'node:test';
import assert from 'node:assert/strict';
import {boundaryLayout,blockedCells,mapExits,boundaryDescription} from '../../agent/map-boundary.mjs';
import {readFileSync} from 'node:fs';
import {projectRpg} from '../../agent/rpg-view.mjs';
const definitions=JSON.parse(readFileSync(new URL('../../agent/growth-maps.json',import.meta.url))).maps;
test('interior viewport labels all four edges as continuation, never world boundary',()=>{
 const p=projectRpg({map:{width:59,height:13},world:{x:33,y:7}},'quest');
 const b=boundaryLayout({...p,tile:64,x:0,y:0});
 assert.deepEqual(b.edges.map(e=>e.kind),['viewport','viewport','viewport','viewport']);
 assert.match(boundaryDescription(b),/59 × 13/);assert.match(boundaryDescription(b),/视口/);
});
test('world corner separates actual north/west edges from east/south clipping',()=>{
 const b=boundaryLayout({width:59,height:13,startX:0,startY:0,cols:13,rows:9,tile:64,x:0,y:0});
 assert.deepEqual(b.edges.map(e=>[e.side,e.kind]),[['north','world'],['east','viewport'],['south','viewport'],['west','world']]);
});
test('training outlines authoritative 9 by 3 grid and fits within canvas',()=>{
 const p=projectRpg({world:{x:1,y:1}},'training');const b=boundaryLayout({...p,tile:80,x:56,y:152});
 assert.equal(b.rect.width,720);assert.equal(b.rect.height,240);assert(b.edges.every(e=>e.kind==='world'));
 assert.equal(b.rect.x+b.rect.width,776);
});
test('growth collision overlays exactly the server collision while bridges stay open',()=>{
 for(const m of Object.values(definitions)){assert.equal(blockedCells(m).length,m.collision.filter(v=>v!==0).length);}
 for(const bridge of definitions.river.bridges)assert(!blockedCells(definitions.river).some(t=>t.x===bridge.x&&t.y===bridge.y));
 assert.deepEqual(blockedCells({width:3,height:1,tiles:[{x:0,y:0,kind:'wall'},{x:1,y:0,kind:'water'},{x:2,y:0,kind:'grass'}]}),[{x:0,y:0}]);
});
test('exit labels come from real exits and no unlabeled legacy gate becomes invented route',()=>{
 assert.deepEqual(mapExits(definitions.forest,{lab:'研究室',river:'浅溪河岸'}).map(e=>e.label),['前往研究室','前往浅溪河岸']);
 assert.equal(mapExits({gate:{x:1,y:1}}).length,0);
 assert.equal(mapExits({exits:[{regionId:'river',x:1,y:2,label:'河岸出口'}]}).at(0).label,'河岸出口');
});
test('fractional growth camera viewport keeps far world boundaries off screen',()=>{
 const b=boundaryLayout({width:20,height:14,startX:4.5,startY:3.5,cols:13,rows:9,tile:64,x:288,y:224});
 assert(b.edges.every(e=>e.kind==='viewport'));assert.equal(b.rect.x,288);assert.equal(b.rect.width,832);
});
import {attachBoundaryLegend,drawBoundary,drawBlocked} from '../../agent/map-boundary.mjs';
test('legend remains in document flow, exposes authoritative edge state and cleans up',()=>{
 const previous=globalThis.document;let removed=false;
 const node={style:{},setAttribute(){},remove(){removed=true;}};
 globalThis.document={createElement:()=>node};
 try{const host={dataset:{},after(n){assert.equal(n,node);}},legend=attachBoundaryLegend(host);
 const b=boundaryLayout({width:20,height:14,startX:0,startY:0,cols:13,rows:9});legend.update(b,[{label:'前往森林',x:2,y:10}]);
 assert.match(node.textContent,/前往森林（2,10）/);assert.match(node.style.cssText,/overflow-wrap:anywhere/);assert.doesNotMatch(node.style.cssText,/position:\s*(absolute|fixed)/);assert.equal(JSON.parse(host.dataset.boundary).width,20);
 legend.fallback('地图未加载');assert.match(node.textContent,/方向按钮/);legend.destroy();assert.equal(removed,true);
 }finally{globalThis.document=previous;}
});
test('Phaser boundary drawing sends solid world edges and segmented viewport continuation',()=>{
 const lines=[],styles=[];const g={setDepth(){return this;},lineStyle(...args){styles.push(args);return this;},lineBetween(...args){lines.push(args);return this;}};
 drawBoundary({add:{graphics:()=>g}},boundaryLayout({width:20,height:14,cols:13,rows:9,tile:64}));
 assert.equal(styles.filter(s=>s[0]===5).length,2);assert.equal(styles.filter(s=>s[0]===3).length,2);
 assert(lines.some(l=>l[0]===5&&l[2]===827&&l[1]===5));assert(!lines.some(l=>l[0]===827&&l[1]===5&&l[3]===571));
 assert(lines.every(l=>l[0]>=5&&l[0]<=827&&l[2]>=5&&l[2]<=827&&l[1]>=5&&l[1]<=571&&l[3]>=5&&l[3]<=571));
});
test('blocked overlay culls offscreen collision cells instead of painting false edges',()=>{
 const rectangles=[];const g={setDepth(){return this;},lineStyle(){return this;},fillStyle(){return this;},fillRect(...args){rectangles.push(args);},strokeRect(){},lineBetween(){}};
 drawBlocked({add:{graphics:()=>g}},[{x:0,y:0},{x:6,y:4}],{startX:4,startY:3,cols:4,rows:3,x:0,y:0,tile:64});
 assert.deepEqual(rectangles,[[130,66,60,60]]);
});

test('boundary rendering insets translated camera edges without mutating geometry',()=>{
 const lines=[];const g={setDepth(){return this;},lineStyle(){return this;},lineBetween(...args){lines.push(args);return this;}};
 const b=boundaryLayout({width:20,height:14,startX:4.5,startY:3.5,cols:13,rows:9,tile:64,x:288,y:224});const before=structuredClone(b);
 drawBoundary({add:{graphics:()=>g}},b);assert.deepEqual(b,before);
 assert(lines.every(([x1,y1,x2,y2])=>x1>=293&&x2>=293&&x1<=1115&&x2<=1115&&y1>=229&&y2>=229&&y1<=795&&y2<=795));
 assert(lines.some(l=>l[0]===1115&&l[2]===1115));assert(lines.some(l=>l[1]===795&&l[3]===795));
});
