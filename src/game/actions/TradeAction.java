package game.actions;

import edu.monash.fit2099.engine.actions.Action;
import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.positions.GameMap;
import java.util.Random;

public class TradeAction extends Action {
  private final String direction;
  protected Actor target;
  private final Random rand = new Random();

  public TradeAction(Actor target ,String direction) {
    this.target = target;
    this.direction = direction;
  }

  @Override
  public String execute(Actor actor, GameMap map) {
    return null;
  }

  @Override
  public String menuDescription(Actor actor) {
    return actor + " talks to " + target + " at " + direction;
  }
}
