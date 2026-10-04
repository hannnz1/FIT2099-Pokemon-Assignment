import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

// Import inside a test so the missing presentation manifest produces a targeted failure.
test('named world objects expose usable art without inventing unknown object URLs', async () => {
 const {worldArt,getWorldArt,worldArtUrl,worldArtDataUrl}=await import('../../agent/world-art.mjs');
 for(const id of ['professor','merchant','player','orchard','market','laboratory','alternative','berry','recovery','gate']) {
  assert.ok(getWorldArt(id).label);
  assert.equal(getWorldArt(id),worldArt[id]);
  if(worldArt[id].kind==='portrait'){
   const art=worldArt[id],file=new URL('../../'+art.path,import.meta.url),svg=fs.readFileSync(file,'utf8');
   assert.match(svg,/<svg[^>]*viewBox="0 0 64 88"/);assert.doesNotMatch(svg,/<script|<foreignObject|https?:\/\/(?!www.w3.org)/);
  } else if(worldArt[id].kind==='symbol') {
   assert.match(worldArtDataUrl(id),/^data:image\/svg\+xml,/);
   assert.match(decodeURIComponent(worldArtDataUrl(id).split(',')[1]),/<svg[^>]*viewBox="0 0 64 64"/);
  } else {
   const bytes=fs.readFileSync(new URL('../../'+worldArtUrl(id).slice('/rpg/'.length),import.meta.url));
   assert.equal(bytes.subarray(0,8).toString('hex'),'89504e470d0a1a0a');
   const width=bytes.readUInt32BE(16),height=bytes.readUInt32BE(20);
   const a=worldArt[id],rect=a.kind==='sprite'?{x:a.frame%4*a.frameWidth,y:Math.floor(a.frame/4)*a.frameHeight,width:a.frameWidth,height:a.frameHeight}:a.crop;
   assert.ok(rect.x>=0&&rect.y>=0&&rect.width>0&&rect.height>0&&rect.x+rect.width<=width&&rect.y+rect.height<=height,id+' must fit real PNG');
  }
 }
 assert.equal(worldArt.professor.kind,'portrait');assert.equal(worldArt.merchant.kind,'portrait');
 assert.notEqual(worldArt.professor.path,worldArt.merchant.path);
 const list=JSON.parse(fs.readFileSync(new URL('../../agent/rpg-assets.json',import.meta.url)));for(const id of ['professor','merchant'])assert.ok(list.includes(worldArt[id].path));
 for(const id of ['unknown','__proto__','../secret']) assert.throws(()=>getWorldArt(id),/UNKNOWN_WORLD_ART/);
 assert.throws(()=>worldArtDataUrl('professor'),/NOT_WORLD_SYMBOL/);
});

test('DOM crop styles isolate the inspected market and preserve symbol URLs',async()=>{
 const {worldArtDomStyle}=await import('../../agent/world-art.mjs');
 const market=worldArtDomStyle('market',64);
 assert.equal(market.width,'64px');
 assert.equal(market.height,(384/448*64)+'px');
 assert.equal(market.backgroundPosition,(-1472/448*64)+'px '+(-1632/448*64)+'px');
 // Hand-inspected atlas tree is at row 78, not the empty grass at row 76.
 assert.equal(worldArtDomStyle('alternative',64).backgroundPosition,'-640px -2496px');
 assert.match(worldArtDomStyle('berry',32).backgroundImage,/data:image\/svg\+xml/);
 assert.throws(()=>worldArtDomStyle('berry',0),/INVALID_ART_SIZE/);
 assert.throws(()=>worldArtDomStyle('berry',NaN),/INVALID_ART_SIZE/);
});

test('NPC portraits fit DOM cards at original aspect ratio',async()=>{const {worldArtDomStyle}=await import('../../agent/world-art.mjs');for(const id of ['professor','merchant']){const style=worldArtDomStyle(id,32);assert.equal(style.height,'44px');assert.equal(style.backgroundSize,'100% 100%');assert.match(style.backgroundImage,/assets\/ui\/npcs\//);}});
