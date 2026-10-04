package game.agent.demo;

import java.util.*;
import edu.monash.fit2099.engine.positions.*;
import game.environments.*;
import game.environments.structures.*;

/** Original RPG geography in the opt-in peaceful quest mode. Wild spawning is
 * disabled in this mode; the ordinary Application keeps its spawning/battles. */
public final class V1WorldFactory {
    private V1WorldFactory() { }
    public static final class LocalPuddle extends Puddle {
        public LocalPuddle(){super();}
        @Override public void registerInstance(){ }
        @Override protected Puddle createExpansion(){return new LocalPuddle();}
    }
    public static final class LocalLava extends Lava {
        public LocalLava(){super();}
        @Override public void registerInstance(){ }
        @Override protected Lava createExpansion(){return new LocalLava();}
    }
    public static FancyGroundFactory groundFactory(){return new FancyGroundFactory(new Dirt(),new Wall(),new Floor(),new Hay(),new LocalPuddle(),new LocalLava());}
    public static List<String> originalLayout() {
        return Arrays.asList(
            ",,T,,,,,,,,T,,,..............................^^^^^^^^^C^^^^",
            ",,,,,,,,,,,,.......................................^^^C^^^^",
            ",,,,,T,,,............................................^^^^^^",
            ",,,,,,,.................................................^^^",
            ",,,.......................#######........................^^",
            "..........................#_____#.........................^",
            "..........................#_____#..........................",
            "...........~..............###_###...T,.....................",
            "...~~~~~~~~................................................",
            "....~~~~~..................................................",
            "~~~~~~~....................................................",
            "~~W~~~.....................................................",
            "~~~~~W~~~..................................................");
    }
    public static GameMap create(boolean original) {
        if(!original)return new GameMap(new FancyGroundFactory(new Dirt()),Arrays.asList(".........",".........","........."));
        List<String> rows=new ArrayList<>();for(String row:originalLayout())rows.add(row.replace('T',',').replace('C','^').replace('W','~'));
        return new GameMap(groundFactory(),rows);
    }
}
