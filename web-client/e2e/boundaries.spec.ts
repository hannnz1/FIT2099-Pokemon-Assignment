import {test,expect} from '@playwright/test';
test('a map-version mismatch blocks keyboard and mouse actions',async({page})=>{
 await page.route('**/api/games',async route=>{const response=await route.fetch();const body=await response.json();body.map.version='different-map';await route.fulfill({response,json:body});});
 await page.goto('/');await expect(page.locator('#error')).toContainText('地图版本不匹配');
 await page.keyboard.press('Space');await page.waitForTimeout(300);
 const state=await page.evaluate(async()=>await (await fetch('/api/games/current')).json());expect(state.turn).toBe(0);
});
test('reading, viewing menus, toggling simplified animation do not advance the engine',async({page})=>{
 await page.goto('/');await expect(page.locator('#state')).toHaveAttribute('data-state','READY');await page.locator('#motion').check();await expect(page.locator('#skip')).toHaveCount(0);await page.locator('#refresh').click();await expect(page.locator('#state')).toHaveAttribute('data-state','READY');await expect(page.locator('#turn')).toHaveText('0');await page.reload();await expect(page.locator('#state')).toHaveAttribute('data-state','READY');await expect(page.locator('#turn')).toHaveText('0');
});
test('the guide describes the real engine preferences',async({page})=>{await page.goto('/');await page.locator('#help-toggle').click();await expect(page.locator('#help')).toContainText('水跃鱼喜欢拍胸脯');await expect(page.locator('#help')).toContainText('火稚鸡喜欢唱歌');});

test('movement pad has four directions and diagonal keys do nothing',async({page})=>{
 await page.goto('/');await expect(page.locator('#state')).toHaveAttribute('data-state','READY');
 await expect(page.locator('#pad button')).toHaveCount(4);
 expect(await page.locator('#pad button').evaluateAll(buttons=>buttons.map(b=>b.getAttribute('data-direction')))).toEqual(['North','West','East','South']);
 for(const key of ['Numpad1','Numpad3','Numpad7','Numpad9'])await page.keyboard.press(key);
 await expect(page.locator('#turn')).toHaveText('0');
 await page.keyboard.press('ArrowDown');await expect(page.locator('#turn')).toHaveText('1');
});
