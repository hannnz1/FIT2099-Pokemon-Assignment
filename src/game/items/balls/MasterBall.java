package game.items.balls;


import game.conditions.Status;
import game.items.balls.Ball;

//  MasterBall which is illustrated as 0(zero)
//  will behave similar to the Pokeball with the exception of being limited(see REQ6) and more effective
//  at capturing Pokemon(see REQ 4). These items should be able to do everything the Pokeball does besides the exceptions outlined above.
//  The concept of these balls are quite different from pokeballs as they are items that will be in your
//  inventory, as opposed to pokeballs, which does not have to be in your inventory.
public class MasterBall extends Ball {

  public MasterBall() {
    super("MasterBall", '0', true);
    this.addCapability(Status.CAPTURE_POKEMON);

  }



}
