import Phaser from 'phaser';
import type {Snapshot,Actor,GameEvent} from '../api/types';
import {assetFor} from './asset-manifest';
import mapMetadata from '../generated/map.json';

export const MOVE_MS=180;
const ROOT='/assets/phaser-rpg/';
const humans=new Set(['PLAYER','PROFESSOR','MERCHANT']);
// Keep the reference Tiled layers and Misa frames. Java owns all movement rules.
export class WorldScene extends Phaser.Scene {
 private ground!:Phaser.GameObjects.Container;
 private items!:Phaser.GameObjects.Container;
 private foliage!:Phaser.GameObjects.Container;
 private entities=new Map<string,Phaser.GameObjects.Container>();
 private last:Snapshot|null=null;
 private readyResolve!:()=>void;
 private readyReject!:(reason:Error)=>void;
 readonly ready=new Promise<void>((resolve,reject)=>{this.readyResolve=resolve;this.readyReject=reject;});
 onSelect=(id:string)=>{};
 reduceMotion=false;
 constructor(){super('world');}
 preload(){
  this.load.on('loaderror',(file:Phaser.Loader.File)=>this.readyReject(new Error(`场景素材加载失败：${file.key}，请刷新页面重试。`)));
  this.load.tilemapTiledJSON('town',ROOT+`world.json?v=${mapMetadata.version}`);
  this.load.image('tuxemon',ROOT+'tuxemon-sample-32px-extruded.png');
  this.load.spritesheet('terrain-tiles',ROOT+'tuxemon-sample-32px-extruded.png',{frameWidth:32,frameHeight:32,margin:1,spacing:2});
  this.load.atlas('misa',ROOT+'atlas.png',ROOT+'atlas.json');
 }
 create(){
  if(!this.cache.tilemap.exists('town')||!this.textures.exists('tuxemon')||!this.textures.exists('misa'))return;
  const map=this.make.tilemap({key:'town'});
  const tiles=map.addTilesetImage('tuxemon-sample-32px-extruded','tuxemon');
  if(!tiles){this.readyReject(new Error('小镇瓦片集不可用'));return;}
  map.createLayer('Below Player',tiles,0,0)?.setDepth(0);
  map.createLayer('World',tiles,0,0)?.setDepth(1);
  map.createLayer('Above Player',tiles,0,0)?.setDepth(10000);
  this.ground=this.add.container(0,0).setDepth(2);
  this.items=this.add.container(0,0).setDepth(3);
  this.foliage=this.add.container(0,0).setDepth(9000);
  for(const direction of ['left','right','back','front'])this.anims.create({key:`walk-${direction}`,frames:this.anims.generateFrameNames('misa',{prefix:`misa-${direction}-walk.`,start:0,end:3,zeroPad:3}),frameRate:10,repeat:-1});
  this.cameras.main.setBounds(0,0,map.widthInPixels,map.heightInPixels).setZoom(1.5).setRoundPixels(true);
  this.cameras.main.setBackgroundColor('#25382f');
  this.readyResolve();
 }
 private entity(a:Actor,tile:number){
  const asset=assetFor(a.kind),c=this.add.container((a.x+.5)*tile,(a.y+.5)*tile).setDepth(100+a.y*tile);
  c.add(this.add.ellipse(0,10,23,9,0x152621,.3));
  if(humans.has(a.kind)){
   const sprite=this.add.sprite(0,16,'misa','misa-front').setOrigin(.5,1).setName('sprite');
   if(a.kind==='PROFESSOR')sprite.setTint(0xc5e2ff);
   if(a.kind==='MERCHANT')sprite.setTint(0xffd6a2);
   c.add(sprite);
  }else{
   // Only Pokemon missing from the reference use named placeholder shapes.
   const g=this.add.graphics().fillStyle(asset.color).lineStyle(2,0x22352c);
   if(asset.shape==='leaf')g.fillTriangle(-11,6,0,-15,11,6).strokeTriangle(-11,6,0,-15,11,6);
   else if(asset.shape==='flame'){g.fillTriangle(-11,9,3,-17,11,9);g.fillCircle(0,4,9);}
   else if(asset.shape==='drop'){g.fillEllipse(0,1,25,19);g.fillTriangle(-5,-6,0,-17,5,-6);}
   else g.fillCircle(0,0,11);
   c.add(g);
  }
  const tag=this.add.text(0,humans.has(a.kind)?-38:-26,a.kind==='PLAYER'?'你':a.kind==='PROFESSOR'?'博士':asset.label,{fontFamily:'Microsoft YaHei, sans-serif',fontSize:'8px',color:'#fff9df',backgroundColor:'#243448dd',padding:{x:3,y:2}}).setOrigin(.5);
  c.add([tag,this.add.graphics().setName('hp')]);
  c.setSize(tile,tile).setInteractive({useHandCursor:true}).on('pointerdown',()=>this.onSelect(a.id));
  this.entities.set(a.id,c);
  if(a.kind==='PLAYER')this.cameras.main.startFollow(c,true,1,1);
  return c;
 }
 private drawTerrain(s:Snapshot){
  this.ground.removeAll(true);this.foliage.removeAll(true);
  const t=s.map.tileSize;
  const tile=(x:number,y:number,frame:number,tint=0xffffff)=>this.add.image(x*t,y*t,'terrain-tiles',frame).setOrigin(0).setTint(tint);
  for(const cell of s.map.grounds){
   const {x,y,kind}=cell;
   if(['DIRT','FLOOR','WALL'].includes(kind))continue;
   const frame=kind==='HAY'||kind==='TREE'?125:kind==='PUDDLE'||kind==='WATERFALL'?249:173;
   this.ground.add(tile(x,y,frame,kind==='LAVA'?0xee8659:0xffffff));
   if(kind==='HAY')this.ground.add(tile(x,y,121));
   if(kind==='TREE'){
    this.ground.add(tile(x-.5,y,192));this.ground.add(tile(x+.5,y,193));
    for(let dx=0;dx<2;dx++)this.foliage.add(tile(x-.5+dx,y-1,168+dx));
   }
   if(kind==='CRATER')this.ground.add(tile(x,y,225,0xed9d76));
   if(kind==='WATERFALL')this.ground.add(tile(x,y,274,0xb2e6ff));
  }
 }
 async render(s:Snapshot,animate=false,events:GameEvent[]=[]){
  await this.ready;const t=s.map.tileSize;
  if(!this.last||this.last.revision!==s.revision||this.last.gameId!==s.gameId)this.drawTerrain(s);
  this.items.removeAll(true);
  const piles=new Set<string>();
  for(const gi of s.groundItems){const key=`${gi.x},${gi.y}`;if(piles.has(key))continue;piles.add(key);this.items.add(this.add.text((gi.x+.7)*t,(gi.y+.7)*t,gi.item.kind==='CANDY'?'◆':'◒',{fontSize:'16px',color:'#ffe2a0',stroke:'#493b2b',strokeThickness:3}).setOrigin(.5));}
  const promises:Promise<void>[]=[];
  const active=new Set(s.actors.map(a=>a.id));
  for(const [id,c] of this.entities)if(!active.has(id)){
   this.entities.delete(id);
   if(animate&&!this.reduceMotion)promises.push(new Promise<void>(resolve=>this.tweens.add({targets:c,scaleX:0,scaleY:0,alpha:0,duration:MOVE_MS,onComplete:()=>{c.destroy();resolve();}})));
   else c.destroy();
  }
  for(const a of s.actors){
   let c=this.entities.get(a.id);const exists=!!c;if(!c)c=this.entity(a,t);
   const old=this.last?.gameId===s.gameId?this.last.actors.find(v=>v.id===a.id):null;
   const sprite=c.getByName('sprite') as Phaser.GameObjects.Sprite|null;
   const moved=!!old&&(old.x!==a.x||old.y!==a.y);
   const direction=moved?(a.x!==old.x?(a.x>old.x?'right':'left'):(a.y>old.y?'front':'back')):'front';
   const bar=c.getByName('hp') as Phaser.GameObjects.Graphics;bar.clear();
   if(a.affection!==null){bar.fillStyle(0x21332a).fillRect(-12,14,24,3);bar.fillStyle(a.hp/a.maxHp>.3?0xbfe29a:0xe78b65).fillRect(-12,14,24*a.hp/a.maxHp,3);}
   c.setDepth(100+a.y*t);
   if(animate&&exists&&!this.reduceMotion&&moved){
    sprite?.play(`walk-${direction}`,true);
    const entity=c;
    promises.push(new Promise<void>(resolve=>this.tweens.add({targets:entity,x:(a.x+.5)*t,y:(a.y+.5)*t,duration:MOVE_MS,onComplete:()=>{sprite?.stop().setFrame(`misa-${direction}`);resolve();}})));
   }else{c.setPosition((a.x+.5)*t,(a.y+.5)*t);if(moved)sprite?.stop().setFrame(`misa-${direction}`);}
  }
  if(animate&&!this.reduceMotion)for(const e of events)if(e.kind==='HP_CHANGE'||e.kind==='AFFECTION_CHANGE'){
   const c=e.actorId?this.entities.get(e.actorId):null;
   if(c){const text=this.add.text(c.x,c.y-20,`${e.kind==='AFFECTION_CHANGE'?'♥ ':''}${(e.amount??0)>0?'+':''}${e.amount??0}`,{fontSize:'13px',color:e.kind==='AFFECTION_CHANGE'?'#ffd5cf':'#fff3a8',stroke:'#243529',strokeThickness:3}).setOrigin(.5).setDepth(11000);this.tweens.add({targets:text,y:text.y-25,alpha:0,duration:600,onComplete:()=>text.destroy()});}
  }
  this.cameras.main.setAlpha(s.period==='NIGHT'?.78:1);this.last=s;await Promise.all(promises);
 }
 skip(){this.tweens.getTweens().forEach(t=>t.complete());}
}
