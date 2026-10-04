package game.agent.memory;

import java.util.Objects;

/**
 * Java adaptation of Generative Agents ConceptNode event records.
 * Original author: Joon Sung Park. Apache-2.0; see third_party/generative-agents.
 * Changes: immutable event-only record, explicit world/NPC/source, game turns;
 * embeddings, generated importance scores and reflection removed.
 */
public final class NpcMemory {
    public enum Source { SELF_OBSERVATION, NPC_MESSAGE, PLAYER_MESSAGE, TASK_RESULT }
    private final String eventId, worldId, npcId, eventType, subject, content, areaId, sourceNpcId;
    private final int x, y;
    private final long occurredAt;
    private final Source source;
    public NpcMemory(String eventId,String worldId,String npcId,String eventType,String subject,String content,
                     String areaId,int x,int y,long occurredAt,Source source,String sourceNpcId) {
        this.eventId = required(eventId); this.worldId = required(worldId); this.npcId = required(npcId);
        this.eventType = required(eventType); this.subject = required(subject); this.content = required(content);
        this.areaId = required(areaId); this.x = x; this.y = y;
        if (occurredAt < 0) throw new IllegalArgumentException("Negative world turn");
        this.occurredAt = occurredAt; this.source = Objects.requireNonNull(source);
        this.sourceNpcId = source == Source.NPC_MESSAGE ? required(sourceNpcId) : sourceNpcId;
    }
    private static String required(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Missing memory field");
        return value;
    }
    public String getEventId() { return eventId; }
    public String getWorldId() { return worldId; }
    public String getNpcId() { return npcId; }
    public String getEventType() { return eventType; }
    public String getSubject() { return subject; }
    public String getContent() { return content; }
    public String getAreaId() { return areaId; }
    public int getX() { return x; }
    public int getY() { return y; }
    public long getOccurredAt() { return occurredAt; }
    public Source getSource() { return source; }
    public String getSourceNpcId() { return sourceNpcId; }
}
