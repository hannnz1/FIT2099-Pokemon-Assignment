package game.runtime;
import java.util.*;
import edu.monash.fit2099.engine.positions.*;
import game.actors.Player;
import game.environments.*;
import game.environments.spawners.*;
import game.environments.structures.*;

public final class GameFactory {
    public static Ground ground(char c) {
        switch(c) {case '#':return new Wall();case '_':return new Floor();case ',':return new Hay();case 'T':return new Tree();case '~':return new Puddle();case 'W':return new Waterfall();case '^':return new Lava();case 'C':return new Crater();case '.':return new Dirt();default:throw new IllegalArgumentException("Unknown ground "+c);}
    }
    public static GameSession fromRows(String[] rows,int x,int y,RandomSource random) {
        GameMap map=new GameMap(GameFactory::ground,Arrays.asList(rows));map.setContext(new GameContext(random));return new GameSession(map,new Player("Ash",'@',1),x,y);
    }
}
