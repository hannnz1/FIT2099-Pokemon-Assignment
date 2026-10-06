// Reproducible local, unpaid presentation benchmark. TEST_SITE must use a disabled provider.
const {chromium}=require('C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs=require('fs'),path=require('path');
(async()=>{const browser=await chromium.launch({headless:true,executablePath:'C:/Users/Administrator/AppData/Local/ms-playwright/chromium_headless_shell-1243/chrome-headless-shell-win64/chrome-headless-shell.exe'});
try{const page=await browser.newPage({viewport:{width:1280,height:950}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{
  let phaser;Object.defineProperty(window,'Phaser',{configurable:true,get:()=>phaser,set:v=>{phaser=v;const Game=v.Game;v.Game=new Proxy(Game,{construct(t,args){const g=Reflect.construct(t,args);window.__benchmarkGame=g;return g;}});}});
  window.__animation={frames:[],longTasks:[],roundTrips:[],objects:[]};let previous;
  const frame=now=>{if(previous&&now-previous<1000)__animation.frames.push(now-previous);previous=now;requestAnimationFrame(frame);};requestAnimationFrame(frame);
  new PerformanceObserver(list=>list.getEntries().forEach(e=>__animation.longTasks.push(e.duration))).observe({type:'longtask',buffered:true});
  const fetchOriginal=window.fetch;window.fetch=async(...args)=>{const start=performance.now();try{return await fetchOriginal(...args);}finally{if(String(args[0]).endsWith('/commands'))__animation.roundTrips.push(performance.now()-start);}};
 });
 const site=process.env.TEST_SITE||'http://127.0.0.1:8098/growth/';await page.goto(site);await page.waitForSelector('#starter button:enabled');await page.locator('#starter button').first().click();await page.waitForSelector('#map[data-state=READY]');await page.waitForTimeout(1000);
 for(let i=0;i<40;i++){await page.keyboard.press(i%2?'ArrowLeft':'ArrowRight');await page.waitForTimeout(220);await page.evaluate(()=>__animation.objects.push(__benchmarkGame?.scene?.scenes?.[0]?.children?.length??null));}
 await page.waitForTimeout(1100);const raw=await page.evaluate(()=>({metrics:__animation,device:{ua:navigator.userAgent,cores:navigator.hardwareConcurrency,dpr:devicePixelRatio},map:{...document.querySelector('#map').dataset}}));
 const quantile=(a,q)=>a.length?[...a].sort((a,b)=>a-b)[Math.min(a.length-1,Math.ceil(a.length*q)-1)]:null;
 const result={environment:'Windows, headless Chromium; not actual mobile hardware',viewport:{width:1280,height:950},site,recordedAt:new Date().toISOString(),errors,...raw,summary:{frameP50:quantile(raw.metrics.frames,.5),frameP95:quantile(raw.metrics.frames,.95),commandP95:quantile(raw.metrics.roundTrips,.95),longTasks:raw.metrics.longTasks.length,objectsMin:Math.min(...raw.metrics.objects.filter(Number.isFinite)),objectsMax:Math.max(...raw.metrics.objects.filter(Number.isFinite))}};
 const out=process.env.BENCH_OUT||'benchmarks/animation/baseline.json';fs.mkdirSync(path.dirname(out),{recursive:true});fs.writeFileSync(out,JSON.stringify(result,null,2));console.log(JSON.stringify(result.summary));if(errors.length)throw new Error(errors.join('; '));
}finally{await browser.close();}})().catch(e=>{console.error(e.message);process.exitCode=1;});
