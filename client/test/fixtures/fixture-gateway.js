// UI-only scripted fixtures. Never imported by index.html or used as game rules.
const option=(label,action,targetId='lan',params={})=>({label,action,targetId,params,enabled:true});
export class FixtureGateway {
  constructor(){this.listeners=new Set();this.results=new Map();this.roomId='fixture-room';}
  async getSession(){return {playerId:'preview-player',environment:'fixture'};}
  async openRoom(){this.reset();return {roomId:this.roomId};}
  reset(){
    this.step=0;this.results.clear();this.view={protocolVersion:1,environment:'fixture',worldId:'fixture-world',roomId:this.roomId,sessionId:crypto.randomUUID(),seq:0,mapId:'chapter1',mapVersion:1,selfPlayerId:'preview-player',hostPlayerId:'preview-player',roomStatus:'ACTIVE',joinCode:'UI-ONLY',revisions:{quest:0,assets:0},entities:[{entityId:'preview-player',kind:'PLAYER',name:'你',gridX:5,gridY:4},{entityId:'lan',kind:'NPC',name:'调查员岚',gridX:6,gridY:3},{entityId:'momo',speciesId:'Mudkip',name:'沫沫 · 公共伙伴',gridX:8,gridY:5}],selfAssets:{party:[],inventory:[]},worldTools:[],quest:{title:'出发前的考核',objective:'与岚交谈，借用火稚鸡参加考核。',evidence:[],rescuedIds:[],actions:[option('获取提示','HINT')]},availableActions:[option('与岚交谈','TALK')]};
  }
  subscribe(fn){this.listeners.add(fn);return()=>this.listeners.delete(fn);}
  emit(message){this.listeners.forEach(fn=>fn(structuredClone(message)));}
  async connect(){if(!this.view)this.reset();this.emit({type:'SNAPSHOT',snapshot:this.view});return this.view;}
  async getSnapshot(){return structuredClone(this.view);}
  disconnect(){}
  publish(){this.view.seq++;this.view.revisions.quest=this.view.seq;this.emit({type:'STATE',sessionId:this.view.sessionId,seq:this.view.seq,eventId:`fixture-${this.view.seq}`,snapshot:this.view});}
  async send(command){
    if(this.results.has(command.requestId))return this.results.get(command.requestId);
    await new Promise(resolve=>setTimeout(resolve,180));const v=this.view;v.presentationEvents=[];
    if(command.action==='MOVE'){
      const p=v.entities[0],delta={UP:[0,-1],DOWN:[0,1],LEFT:[-1,0],RIGHT:[1,0]}[command.params.direction]||[0,0];p.gridX=Math.max(0,Math.min(10,p.gridX+delta[0]));p.gridY=Math.max(1,Math.min(7,p.gridY+delta[1]));
    }else if(command.action==='TALK')v.dialog={speaker:'岚 · 群岛调查员',text:'欢迎来到栖流岛。最近浅潭的水位下降得很快。出发前，先借用我的火稚鸡参加林地考核吧。你的任务是与木守宫建立联系，之后我们一起去看看水渠。',options:[option('借用火稚鸡 · 开始考核','BORROW')]};
    else if(command.action==='BORROW'){
      delete v.dialog;v.selfAssets.party=[{id:'loan',speciesId:'Torchic',name:'火稚鸡',loan:true,hp:100,maxHp:100,active:true}];v.selfAssets.inventory=[{name:'考核专用球',quantity:1}];v.encounterView={attemptId:'fixture-exam',statusLabel:'你的回合',ally:{speciesId:'Torchic',name:'火稚鸡 · 借用',hp:100,maxHp:100},enemy:{speciesId:'Treecko',name:'木守宫',hp:100,maxHp:100},message:'开发夹具：按三次普通攻击切换教学画面。',actions:[option('普通攻击','ATTACK','exam')]};
    }else if(command.action==='ATTACK'){
      this.step=Math.min(3,this.step+1);const hp=[100,80,60,40][this.step];v.encounterView.enemy.hp=hp;v.encounterView.ally.hp=[100,90,80,70][this.step];v.encounterView.message=this.step===3?'服务视图示例：可捕捉（40 HP）。':'服务视图示例：本回合已确认。';
      v.encounterView.actions=this.step===3?[option('投出专用球','CAPTURE','exam')]:[option('普通攻击','ATTACK','exam')];
    }else if(command.action==='CAPTURE'){
      v.presentationEvents=[{eventId:'capture-'+v.sessionId,kind:'CAPTURE',attemptId:'fixture-exam'}];
      delete v.encounterView;v.selfAssets.party=[{id:'treecko-1',speciesId:'Treecko',name:'木守宫',hp:40,maxHp:100,active:true}];v.selfAssets.inventory=[];v.entities.push({entityId:'treecko-1',speciesId:'Treecko',name:'木守宫',ownerPlayerId:v.selfPlayerId,gridX:4,gridY:4});v.dialog={speaker:'岚',text:'你通过了考核！木守宫愿意与你同行。火稚鸡已归还给我。去水渠看看吧：沫沫离开营地后就没回来，村民觉得是它改变了水流。先调查，再下结论。',options:[option('接取失水调查','ACCEPT')]};
    }else if(command.action==='ACCEPT'){
      delete v.dialog;v.quest.title='失水的浅潭';v.quest.objective='检查现场，记录四条线索，了解沫沫的行为。';v.availableActions=['E11','E12','E13','E14'].map((id,i)=>option(['检查浅潭','观察水流','观察沫沫救援','检查导流板'][i],'EVIDENCE',id));
    }else if(command.action==='EVIDENCE'){
      if(!v.quest.evidence.includes(command.targetId))v.quest.evidence.push(command.targetId);v.availableActions=v.availableActions.filter(a=>a.targetId!==command.targetId);
      if(v.quest.evidence.length===4){v.quest.objective='沫沫正在保护幼崽。领取公共工具，修复水路。';v.availableActions=[option('领取修理工具','TOOLS')];}
    }else if(command.action==='TOOLS'){
      v.worldTools=[{name:'修理工具',quantity:1},{name:'闸门固定栓',quantity:1}];v.availableActions=[option('木守宫 · 切开藤蔓','VINES','vines')];v.quest.objective='用木守宫切开阻塞水路的藤蔓。';
    }else if(['VINES','STONES','LATCH','WATER'].includes(command.action)){
      const next={VINES:['搬开落石','STONES','清理落石，为安装固定栓留出空间。'],STONES:['固定闸门','LATCH','固定闸门，单人也能保持水路稳定。'],LATCH:['请沫沫引水','WATER','请公共伙伴沫沫引水，让浅潭恢复安全水位。'],WATER:['护送第一只幼崽','RESCUE','水位安全。逐只护送三只幼崽。']}[command.action];v.availableActions=[option(next[0],next[1],'waterway')];v.quest.objective=next[2];
    }else if(command.action==='RESCUE'){
      v.quest.rescuedIds.push('baby-'+(v.quest.rescuedIds.length+1));const count=v.quest.rescuedIds.length;v.quest.objective=count===3?'幼崽全部回到浅潭。回营地向岚报告。':`已护送 ${count}/3，继续护送下一只。`;v.availableActions=[count===3?option('回营地报告','FINISH'):option('护送下一只幼崽','RESCUE','baby')];
    }else if(command.action==='FINISH'){
      v.quest.title='水流重新响起';v.quest.objective='首章界面流程预览完成。真实章节结算仍需 Java 服务实现。';v.availableActions=[];
    }else if(command.action==='HINT'){
      v.quest.hintLevel=Math.min(3,(v.quest.hintLevel||0)+1);v.quest.hint=`提示 ${v.quest.hintLevel}/3：${v.quest.objective}`;
    }
    this.publish();const result={requestId:command.requestId,outcome:'APPLIED',message:'开发夹具已切换画面 · 非真实规则验收'};this.results.set(command.requestId,result);return result;
  }
}
