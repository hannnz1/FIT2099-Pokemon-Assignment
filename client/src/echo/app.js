import Phaser from '../lib/phaser.js';
import {ClientState} from '../integration/client-state.js';
import {EchoScene} from './echo-scene.js';
import {loadPreferences,savePreferences} from '../utils/preferences-store.js';
import {SessionController,canSubmit} from '../integration/session-controller.js';
import {nextMode} from '../integration/navigation.js';
export function boot(gateway,{preview=false}={}) {
  const root=document.getElementById('app');
  root.innerHTML=`<header><div><strong>回声群岛</strong><small>ECHO ISLANDS · FIELD NOTES</small></div><span class="badge ${preview?'preview':''}">${preview?'开发夹具 · 非 Java 联机 / 不保存':'JAVA 服务 · 权威状态'}</span></header><main><nav class="toolbar" aria-label="游戏导航"></nav><div id="game-container" role="img" aria-label="回声群岛游戏画面"></div><div id="status" role="status" aria-live="polite"></div><section id="actions" aria-label="当前可用操作"></section><div id="help">WASD / 方向键移动 · E 交互 · Tab 调查板 · Esc 返回地图</div><footer><span>UI 基于 Dev Share Academy / Monster Tamer · Phaser 3<br>当前精灵使用明确标注的上游占位美术。</span><nav><a href="reference.html">上游原版</a><a href="${preview?'index.html':'preview.html'}">${preview?'正式服务入口':'开发界面预览'}</a><a href="THIRD_PARTY_NOTICES.md">素材来源</a></nav></footer></main>`;
  const state=new ClientState();let scene,mode='title',connectionBusy=false,lastMode,lastView;
  const status=document.getElementById('status'),actions=document.getElementById('actions'),toolbar=root.querySelector('.toolbar');
  const preferences=loadPreferences();
  const session=new SessionController(gateway,state,(message,error)=>{notice(message,error);render();});
  const context={state,preview,preferences,get mode(){return mode;},onReady(s){scene=s;render();},get busy(){return session.busy;},navigate,command,notice};
  const game=new Phaser.Game({type:Phaser.CANVAS,parent:'game-container',width:1024,height:576,pixelArt:true,backgroundColor:'#1c3028',scale:{mode:Phaser.Scale.FIT,autoCenter:Phaser.Scale.CENTER_BOTH},scene:[new EchoScene(context)],audio:{noAudio:true}});
  function notice(message,error=false){status.textContent=message;status.classList.toggle('error',error);}
  function button(label,fn,{disabled=false,parent=actions}={}){const b=document.createElement('button');b.textContent=label;b.disabled=disabled;b.onclick=fn;parent.append(b);return b;}
  const pages=[['title','首页'],['room','房间'],['world','探索'],['party','伙伴'],['inventory','背包'],['journal','调查板'],['settings','设置']];
  function navigate(next){mode=next;render();}
  function render(){
    if(!scene)return;
    if(lastMode!==mode||lastView!==state.getView()){scene.present(mode,state.getView());lastMode=mode;lastView=state.getView();}
    toolbar.replaceChildren();
    for(const [key,label]of pages){const b=button(label,()=>navigate(key),{parent:toolbar,disabled:!state.getView()&&['world','party','inventory','journal'].includes(key)});b.setAttribute('aria-current',String(mode===key));}
    actions.replaceChildren();
    if(mode==='title'||mode==='room') {
      button(preview?'进入开发预览':'创建世界 / 房间',()=>join('CREATE'),{disabled:connectionBusy||session.busy||!!session.pending});
      const label=document.createElement('label');label.textContent='房间码 ';const input=document.createElement('input');input.id='join-code';input.maxLength=12;input.autocomplete='off';input.setAttribute('aria-label','房间码');label.append(input);actions.append(label);
      button('加入房间',()=>{const code=input.value.trim();if(!code){notice('请输入房间码',true);return;}join('JOIN',code);},{disabled:connectionBusy||session.busy||!!session.pending});
      if(state.getView())button('返回现场',()=>navigate('world'));
    }
    if(mode==='world'||mode==='battle'||mode==='dialog'){
      const view=state.getView(),opts=mode==='battle'?view?.encounterView?.actions:mode==='dialog'?view?.dialog?.options:view?.availableActions;
      for(const option of opts||[])button(option.label||option.action,()=>command(option),{disabled:!canSubmit(session,view,option)});
      if(mode==='world'&&view?.encounterView)button('查看考核',()=>navigate('battle'));
      if(mode==='dialog')button('关闭对白',()=>navigate('world'));
    }
    if(mode==='party')for(const p of state.getView()?.selfAssets?.party||[])for(const option of p.actions||[])button(`${p.name} · ${option.label}`,()=>command(option),{disabled:!canSubmit(session,state.getView(),option)});
    if(mode==='inventory')for(const item of state.getView()?.selfAssets?.inventory||[])for(const option of item.actions||[])button(`${item.name} · ${option.label}`,()=>command(option),{disabled:!canSubmit(session,state.getView(),option)});
    if(mode==='journal')for(const option of state.getView()?.quest?.actions||[])button(option.label,()=>command(option),{disabled:!canSubmit(session,state.getView(),option)});
    if(mode==='settings'){
      const label=document.createElement('label');label.textContent='文字速度 ';const select=document.createElement('select');select.setAttribute('aria-label','文字速度');for(const [value,text]of [[50,'舒缓'],[30,'标准'],[15,'快速']]){const option=document.createElement('option');option.value=value;option.textContent=text;option.selected=value===preferences.textSpeed;select.append(option);}select.onchange=()=>{preferences.textSpeed=Number(select.value);savePreferences(preferences);notice('显示偏好已保存在本机');};label.append(select);actions.append(label);
    }
    if(session.roomId&&!session.connected)button('重新连接',reconnect,{disabled:connectionBusy});
    if(session.pending&&!session.busy)button('重试上次操作',()=>submit(session.pending),{disabled:!session.connected});
    if(preview&&state.getView()){button('重置预览',()=>join('CREATE'),{disabled:session.busy||!!session.pending});}
  }
  async function join(kind,joinCode){
    if(connectionBusy||session.busy||session.pending)return;connectionBusy=true;notice('正在连接游戏服务…');render();
    try{await gateway.getSession();const room=await gateway.openRoom({requestId:crypto.randomUUID(),mode:kind,joinCode});await connect(room.roomId);navigate('world');}
    catch(error){notice(`${error.message}。${preview?'':'可先打开“开发界面预览”查看 UI；预览不会连接 Java。'}`,true);}
    finally{connectionBusy=false;render();}
  }
  async function connect(roomId){await session.connect(roomId);notice(preview?'开发夹具已载入：操作仅用于验证界面，不代表真实游戏规则。':'已连接，游戏状态以 Java 返回结果为准。');}
  async function reconnect(){if(connectionBusy)return;connectionBusy=true;render();try{await connect(gateway.roomId);}catch(error){notice(error.message,true);}finally{connectionBusy=false;render();}}
  async function command(option){
    const view=state.getView();if(!canSubmit(session,view,option))return;
    const cmd={requestId:crypto.randomUUID(),worldId:view.worldId,action:option.action,targetId:option.targetId,attemptId:view.encounterView?.attemptId,expectedRevision:option.expectedRevision||view.revisions||{},params:option.params||{}};
    await submit(cmd);
  }
  async function submit(cmd){
    if(session.busy||!session.connected)return;notice('等待服务确认…');
    try{const response=session.send(cmd);render();const result=await response;notice(result.message|| (result.outcome==='REJECTED'?`操作未通过：${result.reasonCode}`:'操作已确认'));}
    catch(error){notice(error.definitive?`${error.message}；操作已被拒绝。`:`${error.message}；可重试原操作，不会创建新的请求。`,true);}
    finally{render();}
  }
  state.subscribe(view=>{mode=nextMode(mode,view);render();});
  window.addEventListener('keydown',event=>{
    if(['INPUT','SELECT','TEXTAREA'].includes(event.target.tagName))return;
    if(event.key==='Tab'){event.preventDefault();if(state.getView())navigate(mode==='journal'?'world':'journal');}
    if(event.key==='Escape'&&state.getView())navigate('world');
    if(mode!=='world'||event.repeat)return;
    const direction={w:'UP',ArrowUp:'UP',s:'DOWN',ArrowDown:'DOWN',a:'LEFT',ArrowLeft:'LEFT',d:'RIGHT',ArrowRight:'RIGHT'}[event.key];
    if(direction){event.preventDefault();command({action:'MOVE',params:{direction}});}
    if(event.key.toLowerCase()==='e'){const action=state.getView()?.availableActions?.find(a=>a.enabled!==false);if(action)command(action);}
  });
  window.addEventListener('pagehide',()=>session.dispose(),{once:true});
  notice(preview?'这是独立的 UI 开发预览，所有数据均为夹具。':'创建或加入房间后进入栖流岛。Java 接口未运行时会显示连接错误。');
  return {game,state,gateway};
}
