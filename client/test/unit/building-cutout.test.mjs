import test from 'node:test';import assert from 'node:assert/strict';
import {buildingCutout} from '../../agent/building-cutout.mjs';
function image(rows){const colors={g:[175,208,124,255],w:[240,240,235,255],b:[70,90,140,255],s:[30,130,40,255]};return new Uint8ClampedArray(rows.flatMap(r=>[...r].flatMap(c=>colors[c])));}
test('removes connected exterior grass but keeps enclosed green shop sign and building',()=>{const input=image(['ggggggg','gwwwwwg','gwbsbwg','gwwwwwg','ggggggg']),before=input.slice(),out=buildingCutout(input,7,5);assert.deepEqual(input,before);assert.equal(out[3],0);assert.equal(out[(2*7+3)*4+3],255);assert.equal(out[(1*7+1)*4+3],255);});
test('drops detached map decoration but keeps roof touching crop edge',()=>{const out=buildingCutout(image(['ggbbggg','ggbbggg','ggbbggg','ggggggg','wgggggg']),7,5);assert.equal(out[(0*7+2)*4+3],255);assert.equal(out[(4*7)*4+3],0);});
test('transparent input stays transparent; malformed dimensions rejected',()=>{assert.equal(buildingCutout(new Uint8ClampedArray(16),2,2).some(v=>v),false);assert.throws(()=>buildingCutout(new Uint8ClampedArray(4),2,2),/INVALID_BUILDING_PIXELS/);});
