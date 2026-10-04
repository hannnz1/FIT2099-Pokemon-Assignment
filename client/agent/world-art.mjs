// Presentation-only names and assets. No gameplay rules or snapshot mutation.
const terrain='assets/images/monster-tamer/map/main_1_level_background.png';
const human='assets/images/axulart/character/custom.png';
const atlas=(label,frame,crop)=>({label,kind:'atlas',path:terrain,texture:'terrain',frame,crop,sheetWidth:2560,sheetHeight:5568,provenance:'existing-monster-tamer-map'});
const person=(label,frame,tint)=>({label,kind:'sprite',path:human,texture:'human',frame,frameWidth:64,frameHeight:88,sheetWidth:256,sheetHeight:264,tint,provenance:'existing-axulart-role-adaptation'});
const portrait=(label,id)=>({label,kind:'portrait',path:'assets/ui/npcs/'+id+'.svg',texture:'npc-'+id,width:64,height:88,provenance:'original-demo-npc'});
const symbol=(label,body)=>({label,kind:'symbol',texture:'world-'+label,svg:`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" shape-rendering="crispEdges">${body}</svg>`,provenance:'original-ui-symbol'});
const berry='<path fill="#3f773c" d="M29 16h7v11h-7zM34 12h16v8H34z"/><path fill="#e88a66" d="M18 26h28v6h6v19h-6v6H18v-6h-6V32h6z"/><path fill="#ffd7a3" d="M20 31h7v7h-7z"/>';
export const worldArt=Object.freeze(Object.fromEntries(Object.entries({
 professor:portrait('博士','professor'),
 merchant:portrait('商人','merchant'),
 player:person('玩家',7,0xffffff),
 market:atlas('商店','world-market',{x:1472,y:1632,width:448,height:384}),
 laboratory:atlas('研究室','world-laboratory',{x:640,y:1344,width:256,height:192}),
 alternative:atlas('林间','world-alternative',{x:1280,y:4992,width:128,height:128}),
 orchard:symbol('果园','<path fill="#765d42" d="M28 34h8v24h-8z"/><path fill="#518f44" d="M16 10h32v6h8v24h-8v6H16v-6H8V16h8z"/><path fill="#e89767" d="M15 22h8v8h-8zM39 18h8v8h-8zM30 33h8v8h-8z"/>'),
 berry:symbol('树果',berry),
 recovery:symbol('恢复点','<path fill="#f1e6ba" d="M32 8 58 54H6z"/><path fill="#78634a" d="M32 30 46 54H18z"/><path fill="#75d8d0" d="M46 8h10v6h6v10h-6v6H46v-6h-6V14h6z"/>'),
 gate:symbol('入口','<path fill="#c39a5e" d="M8 12h48v8H8zM8 20h8v38H8zM48 20h8v38h-8z"/><path fill="#ffe3a2" d="M24 30h14v-8l14 14-14 14v-8H24z"/>')
}).map(([id,art])=>[id,Object.freeze(art.crop?{...art,crop:Object.freeze(art.crop)}:art.kind==='symbol'?{...art,texture:'world-'+id}:art)])));

export function getWorldArt(id){if(!Object.hasOwn(worldArt,id))throw new Error('UNKNOWN_WORLD_ART');return worldArt[id];}
export function worldArtUrl(id){const art=getWorldArt(id);return art.kind==='symbol'?worldArtDataUrl(id):'/rpg/'+art.path;}
export function worldArtDataUrl(id){const art=getWorldArt(id);if(art.kind!=='symbol')throw new Error('NOT_WORLD_SYMBOL');return 'data:image/svg+xml,'+encodeURIComponent(art.svg);}

// CSS backgrounds isolate original sheet pixels for DOM cards; use tint in Phaser.
export function worldArtDomStyle(id,size=48){
 if(!Number.isFinite(size)||size<=0)throw new Error('INVALID_ART_SIZE');
 const art=getWorldArt(id),style={display:'inline-block',width:size+'px',imageRendering:'pixelated',backgroundRepeat:'no-repeat'};
 if(art.kind==='portrait')return {...style,height:(size*art.height/art.width)+'px',backgroundImage:`url("${worldArtUrl(id)}")`,backgroundSize:'100% 100%'};
 if(art.kind==='symbol')return {...style,height:size+'px',backgroundImage:`url("${worldArtDataUrl(id)}")`,backgroundSize:'100% 100%'};
 const r=art.kind==='sprite'?{x:art.frame%4*art.frameWidth,y:Math.floor(art.frame/4)*art.frameHeight,width:art.frameWidth,height:art.frameHeight}:art.crop;
 const scale=size/r.width;
 return {...style,height:(r.height*scale)+'px',backgroundImage:`url("${worldArtUrl(id)}")`,backgroundSize:(art.sheetWidth*scale)+'px '+(art.sheetHeight*scale)+'px',backgroundPosition:(-r.x*scale)+'px '+(-r.y*scale)+'px'};
}
