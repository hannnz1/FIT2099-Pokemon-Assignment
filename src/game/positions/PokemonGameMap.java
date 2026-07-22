package game.positions;

import edu.monash.fit2099.engine.items.Item;
import edu.monash.fit2099.engine.positions.GameMap;
import edu.monash.fit2099.engine.positions.GroundFactory;
import edu.monash.fit2099.engine.positions.Location;
import game.time.TimePerceptionManager;
import java.util.ArrayList;
import java.util.List;

public class PokemonGameMap extends GameMap {


  public PokemonGameMap(GroundFactory groundFactory, List<String> lines) {
    super(groundFactory, lines);
  }

//  @Override
//  protected Location makeNewLocation(int x, int y) {
//    return new PokemonLocations(this, x, y);
//  }

  @Override
  public void tick() {
    super.tick();
    TimePerceptionManager.getInstance().run();

  }
}
