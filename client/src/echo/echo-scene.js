import Phaser from '../lib/phaser.js';
import {NineSlice} from '../utils/nine-slice.js';
import {HealthBar} from '../common/health-bar.js';
import {HEALTH_BAR_ASSET_KEYS as H} from '../assets/asset-keys.js';
import {toEntityView,toQuestView,SPECIES} from '../integration/scene-adapter.js';
import {BattlePresenter} from '../integration/battle-presenter.js';
const ROOT='assets/images/';
const STYLE={fontFamily:'Microsoft YaHei, PingFang SC, sans-serif',fontSize:'18px',color:'#253e37',lineSpacing:9};
export class EchoScene extends Phaser.Scene {
  constructor(context){super('Echo');this.context=context;}
  preload(){
    this.add.text(390,260,'正在装载群岛资源…',{...STYLE,color:'#e3e9d3'});
    this.failures=[];this.load.on('loaderror',file=>this.failures.push(file.key));
    const files={panel:'kenneys-assets/ui-space-expansion/glassPanel.png',title:'monster-tamer/ui/title/background.png',forest:'monster-tamer/battle-backgrounds/forest-background.png',map:'monster-tamer/map/main_1_level_background.png',grass:'monster-tamer/monsters/carnodusk.png',fire:'monster-tamer/monsters/iguanignite.png',water:'monster-tamer/monsters/aquavalor.png',ball:'monster-tamer/battle/cosmoball.png'};
    Object.entries(files).forEach(([key,path])=>this.load.image(key,ROOT+path));
    this.load.spritesheet('player',ROOT+'axulart/character/custom.png',{frameWidth:64,frameHeight:88});
    for(const [key,path]of [[H.LEFT_CAP,'green_left'],[H.MIDDLE,'green_mid'],[H.RIGHT_CAP,'green_right'],[H.LEFT_CAP_SHADOW,'shadow_left'],[H.MIDDLE_SHADOW,'shadow_mid'],[H.RIGHT_CAP_SHADOW,'shadow_right']])this.load.image(key,ROOT+`kenneys-assets/ui-space-expansion/barHorizontal_${path}.png`);
  }
  create(){
    if(this.failures.length){this.children.removeAll(true);this.label(50,200,'资源加载失败：'+this.failures.join('、'),24,'#f7d4a0',920);this.context.notice('资源不完整，请检查本地 assets 目录。',true);return;}
    this.panels=new NineSlice({cornerCutSize:32,textureManager:this.textures,assetKeys:['panel']});
    this.presenter=new BattlePresenter(event=>new Promise(resolve=>{
      if(event.kind!=='CAPTURE'){resolve();return;}
      const ball=this.add.image(430,260,'ball').setDisplaySize(54,54);
      this.animationEnd=resolve;
      this.tweens.add({targets:ball,x:590,y:230,angle:360,duration:450,onComplete:()=>{ball.destroy();this.animationEnd=undefined;resolve();}});
    }));this.context.onReady(this);
  }
  label(x,y,text,size=18,color='#253e37',width){return this.add.text(x,y,String(text),{...STYLE,fontSize:`${size}px`,color,...(width?{wordWrap:{width,useAdvancedWrap:true}}:{})});}
  panel(x,y,w,h){this.add.rectangle(x,y,w,h,0xf0efd8,.97).setOrigin(0);return this.panels.createNineSliceContainer(this,w,h,'panel').setPosition(x,y).setAlpha(.8);}
  backdrop(key,alpha=1){return this.add.image(512,288,key).setDisplaySize(1024,576).setAlpha(alpha);}
  header(title,subtitle=''){this.add.rectangle(0,0,1024,76,0x172e27,.94).setOrigin(0);this.label(28,16,title,25,'#f0efcf');this.label(29,49,subtitle,12,'#b9caaa');}
  present(mode,view){
    this.animationEnd?.();this.animationEnd=undefined;this.presenter.cancel();
    this.renderFrame(mode,view);
    if(['world','battle','dialog'].includes(mode))void this.presenter.play(view?.presentationEvents||[]);
  }
  renderFrame(mode,view){
    this.tweens.killAll();this.time.removeAllEvents();this.children.removeAll(true);
    if(mode==='title'){this.title();return;}
    if(mode==='settings'){this.backdrop('title',.35);this.header('旅途设置','偏好仅保存在本机，不影响伙伴、进度或房间');this.panel(175,165,674,255);this.label(218,203,'对白与显示',26);this.label(218,257,'在画面下方调整文字速度。\n中文采用系统字体回退。当前界面未启用音频。\n方向键与 WASD 均可移动；鼠标选择下方操作。',18,undefined,590);return;}
    if(!view){this.backdrop('title',.4);this.header('组建调查小队','一位调查员也能完成旅程');this.panel(210,160,604,220);this.label(251,204,'创建世界，或凭房间码加入同伴。',25,undefined,530);this.label(251,274,'进入后显示服务器确认的队员、伙伴与世界进度。',17,undefined,510);return;}
    if(mode==='battle'){this.battle(view);return;}
    if(mode==='party'){this.party(view);return;}
    if(mode==='inventory'){this.inventory(view);return;}
    if(mode==='journal'){this.journal(view);return;}
    if(mode==='room'){this.room(view);return;}
    this.world(view);
    if(mode==='dialog'&&view.dialog)this.dialog(view.dialog);
  }
  title(){
    this.backdrop('title');this.add.rectangle(0,0,1024,576,0x142e26,.45).setOrigin(0);
    this.label(62,59,'栖流岛调查档案  /  01',15,'#e7e7bd');
    this.label(58,128,'回声群岛',64,'#fff6d1');this.label(63,220,'让水流回到它原来的地方。',24,'#f6efcd');
    this.panel(62,319,560,175);this.label(88,344,'一段关于伙伴、误解与修复的旅程',24);
    this.label(88,393,'借用火稚鸡完成考核，遇见第一位伙伴。\n沿着失水的河道调查，与同伴一起护送幼崽。',18,undefined,500);
    this.label(68,531,'01  探索取证     02  获得伙伴     03  协作修复',14,'#f4ebc8');
  }
  world(view){
    this.backdrop('map');this.header('栖流岛 · 营地与水渠',`${this.context.preview?'上游参考地图 / 开发夹具':'现场状态'}  ·  ${view.roomStatus==='ACTIVE'?'探索中':view.roomStatus==='FROZEN'?'房主离线 · 世界已冻结':view.roomStatus}`);
    for(const raw of view.entities){const e=toEntityView(raw),x=e.x+32,y=e.y+32;
      const sprite=this.add.image(x,y,e.texture,0);sprite.setDisplaySize(e.texture==='player'?40:52,e.texture==='player'?55:52);
      if(e.ownerPlayerId&&e.ownerPlayerId!==view.selfPlayerId)sprite.setTint(0xb7e2ff);
      this.add.rectangle(x,y+39,Math.max(68,e.name.length*13+18),24,0x18392e,.85);
      this.label(x,y+29,e.name,12,'#ffffe4').setOrigin(.5,0);
    }
    const q=toQuestView(view);this.panel(720,97,282,383);this.label(742,122,'当前调查',14,'#677052');this.label(742,157,q.title,24,undefined,236);
    this.label(742,210,q.objective,17,undefined,232);this.label(742,329,`线索 ${q.evidence.length}/4   ·   幼崽 ${q.rescued}/3`,17);this.label(742,382,'Tab 打开调查板\nE 执行当前交互',15,'#5a715b');
    if(view.roomStatus!=='ACTIVE'){this.panel(250,225,450,118);this.label(276,249,'世界暂停',23);this.label(276,292,'等待房间恢复，当前操作不可提交。',17);}
  }
  health(x,y,name,hp,maxHp=100){this.panel(x,y,315,94);this.label(x+18,y+12,name,20);const bar=new HealthBar(this,0,0,200);bar.container.setPosition(x+19,y+59);bar.setMeterPercentageAnimated(Math.max(0,Math.min(1,hp/maxHp)),{skipBattleAnimations:true});this.label(x+231,y+49,`${hp}/${maxHp}`,13);}
  battle(view){
    const b=view.encounterView;if(!b){this.world(view);return;}
    this.backdrop('forest');this.header('林地考核',`个人遭遇 · ${b.statusLabel||'等待行动'} · 结果由服务状态确认`);
    this.add.image(735,260,SPECIES[b.enemy.speciesId]?.texture||'grass').setDisplaySize(210,210);
    this.add.image(253,352,SPECIES[b.ally.speciesId]?.texture||'fire').setDisplaySize(240,240).setFlipX(true);
    this.health(46,103,b.enemy.name,b.enemy.hp,b.enemy.maxHp);this.health(660,365,b.ally.name,b.ally.hp,b.ally.maxHp);
    this.panel(37,478,950,76);this.label(58,491,b.message||'选择下方操作。捕捉资格以服务端允许的选项为准。',18,undefined,907);
    this.label(668,335,'示例立绘占位 · 非最终精灵形象',12,'#f6f4e1');
  }
  party(view){
    this.backdrop('forest',.3);this.header('伙伴手册','能力用于探索和修复 · 每人同时出场一只');
    const party=view.selfAssets.party||[];
    if(!party.length){this.panel(150,190,724,155);this.label(186,225,'尚未拥有伙伴',26);this.label(186,276,'先与岚交谈，借用火稚鸡参加考核。',18);}
    party.slice(0,3).forEach((p,i)=>{const x=35+i*329,species=SPECIES[p.speciesId];this.panel(x,111,303,414);this.label(x+22,134,p.loan?'临时借用 · 考核后归还':'个人伙伴',14,'#6c7756');this.add.image(x+151,265,species?.texture||'grass').setDisplaySize(163,163);this.label(x+22,369,p.name||species?.name,27);this.label(x+22,412,species?.ability||'能力待确认',17);this.label(x+22,455,`HP ${p.hp}/${p.maxHp||100} · ${p.active?'出场中':'等待'}`,15);this.label(x+22,491,'示例立绘占位',11,'#6c7756');});
  }
  inventory(view){
    this.backdrop('title',.3);this.header('行囊与公共工具','个人消耗品与世界任务工具分开显示');
    for(const [i,[title,items]]of [['个人行囊',view.selfAssets.inventory||[]],['公共工具',view.worldTools||[]]].entries()){
      const x=35+i*490;this.panel(x,112,462,409);this.label(x+27,139,title,25);
      if(!items.length)this.label(x+27,218,'暂无物品',18,'#687565');
      items.slice(0,5).forEach((item,j)=>{this.label(x+28,211+j*57,item.name,20);this.label(x+364,215+j*57,`× ${item.quantity??1}`,17);});
    }
  }
  journal(view){
    const q=toQuestView(view);this.backdrop('title',.3);this.header('调查板 · 第一章',q.title);
    this.panel(35,105,424,428);this.label(61,134,'下一步',15,'#677052');this.label(61,175,q.objective,25,undefined,370);
    this.label(61,296,`护送进度  ${q.rescued} / 3`,23);this.label(61,352,q.hint||'需要帮助时，在下方请求提示。\n提示不会自动完成任务或消耗道具。',17,undefined,365);
    const labels={E11:'干涸的浅潭',E12:'反常的水流',E13:'沫沫的救援行为',E14:'人工导流板'};
    Object.entries(labels).forEach(([id,name],i)=>{const y=105+i*110,found=q.evidence.includes(id);this.panel(480,y,508,96);this.label(501,y+18,`${found?'●':'○'}  ${name}`,21,found?'#304f36':'#788176');this.label(501,y+57,found?(q.discoverers?.[id]||'已记录到共享调查板'):'尚未发现 · 前往现场观察',14,'#61725c');});
  }
  room(view){
    this.backdrop('title',.4);this.header('调查小队',`房间 ${view.joinCode||view.roomId} · 世界进度归房主，伙伴归个人`);
    const players=view.entities.filter(e=>e.kind==='PLAYER');
    players.slice(0,2).forEach((p,i)=>{const x=170+i*365;this.panel(x,154,315,291);this.add.image(x+157,257,'player',0).setDisplaySize(60,83).setTint(i?0xb7e2ff:0xffffff);this.label(x+32,330,p.name||'调查员',26);this.label(x+32,380,p.entityId===view.hostPlayerId?'房主 · 在线':'队员 · 在线',17);});
    if(players.length<2){this.panel(535,154,315,291);this.label(569,251,'等待同伴加入',25);this.label(569,315,'也可以独自出发。',18);}
  }
  dialog(dialog){
    this.add.rectangle(0,0,1024,576,0x102c23,.36).setOrigin(0);this.panel(35,335,954,211);this.label(62,358,dialog.speaker||'岚',24);
    const text=this.label(62,402,'',20,undefined,894),content=dialog.text||'';let index=0;
    this.time.addEvent({delay:this.context.preferences.textSpeed,repeat:Math.max(0,content.length-1),callback:()=>text.setText(content.slice(0,++index))});
    const skip=this.label(812,509,'点击显示全文',12,'#657360').setInteractive({useHandCursor:true});skip.on('pointerdown',()=>{index=content.length;text.setText(content);});
  }
}
