import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const read=(path:string)=>JSON.parse(fs.readFileSync(new URL(path,import.meta.url),'utf8'));

test('Java map uses the complete reference town, with identical visual obstacle cells',()=>{
 const semantic=read('../src/generated/map.json');
 assert.equal(semantic.width,40,'must replace the old placeholder map with the complete town');
 assert.equal(semantic.height,40);
 const town=read('../public/assets/phaser-rpg/tuxemon-town.json');
 const collides=new Set(town.tilesets[0].tiles.filter((t:any)=>t.properties?.some((p:any)=>p.name==='collides'&&p.value)).map((t:any)=>t.id+1));
 const world=town.layers.find((l:any)=>l.name==='World').data;
 for(let i=0;i<world.length;i++)assert.equal(semantic.rows[Math.floor(i/40)][i%40]==='#',collides.has(world[i]),`collision mismatch at ${i%40},${Math.floor(i/40)}`);
 assert.deepEqual(semantic,read('../../resources/demo-map.json'));
});

test('all demo actors and ecology are reachable without crossing reference buildings',()=>{
 const m=read('../src/generated/map.json'),p=m.entities.find((e:any)=>e.kind==='PLAYER');
 const todo=[p],seen=new Set([`${p.x},${p.y}`]);
 for(const a of todo)for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){
  if(Math.abs(dx)+Math.abs(dy)!==1)continue;
  const x=a.x+dx,y=a.y+dy,k=`${x},${y}`;
  if(x>=0&&y>=0&&x<m.width&&y<m.height&&m.rows[y][x]!=='#'&&!seen.has(k)){seen.add(k);todo.push({x,y});}
 }
 for(const e of m.entities)assert.ok(seen.has(`${e.x},${e.y}`),`unreachable ${e.kind}`);
 for(const symbol of ['T','W','C'])assert.ok(m.rows.some((r:string,y:number)=>[...r].some((s,x)=>s===symbol&&seen.has(`${x},${y}`))),`missing reachable ${symbol}`);
});
