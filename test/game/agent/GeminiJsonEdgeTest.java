package game.agent;

import game.agent.llm.Json;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeminiJsonEdgeTest {
    @Test void unicodeEscapeDigitsMustBeAsciiJsonHex() {
        assertThrows(IllegalArgumentException.class,()->Json.read("\"\\uＦＦＦＦ\""));
    }
}
