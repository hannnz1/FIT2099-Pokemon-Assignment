import {failureReason} from './failure-recovery.mjs';
export const labels={IDLE:'准备对战',RUNNING:'对战进行中',PAUSED:'对战已暂停',COMPLETED:'对战结束',CANCELLED:'本局已结束',STORAGE_ERROR:'存档失败，请恢复'};
export const results={LEFT_WON:'己方获胜',RIGHT_WON:'对手获胜',DRAW:'双方倒下 · 平局',TURN_LIMIT_DRAW:'达到回合上限 · 平局',ACTIVE:'对战进行中'};
export function art(species){return `/rpg/assets/images/pokemon/sprites/${String(species).toLowerCase()}.png`;}
export function hpPercent(p){return p?.maxHp>0?Math.max(0,Math.min(100,100*p.hp/p.maxHp)):0;}
export function hint(v){if(v.status==='PAUSED'&&v.usageIncomplete)return '有模型请求的用量尚未确认，已暂停付费调用；可手动接管或开始新局。';if(v.status==='PAUSED')return v.errorCode?`已暂停：${v.errorCode}。可继续或接管己方。`:'已暂停，可继续或接管己方。';if(v.status!=='RUNNING')return labels[v.status]??v.status;if(v.controlMode!=='manual')return 'AI 正在选择行动；可随时暂停。';if(v.world?.choiceLocked)return '己方选择已锁定，正在等待对手。';return v.world?.ownTeam?.[v.world.activeSlot]?.hp===0?'当前伙伴倒下，请选择能够战斗的队友。':'选择技能或换人；双方选择完成后统一结算。';}

export function canRetryAction(before,after,params){if(before?.taskId!==after?.taskId||after?.status!=='RUNNING'||after?.controlMode!=='manual'||!after.world?.canChoose||before?.world?.round!==after.world.round||before?.world?.activeSlot!==after.world.activeSlot)return false;return params?.tool==='battle_move'?after.world.legalMoves.includes(params.arguments?.moveId):params?.tool==='battle_switch'&&after.world.legalSwitches.includes(params.arguments?.slot);}

export async function recoverDuelConnection(client){await client.connect();if(client.pendingRequest){await client.retry();await client.poll();}return client.view;}
