package game.agent;

import game.agent.llm.Json;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JsonTest {
    @Test void unicodeEscapesAndExactNumbersRoundTrip() {
        Map<String,Object> data=new LinkedHashMap<>(); data.put("text","水跃鱼\n\"\\😀");
        data.put("precise",new BigDecimal("1.00000000000000000001")); data.put("bool",true); data.put("null",null);
        assertEquals(data,Json.read(Json.write(data)));
        assertEquals("树果",Json.read("\"\\u6811\\u679c\""));
    }
    @Test void malformedAndAmbiguousJsonIsRejected() {
        for(String bad:Arrays.asList("{\"a\":1,\"a\":2}","[1,]","01","NaN","1e","\"\\x\"","{} trailing","\"\n\"","\"\\uD800\"","+1"))
            assertThrows(IllegalArgumentException.class,()->Json.read(bad),bad);
        assertThrows(IllegalArgumentException.class,()->Json.write(Double.NaN));
    }
    @Test void depthAndSizeAreBounded() {
        String deep="0"; for(int i=0;i<40;i++) deep="["+deep+"]";
        final String nested=deep; assertThrows(IllegalArgumentException.class,()->Json.read(nested));
        char[] huge=new char[1100000]; Arrays.fill(huge,'x');
        assertThrows(IllegalArgumentException.class,()->Json.read(new String(huge)));
        Map<String,Object> cycle=new HashMap<>(); cycle.put("self",cycle);
        assertThrows(IllegalArgumentException.class,()->Json.write(cycle));
    }
}
