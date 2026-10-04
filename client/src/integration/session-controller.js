export function canSubmit(session,view,option){return !!(session.connected&&!session.busy&&!session.pending&&view?.roomStatus==='ACTIVE'&&option?.enabled!==false);}
export class SessionController {
  constructor(gateway,state,onStatus=()=>{}){
    this.gateway=gateway;this.state=state;this.onStatus=onStatus;this.epoch=0;this.connected=false;this.busy=false;this.queue=[];
    this.unsubscribe=gateway.subscribe(message=>this.receive(message));
  }
  async connect(roomId){
    if(this.pending&&this.roomId!==roomId)throw Error('请先确认原房间的待处理操作');
    const epoch=++this.epoch;this.connected=false;this.resyncing=false;this.queue=[];this.roomId=roomId;this.state.reset();
    await this.gateway.connect(roomId);
    if(epoch!==this.epoch)throw Error('连接已失效');
    if(!this.state.getView())throw Error('没有收到有效房间快照');
    this.connected=true;
  }
  async send(command){
    if(!this.connected||this.busy)throw Error('当前连接不可提交');
    if(this.pending&&JSON.stringify(this.pending)!==JSON.stringify(command))throw Error('请先重试待确认操作');
    const room=this.roomId;this.pending=structuredClone(command);this.busy=true;
    try{
      const result=await this.gateway.send(this.pending);
      if(this.roomId!==room)throw Error('操作所属房间已改变');
      if(result.requestId!==command.requestId)throw Error('请求结果不匹配');
      this.pending=undefined;return result;
    }catch(error){if(error.definitive)this.pending=undefined;throw error;}
    finally{this.busy=false;}
  }
  receive(message){
    try{
      if(message.type==='DISCONNECTED'){this.epoch++;this.connected=false;this.resyncing=false;this.queue=[];this.onStatus('连接中断，停止提交操作；重新连接后可以重试原操作。',true);return;}
      if(message.type==='ERROR')throw Error(message.message);
      if(message.type==='SNAPSHOT'){
        if(message.snapshot?.roomId!==this.roomId)return;
        this.state.applySnapshot(message.snapshot);return;
      }
      const view=this.state.getView();if(!view||message.sessionId!==view.sessionId)return;
      if(this.resyncing){this.queue.push(message);if(this.queue.length>512)throw Error('同步缓冲超限，请重新连接');return;}
      if(this.state.applyEvent(message)==='RESYNC'){this.queue.push(message);void this.resync();}
    }catch(error){this.connected=false;this.onStatus(error.message,true);}
  }
  async resync(){
    const epoch=this.epoch,room=this.roomId,session=this.state.getView().sessionId;this.resyncing=true;
    try{
      for(let attempt=0;attempt<3;attempt++){
        const snapshot=await this.gateway.getSnapshot(room);
        if(epoch!==this.epoch)return;
        if(snapshot.roomId!==room||snapshot.sessionId!==session)throw Error('同步会话已改变，请重新连接');
        this.state.applySnapshot(snapshot);
        const buffered=this.queue.splice(0).sort((a,b)=>a.seq-b.seq);
        for(const event of buffered)if(this.state.applyEvent(event)==='RESYNC')this.queue.push(event);
        if(!this.queue.length)return;
      }
      throw Error('无法恢复连续状态，请重新连接');
    }catch(error){if(epoch===this.epoch){this.connected=false;this.onStatus(error.message,true);}}
    finally{if(epoch===this.epoch)this.resyncing=false;}
  }
  dispose(){this.epoch++;this.unsubscribe();this.gateway.disconnect();}
}
