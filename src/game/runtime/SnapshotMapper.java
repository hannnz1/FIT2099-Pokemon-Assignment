package game.runtime;
import edu.monash.fit2099.engine.actors.Actor;
import edu.monash.fit2099.engine.items.Item;
import edu.monash.fit2099.engine.positions.*;
import game.actors.pokemon.Pokemon;
import game.conditions.Element;
import game.items.balls.Pokeball;
import game.web.dto.*;
import game.web.dto.SnapshotDto.*;
import java.util.*;

public final class SnapshotMapper {
    public static String kind(Object o) {
        String name=o.getClass().getSimpleName();
        if(name.equals("ProffesorOak")) return "PROFESSOR";
        if(name.equals("Shopkeeper")) return "MERCHANT";
        if(name.equals("GreatBall")) return "GREAT_BALL";
        if(name.equals("MasterBall")) return "MASTER_BALL";
        return name.toUpperCase(Locale.ROOT);
    }
    private static ActorDto actor(Actor a,GameSession s) {
        ActorDto d=new ActorDto();d.id=s.context().id(a);d.kind=kind(a);d.name=GameText.chinese(a.getName());d.hp=a.getHitPoints();d.maxHp=a.getMaxHitPoints();
        for(Element e:a.findCapabilitiesByType(Element.class))d.elements.add(e.name());
        if(a instanceof Pokemon) d.affection=s.context().affection.isRegistered(a)?s.context().affection.getAffectionPoint(a):0;
        if(s.map().contains(a)) {Location l=s.map().locationOf(a);d.x=l.x();d.y=l.y();}return d;
    }
    private static ItemDto item(Item i,GameSession s) { ItemDto d=new ItemDto();d.id=s.context().id(i);d.kind=kind(i);d.name=GameText.chinese(i.toString());if(i instanceof Pokeball && ((Pokeball)i).containsPokemon())d.containedPokemon=actor(((Pokeball)i).getPokemon(),s);return d; }
    public static SnapshotDto map(GameSession s) {
        SnapshotDto d=new SnapshotDto();d.gameId=s.gameId;d.revision=d.turn=s.turn();d.phase=s.ended()?"ENDED":"WAITING_FOR_PLAYER";d.period=s.period();d.nextActionPeriod=GameSession.periodAt(s.turn()+1);d.playerId=s.context().id(s.player());d.map.version=s.mapVersion;d.map.tileSize=s.tileSize;
        for(int y:s.map().getYRange())for(int x:s.map().getXRange()) {
            d.map.width=Math.max(d.map.width,x+1);d.map.height=Math.max(d.map.height,y+1);Location l=s.map().at(x,y);
            d.map.grounds.add(new Tile(x,y,kind(l.getGround())));
            for(Item i:l.getItems()){GroundItem gi=new GroundItem();gi.x=x;gi.y=y;gi.item=item(i,s);d.groundItems.add(gi);}
        }
        for(Actor a:s.activeActors())d.actors.add(actor(a,s));
        for(Item i:s.player().getInventory()) d.inventory.add(item(i,s));
        for(ActionCatalog.Entry e:ActionCatalog.entries(s))d.availableActions.add(e.dto);
        d.log=s.logs();return d;
    }
    public static void diff(SnapshotDto before,SnapshotDto after,List<EventDto> out) {
        Map<String,ActorDto> old=new HashMap<>();for(ActorDto a:before.actors)old.put(a.id,a);
        Set<String> captured=new HashSet<>();for(ItemDto i:after.inventory)if(i.containedPokemon!=null)captured.add(i.containedPokemon.id);
        for(ActorDto a:after.actors) { ActorDto b=old.remove(a.id);
            if(b==null){EventDto event=new EventDto("SPAWN",a.id,null,a.name);event.to=new EventDto.Position(a.x,a.y);out.add(event);}
            else {
                if(a.x!=b.x||a.y!=b.y)out.add(new EventDto("MOVE",a.id,null,"").positions(b.x,b.y,a.x,a.y));
                if(a.hp!=b.hp)out.add(new EventDto("HP_CHANGE",a.id,null,"生命值变化").amount(a.hp-b.hp));
                if(!Objects.equals(a.affection,b.affection))out.add(new EventDto("AFFECTION_CHANGE",a.id,null,"好感变化").amount((a.affection==null?0:a.affection)-(b.affection==null?0:b.affection)));
            }
        }
        for(ActorDto a:old.values()) {
            String kind=captured.contains(a.id)?"CAPTURE":"REMOVE";
            if(out.stream().noneMatch(e->e.kind.equals(kind)&&a.id.equals(e.actorId)))out.add(new EventDto(kind,a.id,kind.equals("CAPTURE")?after.playerId:null,a.name));
            for(EventDto event:out)if(event.kind.equals(kind)&&a.id.equals(event.actorId))event.from=new EventDto.Position(a.x,a.y);
        }
        for(int i=0;i<after.map.grounds.size();i++)if(!before.map.grounds.get(i).kind.equals(after.map.grounds.get(i).kind)){Tile tile=after.map.grounds.get(i);EventDto event=new EventDto("GROUND_CHANGE",null,null,tile.kind);event.to=new EventDto.Position(tile.x,tile.y);out.add(event);}
        if(!before.period.equals(after.period))out.add(new EventDto("PERIOD_CHANGE",null,null,after.period));
        List<String> bi=new ArrayList<>(),ai=new ArrayList<>();for(ItemDto i:before.inventory)bi.add(i.id);for(ItemDto i:after.inventory)ai.add(i.id);
        if(!bi.equals(ai))out.add(new EventDto("INVENTORY_CHANGE",after.playerId,null,"背包已更新").amount(ai.size()-bi.size()));
    }
}
