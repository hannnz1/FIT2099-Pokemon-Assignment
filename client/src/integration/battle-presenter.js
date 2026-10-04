export class BattlePresenter {
  constructor(render){this.render=render;this.seen=new Set();this.epoch=0;}
  async play(events){const epoch=this.epoch;for(const event of events){if(epoch!==this.epoch)return;if(this.seen.has(event.eventId))continue;this.seen.add(event.eventId);await this.render(event);}}
  cancel(){this.epoch++;}
}
