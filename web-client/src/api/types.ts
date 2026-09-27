export interface Actor {id:string;kind:string;name:string;x:number;y:number;hp:number;maxHp:number;affection:number|null;elements:string[]}
export interface Item {id:string;kind:string;name:string;containedPokemon:Actor|null}
export interface Action {id:string;kind:string;label:string;targetId:string|null;direction:string|null;enabled:boolean;reason:string|null}
export interface Snapshot {gameId:string;revision:number;turn:number;phase:string;period:string;nextActionPeriod:string;playerId:string;map:{id:string;version:string;width:number;height:number;tileSize:number;grounds:{x:number;y:number;kind:string}[]};actors:Actor[];groundItems:{x:number;y:number;item:Item}[];inventory:Item[];availableActions:Action[];log:{id:string;turn:number;text:string}[]}
export interface GameEvent {kind:string;actorId:string|null;targetId:string|null;text:string}
export interface ActionResult {snapshot:Snapshot;events:GameEvent[];replayed:boolean;appliedRevision:number;requestId:string}
