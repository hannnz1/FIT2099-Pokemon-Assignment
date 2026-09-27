/** All first-release assets are original programmatic placeholders, keyed by semantics. */
export interface Asset {label:string;short:string;color:number;shape:'person'|'round'|'leaf'|'drop'|'flame';file?:string;frame?:number;frameWidth?:number;frameHeight?:number;scale:number;anchor:[number,number]}
export const assets:Record<string,Asset>={
 PLAYER:{label:'训练家',short:'你',color:0xeebd62,shape:'person',scale:1,anchor:[.5,1]},
 TREECKO:{label:'木守宫',short:'木',color:0x79bf70,shape:'leaf',scale:1,anchor:[.5,1]},
 MUDKIP:{label:'水跃鱼',short:'水',color:0x70bce6,shape:'drop',scale:1,anchor:[.5,1]},
 TORCHIC:{label:'火稚鸡',short:'火',color:0xf19960,shape:'flame',scale:1,anchor:[.5,1]},
 PROFESSOR:{label:'大木博士',short:'博',color:0xe6e3d4,shape:'person',scale:1,anchor:[.5,1]},
 MERCHANT:{label:'商人',short:'商',color:0xc7a6dc,shape:'person',scale:1,anchor:[.5,1]},
};
export const terrain:Record<string,{color:number;label:string;mark:string}>={DIRT:{color:0x526b49,label:'草土地',mark:''},WALL:{color:0x394646,label:'墙',mark:'▤'},FLOOR:{color:0xb6a780,label:'地板',mark:''},HAY:{color:0x6b8a45,label:'草丛',mark:'〃'},TREE:{color:0x365f3c,label:'树',mark:'♠'},PUDDLE:{color:0x4d889b,label:'水洼',mark:'≈'},WATERFALL:{color:0x8dc5ca,label:'瀑布',mark:'≋'},LAVA:{color:0xa45538,label:'熔岩',mark:'~'},CRATER:{color:0x6e4637,label:'火山口',mark:'◉'}};
export function assetFor(kind:string):Asset{return assets[kind]??{label:`未知 ${kind}`,short:'?',color:0xff00aa,shape:'round',scale:1,anchor:[.5,1]};}
