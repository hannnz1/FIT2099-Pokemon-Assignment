import fs from 'node:fs';
import crypto from 'node:crypto';
const source=fs.readFileSync(new URL('../maps/demo.tmj',import.meta.url),'utf8');
const m=JSON.parse(source), kinds=['DIRT','WALL','FLOOR','HAY','TREE','PUDDLE','WATERFALL','LAVA','CRATER'];
const tiles=m.layers.find(l=>l.name==='terrain').data.map(id=>id-1000),objects=m.layers.find(l=>l.name==='entities').objects;
const visualLayers=m.layers.filter(l=>['Below Player','World','Above Player','Objects'].includes(l.name));
const visualTileset=structuredClone(m.tilesets[0]);visualTileset.image='tuxemon-sample-32px-extruded.png';
const collides=new Set(visualTileset.tiles.filter(t=>t.properties?.some(p=>p.name==='collides'&&p.value)).map(t=>t.id+1));
const obstacles=m.layers.find(l=>l.name==='World').data;
if(tiles.some((id,i)=>(id===2)!==collides.has(obstacles[i])))throw Error('Visual and Java collisions disagree');
if(!Number.isInteger(m.width)||!Number.isInteger(m.height)||m.width<3||m.height<3||m.width>100||m.height>100||tiles.length!==m.width*m.height||tiles.some(t=>!Number.isInteger(t)||t<1||t>9))throw Error('Invalid terrain');
const seen=new Set();const entities=objects.map(o=>{const x=o.x/m.tilewidth,y=o.y/m.tileheight;
 if(!Number.isInteger(x)||!Number.isInteger(y)||x<0||y<0||x>=m.width||y>=m.height||tiles[y*m.width+x]===2)throw Error('Invalid entity spawn');
 if(!['PLAYER','PROFESSOR','MERCHANT','TREECKO','MUDKIP','TORCHIC'].includes(o.type))throw Error('Invalid entity type');
 if(o.type!=='CANDY'){const key=`${x},${y}`;if(seen.has(key))throw Error('Duplicate actor spawn');seen.add(key);}return {kind:o.type,x,y};});
if(entities.filter(e=>e.kind==='PLAYER').length!==1)throw Error('Need one player');
for(const kind of ['PROFESSOR','MERCHANT','TREECKO','MUDKIP','TORCHIC'])if(!entities.some(e=>e.kind===kind))throw Error(`Missing ${kind}`);
const start=entities.find(e=>e.kind==='PLAYER'),reachable=new Set([`${start.x},${start.y}`]),queue=[start];
for(const e of queue)for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){if(Math.abs(dx)+Math.abs(dy)!==1)continue;let x=e.x+dx,y=e.y+dy,key=`${x},${y}`;if(x>=0&&y>=0&&x<m.width&&y<m.height&&tiles[y*m.width+x]!==2&&!reachable.has(key)){reachable.add(key);queue.push({x,y});}}
if(entities.some(e=>!reachable.has(`${e.x},${e.y}`)))throw Error('Unreachable entity');
for(const [idx,id] of tiles.entries())if([5,7].includes(id)){let count=0,x=idx%m.width,y=Math.floor(idx/m.width);for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++)if((dx||dy)&&x+dx>=0&&x+dx<m.width&&y+dy>=0&&y+dy<m.height&& (id===5?[4,5]:[6,7]).includes(tiles[(y+dy)*m.width+x+dx]))count++;if(count<(id===5?1:2))throw Error('Unsatisfied spawner condition');}
const data={id:'demo',version:crypto.createHash('sha256').update(source).digest('hex').slice(0,16),width:m.width,height:m.height,tileSize:m.tilewidth,rows:Array.from({length:m.height},(_,y)=>tiles.slice(y*m.width,(y+1)*m.width).map(t=>'.#_,T~W^C'[t-1]).join('')),entities,kinds};
for(const dest of ['../resources/demo-map.json','../web-client/src/generated/map.json']){const path=new URL(dest,import.meta.url);fs.mkdirSync(new URL('.',path),{recursive:true});fs.writeFileSync(path,JSON.stringify(data,null,2)+'\n');}
console.log(`Validated ${m.width}×${m.height} map ${data.version}`);
fs.writeFileSync(new URL('../web-client/public/assets/phaser-rpg/world.json',import.meta.url),JSON.stringify({...m,layers:visualLayers,tilesets:[visualTileset]})+'\n');
