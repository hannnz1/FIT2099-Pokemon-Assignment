// Visual decoration only; Java remains authoritative for movement and collision.
export const atlasCell=(x,y)=>y*40+x;
export function drawAtlasCell(scene,key,cell,x,y,size=64,layer=null){const texture=scene.textures.get(key),frame='polish-'+cell;if(!texture.has(frame))texture.add(frame,0,(cell%40)*64,Math.floor(cell/40)*64,64,64);const image=scene.add.image(x,y,key,frame).setOrigin(0).setDisplaySize(size,size);if(layer)layer.add(image);return image;}
export function forestGroundDetails(map){const cells=new Map(),put=(x,y,cell)=>{if(x>=0&&y>=0&&x<map.width&&y<map.height&&!map.collision[y*map.width+x])cells.set(x+','+y,{x,y,cell});},line=(x1,y1,x2,y2)=>{for(let x=Math.min(x1,x2);x<=Math.max(x1,x2);x++)put(x,y1,atlasCell(0,13));for(let y=Math.min(y1,y2);y<=Math.max(y1,y2);y++)put(x2,y,atlasCell(0,13));};
 line(9,10,9,7);line(6,7,13,7);line(6,7,6,5);line(6,5,7,5);line(13,7,13,5);line(9,7,9,3);line(9,4,11,4);
 for(const [x,y] of [[5,4],[5,6],[6,8],[13,3],[14,6],[12,8],[11,2],[4,7]])put(x,y,atlasCell(20+(x%3),82));
 for(const [x,y] of [[8,8],[9,8],[10,8]])put(x,y,atlasCell(1,13));
 return [...cells.values()];}
export function trainingDecorations(grid){const {x,y,tile}=grid;return {signs:[{x:x+2.5*tile,y:y-32,label:'捕捉练习区'},{x:x+6.5*tile,y:y-32,label:'对战练习区'}],trees:[{x:0,y:0},{x:64,y:0},{x:704,y:0},{x:768,y:0},{x:0,y:448},{x:64,y:448},{x:704,y:448},{x:768,y:448}],flowers:[{x:192,y:32},{x:576,y:32},{x:192,y:448},{x:576,y:448}]};}
export function drawTrainingBackdrop(scene,layer,grid){for(let yy=0;yy<576;yy+=64)for(let xx=0;xx<832;xx+=64)drawAtlasCell(scene,'terrain',atlasCell(20+(xx/64%2),5),xx,yy,64,layer);const plan=trainingDecorations(grid),texture=scene.textures.get('terrain');if(!texture.has('polish-tree'))texture.add('polish-tree',0,9*64,82*64,64,128);
 for(const t of plan.trees)layer.add(scene.add.image(t.x,t.y,'terrain','polish-tree').setOrigin(0));for(const f of plan.flowers)drawAtlasCell(scene,'terrain',atlasCell(20,82),f.x,f.y,64,layer);
 for(const s of plan.signs){layer.add(scene.add.rectangle(s.x,s.y+12,5,24,0x604934));layer.add(scene.add.rectangle(s.x,s.y,150,28,0x7d6041).setStrokeStyle(2,0xd4bc87));layer.add(scene.add.text(s.x,s.y,s.label,{fontSize:'14px',color:'#fff2cd',fontFamily:'Microsoft YaHei, sans-serif'}).setOrigin(.5));}
 const width=grid.tile*9;for(const yy of [grid.y-8,grid.y+grid.tile*3+8]){layer.add(scene.add.rectangle(grid.x,yy,width,6,0x95704b).setOrigin(0,.5));for(let xx=grid.x;xx<=grid.x+width;xx+=80)layer.add(scene.add.rectangle(xx,yy,6,18,0x604934));}
 layer.add(scene.add.text(416,26,'林间训练场',{fontSize:'22px',color:'#fff2cd',stroke:'#29432e',strokeThickness:4}).setOrigin(.5));layer.add(scene.add.text(416,466,'走到目标身旁，练习捕捉与对战',{fontSize:'14px',color:'#f7f2d8',stroke:'#29432e',strokeThickness:3}).setOrigin(.5));}
