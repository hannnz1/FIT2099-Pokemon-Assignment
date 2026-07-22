package game.positions;

import edu.monash.fit2099.engine.actions.DoNothingAction;
import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.displays.Display;
import edu.monash.fit2099.engine.positions.GameMap;
import edu.monash.fit2099.engine.positions.World;
import game.time.TimePerception;
import game.time.TimePerceptionManager;

public class PokemonWorld extends World {

  /**
   * Constructor.
   *
   * @param display the Display that will display this World.
   */
  public PokemonWorld(Display display) {
    super(display);
  }

  @Override
  public void run() {
    if (player == null)
      throw new IllegalStateException();

    // initialize the last action map to nothing actions;
    for (Actor actor : actorLocations) {
      lastActionMap.put(actor, new DoNothingAction());
    }

    // This loop is basically the whole game
    while (stillRunning()) {
      GameMap playersMap = actorLocations.locationOf(player).map();
      playersMap.draw(display);


      TimePerceptionManager.getInstance().manageTimePeriod(display);
      //display.println("hello");

      // Process all the actors.
      for (Actor actor : actorLocations) {
        if (stillRunning())
          processActorTurn(actor);
      }

      // Tick over all the maps. For the map stuff.
      for (GameMap gameMap : gameMaps) {
        gameMap.tick();
      }

      }
    display.println(endGameMessage());
  }

}
