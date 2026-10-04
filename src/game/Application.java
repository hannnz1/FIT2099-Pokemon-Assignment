package game;

import game.actors.Player;
import game.actors.npc.ProffesorOak;
import game.actors.npc.Shopkeeper;
import game.environments.spawners.Crater;
import game.environments.Dirt;
import game.environments.structures.Floor;
import game.environments.Hay;
import game.environments.Lava;
import game.environments.Puddle;
import game.environments.spawners.Tree;
import game.environments.structures.Wall;
import game.environments.spawners.Waterfall;
import game.positions.PokemonGameMap;
import game.positions.PokemonWorld;
import game.weapons.BackupWeaponsManager;
import java.util.Arrays;
import java.util.List;

import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.displays.Display;
import edu.monash.fit2099.engine.positions.FancyGroundFactory;
import edu.monash.fit2099.engine.positions.GameMap;
import edu.monash.fit2099.engine.positions.World;

/**
 * The main class to start the game.
 * Created by:
 *
 * @author Riordan D. Alfredo
 * Modified by: Ian K. Felix
 */
public class Application {

    public static void main(String[] args) {
        if(args.length==1 && "--agent-v1".equals(args[0])) {
            try {game.agent.web.AgentWebServer.main(new String[0]);}
            catch(Exception failure){throw new IllegalStateException("Agent V1 could not start",failure);}
            return;
        }
        //World controls and manages the main game loop. It contains one or more GameMap.
        //Display is responsible for printing
        //World world = new World(new Display());
        World world = new PokemonWorld(new Display());

        //Class that can create different types of Ground based on the character that represents it.
        FancyGroundFactory groundFactory = new FancyGroundFactory(new Dirt(), new Wall(),
                new Floor(), new Tree(), new Lava(), new Puddle(), new Crater(), new Waterfall(),
                new Hay());


        List<String> map = Arrays.asList(
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

        //GameMap represents the 2D map and helps the World in running the main game loop by managing the entities located in the map.
        //It is defined by several Location components.
        //GameMap gameMap = new GameMap(groundFactory, map);
        GameMap gameMap = new GameMap(groundFactory, map);
        world.addGameMap(gameMap);

        //Add player - Ash
        Player ash = new Player("Ash", '@', 1);
        world.addPlayer(ash, gameMap.at(29, 10));

        //Add first pokemon - Torchic
//        Actor torchic = new Torchic();
//        gameMap.at(33, 10).addActor(torchic);

        Actor proffesoroak = new ProffesorOak();
        gameMap.at(28,5).addActor(proffesoroak);

        Actor shopkeeper = new Shopkeeper();
        gameMap.at(30,5).addActor(shopkeeper);

        BackupWeaponsManager.getInstance();


        world.run();

    }
}

//Original GameMap
//List<String> map = Arrays.asList(
//    ".............................................^^^^^^^^^^^^^^",
//    "............+..................................+...^^^^^^^^",
//    ".....................................................^^^^^^",
//    "........................................................^^^",
//    "..........................#######........................^^",
//    "..........................#_____#............+............^",
//    ".....................+....#_____#..........................",
//    "...+.......~..............###_###..........................",
//    "...~~~~~~~~................................................",
//    "....~~~~~..................................................",
//    "~~~~~~~....................................................",
//    "~~~~~~..+.............................+....................",
//    "~~~~~~~~~..................................................");

//New GameMap
//    List<String> map = Arrays.asList(
//        ",,T,,,,,,,,T,,,..............................^^^^^^^^^C^^^^",
//        ",,,,,,,,,,,,+..................................+...^^^C^^^^",
//        ",,,,,T,,,............................................^^^^^^",
//        ",,,,,,,.................................................^^^",
//        ",,,.......................#######........................^^",
//        "..........................#_____#............+............^",
//        ".....................+....#_____#..........................",
//        "...+.......~..............###_###..........................",
//        "...~~~~~~~~................................................",
//        "....~~~~~..................................................",
//        "~~~~~~~....................................................",
//        "~~W~~~..+.............................+....................",
//        "~~~~~W~~~..................................................");
