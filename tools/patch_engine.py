from pathlib import Path
root=Path(__file__).resolve().parents[1]
def edit(path,old,new):
 p=root/path;s=p.read_text();assert old in s,(path,old);p.write_text(s.replace(old,new),encoding='utf-8')
edit('src/edu/monash/fit2099/engine/actors/Actor.java','hitPoints -= points;','hitPoints = (int) Math.max(0L, (long) hitPoints - Math.max(0, points));')
edit('src/edu/monash/fit2099/engine/actors/Actor.java','hitPoints += points;\n\t\thitPoints = Math.min(hitPoints, maxHitPoints);','hitPoints = (int) Math.min(maxHitPoints, (long) hitPoints + Math.max(0, points));')
edit('src/edu/monash/fit2099/engine/actors/Actor.java','public abstract class Actor implements Capable, Printable {','public abstract class Actor implements Capable, Printable {\n    public final int getHitPoints() { return hitPoints; }\n    public final int getMaxHitPoints() { return maxHitPoints; }\n    public final String getName() { return name; }')
edit('src/game/conditions/AffectionManager.java','private AffectionManager()','public AffectionManager()')
edit('src/game/conditions/AffectionManager.java','public void reset() {','public void forget(Actor actor) { affectionPoints.remove(actor); }\n\n    public void reset() {')
edit('src/edu/monash/fit2099/engine/positions/GameMap.java','public class GameMap','public class GameMap')
p=root/'src/edu/monash/fit2099/engine/positions/GameMap.java';s=p.read_text();pos=s.index('{',s.index('public class GameMap'));s=s[:pos+1]+'\n    private game.runtime.GameContext context = new game.runtime.GameContext();\n    public game.runtime.GameContext context() { return context; }\n    public void setContext(game.runtime.GameContext value) { context = value; }\n'+s[pos+1:];s=s.replace('actorLocations.add(actor, location);','actorLocations.add(actor, location);\n        context.id(actor);');p.write_text(s)
edit('src/game/actions/AffectionAction.java','AffectionManager manager = AffectionManager.getInstance();\n        manager.registerTrainer(actor);','AffectionManager manager = map == null ? AffectionManager.getInstance() : map.context().affection;\n        manager.registerTrainer(actor);')
for name in ['Treecko','Mudkip','Torchic']:
 path=f'src/game/actors/pokemon/{name}.java';p=root/path;s=p.read_text().replace('this.registerInstance();','// Active map traversal owns time effects; constructors have no global registration.');s=s.replace('Location here = map.locationOf(this);','if (!isConscious() || !map.contains(this)) return new DoNothingAction();\n    Location here = map.locationOf(this);');p.write_text(s)
for path in ['src/game/environments/Lava.java','src/game/environments/Puddle.java','src/game/environments/spawners/Tree.java','src/game/environments/spawners/Waterfall.java','src/game/environments/spawners/Crater.java']:
 p=root/path;s=p.read_text().replace('this.registerInstance();','// Time effects are scoped to active map tiles.');s=s.replace('Random rand = new Random();','game.runtime.RandomSource rand = location.map().context().random;');s=s.replace('    game.runtime.RandomSource rand = location.map().context().random;\n    private Location location;','    private Location location;');s=s.replace('if (location != null) {','if (location != null && location.getGround() == this) {');p.write_text(s)
edit('src/game/behaviours/WanderBehaviour.java','random.nextInt(actions.size())','map.context().random.nextInt(actions.size())')
edit('src/game/actions/AttackAction.java','rand.nextInt(100)','map.context().random.nextInt(100)')
edit('src/game/actions/AttackAction.java','return actor + " misses " + target + ".";','map.context().event("MISS", actor, target, "攻击落空");\n            return actor + " misses " + target + ".";')
edit('src/game/actions/AttackAction.java','target.hurt(damage);','target.hurt(damage);\n        map.context().event("ATTACK", actor, target, "命中：" + damage);')
# Expose semantic targets, never parse menu strings for the web protocol.
for path,body in {
 'src/game/actions/AffectionAction.java':'public Actor getTarget() { return target; }',
 'src/game/actions/CaptureAction.java':'public Actor getTarget() { return targetActor; }',
 'src/game/actions/AttackAction.java':'public Actor getTarget() { return target; }',
 'src/game/actions/TalkAction.java':'public Actor getTarget() { return target; }',
 'src/game/actions/TradeAction.java':'public Actor getTarget() { return shopkeeper; }\n    public TradeOffer getOffer() { return offer; }',
 'src/edu/monash/fit2099/engine/items/PickUpItemAction.java':'public Item getItem() { return item; }',
 'src/edu/monash/fit2099/engine/items/DropItemAction.java':'public Item getItem() { return item; }',
 'src/edu/monash/fit2099/engine/actions/MoveActorAction.java':'public String getDirection() { return direction; }'
}.items():
 p=root/path;s=p.read_text();pos=s.index('{',s.index('public ',s.index('class ') - 30 if s.index('class ') > 30 else 0)) if False else s.index('{',s.index('class '));s=s[:pos+1]+'\n    '+body+'\n'+s[pos+1:];p.write_text(s)
