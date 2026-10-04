package game.agent.memory;

import java.util.*;

/**
 * Java adaptation of Generative Agents perceive.py / associative_memory.py.
 * Original author: Joon Sung Park. Apache-2.0; see third_party/generative-agents.
 * Keeps visibility filtering, nearest-event attention and deduplication. Replaces
 * embeddings with subject/time queries and scopes all data by world and NPC.
 * In-memory, trusted-engine API: never expose record/perceive directly as LLM tools.
 */
public final class MemoryService {
    private final Map<List<String>,LinkedHashMap<String,NpcMemory>> memories = new HashMap<>();
    private List<String> key(String worldId,String npcId) {
        return Arrays.asList(Objects.requireNonNull(worldId),Objects.requireNonNull(npcId));
    }
    /** Existing event IDs are immutable; a new occurrence needs a new ID. */
    public synchronized boolean record(NpcMemory memory) {
        Objects.requireNonNull(memory);
        Map<String,NpcMemory> scoped = memories.computeIfAbsent(key(memory.getWorldId(),memory.getNpcId()),k -> new LinkedHashMap<>());
        return scoped.putIfAbsent(memory.getEventId(),memory) == null;
    }
    /** Candidate events must be built from authoritative observations for this NPC. */
    public synchronized int perceive(String worldId,String npcId,String areaId,int x,int y,long now,
                                     int radius,int attentionLimit,Collection<NpcMemory> candidates) {
        if (now < 0 || radius < 0 || attentionLimit < 0) throw new IllegalArgumentException("Invalid perception limits");
        Objects.requireNonNull(areaId); Objects.requireNonNull(candidates);
        List<NpcMemory> visible = new ArrayList<>();
        for (NpcMemory event : candidates) {
            if (worldId.equals(event.getWorldId()) && npcId.equals(event.getNpcId()) && areaId.equals(event.getAreaId())
                    && event.getSource() == NpcMemory.Source.SELF_OBSERVATION && event.getOccurredAt() <= now
                    && distance(event,x,y) <= radius) visible.add(event);
        }
        visible.sort(Comparator.comparingDouble((NpcMemory m) -> distance(m,x,y)).thenComparing(NpcMemory::getEventId));
        int recorded = 0;
        Set<String> seen = new HashSet<>();
        int attended = 0;
        for (NpcMemory event : visible) {
            if (!seen.add(event.getEventId())) continue;
            if (attended++ >= attentionLimit) break;
            if (record(event)) recorded++;
        }
        return recorded;
    }
    /** Returns historical claims, never live world facts. Newest occurrences first. */
    public synchronized List<NpcMemory> recall(String worldId,String npcId,String subject,long asOfTurn,int limit) {
        if (asOfTurn < 0 || limit < 0) throw new IllegalArgumentException("Invalid retrieval limits");
        Objects.requireNonNull(subject);
        Map<String,NpcMemory> scoped = memories.get(key(worldId,npcId));
        if (scoped == null || limit == 0) return Collections.emptyList();
        List<NpcMemory> result = new ArrayList<>();
        for (NpcMemory memory : scoped.values()) {
            if (subject.equals(memory.getSubject()) && memory.getOccurredAt() <= asOfTurn) result.add(memory);
        }
        result.sort(Comparator.comparingLong(NpcMemory::getOccurredAt).reversed().thenComparing(NpcMemory::getEventId));
        return Collections.unmodifiableList(new ArrayList<>(result.subList(0,Math.min(limit,result.size()))));
    }
    private double distance(NpcMemory memory,int x,int y) {
        return Math.hypot((double)memory.getX()-x,(double)memory.getY()-y);
    }
    /** Trusted persistence API; never expose the cross-NPC snapshot as a model tool. */
    public synchronized List<NpcMemory> snapshot() {
        List<NpcMemory> result=new ArrayList<>();
        for(Map<String,NpcMemory> scoped:memories.values()) result.addAll(scoped.values());
        result.sort(Comparator.comparing(NpcMemory::getWorldId).thenComparing(NpcMemory::getNpcId).thenComparing(NpcMemory::getEventId));
        return Collections.unmodifiableList(result);
    }
    /** Replace a trusted bounded checkpoint, not a public/model memory-writing API. */
    public synchronized void restore(Collection<NpcMemory> events){
        Objects.requireNonNull(events);if(events.size()>64)throw new IllegalArgumentException("MEMORY_LIMIT");
        for(NpcMemory event:events)Objects.requireNonNull(event);
        memories.clear();for(NpcMemory event:events)record(event);
    }
}
