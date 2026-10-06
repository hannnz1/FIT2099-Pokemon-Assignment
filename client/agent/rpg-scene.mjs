import {moveVisual,reducedMotion} from './scene-motion.mjs';
import {atlasCell,drawAtlasCell,drawTrainingBackdrop} from './map-polish.mjs';
import {installBuildingCutout} from './building-cutout.mjs';
import {worldArt} from './world-art.mjs';
import {boundaryLayout,blockedCells,attachBoundaryLegend,drawBoundary,drawBlocked} from './map-boundary.mjs';
import {projectRpg,bindMovementKeys,targetAt,rpgFeedback,controlHint,guideSteps} from './rpg-view.mjs';
import {spriteUrl,pokemonArt} from './pokemon-art.mjs';
const root='/rpg/assets/images/';
// Terrain uses Monster Tamer/AxulArt; Pokemon use consistent Gen III sprites.
export function mountRpg(host,{mode='quest',onMove=()=>{},canMove=()=>false,movement,onTarget=()=>{}}={}){
 const legend=attachBoundaryLegend(host);const status=host.parentElement.querySelector('.rpg-status');let latest=null,scene=null,lastKey='',selectedId=null,previous=null,sourceView=null;
 const mapInfo=document.createElement('details');mapInfo.className='map-accessible rpg-map-info';const mapInfoTitle=document.createElement('summary');mapInfoTitle.textContent='地图说明';mapInfoTitle.style.color='#dce8d3';mapInfo.append(mapInfoTitle);const boundaryNote=host.nextElementSibling;host.parentElement.append(mapInfo);if(boundaryNote?.classList.contains('map-boundary-legend'))mapInfo.append(boundaryNote);if(status)mapInfo.append(status);
 const setStatus=(text,state)=>{if(status)status.textContent=text;host.dataset.state=state;if(state==='ERROR')mapInfo.open=true;};
 if(!globalThis.Phaser){setStatus('Phaser 未能装载；可以继续使用下方文字地图和操作按钮。','ERROR');legend.fallback('图形地图未加载。');return {update(){},destroy(){legend.destroy();}};}
 const P=globalThis.Phaser;
 const guide=document.createElement('details');guide.className='rpg-guide';guide.open=true;
 const summary=document.createElement('summary');summary.textContent='首次试玩 · 捕捉与收藏';
 const steps=document.createElement('ol'),hint=document.createElement('p');hint.className='rpg-operation-hint';hint.setAttribute('role','status');
 guide.append(summary,steps,hint);host.parentElement.after(guide);
 const reduced=reducedMotion;
 class LiveScene extends P.Scene {
  constructor(){super('JavaLiveRpg');this.failure=[];this.layer=null;this.entities=new Map();this.mapKey=null;}
  preload(){
   this.load.on('loaderror',file=>{if(!file.key.startsWith('world-')&&!file.key.startsWith('npc-'))this.failure.push(file.key);});
   for(const [id,art]of Object.entries(worldArt))if(art.kind==='portrait')this.load.svg(art.texture,'/rpg/'+art.path,{width:art.width,height:art.height});
   for(const [id,art]of Object.entries(worldArt))if(art.kind==='symbol')this.load.svg('world-'+id,'/rpg/assets/ui/world/'+id+'.svg',{width:64,height:64});
   for(const [key,path]of Object.entries({terrain:'monster-tamer/map/main_1_level_background.png',bush:'monster-tamer/map/bushes.png',forest:'monster-tamer/battle-backgrounds/forest-background.png'}))this.load.image(key,root+path);
   for(const species of Object.keys(pokemonArt))this.load.image(species,spriteUrl(species));
   this.load.spritesheet('human',root+'axulart/character/custom.png',{frameWidth:64,frameHeight:88});
  }
  create(){
   scene=this;for(const key of ['terrain','bush','forest','MUDKIP','TREECKO','TORCHIC','human'])if(!this.textures.exists(key))this.failure.push(key);if(this.failure.length){setStatus('RPG 素材装载失败：'+this.failure.join('、')+'；文字地图仍可使用。','ERROR');legend.fallback('RPG 素材未加载，图形地图不可用。');return;}
   this.textures.get('terrain').add('grass',0,1152,320,64,64);
   this.textures.get('terrain').add('path',0,0,832,64,64);
   this.textures.get('terrain').add('water',0,2176,1472,64,64);
   for(const art of Object.values(worldArt))if(art.kind==='atlas'){const c=art.crop;this.textures.get('terrain').add(art.frame,0,c.x,c.y,c.width,c.height);}
   for(const id of ['market','laboratory'])installBuildingCutout(this.textures,this.textures.get('terrain').getSourceImage(),worldArt[id]);
   this.textures.get('bush').add('bush',0,0,0,64,64);
   this.cameras.main.setBackgroundColor('#233f36');host.tabIndex=0;host.setAttribute('aria-label','Phaser RPG 实时游戏地图；方向键或WASD移动，点击野生伙伴选择目标');
   this.input.on('pointerdown',pointer=>{if(mode==='training')return;host.focus({preventScroll:true});if(!latest||!this.grid)return;const {tile,x,y}=this.grid;const point=this.cameras.main.getWorldPoint(pointer.x,pointer.y),wx=Math.floor((point.x-x)/tile),wy=Math.floor((point.y-y)/tile);if(pointer.x<x||pointer.y<y||wx>=latest.width||wy>=latest.height)return;const id=targetAt(latest,wx,wy);if(id)onTarget(id);});
   if(mode==='training')host.addEventListener('pointerdown',event=>{
    const canvas=host.querySelector('canvas');if(!canvas||!latest||!this.grid)return;
    const bounds=canvas.getBoundingClientRect(),px=(event.clientX-bounds.left)*832/bounds.width,py=(event.clientY-bounds.top)*576/bounds.height;
    const {tile,x,y}=this.grid;if(px<x||py<y)return;
    const id=targetAt(latest,Math.floor((px-x)/tile),Math.floor((py-y)/tile));
    host.focus({preventScroll:true});if(id)onTarget(id);
   });
   if(latest)this.present(latest);else setStatus('RPG 素材已装载，正在等待 Java 游戏状态。','LOADED');
  }
  text(x,y,message,size=12,color='#ffffff'){const t=this.add.text(x,y,message,{fontFamily:'Microsoft YaHei, sans-serif',fontSize:`${size}px`,color,stroke:'#172b26',strokeThickness:3});this.layer.add(t);return t;}
  present(p){
   if(previous?.taskId===p.taskId&&p.turn<previous.turn)return;const effects=rpgFeedback(previous,p);const old=previous;previous=p;
   if(this.failure.length)return;
   const tile=mode==='training'?80:64,x=Math.max(0,(832-p.width*tile)/2),y=mode==='training'?152:0;this.grid={tile,x,y};const bounds=boundaryLayout({...p,tile,x,y,startX:0,startY:0,cols:p.width,rows:p.height});legend.update(boundaryLayout({...p,tile,x:0,y:0}));
   const mapKey=JSON.stringify([p.taskId,p.tiles,p.locations,p.width,p.height]);if(mapKey!==this.mapKey){for(const r of this.entities.values()){if(r.motion)r.motion.stop();r.group.destroy(true);}this.entities.clear();this.cameraAnchor=null;if(this.layer)this.layer.destroy(true);this.layer=this.add.container();this.mapKey=mapKey;host.dataset.mapBuilds=String(Number(host.dataset.mapBuilds??0)+1);
   if(mode==='training')drawTrainingBackdrop(this,this.layer,this.grid);
   for(const t of p.tiles){const tx=x+t.x*tile,ty=y+t.y*tile;
    if(mode==='training')drawAtlasCell(this,'terrain',t.y===1?atlasCell(1,13):atlasCell(20+(t.x%2),5),tx,ty,tile,this.layer);
    else this.layer.add(this.add.image(tx+tile/2,ty+tile/2,'terrain',t.kind==='grass'||t.kind==='wall'||t.kind==='hay'?'grass':t.kind==='water'?'water':'path').setDisplaySize(tile,tile));
    if(t.kind==='wall')this.layer.add(this.add.image(tx+tile/2,ty+tile/2,'bush','bush').setDisplaySize(tile,tile));
    if(['floor','lava','water','hay'].includes(t.kind))this.layer.add(this.add.rectangle(tx+tile/2,ty+tile/2,tile,tile,({floor:0xc5b58a,lava:0xe55c36,water:0x63bce0,hay:0xa4b25d})[t.kind],t.kind==='floor'?.9:.55));
    this.layer.add(this.add.rectangle(tx+tile/2,ty+tile/2,tile,tile).setStrokeStyle(1,0x284634,.14));
   }
   drawBlocked(this,blockedCells(p),{...this.grid,startX:0,startY:0,cols:p.width,rows:p.height,layer:this.layer});
   for(const [id,pos]of Object.entries(p.locations)){if(!['orchard','market','laboratory','alternative'].includes(id))continue;const tx=x+(pos.x+.5)*tile,ty=y+(pos.y+.5)*tile;const art=worldArt[id],cutout=['market','laboratory'].includes(id),key=cutout?art.frame+'-cutout':art.kind==='symbol'?'world-'+id:'terrain';if(this.textures.exists(key))this.layer.add(this.add.image(tx,ty-4,key,art.kind==='atlas'&&!cutout?art.frame:undefined).setDisplaySize(tile*.8,art.crop?tile*.8*art.crop.height/art.crop.width:tile*.8));this.text(tx-22,ty+tile*.22,art.label,12,'#ffe1a0');}
   drawBoundary(this,bounds,{layer:this.layer});this.layer.setDepth(-1);this.cameras.main.setBounds(0,0,Math.max(832,p.width*tile),Math.max(576,p.height*tile));this.cameras.main.roundPixels=true;
   }
   const resources=[...(sourceView?.player?.resources??[])];if(Number(sourceView?.world?.visibleBerry)>0)resources.push({x:Number(sourceView.world.x),y:Number(sourceView.world.y),quantity:Number(sourceView.world.visibleBerry)});
   const resourceKey=JSON.stringify(resources);if(resourceKey!==this.resourceKey){this.resources?.destroy(true);this.resources=this.add.container().setDepth(5000);this.resourceKey=resourceKey;const seen=new Set();for(const res of resources){const key=res.x+','+res.y;if(seen.has(key)||Number(res.quantity??res.count)<=0)continue;seen.add(key);if(this.textures.exists('world-berry'))this.resources.add(this.add.image(x+(res.x+.75)*tile,y+(res.y+.28)*tile,'world-berry').setDisplaySize(22,22));}}
   const ids=new Set(p.entities.map(e=>e.id));for(const [id,r]of this.entities)if(!ids.has(id)){if(r.motion)r.motion.stop();r.group.destroy(true);this.entities.delete(id);}
   for(const e of p.entities){let r=this.entities.get(e.id);if(!r){r=this.makeEntity(e,tile);this.entities.set(e.id,r);}
    const human=!e.species,roleArt=worldArt[e.id.replace('npc-','')],portrait=human&&roleArt?.kind==='portrait'&&this.textures.exists(roleArt.texture),texture=portrait?roleArt.texture:human?'human':e.species;
    if(r.texture!==texture){r.sprite.setTexture(texture,human&&!portrait?(roleArt?.frame??10):undefined).setDisplaySize(human?36:tile*.68,human?49.5:tile*.68);r.texture=texture;}
    r.name.setText(e.role==='wild'?e.name.replace('野生','野生·'):e.name);r.selected.setVisible(e.id===selectedId);r.targetLabel.setVisible(e.id===selectedId);
    const ratio=Math.max(0,Math.min(1,(e.hp??0)/(e.maxHp||1)));r.hpBase.setVisible(Boolean(e.maxHp));r.hp.setVisible(Boolean(e.maxHp)).setSize((tile-10)*ratio,4).setFillStyle(ratio>.3?0x8de16d:0xf29966);
    const next={...e,region:p.taskId,turn:p.turn};moveVisual(this,r,r.data,next,tile,{x,y});
    r.data=next;r.group.setDepth(e.y*tile+100);
    if(effects.some(f=>f.kind==='damage'&&f.id===e.id)&&!reduced()){r.sprite.setTint(0xff7777);this.tweens.add({targets:r.sprite,alpha:.35,duration:80,yoyo:true,repeat:1,onComplete:()=>r.sprite.clearTint()});}
   }
   const anchor=this.entities.get((sourceView?.cooperative?p.entities.find(e=>e.id==='player'):p.entities.find(e=>e.id==='leader'))?.id);
   if(anchor&&this.cameraAnchor!==anchor.group){this.cameras.main.startFollow(anchor.group,true,.18,.18);this.cameras.main.setDeadzone(128,96);this.cameraAnchor=anchor.group;this.cameras.main.centerOn(anchor.group.x,anchor.group.y);}
   for(const f of effects.filter(f=>f.kind!=='move')){
    const tx=x+(f.x+.5)*tile,ty=y+(f.y+.5)*tile;
    const label=this.add.text(tx-25,ty-18,f.kind==='damage'?`−${f.amount}`:f.kind==='capture'?'收服成功':f.kind==='miss'?'未命中':'已击败',{fontSize:'14px',color:f.kind==='damage'?'#ffbbb0':'#fff3a1',stroke:'#172b26',strokeThickness:3}).setDepth(5100);
    if(!reduced())this.tweens.add({targets:label,y:ty-40,alpha:0,duration:850,onComplete:()=>label.destroy()});else this.time.delayedCall(850,()=>label.destroy());
   }
   host.dataset.feedback=JSON.stringify(effects);host.dataset.selectedTarget=p.entities.some(e=>e.id===selectedId&&e.role==='wild')?selectedId:'';
   if(!this.night)this.night=this.add.rectangle(416,288,832,576,0x102949,.28).setScrollFactor(0).setDepth(4900);this.night.setVisible(p.period==='NIGHT');host.dataset.objects=String(this.children.length);host.dataset.entityObjects=String(this.entities.size);
   const rows=p.entities.map(e=>({id:e.id,name:e.name,x:e.x,y:e.y,hp:e.hp}));host.dataset.entities=JSON.stringify(rows);host.dataset.turn=String(p.turn);host.dataset.taskId=p.taskId??'';
   setStatus(`Phaser RPG · Java 实时状态 · 第 ${p.turn??'—'} 回合 · 方向键/WASD移动，点击野生目标选择 · 白圈为当前目标`,'READY');
  }
  makeEntity(e,tile){
   const group=this.add.container((e.x+.5)*tile,(e.y+.5)*tile),color=({leader:0x66d6ef,player:0xd69bff,owned:0xa8ed82,wild:0xffd276,npc:0xe4d6bb})[e.role];
   const shadow=this.add.ellipse(0,13,tile*.65,14,0x132e22,.3),border=this.add.rectangle(0,0,tile-4,tile-4).setStrokeStyle(e.role==='owned'?3:2,color,.7),selected=this.add.ellipse(0,0,tile-7,tile-7).setStrokeStyle(4,0xffffff,1);
   const sprite=this.add.image(0,e.species?-5:-8,e.species??'human'),style={fontFamily:'Microsoft YaHei, sans-serif',fontSize:'10px',color:'#ffffff',stroke:'#172b26',strokeThickness:3};
   const targetLabel=this.add.text(-tile/2+3,-tile/2+9,'当前目标',style),name=this.add.text(-tile/2+3,e.species?15:22,'',style),hpBase=this.add.rectangle(0,-tile/2+4,tile-10,4,0x25332a),hp=this.add.rectangle(-tile/2+5,-tile/2+4,tile-10,4,0x8de16d).setOrigin(0,.5);
   group.add([shadow,border,selected,sprite,name,hpBase,hp,targetLabel]);return {group,sprite,selected,targetLabel,name,hpBase,hp,data:null};
  }
 }
 const game=new P.Game({type:P.CANVAS,parent:host,width:832,height:576,pixelArt:true,backgroundColor:'#233f36',scale:{mode:P.Scale.FIT,autoCenter:P.Scale.CENTER_BOTH},audio:{noAudio:true},scene:[LiveScene]});
 let size='',frame;const observer=new ResizeObserver(entries=>{const r=entries[0].contentRect,next=Math.round(r.width)+':'+Math.round(r.height);if(next===size)return;size=next;cancelAnimationFrame(frame);frame=requestAnimationFrame(()=>{game.scale.getParentBounds();game.scale.refresh();});});observer.observe(host);
 const unbindKeys=bindMovementKeys({onMove,canMove,controller:movement,isReady:()=>host.dataset.state==='READY'});
 return {update(view,{targetId=null,online=true,busy=false}={}){selectedId=targetId;sourceView=view;
 summary.textContent=view.cooperative?'首次试玩 · 与 AI 分工合作':'首次试玩 · 捕捉与收藏';
 const guidance=guideSteps(view,mode);steps.replaceChildren(...guidance.map(step=>{const li=document.createElement('li');li.textContent=(step.done?'✓ ':'')+step.label;li.dataset.done=String(step.done);return li;}));
 hint.textContent=view.cooperative&&online&&!busy&&view.playerAllowed?'方向键/WASD控制你自己，水跃鱼继续执行委托。接管水跃鱼需要先暂停。':view.cooperative&&view.status==='WAITING_APPROVAL'?'等待购买审批，双方世界动作暂停。':controlHint(view,online,busy);
 const p=projectRpg(view,mode);if(host.dataset.state==='ERROR')legend.fallback('RPG 素材未加载，图形地图不可用。');else legend.update(boundaryLayout({...p,tile:mode==='training'?80:64,x:(832-p.cols*(mode==='training'?80:64))/2,y:mode==='training'?152:0}));const key=JSON.stringify([p,selectedId]);latest=p;if(key!==lastKey){lastKey=key;if(scene)scene.present(p);}},destroy(){observer.disconnect();cancelAnimationFrame(frame);unbindKeys();game.destroy(true);guide.remove();legend.destroy();}};
}
