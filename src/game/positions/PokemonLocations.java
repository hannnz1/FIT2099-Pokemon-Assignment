package game.positions;

import edu.monash.fit2099.engine.items.Item;
import edu.monash.fit2099.engine.positions.GameMap;
import edu.monash.fit2099.engine.positions.Ground;
import edu.monash.fit2099.engine.positions.Location;
import game.time.TimePerception;
import game.time.TimePerceptionManager;
import game.time.TimePeriod;
import java.util.ArrayList;

public class PokemonLocations extends Location {


  /**
   * Constructor.
   * <p>
   * Locations know which map they are part of, and where.
   *
   * @param map the map that contains this location
   * @param x   x coordinate of this location within the map
   * @param y   y coordinate of this location within the map
   */
  public PokemonLocations(GameMap map, int x, int y) {
    super(map, x, y);
  }

//  public void tick() {
//    getGround().tick(this);
//    for(Item item :  new ArrayList<>(getItems())) {
//      item.tick(this);
//    }
//    //        run(this);
//    TimePerceptionManager.getInstance().run();
//    for (TimePerception object : TimePerceptionManager.getInstance().getTimePerceptionList()) {
//      if (object instanceof Ground){
//        object.dayEffect(this);
//      }
//      else {
//        object.dayEffect(this);
//      }
//      object.dayEffect(object isinstance ;
//    }
//
//
//    if (shift == TimePeriod.DAY) {
//      System.out.println(
//          "It is a Day-time (turn " + turn + ")"); //maybe think of how to use display?
//      for (TimePerception object : timePerceptionList) {
//        object.dayEffect(location);
//      }
//    } else if (shift == TimePeriod.NIGHT) {
//      {
//        System.out.println(
//            "It is a Night-time (turn " + turn + ")"); //maybe think of how to use display?
//        for (TimePerception object : timePerceptionList) {
//          object.nightEffect(location);
//        }
//      }
////      turn += 1;
//    }
//  }
}
