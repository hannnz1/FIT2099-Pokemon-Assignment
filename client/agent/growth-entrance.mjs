import {mapExits} from './map-boundary.mjs';
const facing={lab:{forest:'S'},forest:{lab:'S',river:'E'},river:{forest:'W',mountain:'E'},mountain:{river:'W'}};
export function entranceViews(world,names={}){return mapExits(world.map??{},names).map(g=>{const locked=world.regions?.find(r=>r.id===g.regionId)?.unlocked===false;return {...g,locked,direction:facing[world.region]?.[g.regionId]??'S',label:g.label+(locked?' · 未解锁':'')};});}
export function drawEntrance(scene,gate,map,region){
 const cx=(gate.x+.5)*64,cy=(gate.y+.5)*64,vertical=['N','S'].includes(gate.direction),g=scene.add.graphics().setDepth(2);
 // Dirt lanes and stone thresholds meet the actual destination tile; no floating portal icon.
 g.fillStyle(0x9f8b65,1);g.fillRect(cx-(vertical?23:32),cy-(vertical?32:23),vertical?46:64,vertical?64:46);
 g.fillStyle(region==='lab'?0xbab7a5:0xd3bf8b,1);g.fillRect(cx-(vertical?18:32),cy-(vertical?32:18),vertical?36:64,vertical?64:36);
 for(let i=-22;i<=22;i+=22){g.fillStyle(region==='lab'?0xe2dfcf:0xb1aaa0,1);if(vertical)g.fillRect(cx-13,cy+i-4,26,8);else g.fillRect(cx+i-4,cy-13,8,26);}
 const sign=scene.add.graphics().setDepth(3100),boardWidth=Math.max(148,gate.label.length*12+12),sx=Math.max(8,Math.min(map.width*64-boardWidth-8,gate.direction==='E'?cx-boardWidth-26:cx+26)),sy=Math.max(8,cy-48);
 sign.fillStyle(0x694a32,1);sign.fillRect(sx+10,sy+18,6,29);sign.fillStyle(gate.locked?0x695b49:0x855f3c,1);sign.fillRect(sx,sy,boardWidth,26);sign.lineStyle(2,0xc6a271,1);sign.strokeRect(sx,sy,boardWidth,26);
 scene.add.text(sx+5,sy+5,gate.label,{fontFamily:'Microsoft YaHei, sans-serif',fontSize:'12px',color:gate.locked?'#e2d4bb':'#fff2cf'}).setDepth(3101);
 if(gate.locked){sign.lineStyle(4,0x9e6e4e,1);if(vertical)sign.lineBetween(cx-22,cy,cx+22,cy);else sign.lineBetween(cx,cy-22,cx,cy+22);}
 return g;
}
