import {test,expect,type Page} from '@playwright/test';
async function ready(p:Page){await p.bringToFront();await expect(p.locator('#state')).toHaveAttribute('data-state','READY',{timeout:12000});}
async function clickAction(p:Page,kind:string){await ready(p);await p.locator(`#actions button[data-kind="${kind}"]`).first().click();await ready(p);await p.waitForTimeout(230);}
async function snapshot(p:Page){return p.evaluate(async()=>await (await fetch('/api/games/current')).json());}
async function goToMerchant(p:Page){
 const deltas:Record<string,[number,number]>={'North':[0,-1],'North-East':[1,-1],'East':[1,0],'South-East':[1,1],'South':[0,1],'South-West':[-1,1],'West':[-1,0],'North-West':[-1,-1]};
 for(let i=0;i<25;i++){const s=await snapshot(p);if(s.availableActions.some((a:any)=>a.kind==='TRADE'))return;const me=s.actors.find((a:any)=>a.id===s.playerId),merchant=s.actors.find((a:any)=>a.kind==='MERCHANT');const moves=s.availableActions.filter((a:any)=>a.kind==='MOVE');moves.sort((a:any,b:any)=>{const da=deltas[a.direction],db=deltas[b.direction];return Math.hypot(me.x+da[0]-merchant.x,me.y+da[1]-merchant.y)-Math.hypot(me.x+db[0]-merchant.x,me.y+db[1]-merchant.y);});await p.locator(`#pad button[data-direction="${moves[0].direction}"]`).click();await ready(p);await p.waitForTimeout(230);}
 throw Error('Merchant not reached');
}
test('real Java exploration, capture, items, trade, day/night and reload',async({page})=>{
 const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message));await page.goto('/');await ready(page);await expect(page.locator('canvas')).toBeVisible();await expect(page.locator('#turn')).toHaveText('0');
 await page.locator('#actions [data-kind="CAPTURE"]').filter({hasText:'Treecko'}).click();await ready(page);await page.waitForTimeout(230);await expect(page.locator('#inventory')).toContainText('木守宫');
 const captured=(await snapshot(page)).inventory.find((i:any)=>i.containedPokemon).containedPokemon;
 if(await page.locator('#actions [data-kind="SING"]').count())await clickAction(page,'SING');
 for(let i=0;i<20;i++)await clickAction(page,'PICK_UP');
 await goToMerchant(page);await clickAction(page,'TALK');await expect(page.locator('#dialogue')).toBeVisible();
 const trades=page.locator('#actions [data-kind="TRADE"]');await trades.filter({hasText:'GreatBall'}).click();await ready(page);await page.waitForTimeout(230);await trades.filter({hasText:'MasterBall'}).click();await ready(page);await page.waitForTimeout(230);await trades.filter({hasText:'Torchic'}).click();await ready(page);
 const s=await snapshot(page);expect(s.inventory.filter((i:any)=>i.kind==='CANDY').length).toBe(1);expect(s.inventory.some((i:any)=>i.kind==='GREAT_BALL')).toBeTruthy();expect(s.inventory.some((i:any)=>i.kind==='MASTER_BALL')).toBeTruthy();expect(s.inventory.some((i:any)=>i.containedPokemon?.kind==='TORCHIC')).toBeTruthy();expect(s.inventory.find((i:any)=>i.containedPokemon?.id===captured.id).containedPokemon.hp).toBe(captured.hp);
 await page.reload();await ready(page);await expect(page.locator('#turn')).toHaveText(String(s.turn));await expect(page.locator('#inventory')).toContainText('火稚鸡');expect(errors).toEqual([]);
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
