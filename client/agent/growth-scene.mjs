import {drawAtlasCell} from './map-polish.mjs';
import {bindMovementKeys} from '/rpg-view.mjs';
import {entranceViews,drawEntrance} from './growth-entrance.mjs';
import {boundaryLayout,blockedCells,mapExits,attachBoundaryLegend,drawBoundary,drawBlocked} from './map-boundary.mjs';
import {mapEntities,growthDirection,regionNames,hpFeedbackDelta} from './growth-view.mjs';
import {pokemonArt,spriteUrl} from './pokemon-art.mjs';
export function mountGrowth(host,{onTarget,onMove,canMove}){
 const legendOptions={showBlocked:false,exitLabel:'木质路牌：区域出口，走到入口自动切换'};const legend=attachBoundaryLegend(host,legendOptions);host.dataset.collisionOverlay='false';let latest=null,scene=null,selected=null,last='',previous=null;const status=document.querySelector('#map-status');host.tabIndex=0;host.setAttribute('aria-label','成长地图；方向键或 WASD 移动，点击野生伙伴选择目标');
 if(!globalThis.Phaser){status.textContent='Phaser 未加载，仍可使用区域和方向按钮';host.dataset.state='ERROR';legend.fallback('图形地图未加载。');return {update(){},destroy(){legend.destroy();}};}
 const P=globalThis.Phaser;
 class Field extends P.Scene{
  constructor(){super('GrowthField');this.failed=[];}
  preload(){this.load.on('loaderror',f=>{if(!f.key.startsWith('world-'))this.failed.push(f.key);});for(const id of ['recovery'])this.load.svg('world-'+id,'/rpg/assets/ui/world/'+id+'.svg',{width:64,height:64});for(const region of ['lab','forest']){const path=region==='lab'?'buildings/building_1':'forest_1';for(const layer of ['background','foreground'])this.load.image(region+'-'+layer,'/rpg/assets/images/monster-tamer/map/'+path+'_level_'+layer+'.png');}this.load.image('region-atlas','/rpg/assets/images/monster-tamer/map/main_1_level_background.png');for(const species of Object.keys(pokemonArt))this.load.image(species,spriteUrl(species));}
  create(){scene=this;if(this.failed.length){host.dataset.state='ERROR';status.textContent='素材加载失败：'+this.failed.join('、')+'；区域和方向按钮仍可使用。';legend.fallback('素材加载失败，图形地图不可用。');return;}host.dataset.state='READY';if(latest)this.present(latest);}
  present(w){if(this.failed.length)return;this.tweens.killAll();this.children.removeAll(true);const m=w.map;
   if(m.artTiles){const atlas=this.textures.get('region-atlas');for(let i=0;i<m.artTiles.length;i++){const frame='tile-'+m.artTiles[i];if(!atlas.has(frame))atlas.add(frame,0,(m.artTiles[i]%m.atlasColumns)*64,Math.floor(m.artTiles[i]/m.atlasColumns)*64,64,64);this.add.image(i%m.width*64,Math.floor(i/m.width)*64,'region-atlas',frame).setOrigin(0).setDepth(0);}
    for(const b of m.bridges??[]){this.add.rectangle(b.x*64,b.y*64,64,64,0xa57a58).setOrigin(0).setDepth(1);for(let i=0;i<4;i++)this.add.rectangle(b.x*64,b.y*64+i*16,64,3,0x73533e).setOrigin(0).setDepth(2);}
   }else this.add.image(0,0,w.region+'-background').setOrigin(0).setDepth(0);
   for(const detail of m.groundDetails??[])drawAtlasCell(this,'region-atlas',detail.cell,detail.x*64,detail.y*64).setDepth(1);
   for(const prop of m.scenery??[]){const texture=this.textures.get('region-atlas'),frame=prop.kind==='tree'?'scenery-tree':'scenery-rock';if(!texture.has(frame)){const crop=prop.kind==='tree'?[9*64,82*64,64,128]:[2*64,13*64,64,64];texture.add(frame,0,...crop);}this.add.image((prop.x+.5)*64,(prop.y+1)*64,'region-atlas',frame).setOrigin(.5,1).setDepth(prop.y*64+75);}
   const camp=m.recovery;if(camp){if(this.textures.exists('world-recovery'))this.add.image((camp.x+.5)*64,(camp.y+.5)*64,'world-recovery').setDisplaySize(46,46).setDepth(3901);this.add.ellipse((camp.x+.5)*64,(camp.y+.7)*64,44,12,0x29432e,.18).setDepth(3);this.add.text(camp.x*64-6,camp.y*64-18,'恢复点',{fontSize:'12px',color:'#c0fff4',stroke:'#29432e',strokeThickness:3}).setDepth(4000);}
   const exits=entranceViews(w,regionNames);for(const gate of exits)drawEntrance(this,gate,m,w.region);host.dataset.entrances=JSON.stringify(exits.map(({regionId,x,y,locked})=>({regionId,x,y,locked})));
   if(legendOptions.showBlocked)drawBlocked(this,blockedCells(m));
   for(const e of mapEntities(w)){const px=(e.x+.5)*64,py=(e.y+.5)*64,depth=e.y*64+100;this.add.ellipse(px,py+16,38,12,0x173824,.3).setDepth(depth);const image=this.add.image(px,py,e.species).setDisplaySize(52,52).setDepth(depth+1);
    if(e.role==='wild'){image.setInteractive({useHandCursor:true});image.on('pointerdown',()=>{host.focus();onTarget(e.id);});}
    const color=e.role==='partner'?0xb6efb1:e.id===selected?0xffffff:0xf2d074;this.add.rectangle(px,py,56,56).setStrokeStyle(e.id===selected?3:1,color,.8).setDepth(depth);
    this.add.rectangle(px-24,py-30,48,4,0x22392e).setOrigin(0,.5).setDepth(depth+2);this.add.rectangle(px-24,py-30,48*Math.max(0,e.hp/e.maxHp),4,e.hp/e.maxHp>.3?0xa6db79:0xf39563).setOrigin(0,.5).setDepth(depth+3);this.add.text(px-30,py+27,e.name+' L'+e.level,{fontSize:'10px',color:'#ffffff',stroke:'#233d2b',strokeThickness:3}).setDepth(depth+4);
   }
   if(previous?.region===w.region){for(const e of [...mapEntities(w),...w.encounters.filter(t=>t.state==='DEFEATED'&&previous.encounters.some(old=>old.id===t.id&&old.state==='WILD'))]){const old=e.role==='partner'?previous.partner:previous.encounters.find(t=>t.id===e.id);const delta=hpFeedbackDelta(old,e);if(delta){const x=(e.x+.5)*64,y=(e.y+.5)*64;const effect=this.add.text(x,y-40,(delta>0?'+':'')+delta,{fontSize:'24px',color:delta>0?'#c0fff4':'#ffe590',stroke:'#233d2b',strokeThickness:3}).setDepth(4100);if(globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches)this.time.delayedCall(900,()=>effect.destroy());else this.tweens.add({targets:effect,y:y-76,alpha:0,duration:900,onComplete:()=>effect.destroy()});}}}previous=w;
   if(m.foreground)this.add.image(0,0,w.region+'-foreground').setOrigin(0).setDepth(3000);this.cameras.main.setBounds(0,0,m.width*64,m.height*64);this.cameras.main.centerOn((w.x+.5)*64,(w.y+.5)*64);
   const camera=this.cameras.main,sx=Math.max(0,camera.scrollX),sy=Math.max(0,camera.scrollY);const bounds=boundaryLayout({width:m.width,height:m.height,startX:sx/64,startY:sy/64,cols:Math.min(m.width,832/64),rows:Math.min(m.height,576/64),tile:64,x:sx,y:sy});drawBoundary(this,bounds);legend.update(bounds,exits);
   host.dataset.region=w.region;host.dataset.turn=String(w.turn);host.dataset.entities=JSON.stringify(mapEntities(w).map(e=>({id:e.id,x:e.x,y:e.y,hp:e.hp})));status.textContent=(m.name??regionNames[w.region])+' · 第 '+w.wave+' 次探索 · 回合 '+w.turn+' · 当前位置 '+w.x+','+w.y;
  }
 }
 const game=new P.Game({type:P.CANVAS,parent:host,width:832,height:576,pixelArt:true,backgroundColor:'#203b32',scale:{mode:P.Scale.FIT,autoCenter:P.Scale.CENTER_BOTH},audio:{noAudio:true},scene:[Field]});
 const unbindKeys=bindMovementKeys({onMove,canMove,directionForKey:growthDirection});
 return {update(w,id){latest=w;selected=id;const next=JSON.stringify([w.region,w.x,w.y,w.turn,w.partner,w.encounters,w.regions,id]);if(next!==last){last=next;if(scene)scene.present(w);}},setCollisionVisible(value){legendOptions.showBlocked=Boolean(value);host.dataset.collisionOverlay=String(legendOptions.showBlocked);if(scene&&latest)scene.present(latest);},destroy(){unbindKeys();game.destroy(true);legend.destroy();}};
}
