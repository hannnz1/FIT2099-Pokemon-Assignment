import test from 'node:test';import assert from 'node:assert/strict';import fs from 'node:fs';import crypto from 'node:crypto';
import {pokemonArt,pokemonNames,portraitUrl,spriteUrl} from '../../agent/pokemon-art.mjs';
test('all twenty-one forms have separate packaged portrait and map images with verified source bytes',()=>{
 const manifest=JSON.parse(fs.readFileSync(new URL('../../assets/images/pokemon/sources.json',import.meta.url)));const resources=JSON.parse(fs.readFileSync(new URL('../../agent/rpg-assets.json',import.meta.url)));
 assert.equal(Object.keys(pokemonArt).length,21);assert.equal(manifest.assets.length,42);const paths=new Set();
 for(const [species,dex] of Object.entries(pokemonArt)){assert.ok(pokemonNames[species]);for(const [style,url] of [['portraits',portraitUrl(species)],['sprites',spriteUrl(species)]]){
  const path=url.slice('/rpg/'.length),source=manifest.assets.find(a=>a.species===species&&a.style===style);assert.equal(source.path,path);assert.equal(source.dex,dex);assert.ok(source.source.endsWith('/'+dex+'.png'));assert.ok(resources.includes(path));assert.ok(!paths.has(path));paths.add(path);
  const bytes=fs.readFileSync(new URL('../../'+path,import.meta.url));assert.equal(bytes.subarray(0,8).toString('hex'),'89504e470d0a1a0a');assert.equal(crypto.createHash('sha256').update(bytes).digest('hex'),source.sha256);
 }}
});
test('unknown species cannot generate arbitrary asset URLs',()=>{assert.throws(()=>portraitUrl('../secret'));assert.throws(()=>spriteUrl('UNKNOWN'));assert.throws(()=>spriteUrl('__proto__'));});
