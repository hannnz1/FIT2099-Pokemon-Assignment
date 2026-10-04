package game.agent;

import game.agent.memory.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class MemoryStoreTest {
    @TempDir Path directory;
    private NpcMemory memory(String npc) {
        return new NpcMemory("event","world",npc,"berry","berry","看到了树果","area",1,2,3,NpcMemory.Source.NPC_MESSAGE,"source");
    }
    @Test void roundTripPreservesSourceScopeAndUnicode() throws Exception {
        MemoryService service=new MemoryService(); service.record(memory("a")); service.record(memory("b"));
        Path file=directory.resolve("memory.bin"); MemoryStore.save(file,service);
        MemoryService loaded=MemoryStore.load(file);
        assertEquals(1,loaded.recall("world","a","berry",3,10).size());
        NpcMemory m=loaded.recall("world","b","berry",3,10).get(0);
        assertEquals("看到了树果",m.getContent()); assertEquals("source",m.getSourceNpcId());
        assertFalse(loaded.record(memory("a")));
        MemoryStore.save(file,loaded); assertEquals(2,MemoryStore.load(file).snapshot().size());
    }
    @Test void corruptTruncatedAndUnknownVersionAreRejected() throws Exception {
        Path file=directory.resolve("bad.bin"); Files.write(file,new byte[]{1,2,3});
        assertThrows(IOException.class,()->MemoryStore.load(file));
        MemoryService service=new MemoryService(); service.record(memory("a")); MemoryStore.save(file,service);
        byte[] valid=Files.readAllBytes(file); Files.write(file,java.util.Arrays.copyOf(valid,valid.length-1));
        assertThrows(IOException.class,()->MemoryStore.load(file));
        valid[7]=99; Files.write(file,valid); assertThrows(IOException.class,()->MemoryStore.load(file));
    }
    @Test void failedSavePreservesExistingMemoryFile() throws Exception {
        Path file=directory.resolve("memory.bin"); MemoryService good=new MemoryService(); good.record(memory("a"));
        MemoryStore.save(file,good); MemoryService invalid=new MemoryService();
        char[] tooLong=new char[70000]; java.util.Arrays.fill(tooLong,'x');
        invalid.record(new NpcMemory("large","w","a","SEEN","berry",new String(tooLong),"area",0,0,0,NpcMemory.Source.SELF_OBSERVATION,null));
        assertThrows(IOException.class,()->MemoryStore.save(file,invalid));
        assertEquals("看到了树果",MemoryStore.load(file).recall("world","a","berry",3,1).get(0).getContent());
    }
}
