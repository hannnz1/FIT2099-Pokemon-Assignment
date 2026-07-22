package game.actions;

import edu.monash.fit2099.engine.actions.Action;
import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.positions.GameMap;
import game.items.balls.Pokeball;

public class CaptureAction extends Action {

  private Actor targetActor;

  public CaptureAction(Actor targetActor) {
    this.targetActor = targetActor;
  }

  @Override
  public String execute(Actor actor, GameMap map) {
    map.removeActor(targetActor);
    actor.addItemToInventory(new Pokeball().capturePokemon(targetActor));
    return actor + " captured a "+ targetActor;
  }

  @Override
  public String menuDescription(Actor actor) {
    return actor + " catches a " + targetActor;
  }

}
