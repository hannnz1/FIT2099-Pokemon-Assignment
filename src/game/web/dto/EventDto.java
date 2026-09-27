package game.web.dto;
public final class EventDto {
    public final String kind, actorId, targetId, text;
    public EventDto(String kind,String actorId,String targetId,String text) { this.kind=kind;this.actorId=actorId;this.targetId=targetId;this.text=text; }
}
