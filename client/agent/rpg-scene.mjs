import {atlasCell,drawAtlasCell,drawTrainingBackdrop} from './map-polish.mjs';
import {installBuildingCutout} from './building-cutout.mjs';
import {worldArt} from './world-art.mjs';
import {boundaryLayout,blockedCells,attachBoundaryLegend,drawBoundary,drawBlocked} from './map-boundary.mjs';
import {projectRpg,bindMovementKeys,targetAt,rpgFeedback,controlHint,guideSteps} from './rpg-view.mjs';
import {spriteUrl,pokemonArt} from './pokemon-art.mjs';
const root='/rpg/assets/images/';
// Terrain uses Monster Tamer/AxulArt; Pokemon use consistent Gen III sprites.
export function mountRpg(host,{mode='quest',onMove=()=>{},canMove=()=>false,onTarget=()=>{}}={}){
 const legend=attachBoundaryLegend(host);const status=host.parentElement.querySelector('.rpg-status');let latest=null,scene=null,lastKey='',selectedId=null,previous=null,sourceView=null;
 const mapInfo=document.createElement('details');mapInfo.className='map-accessible rpg-map-info';const mapInfoTitle=document.createElement('summary');mapInfoTitle.textContent='地图说明';mapInfoTitle.style.color='#dce8d3';mapInfo.append(mapInfoTitle);const boundaryNote=host.nextElementSibling;host.parentElement.append(mapInfo);if(boundaryNote?.classList.contains('map-boundary-legend'))mapInfo.append(boundaryNote);if(status)mapInfo.append(status);
 const setStatus=(text,state)=>{if(status)status.textContent=text;host.dataset.state=state;if(state==='ERROR')mapInfo.open=true;};
 if(!globalThis.Phaser){setStatus('Phaser 未能装载；可以继续使用下方文字地图和操作按钮。','ERROR');legend.fallback('图形地图未加载。');return {update(){},destroy(){legend.destroy();}};}
 const P=globalThis.Phaser;
 const guide=document.createElement('details');guide.className='rpg-guide';guide.open=true;
 const summary=document.createElement('summary');summary.textContent='首次试玩 · 捕捉与收藏';
 const steps=document.createElement('ol'),hint=document.createElement('p');hint.className='rpg-operation-hint';hint.setAttribute('role','status');
 guide.append(summary,steps,hint);host.parentElement.after(guide);
 const reduced=()=>globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches??false;
 class LiveScene extends P.Scene {
  constructor(){super('JavaLiveRpg');this.failure=[];this.layer=null;}
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
   this.input.on('pointerdown',pointer=>{host.focus();if(!latest||!this.grid)return;const {tile,x,y}=this.grid;const wx=latest.startX+Math.floor((pointer.x-x)/tile),wy=latest.startY+Math.floor((pointer.y-y)/tile);if(pointer.x<x||pointer.y<y||wx>=latest.startX+latest.cols||wy>=latest.startY+latest.rows)return;const id=targetAt(latest,wx,wy);if(id)onTarget(id);});
   if(latest)this.present(latest);else setStatus('RPG 素材已装载，正在等待 Java 游戏状态。','LOADED');
  }
  text(x,y,message,size=12,color='#ffffff'){const t=this.add.text(x,y,message,{fontFamily:'Microsoft YaHei, sans-serif',fontSize:`${size}px`,color,stroke:'#172b26',strokeThickness:3});this.layer.add(t);return t;}
  present(p){
   const effects=rpgFeedback(previous,p);previous=p;this.tweens.killAll();
   if(this.failure.length)return;if(this.layer)this.layer.destroy(true);this.layer=this.add.container();
   const tile=mode==='training'?80:64,x=(832-p.cols*tile)/2,y=mode==='training'?152:0;this.grid={tile,x,y};const bounds=boundaryLayout({...p,tile,x,y});legend.update(bounds);
   if(mode==='training')drawTrainingBackdrop(this,this.layer,this.grid);
   for(const t of p.tiles){if(t.x<p.startX||t.x>=p.startX+p.cols||t.y<p.startY||t.y>=p.startY+p.rows)continue;const tx=x+(t.x-p.startX)*tile,ty=y+(t.y-p.startY)*tile;
    if(mode==='training')drawAtlasCell(this,'terrain',t.y===1?atlasCell(1,13):atlasCell(20+(t.x%2),5),tx,ty,tile,this.layer);
    else this.layer.add(this.add.image(tx+tile/2,ty+tile/2,'terrain',t.kind==='grass'||t.kind==='wall'||t.kind==='hay'?'grass':t.kind==='water'?'water':'path').setDisplaySize(tile,tile));
    if(t.kind==='wall')this.layer.add(this.add.image(tx+tile/2,ty+tile/2,'bush','bush').setDisplaySize(tile,tile));
    if(['floor','lava','water','hay'].includes(t.kind))this.layer.add(this.add.rectangle(tx+tile/2,ty+tile/2,tile,tile,({floor:0xc5b58a,lava:0xe55c36,water:0x63bce0,hay:0xa4b25d})[t.kind],t.kind==='floor'?.9:.55));
    this.layer.add(this.add.rectangle(tx+tile/2,ty+tile/2,tile,tile).setStrokeStyle(1,0x284634,.14));
   }
   drawBlocked(this,blockedCells(p),{...this.grid,startX:p.startX,startY:p.startY,cols:p.cols,rows:p.rows,layer:this.layer});
   for(const [id,pos]of Object.entries(p.locations)){if(!['orchard','market','laboratory','alternative'].includes(id)||pos.x<p.startX||pos.x>=p.startX+p.cols||pos.y<p.startY||pos.y>=p.startY+p.rows)continue;const tx=x+(pos.x-p.startX+.5)*tile,ty=y+(pos.y-p.startY+.5)*tile;const art=worldArt[id],cutout=['market','laboratory'].includes(id),key=cutout?art.frame+'-cutout':art.kind==='symbol'?'world-'+id:'terrain';if(this.textures.exists(key))this.layer.add(this.add.image(tx,ty-4,key,art.kind==='atlas'&&!cutout?art.frame:undefined).setDisplaySize(tile*.8,art.crop?tile*.8*art.crop.height/art.crop.width:tile*.8));this.text(tx-22,ty+tile*.22,art.label,12,'#ffe1a0');}
   const resources=[...(sourceView?.player?.resources??[])];if(Number(sourceView?.world?.visibleBerry)>0&&Number.isFinite(Number(sourceView.world.x))&&Number.isFinite(Number(sourceView.world.y)))resources.push({x:Number(sourceView.world.x),y:Number(sourceView.world.y),quantity:Number(sourceView.world.visibleBerry)});const seen=new Set();for(const resource of resources){const id=resource.x+','+resource.y;if(seen.has(id)||Number(resource.quantity??resource.count)<=0||resource.x<p.startX||resource.y<p.startY||resource.x>=p.startX+p.cols||resource.y>=p.startY+p.rows)continue;seen.add(id);const tx=x+(resource.x-p.startX+.5)*tile,ty=y+(resource.y-p.startY+.5)*tile;if(this.textures.exists('world-berry'))this.layer.add(this.add.image(tx+tile*.25,ty-tile*.22,'world-berry').setDisplaySize(22,22));this.text(tx+8,ty-8,'树果 '+(resource.quantity??resource.count),10,'#ffe1a0');}
   for(const e of p.entities){if(e.x<p.startX||e.x>=p.startX+p.cols||e.y<p.startY||e.y>=p.startY+p.rows)continue;const tx=x+(e.x-p.startX+.5)*tile,ty=y+(e.y-p.startY+.5)*tile,color=({leader:0x66d6ef,player:0xd69bff,owned:0xa8ed82,wild:0xffd276,npc:0xe4d6bb})[e.role];
    this.layer.add(this.add.ellipse(tx,ty+13,tile*.65,14,0x132e22,.3));this.layer.add(this.add.rectangle(tx,ty,tile-4,tile-4).setStrokeStyle(e.role==='owned'?3:2,color,.7));
    if(e.id===selectedId){this.layer.add(this.add.ellipse(tx,ty,tile-7,tile-7).setStrokeStyle(4,0xffffff,1));this.text(tx-tile/2+3,ty-tile/2+9,'当前目标',10,'#ffffff');}
    const human=!e.species,roleArt=worldArt[e.id.replace('npc-','')];const portrait=human&&roleArt?.kind==='portrait'&&this.textures.exists(roleArt.texture);const sprite=this.add.image(tx,ty-(human?8:5),portrait?roleArt.texture:human?'human':e.species,human&&!portrait?(roleArt?.frame??10):undefined);if(human&&!portrait&&roleArt?.tint)sprite.setTint(roleArt.tint);sprite.setDisplaySize(human?36:tile*.68,human?49.5:tile*.68);this.layer.add(sprite);
    const move=effects.find(f=>f.kind==='move'&&f.id===e.id);
    if(move&&!reduced()){sprite.setPosition(x+(move.x-p.startX+.5)*tile,y+(move.y-p.startY+.5)*tile-5);this.tweens.add({targets:sprite,x:tx,y:ty-5,duration:180,ease:'Sine.easeOut'});}
    if(effects.some(f=>f.kind==='damage'&&f.id===e.id)&&!reduced()){sprite.setTint(0xff7777);this.tweens.add({targets:sprite,alpha:.35,duration:80,yoyo:true,repeat:1,onComplete:()=>sprite.clearTint()});}
    this.text(tx-tile/2+3,ty+(human?22:15),e.role==='wild'?e.name.replace('野生','野生·'):e.name,10,e.id==='npc-professor'?'#d9f3ff':e.id==='npc-merchant'?'#ffe2a3':'#ffffff');
    if(e.hp!==null&&e.hp!==undefined&&e.maxHp){this.layer.add(this.add.rectangle(tx,ty-tile/2+4,tile-10,4,0x25332a));const ratio=Math.max(0,Math.min(1,e.hp/e.maxHp));this.layer.add(this.add.rectangle(tx-tile/2+5,ty-tile/2+4,(tile-10)*ratio,4,ratio>.3?0x8de16d:0xf29966).setOrigin(0,.5));}
   }
   for(const f of effects.filter(f=>f.kind!=='move')){if(f.x<p.startX||f.x>=p.startX+p.cols||f.y<p.startY||f.y>=p.startY+p.rows)continue;
    const tx=x+(f.x-p.startX+.5)*tile,ty=y+(f.y-p.startY+.5)*tile;
    const label=this.text(tx-25,ty-18,f.kind==='damage'?`−${f.amount}`:f.kind==='capture'?'收服成功':f.kind==='miss'?'未命中':'已击败',14,f.kind==='damage'?'#ffbbb0':'#fff3a1');
    if(!reduced())this.tweens.add({targets:label,y:ty-40,alpha:0,duration:850});
   }
   host.dataset.feedback=JSON.stringify(effects);host.dataset.selectedTarget=p.entities.some(e=>e.id===selectedId&&e.role==='wild')?selectedId:'';
   if(p.period==='NIGHT')this.layer.add(this.add.rectangle(416,288,832,576,0x102949,.28));
   drawBoundary(this,bounds,{layer:this.layer});
   const rows=p.entities.map(e=>({id:e.id,name:e.name,x:e.x,y:e.y,hp:e.hp}));host.dataset.entities=JSON.stringify(rows);host.dataset.turn=String(p.turn);host.dataset.taskId=p.taskId??'';
   setStatus(`Phaser RPG · Java 实时状态 · 第 ${p.turn??'—'} 回合 · 方向键/WASD移动，点击野生目标选择 · 白圈为当前目标`,'READY');
  }
 }
 const game=new P.Game({type:P.CANVAS,parent:host,width:832,height:576,pixelArt:true,backgroundColor:'#233f36',scale:{mode:P.Scale.FIT,autoCenter:P.Scale.CENTER_BOTH},audio:{noAudio:true},scene:[LiveScene]});
 const unbindKeys=bindMovementKeys({onMove,canMove,isReady:()=>host.dataset.state==='READY'});
 return {update(view,{targetId=null,online=true,busy=false}={}){selectedId=targetId;sourceView=view;
 summary.textContent=view.cooperative?'首次试玩 · 与 AI 分工合作':'首次试玩 · 捕捉与收藏';
 const guidance=guideSteps(view,mode);steps.replaceChildren(...guidance.map(step=>{const li=document.createElement('li');li.textContent=(step.done?'✓ ':'')+step.label;li.dataset.done=String(step.done);return li;}));
 hint.textContent=view.cooperative&&online&&!busy&&view.playerAllowed?'方向键/WASD控制你自己，水跃鱼继续执行委托。接管水跃鱼需要先暂停。':view.cooperative&&view.status==='WAITING_APPROVAL'?'等待购买审批，双方世界动作暂停。':controlHint(view,online,busy);
 const p=projectRpg(view,mode);if(host.dataset.state==='ERROR')legend.fallback('RPG 素材未加载，图形地图不可用。');else legend.update(boundaryLayout({...p,tile:mode==='training'?80:64,x:(832-p.cols*(mode==='training'?80:64))/2,y:mode==='training'?152:0}));const key=JSON.stringify([p,selectedId]);latest=p;if(key!==lastKey){lastKey=key;if(scene)scene.present(p);}},destroy(){unbindKeys();game.destroy(true);guide.remove();legend.destroy();}};
}
