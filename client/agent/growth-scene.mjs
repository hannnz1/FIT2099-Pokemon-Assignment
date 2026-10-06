import {battleEvents,createEffectQueue} from './battle-effects.mjs';
import {reconcileEntities} from './scene-entities.mjs';
import {moveVisual,reducedMotion} from './scene-motion.mjs';
import {cameraViewport} from './player-polish.mjs';
import {drawAtlasCell} from './map-polish.mjs';
import {bindMovementKeys} from '/rpg-view.mjs';
import {entranceViews,drawEntrance} from './growth-entrance.mjs';
import {boundaryLayout,blockedCells,mapExits,attachBoundaryLegend,drawBoundary,drawBlocked} from './map-boundary.mjs';
import {mapEntities,growthDirection,regionNames,hpFeedbackDelta} from './growth-view.mjs';
import {pokemonArt,spriteUrl} from './pokemon-art.mjs';
export function mountGrowth(host,{onTarget,onMove,canMove,movement}){
 const legendOptions={showBlocked:false,exitLabel:'木质路牌：区域出口，走到入口自动切换'};const legend=attachBoundaryLegend(host,legendOptions);host.dataset.collisionOverlay='false';let latest=null,scene=null,selected=null,last='',previous=null;const status=document.querySelector('#map-status');host.tabIndex=0;host.setAttribute('aria-label','成长地图；方向键或 WASD 移动，点击野生伙伴选择目标');
 if(!globalThis.Phaser){status.textContent='Phaser 未加载，仍可使用区域和方向按钮';host.dataset.state='ERROR';legend.fallback('图形地图未加载。');return {update(){},destroy(){legend.destroy();}};}
 const P=globalThis.Phaser;let viewport=cameraViewport(innerWidth);host.style.aspectRatio=viewport.width+'/'+viewport.height;
 class Field extends P.Scene{
  constructor(){super('GrowthField');this.failed=[];this.entities=new Map();this.mapKey=null;}
  preload(){this.load.on('loaderror',f=>{if(!f.key.startsWith('world-'))this.failed.push(f.key);});for(const id of ['recovery'])this.load.svg('world-'+id,'/rpg/assets/ui/world/'+id+'.svg',{width:64,height:64});for(const region of ['lab','forest']){const path=region==='lab'?'buildings/building_1':'forest_1';for(const layer of ['background','foreground'])this.load.image(region+'-'+layer,'/rpg/assets/images/monster-tamer/map/'+path+'_level_'+layer+'.png');}this.load.image('region-atlas','/rpg/assets/images/monster-tamer/map/main_1_level_background.png');for(const species of Object.keys(pokemonArt))this.load.image(species,spriteUrl(species));}
  create(){scene=this;if(this.failed.length){host.dataset.state='ERROR';status.textContent='素材加载失败：'+this.failed.join('、')+'；区域和方向按钮仍可使用。';legend.fallback('素材加载失败，图形地图不可用。');return;}host.dataset.state='READY';if(latest)this.present(latest);}
  present(w){if(this.failed.length||previous?.region===w.region&&w.turn<previous.turn)return;const m=w.map,exits=entranceViews(w,regionNames);
   const mapKey=JSON.stringify([w.region,m,w.regions,legendOptions.showBlocked]);
   if(this.mapKey!==mapKey){if(previous&&previous.region!==w.region&&!document.hidden&&!reducedMotion())host.animate?.([{opacity:.35},{opacity:1}],{duration:180});for(const record of this.entities.values())if(record.motion)record.motion.stop();this.entities.clear();this.children.removeAll(true);this.boundaryGraphic=null;this.mapKey=mapKey;this.cameraAnchor=null;host.dataset.mapBuilds=String(Number(host.dataset.mapBuilds??0)+1);
   if(m.artTiles){const atlas=this.textures.get('region-atlas');for(let i=0;i<m.artTiles.length;i++){const frame='tile-'+m.artTiles[i];if(!atlas.has(frame))atlas.add(frame,0,(m.artTiles[i]%m.atlasColumns)*64,Math.floor(m.artTiles[i]/m.atlasColumns)*64,64,64);this.add.image(i%m.width*64,Math.floor(i/m.width)*64,'region-atlas',frame).setOrigin(0).setDepth(0);}
    for(const b of m.bridges??[]){this.add.rectangle(b.x*64,b.y*64,64,64,0xa57a58).setOrigin(0).setDepth(1);for(let i=0;i<4;i++)this.add.rectangle(b.x*64,b.y*64+i*16,64,3,0x73533e).setOrigin(0).setDepth(2);}
   }else this.add.image(0,0,w.region+'-background').setOrigin(0).setDepth(0);
   for(const detail of m.groundDetails??[])drawAtlasCell(this,'region-atlas',detail.cell,detail.x*64,detail.y*64).setDepth(1);
   for(const prop of m.scenery??[]){const texture=this.textures.get('region-atlas'),frame=prop.kind==='tree'?'scenery-tree':'scenery-rock';if(!texture.has(frame)){const crop=prop.kind==='tree'?[9*64,82*64,64,128]:[2*64,13*64,64,64];texture.add(frame,0,...crop);}this.add.image((prop.x+.5)*64,(prop.y+1)*64,'region-atlas',frame).setOrigin(.5,1).setDepth(prop.y*64+75);}
   const camp=m.recovery;if(camp){const clinic=w.region==='lab';if(clinic){const atlas=this.textures.get('lab-background');if(!atlas.has('clinic'))atlas.add('clinic',0,128,64,64,96);this.add.image((camp.x+.5)*64,(camp.y+.5)*64,'lab-background','clinic').setDisplaySize(38,57).setDepth(3901);}else if(this.textures.exists('world-recovery'))this.add.image((camp.x+.5)*64,(camp.y+.5)*64,'world-recovery').setDisplaySize(46,46).setDepth(3901);this.add.ellipse((camp.x+.5)*64,(camp.y+.7)*64,44,12,0x29432e,.18).setDepth(3);this.add.text(camp.x*64-6,camp.y*64-18,w.region==='lab'?'休整设备':'恢复点',{fontSize:'12px',color:'#c0fff4',stroke:'#29432e',strokeThickness:3}).setDepth(4000);}
   for(const gate of exits)drawEntrance(this,gate,m,w.region);host.dataset.entrances=JSON.stringify(exits.map(({regionId,x,y,locked})=>({regionId,x,y,locked})));
   if(legendOptions.showBlocked)drawBlocked(this,blockedCells(m));
   if(m.foreground)this.add.image(0,0,w.region+'-foreground').setOrigin(0).setDepth(3000);
   this.cameras.main.setBounds(0,0,m.width*64,m.height*64);this.cameras.main.roundPixels=true;
   }
   const nextEntities=mapEntities(w),diff=reconcileEntities([...this.entities.values()].map(r=>r.data),nextEntities);
   for(const e of diff.removed){const r=this.entities.get(e.id);if(r.motion)r.motion.stop();r.group.destroy(true);this.entities.delete(e.id);}
   for(const e of nextEntities){let r=this.entities.get(e.id);if(!r){r=this.makeEntity(e);this.entities.set(e.id,r);}
    const own=e.role==='partner',chosen=e.id===selected,ratio=Math.max(0,Math.min(1,e.hp/e.maxHp));
    if(r.data?.species!==e.species)r.image.setTexture(e.species).setDisplaySize(52,52);
    r.name.setText((own?'我的 · ':'')+e.name+' L'+e.level);r.hp.setSize(48*ratio,4).setFillStyle(ratio>.3?0xa6db79:0xf39563);
    r.selected.setVisible(chosen);r.ownRing.setVisible(own);r.arrow.setVisible(own);r.show=own||chosen;
    for(const label of r.labels)label.setVisible(r.show||r.hover);
    moveVisual(this,r,r.data?{...r.data,region:r.region,turn:r.turn}:null,{...e,region:w.region,turn:w.turn});
    r.data=e;r.region=w.region;r.turn=w.turn;r.group.setDepth(e.y*64+100);
   }
   const partner=this.entities.get(w.partner?.captureId);
   if(partner){if(this.cameraAnchor!==partner.group){this.cameras.main.startFollow(partner.group,true,.18,.18);this.cameras.main.setDeadzone(128,96);this.cameraAnchor=partner.group;this.cameras.main.centerOn(partner.group.x,partner.group.y);}}
   if(previous?.region===w.region){for(const e of [...mapEntities(w),...w.encounters.filter(t=>t.state==='DEFEATED'&&previous.encounters.some(old=>old.id===t.id&&old.state==='WILD'))]){const old=e.role==='partner'?previous.partner:previous.encounters.find(t=>t.id===e.id);const delta=hpFeedbackDelta(old,e);if(delta){const x=(e.x+.5)*64,y=(e.y+.5)*64;const effect=this.add.text(x,y-40,(delta>0?'+':'')+delta,{fontSize:'24px',color:delta>0?'#c0fff4':'#ffe590',stroke:'#233d2b',strokeThickness:3}).setDepth(4100);if(reducedMotion()||document.hidden)this.time.delayedCall(900,()=>effect.destroy());else this.tweens.add({targets:effect,y:y-76,alpha:0,duration:900,onComplete:()=>effect.destroy()});}}}previous=w;
   host.dataset.objects=String(this.children.length);host.dataset.entityObjects=String(this.entities.size);
   const camera=this.cameras.main,sx=Math.max(0,camera.scrollX),sy=Math.max(0,camera.scrollY);const bounds=boundaryLayout({width:m.width,height:m.height,startX:sx/64,startY:sy/64,cols:Math.min(m.width,viewport.width/64),rows:Math.min(m.height,viewport.height/64),tile:64,x:sx,y:sy});if(this.boundaryGraphic)this.boundaryGraphic.destroy();this.boundaryGraphic=legendOptions.showBlocked?drawBoundary(this,bounds):null;legend.update(bounds,exits);
   host.dataset.region=w.region;host.dataset.turn=String(w.turn);host.dataset.entities=JSON.stringify(mapEntities(w).map(e=>({id:e.id,x:e.x,y:e.y,hp:e.hp})));status.textContent=(m.name??regionNames[w.region])+' · 第 '+w.wave+' 次探索 · 回合 '+w.turn+' · 当前位置 '+w.x+','+w.y;
  }
  makeEntity(e){
   const group=this.add.container((e.x+.5)*64,(e.y+.5)*64),r={group,data:null,show:false,hover:false};
   const shadow=this.add.ellipse(0,16,38,12,0x173824,.3),ownRing=this.add.ellipse(0,18,46,16).setStrokeStyle(3,0xf9ffe3),selectedRing=this.add.rectangle(0,0,58,58).setStrokeStyle(2,0xffdf83);
   const image=this.add.image(0,0,e.species).setDisplaySize(52,52),hpBase=this.add.rectangle(-24,-30,48,4,0x22392e).setOrigin(0,.5),hp=this.add.rectangle(-24,-30,48,4,0xa6db79).setOrigin(0,.5),name=this.add.text(-30,27,'',{fontSize:'10px',color:'#ffffff',stroke:'#233d2b',strokeThickness:3}),arrow=this.add.triangle(0,-38,0,0,12,0,6,8,0xf9ffe3);
   group.add([shadow,ownRing,selectedRing,image,hpBase,hp,name,arrow]);Object.assign(r,{image,hp,name,ownRing,arrow,selected:selectedRing,labels:[hpBase,hp,name]});
   if(e.role==='wild'){image.setInteractive({useHandCursor:true});image.on('pointerdown',()=>{host.focus({preventScroll:true});onTarget(e.id);});image.on('pointerover',()=>{r.hover=true;r.labels.forEach(l=>l.setVisible(true));});image.on('pointerout',()=>{r.hover=false;r.labels.forEach(l=>l.setVisible(r.show));});}
   return r;
  }
 }
 let effectsSnapshot=null;
 const effects=createEffectQueue({play:async(event,signal)=>{if(!scene||document.hidden||reducedMotion()||signal.aborted)return;
  const find=id=>scene.entities.get(id)??[...scene.entities.values()].find(r=>r.data?.captureId===id||latest?.encounters?.find(e=>e.id===r.data?.id)?.captureId===id);
  const actor=find(event.actorId),target=find(event.targetId),record=event.kind==='residual'?actor:target;
  const label=event.kind==='miss'?'未命中':event.kind==='prevented'?'状态影响，未出招':event.outcome==='IMMUNE'?'没有效果':event.kind==='residual'?'异常状态伤害':'';
  if(label)status.textContent=label;
  if(event.kind==='move'&&actor){const x=actor.image.x;await new Promise(resolve=>{const tween=scene.tweens.add({targets:actor.image,x:x+8,duration:65,yoyo:true,onComplete:resolve});signal.addEventListener('abort',()=>{tween.stop();actor.image.x=x;resolve();},{once:true});});}
  if(record&&event.damage>0&&!signal.aborted){await new Promise(resolve=>{const tween=scene.tweens.add({targets:record.image,alpha:.35,duration:55,yoyo:true,repeat:1,onComplete:resolve});signal.addEventListener('abort',()=>{tween.stop();record.image.alpha=1;resolve();},{once:true});});}
 }});
 const clearEffects=()=>{if(document.hidden)effects.clear();};document.addEventListener('visibilitychange',clearEffects);
 const game=new P.Game({type:P.CANVAS,parent:host,width:viewport.width,height:viewport.height,pixelArt:true,backgroundColor:'#203b32',scale:{mode:P.Scale.FIT,autoCenter:P.Scale.CENTER_BOTH},audio:{noAudio:true},scene:[Field]});
 const resize=()=>{const next=cameraViewport(innerWidth);if(next.width===viewport.width)return;viewport=next;game.scale.setGameSize(next.width,next.height);host.style.aspectRatio=next.width+'/'+next.height;if(scene&&latest)scene.present(latest);};window.addEventListener('resize',resize);
 let previousSize='',refreshFrame;const observer=new ResizeObserver(entries=>{const r=entries[0].contentRect,key=Math.round(r.width)+':'+Math.round(r.height);if(key===previousSize)return;previousSize=key;cancelAnimationFrame(refreshFrame);refreshFrame=requestAnimationFrame(()=>{game.scale.getParentBounds();game.scale.refresh();});});observer.observe(host);
 const unbindKeys=bindMovementKeys({onMove,canMove,directionForKey:growthDirection,controller:movement});
 return {resetEffects(){effects.clear();effectsSnapshot=null;previous=null;},update(w,id,context){if(latest&&latest.region!==w.region)effects.clear();if(context){if(effectsSnapshot?.taskId!==context.taskId)effects.clear();const events=battleEvents(effectsSnapshot,context);effectsSnapshot=context;effects.enqueue(events);}latest=w;selected=id;const next=JSON.stringify([w.region,w.x,w.y,w.turn,w.partner,w.encounters,w.regions,id]);if(next!==last){last=next;if(scene)scene.present(w);}},setCollisionVisible(value){legendOptions.showBlocked=Boolean(value);host.dataset.collisionOverlay=String(legendOptions.showBlocked);if(scene&&latest)scene.present(latest);},destroy(){effects.clear();document.removeEventListener('visibilitychange',clearEffects);window.removeEventListener('resize',resize);observer.disconnect();cancelAnimationFrame(refreshFrame);unbindKeys();game.destroy(true);legend.destroy();}};
}
