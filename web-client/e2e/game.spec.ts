import {test,expect,type Page} from '@playwright/test';
async function ready(p:Page){await p.bringToFront();await expect(p.locator('#state')).toHaveAttribute('data-state','READY',{timeout:12000});}
async function selectFor(p:Page,kind:string){const s=await snapshot(p);const a=s.availableActions.find((a:any)=>a.kind===kind);if(a?.targetId)await p.locator(`#targets [data-target-id="${a.targetId}"]`).click();}
async function clickAction(p:Page,kind:string){await ready(p);await selectFor(p,kind);await p.locator(`#actions button[data-kind="${kind}"]`).first().click();await ready(p);await p.waitForTimeout(230);}
async function snapshot(p:Page){return p.evaluate(async()=>await (await fetch('/api/games/current')).json());}
const deltas:Record<string,[number,number]>={'North':[0,-1],'North-East':[1,-1],'East':[1,0],'South-East':[1,1],'South':[0,1],'South-West':[-1,1],'West':[-1,0],'North-West':[-1,-1]};
function limits(s:any){for(const kind of ['TREECKO','MUDKIP','TORCHIC'])expect(s.actors.filter((a:any)=>a.kind===kind).length).toBeLessThanOrEqual(3);expect(s.groundItems.filter((i:any)=>i.item.kind==='CANDY').length).toBeLessThanOrEqual(2);}
// Replan against real snapshots after every move: wild actors and terrain can change.
async function travel(p:Page,goal:'CANDY'|'MERCHANT'|'PROFESSOR'){
 for(let step=0;step<220;step++){
  const s=await snapshot(p);limits(s);const me=s.actors.find((a:any)=>a.id===s.playerId);
  const goals=new Set<string>();
  if(goal==='CANDY')for(const item of s.groundItems.filter((i:any)=>i.item.kind==='CANDY'))goals.add(`${item.x},${item.y}`);
  else {const target=s.actors.find((a:any)=>a.kind===goal);for(const [dx,dy] of Object.values(deltas))goals.add(`${target.x+dx},${target.y+dy}`);}
  if(goals.has(`${me.x},${me.y}`))return;
  const blocked=new Set(s.map.grounds.filter((g:any)=>g.kind==='WALL').map((g:any)=>`${g.x},${g.y}`));
  for(const a of s.actors)if(a.id!==s.playerId)blocked.add(`${a.x},${a.y}`);
  const queue=[{x:me.x,y:me.y,first:''}],seen=new Set([`${me.x},${me.y}`]);let direction='';
  for(const cell of queue){if(goals.has(`${cell.x},${cell.y}`)){direction=cell.first;break;}
   for(const [d,[dx,dy]] of Object.entries(deltas)){if(Math.abs(dx)+Math.abs(dy)!==1)continue;const x=cell.x+dx,y=cell.y+dy,k=`${x},${y}`;if(x>=0&&y>=0&&x<s.map.width&&y<s.map.height&&!blocked.has(k)&&!seen.has(k)){seen.add(k);queue.push({x,y,first:cell.first||d});}}
  }
  if(direction)await p.locator(`#pad [data-direction="${direction}"]`).click();else await p.locator('#wait').click();
  await ready(p);await p.waitForTimeout(230);
 }
 throw Error(`Could not reach ${goal}`);
}

test('real Java exploration, capture, random candy, role-specific NPCs, trade and reload',async({page})=>{
 test.setTimeout(180000);
 const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message));await page.goto('/');await ready(page);await expect(page.locator('canvas')).toBeVisible();await expect(page.locator('#turn')).toHaveText('0');
 const initial=await snapshot(page);expect(initial.groundItems.filter((i:any)=>i.item.kind==='CANDY')).toHaveLength(2);limits(initial);
 await page.locator(`#targets [data-target-id="${initial.actors.find((a:any)=>a.kind==='TREECKO').id}"]`).click();await page.locator('#actions [data-kind="CAPTURE"]').click();await ready(page);await page.waitForTimeout(230);await expect(page.locator('#inventory')).toContainText('木守宫');
 const captured=(await snapshot(page)).inventory.find((i:any)=>i.containedPokemon).containedPokemon;
 if(await page.locator('#actions [data-kind="SING"]').count())await clickAction(page,'SING');
 await page.locator('#motion').check();
 await travel(page,'PROFESSOR');await clickAction(page,'TALK');await expect(page.locator('#dialogue')).toBeVisible();
 for(let i=0;i<3;i++){await travel(page,'CANDY');await clickAction(page,'PICK_UP');const s=await snapshot(page);limits(s);expect(s.groundItems.filter((item:any)=>item.item.kind==='CANDY')).toHaveLength(2);}
 await travel(page,'MERCHANT');const nearby=await snapshot(page);
 for(const npc of nearby.actors.filter((a:any)=>a.kind==='PROFESSOR'||a.kind==='MERCHANT')){
  const actions=nearby.availableActions.filter((a:any)=>a.targetId===npc.id);expect(actions.every((a:any)=>a.kind===(npc.kind==='PROFESSOR'?'TALK':'TRADE'))).toBeTruthy();
 }
 await expect(page.locator('#actions [data-kind="ATTACK"]')).toHaveCount(0);
 await selectFor(page,'TRADE');await page.locator('#actions [data-kind="TRADE"]').filter({hasText:'GreatBall'}).click();await ready(page);
 const s=await snapshot(page);expect(s.inventory.filter((i:any)=>i.kind==='CANDY')).toHaveLength(0);expect(s.inventory.some((i:any)=>i.kind==='GREAT_BALL')).toBeTruthy();expect(s.inventory.find((i:any)=>i.containedPokemon?.id===captured.id).containedPokemon.hp).toBe(captured.hp);
 await page.reload();await ready(page);await expect(page.locator('#turn')).toHaveText(String(s.turn));await expect(page.locator('#inventory')).toContainText('GreatBall');expect(errors).toEqual([]);
 await page.screenshot({path:'test-results/game-1366.png',fullPage:true});await page.setViewportSize({width:1920,height:1080});await page.screenshot({path:'test-results/game-1920.png',fullPage:true});
});

test('lost response retry does not execute twice',async({page})=>{
 await page.goto('/');await ready(page);let dropped=false;
 await page.route('**/api/games/*/actions',async route=>{if(!dropped){dropped=true;await route.fetch();await route.abort('failed');}else await route.continue();});
 await page.locator('#wait').click();await expect(page.locator('#state')).toHaveAttribute('data-state','ERROR');const after=await snapshot(page);expect(after.turn).toBe(1);await page.locator('#retry').click();await ready(page);await expect(page.locator('#turn')).toHaveText('1');
});

test('independent visitors and shared-cookie tabs are isolated correctly',async({browser})=>{
 const a=await browser.newContext(),b=await browser.newContext();try{const p=await a.newPage(),q=await b.newPage();await p.goto('/');await q.goto('/');await ready(p);await ready(q);const sa=await snapshot(p),sb=await snapshot(q);expect(sa.gameId).not.toBe(sb.gameId);
 const tab=await a.newPage();await tab.goto('/');await ready(tab);await p.locator('#wait').click();await ready(p);await tab.locator('#wait').click();await ready(tab);await expect(tab.locator('#turn')).toHaveText('1');await expect(q.locator('#turn')).toHaveText('0');
 const denied=await q.evaluate(async id=>(await fetch('/api/games/'+id)).status,sa.gameId);expect(denied).toBe(404);
 }finally{await a.close();await b.close();}
});

test('choose a target before an interaction; selection does not advance a turn',async({page})=>{
 await page.goto('/');await ready(page);const s=await snapshot(page);
 await expect(page.locator('#actions button')).toHaveCount(0);
 const treecko=s.actors.find((a:any)=>a.kind==='TREECKO'),mudkip=s.actors.find((a:any)=>a.kind==='MUDKIP');
 await page.locator(`#targets [data-target-id="${mudkip.id}"]`).click();
 await expect(page.locator('#target')).toContainText('水跃鱼');
 await expect(page.locator('#actions')).not.toContainText('Treecko');
 await expect(page.locator('#turn')).toHaveText('0');
 await page.locator(`#targets [data-target-id="${treecko.id}"]`).click();
 await expect(page.locator('#actions')).not.toContainText('Mudkip');
 await page.locator('#actions [data-kind="CAPTURE"]').click();await ready(page);
 await expect(page.locator('#inventory')).toContainText('木守宫');
 await expect(page.locator('#targets [aria-pressed="true"]')).toHaveCount(0);
 await expect(page.locator('#actions button')).toHaveCount(0);
});
