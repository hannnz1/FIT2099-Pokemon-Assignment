import {test,expect} from '@playwright/test';
import fs from 'node:fs';
import {createHash} from 'node:crypto';

test('reference town and character images load unchanged; building collision stays authoritative',async({page})=>{
 const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message));
 let mapUrl='';const loaded=new Set<string>();page.on('response',r=>{if(r.ok())loaded.add(new URL(r.url()).pathname);if(r.url().includes('/world.json'))mapUrl=r.url();});
 await page.goto('/');await page.bringToFront();
 await expect(page.locator('#state')).toHaveAttribute('data-state','READY');
 for(const file of ['world.json','atlas.json','atlas.png','tuxemon-sample-32px-extruded.png'])expect(loaded.has('/assets/phaser-rpg/'+file)).toBeTruthy();
 const s=await page.evaluate(async()=>await (await fetch('/api/games/current')).json());
 expect(s.map.width).toBe(40);expect(s.map.height).toBe(40);
 expect(new URL(mapUrl).searchParams.get('v')).toBe(s.map.version);
 // The sign directly north of the initial player is a real reference-map obstacle.
 await expect(page.locator('#pad [data-direction="North"]')).toBeDisabled();
 await page.keyboard.press('ArrowUp');await expect(page.locator('#turn')).toHaveText('0');
 await page.screenshot({path:'test-results/reference-town-1366.png',fullPage:true});
 await page.setViewportSize({width:1920,height:1080});await page.screenshot({path:'test-results/reference-town-1920.png',fullPage:true});
 await page.locator('#pad [data-direction="South"]').click();
 await expect(page.locator('#state')).toHaveAttribute('data-state','READY');
 await expect(page.locator('#position')).toContainText('(11, 36)');
 await page.screenshot({path:'test-results/reference-town-moved.png',fullPage:true});
 for(const file of ['atlas.png','tuxemon-sample-32px-extruded.png']){
  const response=await page.request.get('/assets/phaser-rpg/'+file);
  const local=fs.readFileSync(new URL('../public/assets/phaser-rpg/'+file,import.meta.url));
  expect(createHash('sha256').update(await response.body()).digest('hex')).toBe(createHash('sha256').update(local).digest('hex'));
 }
 await page.goto('/credits.html');await expect(page.getByRole('heading',{name:'素材来源与许可'})).toBeVisible();
 expect(errors).toEqual([]);
});

test('missing reference art is visible as a load failure, never a silent geometry fallback',async({page})=>{
 let games=0;page.on('request',r=>{if(r.method()==='POST'&&new URL(r.url()).pathname==='/api/games')games++;});
 await page.route('**/assets/phaser-rpg/atlas.png',route=>route.abort());
 await page.goto('/');await expect(page.locator('#loading')).toContainText('场景素材加载失败');
 await expect(page.locator('#wait')).toBeDisabled();
 expect(games).toBe(0);
});

test('startup creates a session once and a failed initial connection can recover',async({page})=>{
 let posts=0,fail=true;
 await page.route('**/api/games',async route=>{if(route.request().method()==='POST'){posts++;if(fail){await route.abort();return;}}await route.continue();});
 await page.goto('/');await expect(page.locator('#state')).toHaveAttribute('data-state','ERROR');
 expect(posts).toBe(1);
 fail=false;await page.locator('#recover').click();
 await expect(page.locator('#state')).toHaveAttribute('data-state','READY');
 expect(posts).toBe(2);
 posts=0;await page.reload();await expect(page.locator('#state')).toHaveAttribute('data-state','READY');
 expect(posts).toBe(1);
});

