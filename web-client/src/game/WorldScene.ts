import Phaser from 'phaser';
import type {Snapshot,Actor,GameEvent} from '../api/types';
import {assets,assetFor,terrain} from './asset-manifest';
export const MOVE_MS=150;
export class WorldScene extends Phaser.Scene {
 private ground!:Phaser.GameObjects.Container;private items!:Phaser.GameObjects.Container;
 private entities=new Map<string,Phaser.GameObjects.Container>();private last:Snapshot|null=null;
 private readyResolve!:()=>void;readonly ready=new Promise<void>(r=>this.readyResolve=r);onSelect=(id:string)=>{};reduceMotion=false;
 constructor(){super('world');}
 preload(){
  this.load.on('loaderror',(file:Phaser.Loader.File)=>console.warn(`资源 ${file.key} 加载失败，使用标注占位图`));
  for(const [kind,asset] of Object.entries(assets))if(asset.file){if(asset.frameWidth&&asset.frameHeight)this.load.spritesheet(kind,asset.file,{frameWidth:asset.frameWidth,frameHeight:asset.frameHeight});else this.load.image(kind,asset.file);}
 }
 create(){this.ground=this.add.container(0,0);this.items=this.add.container(0,0).setDepth(1);this.cameras.main.setBackgroundColor('#192b27');this.readyResolve();}
 private entity(a:Actor,tile:number){const asset=assetFor(a.kind),c=this.add.container((a.x+.5)*tile,(a.y+.5)*tile).setDepth(10+a.y);
  const g=this.add.graphics();g.fillStyle(0x142722,.35).fillEllipse(0,9,25,10);g.lineStyle(2,0x22352c,1);g.fillStyle(asset.color);
  if(asset.shape==='leaf'){g.fillTriangle(-11,6,0,-15,11,6).strokeTriangle(-11,6,0,-15,11,6);}
  else if(asset.shape==='flame'){g.fillTriangle(-11,9,3,-17,11,9);g.fillCircle(0,4,9);}
  else if(asset.shape==='drop'){g.fillEllipse(0,1,25,19);g.fillTriangle(-5,-6,0,-17,5,-6);}
  else if(asset.shape==='person'){g.fillRoundedRect(-8,-2,16,15,3);g.fillStyle(0xf0d6aa).fillCircle(0,-8,7);g.fillStyle(asset.color).fillRect(-8,-15,16,5);}
  else g.fillCircle(0,0,11);
  if(asset.file&&this.textures.exists(a.kind)){g.setVisible(false);c.add(this.add.image(0,14,a.kind,asset.frame??0).setOrigin(...asset.anchor).setScale(asset.scale));}
  const tag=this.add.text(0,-25,asset.short,{fontFamily:'Microsoft YaHei, sans-serif',fontSize:'12px',color:'#fff9df',backgroundColor:'#23392de0',padding:{x:3,y:1}}).setOrigin(.5);
  const bar=this.add.graphics().setName('hp');c.add([g,tag,bar]);c.setSize(tile,tile).setInteractive({useHandCursor:true}).on('pointerdown',()=>this.onSelect(a.id));this.entities.set(a.id,c);return c;
 }
 async render(s:Snapshot,animate=false,events:GameEvent[]=[]){await this.ready;const t=s.map.tileSize;
  this.ground.removeAll(true);for(const tile of s.map.grounds){const style=terrain[tile.kind]??{color:0xff00aa,mark:'?',label:'缺失'};const g=this.add.graphics();g.fillStyle(style.color).fillRect(tile.x*t,tile.y*t,t,t);g.lineStyle(1,0xffffff,.025).strokeRect(tile.x*t,tile.y*t,t,t);
   if(tile.kind==='DIRT'||tile.kind==='HAY'){g.fillStyle(0xffffff,.08).fillRect(tile.x*t+5+(tile.y%3)*4,tile.y*t+9,3,2);}
   this.ground.add(g);if(style.mark)this.ground.add(this.add.text((tile.x+.5)*t,(tile.y+.5)*t,style.mark,{fontSize:'21px',color:tile.kind==='TREE'?'#a2c37a':'#dfebc7'}).setOrigin(.5));}
  this.items.removeAll(true);const piles=new Set<string>();for(const gi of s.groundItems){const key=`${gi.x},${gi.y}`;if(piles.has(key))continue;piles.add(key);const icon=this.add.text((gi.x+.7)*t,(gi.y+.7)*t,gi.item.kind==='CANDY'?'◆':'◒',{fontSize:'18px',color:'#ffe2a0',stroke:'#493b2b',strokeThickness:3}).setOrigin(.5);this.items.add(icon);}
  const promises:Promise<void>[]=[];
  const active=new Set(s.actors.map(a=>a.id));for(const [id,c] of this.entities)if(!active.has(id)){
   this.entities.delete(id);if(animate&&!this.reduceMotion)promises.push(new Promise<void>(resolve=>this.tweens.add({targets:c,scaleX:0,scaleY:0,alpha:0,duration:MOVE_MS,onComplete:()=>{c.destroy();resolve();}})));else c.destroy();
  }
  for(const a of s.actors){let c=this.entities.get(a.id);const exists=!!c;if(!c)c=this.entity(a,t);const old=this.last?.actors.find(v=>v.id===a.id);
   if(a.kind==='PLAYER'&&old&&(old.x!==a.x||old.y!==a.y)){const arrow=c.getByName('facing');arrow?.destroy();const dx=a.x-old.x,dy=a.y-old.y;const symbol=Math.abs(dx)>0?(dx>0?'›':'‹'):(dy>0?'⌄':'⌃');c.add(this.add.text(13,-7,symbol,{fontSize:'18px',color:'#fff3bf'}).setName('facing'));}
   const bar=c.getByName('hp') as Phaser.GameObjects.Graphics;bar.clear();if(a.affection!==null){bar.fillStyle(0x21332a).fillRect(-12,14,24,3);bar.fillStyle(a.hp/a.maxHp>.3?0xbfe29a:0xe78b65).fillRect(-12,14,24*a.hp/a.maxHp,3);}
   c.setDepth(10+a.y);if(animate&&exists&&!this.reduceMotion&&(c.x!==(a.x+.5)*t||c.y!==(a.y+.5)*t)){const entity=c;promises.push(new Promise<void>(resolve=>this.tweens.add({targets:entity,x:(a.x+.5)*t,y:(a.y+.5)*t,duration:MOVE_MS,onComplete:()=>resolve()})));}else c.setPosition((a.x+.5)*t,(a.y+.5)*t);
  }
  if(animate&&!this.reduceMotion)for(const e of events)if(e.kind==='HP_CHANGE'||e.kind==='AFFECTION_CHANGE'){const c=e.actorId?this.entities.get(e.actorId):null;if(c){const text=this.add.text(c.x,c.y-20,`${e.kind==='AFFECTION_CHANGE'?'♥ ':''}${(e.amount??0)>0?'+':''}${e.amount??0}`,{fontSize:'13px',color:e.kind==='AFFECTION_CHANGE'?'#ffd5cf':'#fff3a8',stroke:'#243529',strokeThickness:3}).setOrigin(.5).setDepth(100);this.tweens.add({targets:text,y:text.y-25,alpha:0,duration:600,onComplete:()=>text.destroy()});}}
  this.cameras.main.setAlpha(s.period==='NIGHT'?.78:1);this.last=s;await Promise.all(promises);
 }
 skip(){this.tweens.getTweens().forEach(t=>t.complete());}
}
