// Re-run only to reset the editable demo map to the pinned reference layout.
import fs from 'node:fs';
const root=new URL('../',import.meta.url);
const town=JSON.parse(fs.readFileSync(new URL('web-client/public/assets/phaser-rpg/tuxemon-town.json',root),'utf8'));
const collides=new Set(town.tilesets[0].tiles.filter(t=>t.properties?.some(p=>p.name==='collides'&&p.value)).map(t=>t.id+1));
const world=town.layers.find(l=>l.name==='World').data;
const below=town.layers.find(l=>l.name==='Below Player').data;
const terrain=world.map((id,i)=>collides.has(id)?1002:id||below[i]!==126?1003:1001);
function put(x,y,id){const i=y*town.width+x;if(terrain[i]===1002||world[i])throw Error(`Ecology overlaps reference structure at ${x},${y}`);terrain[i]=1000+id;}
for(const [cx,cy,id,ground] of [[4,30,5,4],[22,33,7,6],[29,33,9,8]]){
 for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++)put(cx+dx,cy+dy,ground);
 put(cx,cy,id);
}
const entities=[['PLAYER',11,35],['PROFESSOR',12,32],['MERCHANT',13,32],['TREECKO',10,35],['MUDKIP',12,35],['TORCHIC',12,36],...Array.from({length:20},()=>['CANDY',11,35])];
town.tilesets[0].image='../web-client/public/assets/phaser-rpg/tuxemon-sample-32px-extruded.png';
town.tilesets.push({firstgid:1001,source:'semantic.tsj'});
town.layers.push({id:100,name:'terrain',type:'tilelayer',visible:false,opacity:1,width:40,height:40,x:0,y:0,data:terrain});
town.layers.push({id:101,name:'entities',type:'objectgroup',visible:true,opacity:1,x:0,y:0,objects:entities.map(([type,x,y],i)=>({id:1000+i,name:type,type,x:x*32,y:y*32,width:0,height:0,point:true,rotation:0,visible:true}))});
town.nextlayerid=102;town.nextobjectid=1100;
fs.writeFileSync(new URL('maps/demo.tmj',root),JSON.stringify(town,null,2)+'\n');
console.log('Imported original reference layers and added Java ecology/entities');
