package game.agent.memory;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Versioned event-only save/load adaptation of Generative Agents associative memory.
 * Apache-2.0; original author Joon Sung Park; see third_party/generative-agents.
 * No Java object deserialization, generated files or embeddings. A memory snapshot
 * is separate from game/quest state and is not a transactional game save.
 */
public final class MemoryStore {
    private static final int MAGIC=0x504d454d, VERSION=1, MAX_EVENTS=100000, MAX_BYTES=64*1024*1024;
    private MemoryStore() { }
    public static void save(Path file,MemoryService service) throws IOException {
        Objects.requireNonNull(file); Objects.requireNonNull(service);
        List<NpcMemory> events=service.snapshot();
        if(events.size()>MAX_EVENTS) throw new IOException("Memory event limit exceeded");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)) {
            out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(events.size());
            for(NpcMemory m:events) {
                out.writeUTF(m.getEventId()); out.writeUTF(m.getWorldId()); out.writeUTF(m.getNpcId());
                out.writeUTF(m.getEventType()); out.writeUTF(m.getSubject()); out.writeUTF(m.getContent()); out.writeUTF(m.getAreaId());
                out.writeInt(m.getX()); out.writeInt(m.getY()); out.writeLong(m.getOccurredAt()); out.writeUTF(m.getSource().name());
                out.writeBoolean(m.getSourceNpcId()!=null); if(m.getSourceNpcId()!=null) out.writeUTF(m.getSourceNpcId());
                if(bytes.size()>MAX_BYTES) throw new IOException("Memory size limit exceeded");
            }
        }
        Path target=file.toAbsolutePath(); Path parent=target.getParent();
        Files.createDirectories(parent); Path temporary=Files.createTempFile(parent,"memory-",".tmp");
        try {
            Files.write(temporary,bytes.toByteArray());
            // Fail rather than downgrade to an unprotected replacement on unsupported filesystems.
            Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    public static MemoryService load(Path file) throws IOException {
        if(Files.size(file)>MAX_BYTES) throw new IOException("Memory size limit exceeded");
        MemoryService service=new MemoryService();
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if(in.readInt()!=MAGIC || in.readInt()!=VERSION) throw new IOException("Unsupported memory format");
            int count=in.readInt(); if(count<0 || count>MAX_EVENTS) throw new IOException("Invalid event count");
            for(int i=0;i<count;i++) {
                String id=in.readUTF(),world=in.readUTF(),npc=in.readUTF(),type=in.readUTF(),subject=in.readUTF(),content=in.readUTF(),area=in.readUTF();
                int x=in.readInt(),y=in.readInt(); long turn=in.readLong(); NpcMemory.Source source=NpcMemory.Source.valueOf(in.readUTF());
                String speaker=in.readBoolean()?in.readUTF():null;
                if(!service.record(new NpcMemory(id,world,npc,type,subject,content,area,x,y,turn,source,speaker)))
                    throw new IOException("Duplicate memory event");
            }
            if(in.read()!=-1) throw new IOException("Unexpected trailing memory data");
        } catch(IllegalArgumentException error) { throw new IOException("Invalid memory event",error); }
        return service;
    }
}
