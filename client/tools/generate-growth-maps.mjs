import {forestGroundDetails} from '../agent/map-polish.mjs';
import {readFileSync,writeFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
const digest=bytes=>createHash('sha256').update(bytes).digest('hex');
const maps={};
const exit=(regionId,x,y,ax,ay)=>({regionId,x,y,arrival:{x:ax,y:ay}});
for(const [id,name,source,entry,gate,recovery,exits,spawns] of [
 ['lab','研究室','building_1',[5,6],[5,7],[5,6],[exit('forest',5,7,9,9)],[]],
 ['forest','苔叶森林','forest_1',[9,9],[9,10],[9,8],[exit('lab',9,10,5,6),exit('river',13,5,3,10)],[['TREECKO',7,5],['MUDKIP',11,4],['TORCHIC',9,3]]]
]){
 const bytes=readFileSync(new URL('../assets/data/'+source+'.json',import.meta.url));const tiled=JSON.parse(bytes),layer=tiled.layers.find(l=>l.name==='Collision');
 if(!layer?.data||tiled.tilewidth!==64)throw new Error('Unsupported source map');
 const path='assets/images/monster-tamer/map/'+(id==='lab'?'buildings/':'')+source+'_level_';
 maps[id]={id,name,source:'assets/data/'+source+'.json',sourceSha256:digest(bytes),width:tiled.width,height:tiled.height,tileSize:64,collision:layer.data.map(v=>v?1:0),entry:{x:entry[0],y:entry[1]},gate:{x:gate[0],y:gate[1]},recovery:{x:recovery[0],y:recovery[1]},exits,spawns:spawns.map(([species,x,y])=>({species,x,y})),minimumLevel:5,levelOffset:0,background:path+'background.png',foreground:path+'foreground.png'};
}
const atlas='assets/images/monster-tamer/map/main_1_level_background.png';
const atlasHash=digest(readFileSync(new URL('../'+atlas,import.meta.url)));
maps.forest.decorationAtlas=atlas;maps.forest.decorationAtlasSha256=atlasHash;maps.forest.groundDetails=forestGroundDetails(maps.forest);
const cell=(x,y)=>y*40+x;
// Recompose existing 64px source cells at runtime; no new bitmap artwork.
for(const [id,name,minimumLevel,levelOffset,recovery,exits,spawns] of [
 ['river','浅溪河岸',6,1,[5,2],[exit('forest',2,10,12,5),exit('mountain',17,3,3,10)],[['MUDKIP',5,4],['MUDKIP',14,7],['TREECKO',15,3]]],
 ['mountain','赤岩山道',8,2,[16,11],[exit('river',2,10,16,3)],[['TORCHIC',7,8],['TORCHIC',12,5],['MUDKIP',16,3]]]
]){
 const width=20,height=14,collision=Array(width*height).fill(0),artTiles=Array(width*height).fill(id==='river'?cell(20,5):cell(0,13)),bridges=[];
 const block=(x,y,art)=>{collision[y*width+x]=1;artTiles[y*width+x]=art;};
 for(let y=0;y<height;y++)for(let x=0;x<width;x++){
  if(x===0||x===width-1||y===0||y===height-1)block(x,y,cell(2,13));
  if(id==='river'&&x>=8&&x<=11){block(x,y,cell(x===8?14:x===11?17:15,83));if(y===7||y===8){collision[y*width+x]=0;bridges.push({x,y});}}
  if(id==='mountain'&&((y===6&&x>=6&&x<=17&&x!==10&&x!==11)||(y===10&&x>=7&&x<=17&&x!==14&&x!==15)))block(x,y,cell(0,22));
 }
 // Surface detail uses verified cells from the same atlas. Preserve the original
 // collision footprint so existing saved player positions remain valid.
 const paint=(x,y,art)=>{if(x>0&&x<width-1&&y>0&&y<height-1&&collision[y*width+x]===0&&!bridges.some(b=>b.x===x&&b.y===y))artTiles[y*width+x]=art;};
 const line=(x1,y1,x2,y2,art)=>{for(let x=Math.min(x1,x2);x<=Math.max(x1,x2);x++)paint(x,y1,art);for(let y=Math.min(y1,y2);y<=Math.max(y1,y2);y++)paint(x2,y,art);};
 if(id==='river'){
  line(2,10,4,7,cell(0,13));line(4,7,7,7,cell(1,13));line(4,7,5,2,cell(0,13));line(12,7,16,7,cell(1,13));line(16,7,17,3,cell(0,13));
  for(const [x,y] of [[2,2],[3,3],[6,5],[2,7],[6,11],[13,2],[15,5],[17,9],[14,11],[18,11]])paint(x,y,cell(20+(x%3),82));
  for(let y=1;y<height-1;y++){paint(7,y,cell(19,83));paint(12,y,cell(22,83));}
  for(let y=1;y<5;y++){artTiles[y*width]=cell(9,82);artTiles[y*width+19]=cell(29,82);}
 }else{
  for(const [cx,cy] of [[4,3],[7,8],[16,3],[17,11],[4,11]])for(let yy=cy-1;yy<=cy+1;yy++)for(let xx=cx-1;xx<=cx+1;xx++)paint(xx,yy,cell(20+(xx%2),5));
  line(2,10,4,8,cell(1,13));line(4,8,10,8,cell(0,13));line(10,8,10,5,cell(1,13));line(10,5,16,5,cell(0,13));line(10,8,14,8,cell(1,13));line(14,8,14,11,cell(0,13));line(14,11,16,11,cell(1,13));
  for(const [x,y] of [[3,2],[5,4],[8,3],[13,2],[17,4],[3,12],[6,11],[9,12],[18,8]])paint(x,y,cell(x%2?3:0,13));
  for(const [x,y] of [[4,3],[7,2],[15,2],[18,12],[5,12]])paint(x,y,cell(20+(x%3),82));
 }
 // Larger solids are only drawn over cells already blocked by the Java map.
 const scenery=id==='river'?[{kind:'tree',x:0,y:5},{kind:'tree',x:19,y:9}]:[{kind:'rock',x:7,y:6},{kind:'rock',x:13,y:6},{kind:'rock',x:8,y:10},{kind:'rock',x:17,y:10}];
 for(let yy=recovery[1]-1;yy<=recovery[1]+1;yy++)for(let xx=recovery[0]-1;xx<=recovery[0]+1;xx++)paint(xx,yy,cell(1,13));
 const source='assets/data/'+id+'_demo.json';
 const tiled={type:'map',version:'1.10',orientation:'orthogonal',renderorder:'right-down',width,height,tilewidth:64,tileheight:64,infinite:false,layers:[{type:'tilelayer',name:'Collision',width,height,data:collision}],artTiles,bridges,scenery,provenance:{kind:'local-demo-recomposition',atlas,atlasSha256:atlasHash,upstreamMap:'assets/data/main_1.json',description:'Existing Monster Tamer grass, water, gravel, boulder and cliff cells; bridge planks are drawn by Phaser.'}};
 const bytes=Buffer.from(JSON.stringify(tiled));writeFileSync(new URL('../'+source,import.meta.url),bytes);
 maps[id]={id,name,source,sourceSha256:digest(bytes),width,height,tileSize:64,collision,entry:{x:3,y:10},gate:{x:2,y:10},recovery:{x:recovery[0],y:recovery[1]},exits,spawns:spawns.map(([species,x,y])=>({species,x,y})),minimumLevel,levelOffset,atlas,atlasColumns:40,atlasSha256:atlasHash,artTiles,bridges,scenery};
}
for(const m of Object.values(maps)){
 const points=[m.entry,m.gate,m.recovery,...m.exits,...m.spawns];
 for(const p of points)if(p.x<0||p.x>=m.width||p.y<0||p.y>=m.height||m.collision[p.y*m.width+p.x]!==0)throw new Error('Blocked configuration: '+m.id+' '+JSON.stringify(p));
 for(const e of m.exits){const dest=maps[e.regionId],p=e.arrival;if(!dest||dest.collision[p.y*dest.width+p.x]!==0)throw new Error('Blocked arrival');}
}
writeFileSync(new URL('../agent/growth-maps.json',import.meta.url),JSON.stringify({schemaVersion:2,maps}));
