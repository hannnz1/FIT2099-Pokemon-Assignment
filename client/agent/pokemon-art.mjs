// One species mapping shared by portraits and all live Phaser views.
export const pokemonArt=Object.freeze({"TREECKO": 252, "GROVYLE": 253, "SCEPTILE": 254, "TORCHIC": 255, "COMBUSKEN": 256, "BLAZIKEN": 257, "MUDKIP": 258, "MARSHTOMP": 259, "SWAMPERT": 260,"POOCHYENA":261,"MIGHTYENA":262,"ZIGZAGOON":263,"LINOONE":264,"TAILLOW":276,"SWELLOW":277,"WINGULL":278,"PELIPPER":279,"SHROOMISH":285,"BRELOOM":286,"ELECTRIKE":309,"MANECTRIC":310});
function filename(species){if(!Object.hasOwn(pokemonArt,species))throw new Error('UNKNOWN_POKEMON_ART');return species.toLowerCase()+'.png';}
export const portraitUrl=species=>'/rpg/assets/images/pokemon/portraits/'+filename(species);
export const spriteUrl=species=>'/rpg/assets/images/pokemon/sprites/'+filename(species);
export const pokemonNames=Object.freeze({TREECKO:'木守宫',GROVYLE:'森林蜥蜴',SCEPTILE:'蜥蜴王',TORCHIC:'火稚鸡',COMBUSKEN:'力壮鸡',BLAZIKEN:'火焰鸡',MUDKIP:'水跃鱼',MARSHTOMP:'沼跃鱼',SWAMPERT:'巨沼怪',POOCHYENA:"土狼犬",MIGHTYENA:"大狼犬",ZIGZAGOON:"蛇纹熊",LINOONE:"直冲熊",TAILLOW:"傲骨燕",SWELLOW:"大王燕",WINGULL:"长翅鸥",PELIPPER:"大嘴鸥",SHROOMISH:"蘑蘑菇",BRELOOM:"斗笠菇",ELECTRIKE:"落雷兽",MANECTRIC:"雷电兽"});

// Display-only conversion of exported growth EXP; same bounded curve as Java snapshots.
export function growthLevelForExperience(experience){if(!Number.isFinite(experience)||experience<0)return null;let level=1;for(let n=2;n<=40;n++)if(experience>=Math.max(0,Math.floor(6*n*n*n/5-15*n*n+100*n-140)))level=n;return level;}
