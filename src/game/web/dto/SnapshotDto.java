package game.web.dto;
import java.util.*;
public final class SnapshotDto {
    public String gameId, phase, period, nextActionPeriod, playerId;
    public int revision, turn;
    public MapDto map=new MapDto();
    public List<ActorDto> actors=new ArrayList<>();
    public List<GroundItem> groundItems=new ArrayList<>();
    public List<ItemDto> inventory=new ArrayList<>();
    public List<ActionDto> availableActions=new ArrayList<>();
    public List<LogEntry> log=new ArrayList<>();
    public static final class MapDto { public String id="demo",version;public int width,height,tileSize;public List<Tile> grounds=new ArrayList<>(); }
    public static final class Tile { public int x,y;public String kind;public Tile(int x,int y,String kind){this.x=x;this.y=y;this.kind=kind;} }
    public static final class ActorDto { public String id,kind,name;public int x,y,hp,maxHp;public Integer affection;public List<String> elements=new ArrayList<>(); }
    public static final class ItemDto { public String id,kind,name;public ActorDto containedPokemon; }
    public static final class GroundItem { public int x,y;public ItemDto item; }
    public static final class ActionDto { public String id,kind,label,targetId,direction,reason;public boolean enabled=true; }
    public static final class LogEntry { public final String id,text;public final int turn;public LogEntry(String id,int turn,String text){this.id=id;this.turn=turn;this.text=text;} }
}
