package game.agent.persistence;

import game.agent.llm.Json;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WorldStoreTest {
    @TempDir Path directory;
    private Map<String,Object> checkpoint(String text) {
        return Json.object("turn", 4, "memories", Arrays.asList(Json.object("text",text)),
                "trace", Arrays.asList(Json.object("tool","move", "ok",true)));
    }
    @Test void fileRoundTripIsDetachedAndOwnerScoped() throws Exception {
        try(WorldStore store=new FileWorldStore(directory)) {
            assertNull(store.load("owner-one"));
            Map<String,Object> state=checkpoint("树果");
            store.save("owner-one",state); state.put("turn",99);
            assertEquals("4",store.load("owner-one").get("turn").toString());
            assertNull(store.load("owner-two"));
            store.save("owner-two",checkpoint("different"));
            assertTrue(Json.write(store.load("owner-one")).contains("树果"));
        }
        try(WorldStore restored=new FileWorldStore(directory)) {
            assertTrue(Json.write(restored.load("owner-two")).contains("different"));
        }
    }
    @Test void rejectedWritePreservesPreviousCompleteCheckpoint() throws Exception {
        try(WorldStore store=new FileWorldStore(directory)) {
            store.save("one",checkpoint("before"));
            char[] chars=new char[1100000]; Arrays.fill(chars,'x');
            assertThrows(StoreException.class,()->store.save("one",checkpoint(new String(chars))));
            assertTrue(Json.write(store.load("one")).contains("before"));
            Map<String,Object> invalid=checkpoint("after"); invalid.put("apiKey","private");
            assertThrows(StoreException.class,()->store.save("one",invalid));
            assertTrue(Json.write(store.load("one")).contains("before"));
            Map<String,Object> cycle=new HashMap<>(); cycle.put("self",cycle);
            assertThrows(StoreException.class,()->store.save("one",cycle));
        }
    }
    @Test void malformedAndOversizedFilesFailClosed() throws Exception {
        try(WorldStore store=new FileWorldStore(directory)) {
            store.save("one",checkpoint("valid"));
            Path file;
            try(java.util.stream.Stream<Path> files=Files.list(directory)) { file=files.filter(p->p.toString().endsWith(".json")).findFirst().get(); }
            Files.write(file,"{\"version\":999}".getBytes(StandardCharsets.UTF_8));
            assertEquals(StoreException.Code.CORRUPT,assertThrows(StoreException.class,()->store.load("one")).getCode());
            Files.write(file,new byte[1100000]);
            assertEquals(StoreException.Code.TOO_LARGE,assertThrows(StoreException.class,()->store.load("one")).getCode());
        }
    }
    @Test void traversalOwnersAreOpaqueAndCredentialFieldsAreRejectedRecursively() throws Exception {
        try(WorldStore store=new FileWorldStore(directory)) {
            store.save("../../escape",checkpoint("safe"));
            assertTrue(Json.write(store.load("../../escape")).contains("safe"));
            assertThrows(StoreException.class,()->store.save("",checkpoint("x")));
            assertThrows(StoreException.class,()->store.save("\uD800",checkpoint("x")));
            assertThrows(StoreException.class,()->store.save("one",Json.object("trace",Arrays.asList(Json.object("approvalToken","secret")))));
            assertThrows(StoreException.class,()->store.save("one",checkpoint("Bearer abc-secret")));
        }
        try(java.util.stream.Stream<Path> files=Files.list(directory)) { assertEquals(1,files.count()); }
    }
    @Test void closedStoresRejectWork() {
        WorldStore store=new FileWorldStore(directory); store.close();
        assertEquals(StoreException.Code.CLOSED,assertThrows(StoreException.class,()->store.load("one")).getCode());
    }
    @Test void ownerAndVersionTamperingFailsClosed() throws Exception {
        try(WorldStore store=new FileWorldStore(directory)) {
            store.save("one",checkpoint("before")); Path first;
            try(java.util.stream.Stream<Path> stream=Files.list(directory)) { first=stream.findFirst().get(); }
            String original=new String(Files.readAllBytes(first),StandardCharsets.UTF_8);
            store.save("two",checkpoint("other")); Path second;
            try(java.util.stream.Stream<Path> stream=Files.list(directory)) { second=stream.filter(p->!p.equals(first)).findFirst().get(); }
            Files.write(second,original.getBytes(StandardCharsets.UTF_8));
            assertEquals(StoreException.Code.CORRUPT,assertThrows(StoreException.class,()->store.load("two")).getCode());
            Files.write(first,original.replace("\"version\":1","\"version\":\"1\"").getBytes(StandardCharsets.UTF_8));
            assertEquals(StoreException.Code.CORRUPT,assertThrows(StoreException.class,()->store.load("one")).getCode());
        }
    }
}
