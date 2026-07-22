package game.actors;

import edu.monash.fit2099.engine.actions.Action;
import edu.monash.fit2099.engine.actions.ActionList;
import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.displays.Display;
import edu.monash.fit2099.engine.positions.GameMap;
import edu.monash.fit2099.engine.displays.Menu;
import game.conditions.Status;

/**
 * Class representing the Player.
 *
 * Created by:
 * @author Riordan D. Alfredo
 * Modified by:
 *
 */
public class Player extends Actor {

	private final Menu menu = new Menu();

	/**
	 * Constructor.
	 *
	 * @param name        Name to call the player in the UI
	 * @param displayChar Character to represent the player in the UI
	 * @param hitPoints   Player's starting number of hitpoints
	 */
	public Player(String name, char displayChar, int hitPoints) {
		super(name, displayChar, hitPoints);
		this.addCapability(Status.IMMUNE);
		this.addCapability(Status.TRAINER);
		this.addCapability(Status.TRAINER_POKEBALL);
	}


//	R4.2.Ash(Player) Interactions
//	Ash has a list of actions that should become available to the player when it stands adjacent to a Pokemon.
//	The list of actions include “Singing”, “Dancing”, and “Chest Pounding”.
//	If the action is a favourite action of the pokemon(See REQ2), it will raise the affection by 10 points.
//	If the action is not a favourite action, it will reduce the affection by 20 points.

//	R4.3 Capture and summon a Pokemon
//	Not all Pokemon are capturable/catchable (See REQ3).
//	When the player stands adjacent to a catchable Pokemon, the Player will see an action to capture it using a list of available types of Pokeballs.
//	If the player tries to catch a Pokemon with less than the stated affection point conditions(See REQ4.1), it cannot be captured and automatically decrease its affection by 10 points.
//	A captured Pokemon will be stored in the Pokeball and can be summoned back to the world, next to the trainer's location.
	@Override
	public Action playTurn(ActionList actions, Action lastAction, GameMap map, Display display) {

		//Maybe show inventory
		//this.getInventory().

		// Handle multi-turn Actions
		if (lastAction.getNextAction() != null)
			return lastAction.getNextAction();
		// return/print the console menu
		return menu.showMenu(this, actions, display);
	}

	@Override
	public void creatIntrinsicWeapon() {

	}

	@Override
	public char getDisplayChar() {
		return super.getDisplayChar();
	}
}
