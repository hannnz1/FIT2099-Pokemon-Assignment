package game.web.dto;
public final class EventDto {
    public String id;
    public int revision, order;
    public final String kind, actorId, targetId, message;
    public Position from, to;
    public Integer amount;
    public static final class Position { public final int x,y;public Position(int x,int y){this.x=x;this.y=y;} }
    public EventDto(String kind,String actorId,String targetId,String message) { this.kind=kind;this.actorId=actorId;this.targetId=targetId;this.message=message; }
    public EventDto positions(int fromX,int fromY,int toX,int toY){from=new Position(fromX,fromY);to=new Position(toX,toY);return this;}
    public EventDto amount(int value){amount=value;return this;}
}
