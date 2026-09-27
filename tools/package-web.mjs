import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const source=new URL('../web-client/dist/',import.meta.url),dest=new URL('../resources/public/',import.meta.url);
fs.rmSync(dest,{recursive:true,force:true});fs.mkdirSync(dest,{recursive:true});fs.cpSync(source,dest,{recursive:true});
const files=[];function walk(dir,prefix=''){for(const entry of fs.readdirSync(dir,{withFileTypes:true})){const name=prefix+entry.name;if(entry.isDirectory())walk(path.join(dir,entry.name),name+'/');else files.push(name);}}
walk(fileURLToPath(dest));fs.writeFileSync(new URL('files.json',dest),JSON.stringify(files));console.log(`Packaged ${files.length} static assets`);
