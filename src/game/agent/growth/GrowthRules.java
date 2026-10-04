package game.agent.growth;
import game.agent.llm.Json;
import java.util.*;
/** RSE reference values, fixed IV=15, bounded EV and five Demo natures. Not a complete game simulator. */
public final class GrowthRules {
    public static final int LEVEL_CAP=40;
    public static final class Species {
        public final String id,name,evolution,art;public final int evolutionLevel;public final int[] base;public final String[] types;public final int expYield;public final Map<String,Integer> learn=new LinkedHashMap<>();
        Species(String id,String name,String evolution,String art,int yield,int[] base,String...types){this(id,name,evolution,art,yield,base,evolution==null?0:Arrays.asList("TREECKO","MUDKIP","TORCHIC").contains(id)?16:36,types);}
        Species(String id,String name,String evolution,String art,int yield,int[] base,int evolutionLevel,String...types){this.id=id;this.name=name;this.evolution=evolution;this.evolutionLevel=evolutionLevel;this.art=art;this.expYield=yield;this.base=base.clone();this.types=types.clone();}
        Species moves(Object...items){for(int i=0;i<items.length;i+=2)learn.put((String)items[i],(Integer)items[i+1]);return this;}
    }
    public static final class Move {
        public final String id,name,type,effect;public final int power,accuracy,pp,priority;
        Move(String id,String name,String type,int power,int accuracy,int pp,int priority,String effect){this.id=id;this.name=name;this.type=type;this.power=power;this.accuracy=accuracy;this.pp=pp;this.priority=priority;this.effect=effect;}
        public Map<String,Object> view(){return Json.object("id",id,"name",name,"type",type,"power",power,"accuracy",accuracy,"maxPp",pp,"priority",priority,"effect",effect);}
    }
    private static final Map<String,Species> SPECIES=new LinkedHashMap<>();private static final Map<String,Move> MOVES=new LinkedHashMap<>();
    static {
        add(new Species("TREECKO","木守宫","GROVYLE","treecko.png",65,new int[]{40,45,35,65,55,70},"GRASS").moves("POUND",1,"LEER",1,"ABSORB",6,"QUICK_ATTACK",11,"PURSUIT",16,"SCREECH",21,"MEGA_DRAIN",26,"AGILITY",31,"SLAM",36));
        add(new Species("GROVYLE","森林蜥蜴","SCEPTILE","grovyle.png",141,new int[]{50,65,45,85,65,95},"GRASS").moves("POUND",1,"LEER",1,"ABSORB",6,"QUICK_ATTACK",11,"FURY_CUTTER",16,"PURSUIT",17,"SCREECH",23,"LEAF_BLADE",29,"AGILITY",35));
        add(new Species("MUDKIP","水跃鱼","MARSHTOMP","mudkip.png",65,new int[]{50,70,50,50,50,40},"WATER").moves("TACKLE",1,"GROWL",1,"MUD_SLAP",6,"WATER_GUN",10,"TAKE_DOWN",28));
        add(new Species("MARSHTOMP","沼跃鱼","SWAMPERT","marshtomp.png",143,new int[]{70,85,70,60,70,50},"WATER","GROUND").moves("TACKLE",1,"GROWL",1,"MUD_SLAP",6,"WATER_GUN",10,"MUD_SHOT",16,"TAKE_DOWN",31,"MUDDY_WATER",37));
        add(new Species("TORCHIC","火稚鸡","COMBUSKEN","torchic.png",65,new int[]{45,60,40,70,50,45},"FIRE").moves("SCRATCH",1,"GROWL",1,"EMBER",10,"PECK",16,"SAND_ATTACK",19,"QUICK_ATTACK",28,"SLASH",34));
        add(new Species("COMBUSKEN","力壮鸡","BLAZIKEN","combusken.png",142,new int[]{60,85,60,85,60,55},"FIRE","FIGHTING").moves("SCRATCH",1,"GROWL",1,"EMBER",13,"DOUBLE_KICK",16,"PECK",17,"SAND_ATTACK",21,"BULK_UP",28,"QUICK_ATTACK",32,"SLASH",39));
        add(new Species("SCEPTILE","蜥蜴王",null,"sceptile.png",208,new int[]{70,85,65,105,85,120},"GRASS").moves("POUND",1,"LEER",1,"ABSORB",6,"QUICK_ATTACK",11,"FURY_CUTTER",16,"PURSUIT",17,"SCREECH",23,"LEAF_BLADE",29,"AGILITY",35));
        add(new Species("SWAMPERT","巨沼怪",null,"swampert.png",210,new int[]{100,110,90,85,90,60},"WATER","GROUND").moves("TACKLE",1,"GROWL",1,"MUD_SLAP",6,"WATER_GUN",10,"MUD_SHOT",16,"TAKE_DOWN",31,"MUDDY_WATER",39));
        add(new Species("BLAZIKEN","火焰鸡",null,"blaziken.png",209,new int[]{80,120,70,110,70,80},"FIRE","FIGHTING").moves("SCRATCH",1,"GROWL",1,"EMBER",13,"DOUBLE_KICK",16,"PECK",17,"SAND_ATTACK",21,"BULK_UP",28,"QUICK_ATTACK",32,"FIRE_PUNCH",1,"BLAZE_KICK",36));
        add(new Species("POOCHYENA","土狼犬","MIGHTYENA","poochyena.png",70,new int[]{35,55,35,30,30,35},18,"DARK").moves("TACKLE",1,"GROWL",1,"SAND_ATTACK",5,"BITE",9,"POISON_FANG",20));
        add(new Species("MIGHTYENA","大狼犬",null,"mightyena.png",140,new int[]{70,90,70,60,60,70},0,"DARK").moves("TACKLE",1,"GROWL",1,"SAND_ATTACK",5,"BITE",9,"POISON_FANG",20));
        add(new Species("ZIGZAGOON","蛇纹熊","LINOONE","zigzagoon.png",70,new int[]{38,30,41,30,41,60},20,"NORMAL").moves("TACKLE",1,"GROWL",1,"SAND_ATTACK",5,"QUICK_ATTACK",9,"SLASH",20));
        add(new Species("LINOONE","直冲熊",null,"linoone.png",140,new int[]{78,70,61,50,61,100},0,"NORMAL").moves("TACKLE",1,"GROWL",1,"SAND_ATTACK",5,"QUICK_ATTACK",9,"SLASH",20));
        add(new Species("TAILLOW","傲骨燕","SWELLOW","taillow.png",70,new int[]{40,55,30,30,30,85},22,"NORMAL","FLYING").moves("PECK",1,"GROWL",1,"QUICK_ATTACK",5,"WING_ATTACK",13,"AGILITY",25));
        add(new Species("SWELLOW","大王燕",null,"swellow.png",140,new int[]{60,85,60,75,50,125},0,"NORMAL","FLYING").moves("PECK",1,"GROWL",1,"QUICK_ATTACK",5,"WING_ATTACK",13,"AGILITY",25));
        add(new Species("WINGULL","长翅鸥","PELIPPER","wingull.png",70,new int[]{40,30,30,55,30,85},25,"WATER","FLYING").moves("WATER_GUN",1,"GROWL",1,"SUPERSONIC",7,"WING_ATTACK",13,"MUDDY_WATER",30));
        add(new Species("PELIPPER","大嘴鸥",null,"pelipper.png",140,new int[]{60,50,100,95,70,65},0,"WATER","FLYING").moves("WATER_GUN",1,"GROWL",1,"SUPERSONIC",7,"WING_ATTACK",13,"MUDDY_WATER",30));
        add(new Species("SHROOMISH","蘑蘑菇","BRELOOM","shroomish.png",70,new int[]{60,40,60,40,60,35},23,"GRASS").moves("ABSORB",1,"TACKLE",1,"STUN_SPORE",7,"SLEEP_POWDER",10,"MEGA_DRAIN",16,"DOUBLE_KICK",23));
        add(new Species("BRELOOM","斗笠菇",null,"breloom.png",140,new int[]{60,130,80,60,60,70},0,"GRASS","FIGHTING").moves("ABSORB",1,"TACKLE",1,"STUN_SPORE",7,"SLEEP_POWDER",10,"MEGA_DRAIN",16,"DOUBLE_KICK",23));
        add(new Species("ELECTRIKE","落雷兽","MANECTRIC","electrike.png",70,new int[]{40,45,40,65,40,65},26,"ELECTRIC").moves("TACKLE",1,"GROWL",1,"THUNDER_WAVE",7,"SPARK",10,"BITE",20,"THUNDERBOLT",30));
        add(new Species("MANECTRIC","雷电兽",null,"manectric.png",140,new int[]{70,75,60,105,60,105},0,"ELECTRIC").moves("TACKLE",1,"GROWL",1,"THUNDER_WAVE",7,"SPARK",10,"BITE",20,"THUNDERBOLT",30));
        move("BITE","咬住","DARK",60,100,25,0,"");move("WING_ATTACK","翅膀攻击","FLYING",60,100,35,0,"");move("POISON_FANG","毒牙","POISON",50,100,15,0,"POISON_30");move("SUPERSONIC","超音波","NORMAL",0,55,20,0,"CONFUSION");move("STUN_SPORE","麻痹粉","GRASS",0,75,30,0,"PARALYSIS");move("SLEEP_POWDER","睡眠粉","GRASS",0,75,15,0,"SLEEP");move("THUNDER_WAVE","电磁波","ELECTRIC",0,100,20,0,"PARALYSIS");move("SPARK","电光","ELECTRIC",65,100,20,0,"PARALYSIS_30");move("THUNDERBOLT","十万伏特","ELECTRIC",95,100,15,0,"PARALYSIS_10");
        move("SCREECH","刺耳声","NORMAL",0,85,40,0,"DEF_DOWN_2");move("MEGA_DRAIN","超级吸取","GRASS",40,100,15,0,"DRAIN");move("AGILITY","高速移动","PSYCHIC",0,0,30,0,"SPEED_UP_2");move("SLAM","摔打","NORMAL",80,75,20,0,"");move("LEAF_BLADE","叶刃","GRASS",70,100,15,0,"CRITICAL");
        move("SAND_ATTACK","泼沙","GROUND",0,100,15,0,"ACCURACY_DOWN");move("BULK_UP","健美","FIGHTING",0,0,20,0,"BULK_UP");move("SLASH","劈开","NORMAL",70,100,20,0,"CRITICAL");move("FIRE_PUNCH","火焰拳","FIRE",75,100,15,0,"BURN");move("BLAZE_KICK","火焰踢","FIRE",85,90,10,0,"CRITICAL_BURN");move("TAKE_DOWN","猛撞","NORMAL",90,85,20,0,"RECOIL");move("MUDDY_WATER","浊流","WATER",95,85,10,0,"ACCURACY_DOWN_30");
        move("POUND","拍击","NORMAL",40,100,35,0,"");move("TACKLE","撞击","NORMAL",35,95,35,0,"");move("SCRATCH","抓","NORMAL",40,100,35,0,"");move("LEER","瞪眼","NORMAL",0,100,30,0,"DEF_DOWN");move("GROWL","叫声","NORMAL",0,100,40,0,"ATK_DOWN");
        move("ABSORB","吸取","GRASS",20,100,20,0,"DRAIN");move("QUICK_ATTACK","电光一闪","NORMAL",40,100,30,1,"");move("PURSUIT","追打","DARK",40,100,20,0,"");move("FURY_CUTTER","连斩","BUG",10,95,20,0,"CHAIN");
        move("MUD_SLAP","掷泥","GROUND",20,100,10,0,"ACCURACY_DOWN");move("WATER_GUN","水枪","WATER",40,100,25,0,"");move("MUD_SHOT","泥巴射击","GROUND",55,95,15,0,"SPEED_DOWN");move("EMBER","火花","FIRE",40,100,25,0,"BURN");move("PECK","啄","FLYING",35,100,35,0,"");move("DOUBLE_KICK","二连踢","FIGHTING",30,100,30,0,"DOUBLE");
    }
    static final Move STRUGGLE=new Move("STRUGGLE","挣扎","NORMAL",50,100,1,0,"RECOIL");
    private GrowthRules(){}
    private static void add(Species s){SPECIES.put(s.id,s);}private static void move(String id,String name,String type,int p,int a,int pp,int priority,String effect){MOVES.put(id,new Move(id,name,type,p,a,pp,priority,effect));}
    public static Species species(String id){Species s=SPECIES.get(id);if(s==null)throw new IllegalArgumentException("UNKNOWN_SPECIES");return s;}
    public static Move move(String id){Move m=MOVES.get(id);if(m==null)throw new IllegalArgumentException("UNKNOWN_MOVE");return m;}
    public static Set<String> speciesIds(){return Collections.unmodifiableSet(SPECIES.keySet());}
    public static final List<String> NATURES=Collections.unmodifiableList(Arrays.asList("HARDY","ADAMANT","MODEST","JOLLY","BOLD"));
    public static int[] stats(String id,int level,String nature,int[] ev){int[] result=stats(id,level);if(!NATURES.contains(nature)||ev.length!=6)throw new IllegalArgumentException("INVALID_NATURE_EV");for(int i=0;i<6;i++){result[i]=((2*species(id).base[i]+15+ev[i]/4)*level)/100+(i==0?level+10:5);if(i>0){int plus="ADAMANT".equals(nature)?1:"MODEST".equals(nature)?3:"JOLLY".equals(nature)?5:"BOLD".equals(nature)?2:0;int minus="ADAMANT".equals(nature)||"JOLLY".equals(nature)?3:"MODEST".equals(nature)||"BOLD".equals(nature)?1:0;if(i==plus)result[i]=result[i]*110/100;if(i==minus)result[i]=result[i]*90/100;}}return result;}
    public static String ability(String id){if(Arrays.asList("TREECKO","GROVYLE","SCEPTILE").contains(id))return "OVERGROW";if(Arrays.asList("TORCHIC","COMBUSKEN","BLAZIKEN").contains(id))return "BLAZE";if(Arrays.asList("MUDKIP","MARSHTOMP","SWAMPERT").contains(id))return "TORRENT";if(Arrays.asList("TAILLOW","SWELLOW","WINGULL","PELIPPER").contains(id))return "KEEN_EYE";if(Arrays.asList("ELECTRIKE","MANECTRIC").contains(id))return "STATIC";return "NONE";}
    /** Demo EV yield: one point in the defeated species' highest base stat (ties use first). */
    public static int evIndex(String id){int[] base=species(id).base;int best=0;for(int i=1;i<6;i++)if(base[i]>base[best])best=i;return best;}
    public static String predecessor(String id){for(Species s:SPECIES.values())if(id.equals(s.evolution))return s.id;return null;}
    public static boolean starter(String id){return Arrays.asList("TREECKO","MUDKIP","TORCHIC").contains(id);}
    public static int experienceAt(int level){if(level<1||level>LEVEL_CAP)throw new IllegalArgumentException("INVALID_LEVEL");return Math.max(0,(6*level*level*level)/5-15*level*level+100*level-140);}
    public static int levelAt(int exp){if(exp<0||exp>experienceAt(LEVEL_CAP))throw new IllegalArgumentException("INVALID_EXPERIENCE");int level=1;while(level<LEVEL_CAP&&exp>=experienceAt(level+1))level++;return level;}
    public static int[] stats(String id,int level){if(level<1||level>LEVEL_CAP)throw new IllegalArgumentException("INVALID_LEVEL");int[] b=species(id).base,s=new int[6];for(int i=0;i<6;i++)s[i]=((2*b[i]+15)*level)/100+(i==0?level+10:5);return s;}
    public static int reward(String species,int level){return (GrowthRules.species(species).expYield*level/7)*3;}
    public static boolean special(String type){return Arrays.asList("GRASS","FIRE","WATER","DARK","ELECTRIC","ICE","PSYCHIC","DRAGON").contains(type);}
    public static double typeMultiplier(String attack,String[] defenders){String immune="",strong="",weak="";switch(attack){
        case "NORMAL":immune="GHOST";strong="";weak="ROCK STEEL";break;
        case "FIRE":immune="";strong="GRASS ICE BUG STEEL";weak="FIRE WATER ROCK DRAGON";break;
        case "WATER":immune="";strong="FIRE GROUND ROCK";weak="WATER GRASS DRAGON";break;
        case "GRASS":immune="";strong="WATER GROUND ROCK";weak="FIRE GRASS POISON FLYING BUG DRAGON STEEL";break;
        case "ELECTRIC":immune="GROUND";strong="WATER FLYING";weak="ELECTRIC GRASS DRAGON";break;
        case "ICE":immune="";strong="GRASS GROUND FLYING DRAGON";weak="FIRE WATER ICE STEEL";break;
        case "FIGHTING":immune="GHOST";strong="NORMAL ICE ROCK DARK STEEL";weak="POISON FLYING PSYCHIC BUG";break;
        case "POISON":immune="STEEL";strong="GRASS";weak="POISON GROUND ROCK GHOST";break;
        case "GROUND":immune="FLYING";strong="FIRE ELECTRIC POISON ROCK STEEL";weak="GRASS BUG";break;
        case "FLYING":immune="";strong="GRASS FIGHTING BUG";weak="ELECTRIC ROCK STEEL";break;
        case "PSYCHIC":immune="DARK";strong="FIGHTING POISON";weak="PSYCHIC STEEL";break;
        case "BUG":immune="";strong="GRASS PSYCHIC DARK";weak="FIRE FIGHTING POISON FLYING GHOST STEEL";break;
        case "ROCK":immune="";strong="FIRE ICE FLYING BUG";weak="FIGHTING GROUND STEEL";break;
        case "GHOST":immune="NORMAL";strong="PSYCHIC GHOST";weak="DARK STEEL";break;
        case "DRAGON":immune="";strong="DRAGON";weak="STEEL";break;
        case "DARK":immune="";strong="PSYCHIC GHOST";weak="FIGHTING DARK STEEL";break;
        case "STEEL":immune="";strong="ICE ROCK";weak="FIRE WATER ELECTRIC STEEL";break;
        default:throw new IllegalArgumentException("UNKNOWN_TYPE");}
        double v=1;for(String d:defenders){if(Arrays.asList(immune.split(" ")).contains(d))return 0;if(Arrays.asList(strong.split(" ")).contains(d))v*=2;if(Arrays.asList(weak.split(" ")).contains(d))v*=.5;}return v;}
    public static double stage(int n){n=Math.max(-6,Math.min(6,n));return n>=0?(2.0+n)/2:2.0/(2-n);}
    public static int damage(GrowthPokemon a,GrowthPokemon d,Move move,int random,int power){return damage(a,d,move,random,power,false);}
    public static int damage(GrowthPokemon a,GrowthPokemon d,Move move,int random,int power,boolean critical){
        if(move.power==0)return 0;int[] as=a.stats(),ds=d.stats();boolean special=special(move.type);
        int attackStage=special?0:a.attackStage,defenseStage=special?0:d.defenseStage;
        if(critical){attackStage=Math.max(0,attackStage);defenseStage=Math.min(0,defenseStage);}
        double atk=as[special?3:1]*stage(attackStage),def=ds[special?4:2]*stage(defenseStage);if(!special&&a.burned)atk*=.5;
        if(a.getHitPoints()*3<=a.maxHp()&&ability(a.species).equals("OVERGROW")&&move.type.equals("GRASS")||a.getHitPoints()*3<=a.maxHp()&&ability(a.species).equals("BLAZE")&&move.type.equals("FIRE")||a.getHitPoints()*3<=a.maxHp()&&ability(a.species).equals("TORRENT")&&move.type.equals("WATER"))atk*=1.5;
        double type=typeMultiplier(move.type,species(d.species).types);if(type==0)return 0;double stab=Arrays.asList(species(a.species).types).contains(move.type)?1.5:1;
        return Math.max(1,(int)Math.floor(((int)Math.floor(((2*a.level()/5+2)*power*atk/def)/50)+2)*(critical?2:1)*stab*type*(85+Math.floorMod(random,16))/100));
    }
}
