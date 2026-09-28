import Phaser from 'phaser';
import {GameController,request} from './api/controller';
import type {Action,Item} from './api/types';
import {WorldScene} from './game/WorldScene';
import {assetFor} from './game/asset-manifest';
import map from './generated/map.json';
import './style.css';

document.querySelector<HTMLDivElement>('#app')!.innerHTML=`
<header><a class="brand" href="/" aria-label="口袋原野首页"><span class="brandmark">◈</span><span>口袋原野<small>PHASER RPG × JAVA</small></span></a><div class="header-right"><span class="prototype">小镇探索 · 单人游戏</span><button id="help-toggle" class="quiet">操作指南 ↗</button></div></header>
<main><div class="title-row"><div><span class="eyebrow">TUXEMON TOWN</span><h1>小镇与原野</h1><p class="intro">探索环境、结识精灵，让世界随你的每一步变化。</p></div><div class="session-actions"><button id="refresh" class="quiet" title="重新读取服务器中的本局状态，不推进回合">同步状态</button><button id="restart" class="quiet danger">重新开始</button></div></div>
<div id="error" role="alert" hidden><span id="error-message"></span><button id="retry">重试原请求</button><button id="recover">重新读取</button></div>
<div class="layout"><section class="field-panel"><div class="field-top"><span><i class="live-dot"></i> Tuxemon 小镇 <small>40 × 40</small></span><div><span id="period">☀ 白昼</span><span class="divider">/</span><span>回合 <b id="turn">0</b></span></div></div><div id="board"><div id="loading">正在连接游戏世界…</div></div><div class="field-bottom"><span id="position">等待连接</span><span id="next-period">下一轮：白昼</span><label><input id="motion" type="checkbox"> 简化动画</label></div>
<div class="controls"><div class="pad" id="pad"></div><div class="control-copy"><strong>每一步，都让世界向前。</strong><p>WASD / 方向键移动 · 空格等待<br>上下左右四向移动 · 点击角色查看详情</p><div class="control-buttons"><button id="wait" class="primary">等待一回合 <kbd>SPACE</kbd></button></div></div><span class="compass">N<br>✧<br>S</span></div>
<div class="legend"><span><i class="grass"></i>草系区域</span><span><i class="water"></i>水系区域</span><span><i class="fire"></i>火系区域</span><span>◆ 地面物品</span><span>▤ 墙体不可通行</span></div></section>
<aside><section class="side-card"><div class="card-heading"><h2>附近的互动</h2><span id="state" class="status">连接中</span></div><div id="target" class="target">选择角色，查看血量和好感。</div><div id="actions" class="action-list"></div></section><section class="side-card inventory-card"><div class="card-heading"><h2>随身背包</h2><span id="inventory-count">0 件</span></div><div id="inventory"></div><p class="hint">球内精灵暂停行动与昼夜效果。</p><p id="candy-locations" class="hint"></p></section><section class="side-card journal" aria-labelledby="results-heading"><div class="card-heading"><h2 id="results-heading">互动结果</h2><span class="hint">最新在上</span></div><div id="dialogue" role="status" hidden></div><ol id="logs" aria-live="polite"><li class="muted">你的旅程即将开始。</li></ol></section></aside></div>

<section id="help" hidden><h2>开始你的第一段探索</h2><p>地图随机放置最多 2 颗糖果，拾取后会在可到达的空位随机补充。背包旁显示糖果坐标。在相邻格与木守宫、水跃鱼互动或捕捉，向北寻找博士对话、与商人交易。火稚鸡可与之互动，也可用 10 个糖果在商人处兑换。</p><p>木守宫喜欢跳舞，水跃鱼喜欢拍胸脯，火稚鸡喜欢唱歌。每个合法动作都会推进一回合；昼夜每五回合切换，精灵会自主移动、战斗，地形会生成与扩散。地图上的木守宫、水跃鱼和火稚鸡各最多 3 只，球内精灵不计入。博士只对话，商人只交易，友好 NPC 不可攻击。</p><p>高级球 3 糖果，大师球 6 糖果；首版只保留原引擎已有的交易与物品功能。游戏保存在服务器内存中，刷新可恢复；30 分钟无操作或服务器重启后需重新开始。此版本为桌面优先，地图与人物来自 Phaser RPG；宝可梦、道具和专属效果暂用标注占位素材。</p></section>
<footer><span>口袋原野 / 引擎玩法验证版</span><span><a href="/credits.html" target="_blank" rel="noopener">素材来源与许可 ↗</a></span></footer></main>`;
const el=<T extends HTMLElement=HTMLElement>(id:string)=>document.getElementById(id) as T;
const scene=new WorldScene();new Phaser.Game({type:Phaser.AUTO,parent:'board',width:768,height:512,backgroundColor:'#1f3229',pixelArt:true,antialias:false,scene:[scene],scale:{mode:Phaser.Scale.RESIZE,autoCenter:Phaser.Scale.CENTER_BOTH}});
let selected:string|null=null,versionError=false;
const controller=new GameController(request,async result=>{await scene.render(result.snapshot,true,result.events);const dialogue=result.events.find(e=>e.kind==='DIALOGUE');if(dialogue){el('dialogue').hidden=false;el('dialogue').textContent=dialogue.message;}},map.version);
const directionKeys=['North','West','East','South'];
const arrows=['↑','←','→','↓'];
directionKeys.forEach((direction,i)=>{const b=document.createElement('button');b.textContent=arrows[i];b.title=direction||'当前位置';b.dataset.direction=direction;b.setAttribute('aria-label',direction||'当前位置');b.onclick=()=>move(direction);el('pad').append(b);});
function move(direction:string){const a=controller.snapshot?.availableActions.find(a=>a.kind==='MOVE'&&a.direction===direction);if(a)void controller.act(a.id);}
function button(a:Action){const b=document.createElement('button');b.className='action';b.textContent=a.label;b.dataset.kind=a.kind;b.dataset.actionId=a.id;b.disabled=controller.state!=='READY'||versionError||!a.enabled;b.onclick=()=>void controller.act(a.id);return b;}
function renderInventory(items:Item[],actions:Action[]){const root=el('inventory');root.replaceChildren();if(!items.length){root.textContent='背包还很轻，按下方坐标寻找糖果。';root.classList.add('muted');return;}root.classList.remove('muted');const groups=new Map<string,Item[]>();for(const item of items){const key=item.containedPokemon?item.id:item.kind;groups.set(key,[...(groups.get(key)??[]),item]);}
 for(const group of groups.values()){const item=group[0],row=document.createElement('div');row.className='item';const label=document.createElement('span');label.textContent=item.containedPokemon?`◒ ${assetFor(item.containedPokemon.kind).label} · HP ${item.containedPokemon.hp}/${item.containedPokemon.maxHp} · ♥ ${item.containedPokemon.affection}`:`${item.kind==='CANDY'?'◆':'◒'} ${item.name} × ${group.length}`;row.append(label);const drop=actions.find(a=>a.kind==='DROP'&&a.targetId===item.id);if(drop){const b=button(drop);b.textContent='丢下 1';row.append(b);}root.append(row);}
}
function render(){const s=controller.snapshot;el('state').textContent=({READY:'可操作',REQUESTING:'请求中',ANIMATING:'动画中',ERROR:'待恢复',ENDED:'已结束'})[controller.state];el('state').dataset.state=controller.state;
 el('error').hidden=!controller.error&&!versionError;el('error-message').textContent=versionError?'地图版本不匹配，请重新构建前端和服务端。':controller.error;el('retry').hidden=!controller.pending;el<HTMLButtonElement>('restart').disabled=['REQUESTING','ANIMATING'].includes(controller.state);
 el<HTMLButtonElement>('refresh').disabled=['REQUESTING','ANIMATING'].includes(controller.state);
 if(!s)return;versionError=s.map.version!==map.version;if(versionError){for(const b of document.querySelectorAll<HTMLButtonElement>('#pad button,#actions button,#inventory button,#wait'))b.disabled=true;el('error').hidden=false;el('error-message').textContent='地图版本不匹配，请重新构建前端和服务端。';return;}
 el('loading').hidden=true;el('turn').textContent=String(s.turn);el('period').textContent=s.period==='DAY'?'☀ 白昼':'☾ 夜晚';el('next-period').textContent=`下一轮：${s.nextActionPeriod==='DAY'?'白昼':'夜晚'}`;
 const p=s.actors.find(a=>a.id===s.playerId);el('position').textContent=p?`训练家 (${p.x}, ${p.y})`:'本局已结束';
 if(controller.state==='READY'||controller.state==='ENDED')void scene.render(s);
 for(const b of el('pad').querySelectorAll('button'))b.disabled=controller.state!=='READY'||!s.availableActions.some(a=>a.kind==='MOVE'&&a.direction===b.dataset.direction);
 el<HTMLButtonElement>('wait').disabled=controller.state!=='READY'||!s.availableActions.some(a=>a.kind==='WAIT');
 const target=s.actors.find(a=>a.id===selected);if(target){const appearance=assetFor(target.kind);el('target').textContent=`${appearance.label} · ${target.name}\nHP ${target.hp}/${target.maxHp}${target.affection===null?'':` · 好感 ${target.affection}`} · (${target.x}, ${target.y})`;}else{selected=null;el('target').textContent='点击地图角色可查看状态；下方显示当前可用动作。';}
 const actionsRoot=el('actions');actionsRoot.replaceChildren();const shown=new Set<string>();for(const a of s.availableActions){if(['MOVE','WAIT','DROP'].includes(a.kind))continue;if(selected&&a.targetId&&a.targetId!==selected&&a.kind!=='PICK_UP')continue;const key=a.kind==='PICK_UP'?a.label:a.id;if(shown.has(key))continue;shown.add(key);actionsRoot.append(button(a));}
 if(!actionsRoot.children.length)actionsRoot.textContent='附近暂无可用互动，靠近角色或物品试试。';if(selected){const b=document.createElement('button');b.className='quiet';b.textContent='显示全部附近动作';b.onclick=()=>{selected=null;render();};actionsRoot.append(b);}
 const candies=s.groundItems.filter(i=>i.item.kind==='CANDY');el('candy-locations').textContent='地图糖果 '+candies.length+'/2：'+candies.map(i=>'('+i.x+', '+i.y+')').join('、')+'。地图满额时不可丢下糖果。';
 el('inventory-count').textContent=`${s.inventory.length} 件`;renderInventory(s.inventory,s.availableActions);
 const logs=el('logs');logs.replaceChildren();for(const log of s.log.slice(-30).reverse()){const li=document.createElement('li'),stamp=document.createElement('small');stamp.textContent=`T${String(log.turn).padStart(3,'0')}`;li.append(stamp,document.createTextNode(log.text));logs.append(li);}if(!s.log.length)logs.textContent='旅程开始。寻找散落在小镇的糖果，结识身边的新朋友。';
 if(s.phase==='ENDED'){el('error').hidden=false;el('error-message').textContent='本局已结束，请重新开始。';}
}
scene.onSelect=id=>{selected=id;render();};controller.onChange=render;
el('wait').onclick=()=>{const a=controller.snapshot?.availableActions.find(a=>a.kind==='WAIT');if(a)void controller.act(a.id);};
el('retry').onclick=()=>void controller.retry();
el('recover').onclick=()=>controller.snapshot?void controller.refresh():void controller.load();
el('refresh').onclick=()=>void controller.refresh();
el('restart').onclick=()=>{if(confirm('结束本局并重新开始？当前背包与进度会清空。')){selected=null;el('dialogue').hidden=true;void controller.restart();}};
el<HTMLInputElement>('motion').onchange=e=>{scene.reduceMotion=(e.target as HTMLInputElement).checked;scene.skip();};
el('help-toggle').onclick=()=>{el('help').hidden=!el('help').hidden;if(!el('help').hidden)el('help').scrollIntoView({behavior:'smooth'});};
const keys:Record<string,string>={w:'North',ArrowUp:'North',s:'South',ArrowDown:'South',a:'West',ArrowLeft:'West',d:'East',ArrowRight:'East',Numpad8:'North',Numpad6:'East',Numpad2:'South',Numpad4:'West'};
window.addEventListener('keydown',e=>{if(e.target instanceof HTMLInputElement||e.ctrlKey||e.metaKey||e.altKey)return;const direction=keys[e.code]??keys[e.key];if(direction||e.code==='Space'){e.preventDefault();if(controller.state==='READY'){if(direction)move(direction);else el('wait').click();}}});
void scene.ready.then(()=>controller.load()).catch(error=>{versionError=true;el('loading').textContent=error.message;el('error').hidden=false;el('error-message').textContent=error.message;el('state').textContent='素材加载失败';for(const b of document.querySelectorAll<HTMLButtonElement>('#pad button,#wait,#refresh,#restart,#retry,#recover'))b.disabled=true;});
